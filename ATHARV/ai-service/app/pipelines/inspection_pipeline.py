"""Inspection CV/ML Analysis Pipeline for FireSphere."""

from datetime import datetime, timezone
import time
from typing import Dict, List, Optional, Any
import uuid
from app.core.config import settings
from app.core.exceptions import (
    FireSphereAIException,
    PipelineProcessingException,
)
from app.storage.s3_storage import StorageUnavailableException
from app.core.logging import logger
from app.cv.preprocessor import CVPreprocessor, PreprocessingResult
from app.cv.transforms import BoundingBoxTransformer
from app.intelligence.aggregation import InspectionSummaryAggregator
from app.intelligence.evidence_quality import EvidenceQualityEvaluator
from app.intelligence.finding_mapper import FindingMapper
from app.models.base_model import BaseInspectionModel
from app.models.cv_model_adapter import CVModelAdapter
from app.pipelines.base import BasePipeline
from app.schemas.finding import BoundingBox, Finding
from app.schemas.run import AIRun, RunStatus
from app.storage.evidence_client import EvidenceClient, evidence_client


class InspectionPipeline(BasePipeline):
    """End-to-end pipeline orchestrating evidence processing, model inference, and finding generation.

    Integrates Phase 2 OpenCV preprocessing with Phase 3 CV model inference, letterbox coordinate
    transformation, and Phase 4 evidence-grounded AI findings with confidence, severity, risk, and summary.
    """

    def __init__(
        self,
        model: Optional[BaseInspectionModel] = None,
        preprocessor: Optional[CVPreprocessor] = None,
        storage_client: Optional[EvidenceClient] = None,
    ):
        self.model = model or CVModelAdapter(
            model_name=settings.model_name,
            model_version=settings.model_version,
        )
        self.preprocessor = preprocessor or CVPreprocessor()
        self.storage_client = storage_client or evidence_client

        # Ensure model readiness
        if not self.model.is_ready():
            self.model.load()

    def execute(
        self,
        inspection_id: uuid.UUID,
        evidence_ids: List[uuid.UUID],
        test_mode: bool = False,
        occupancy_context: str = "UNKNOWN",
        historical_context: str = "UNAVAILABLE",
        tenant_id: Optional[str] = "default",
    ) -> AIRun:
        """Executes the inspection analysis pipeline across all provided evidence items.

        Provides per-evidence fault isolation: a single corrupted evidence file does not crash
        the remaining valid evidence processing. Restores model letterbox bounding boxes to original
        photograph coordinates. Generates enriched, explainable findings and an executive AI summary.
        """
        from app.models.model_approval_gate import model_approval_gate

        run_id = uuid.uuid4()
        start_time = time.perf_counter()
        started_at = datetime.now(timezone.utc)

        logger.info(
            f"Starting AI analysis run runId={run_id} for inspectionId={inspection_id} "
            f"with {len(evidence_ids)} evidence items (testMode={test_mode}, tenantId={tenant_id})"
        )

        all_findings: List[Finding] = []
        evidence_processing_summary: Dict[str, Any] = {}
        evidence_quality_records: Dict[str, str] = {}
        has_quality_warnings = False
        successful_evidence_count = 0
        failed_evidence_count = 0
        meta = self.model.get_metadata()

        for idx, eid in enumerate(evidence_ids, 1):
            eid_str = str(eid)
            logger.debug(
                f"Processing evidence item [{idx}/{len(evidence_ids)}] "
                f"evidenceId={eid} for runId={run_id}"
            )

            try:
                # 1. Fetch evidence metadata & raw bytes securely
                evidence_meta = self.storage_client.get_evidence_metadata(eid)
                media_bytes = self.storage_client.fetch_evidence_bytes(eid)

                # 2. Run OpenCV Preprocessing (validation, decode, quality, letterbox, normalization)
                t_prep_start = time.perf_counter()
                preprocessed_result = self.preprocessor.preprocess(
                    raw_bytes=media_bytes,
                    evidence_id=eid_str,
                )
                prep_duration_ms = round((time.perf_counter() - t_prep_start) * 1000, 2)

                # Evaluate photographic evidence quality (GOOD, ACCEPTABLE, POOR, UNUSABLE)
                evidence_quality = EvidenceQualityEvaluator.evaluate(preprocessed_result.quality)
                evidence_quality_records[eid_str] = evidence_quality
                if preprocessed_result.quality.warnings:
                    has_quality_warnings = True

                successful_evidence_count += 1
                evidence_processing_summary[eid_str] = {
                    "status": preprocessed_result.status,
                    "evidenceQuality": evidence_quality,
                    "originalDimensions": [
                        preprocessed_result.metadata.originalWidth,
                        preprocessed_result.metadata.originalHeight,
                    ],
                    "targetDimensions": [
                        preprocessed_result.metadata.targetWidth,
                        preprocessed_result.metadata.targetHeight,
                    ],
                    "scaleFactor": preprocessed_result.metadata.scaleFactor,
                    "warnings": preprocessed_result.quality.warnings,
                    "preprocessingDurationMs": prep_duration_ms,
                }

                # 3. Model inference on preprocessed array
                t_inf_start = time.perf_counter()
                raw_detections = self.model.predict(
                    evidence_id=eid_str,
                    preprocessed_image=preprocessed_result.preprocessed_array,
                    test_mode=test_mode,
                )
                inf_duration_ms = round((time.perf_counter() - t_inf_start) * 1000, 2)
                evidence_processing_summary[eid_str]["inferenceDurationMs"] = inf_duration_ms

                # Gate detections: filter out unsupported classes and enforce production boundaries
                actionable_detections, suppressed_dets = model_approval_gate.filter_and_gate_detections(
                    raw_detections,
                    is_test_mode=test_mode,
                )
                if suppressed_dets:
                    logger.info(
                        f"Suppressed {len(suppressed_dets)} unsupported class detections for evidenceId={eid_str}"
                    )

                # 4. Generate enriched, explainable findings via Phase 4 FindingMapper
                for det in actionable_detections:
                    if det.confidence < settings.confidence_threshold:
                        logger.debug(
                            f"Filtered low-confidence detection '{det.className}' "
                            f"({det.confidence:.2f} < {settings.confidence_threshold:.2f})"
                        )
                        continue

                    # Transform model letterbox bounding box back to original photograph dimensions
                    if det.boundingBox:
                        raw_box = BoundingBox(**det.boundingBox)
                        original_box = BoundingBoxTransformer.letterbox_to_original(
                            raw_box, preprocessed_result.metadata
                        )
                    else:
                        original_box = None

                    finding = FindingMapper.map_detection(
                        detection=det,
                        evidence_id=eid,
                        model_name=meta.get("modelName", settings.model_name),
                        model_version=meta.get("modelVersion", settings.model_version),
                        transformed_bounding_box=original_box,
                        evidence_quality=evidence_quality,
                        occupancy_context=occupancy_context,
                        historical_context=historical_context,
                        is_test_data=test_mode,
                        tenant_id=tenant_id,
                        inspection_id=inspection_id,
                        analysis_run_id=run_id,
                    )
                    all_findings.append(finding)

            except StorageUnavailableException:
                raise
            except Exception as ev_err:
                failed_evidence_count += 1
                error_code = getattr(ev_err, "error_code", "EVIDENCE_PROCESSING_FAILED")
                logger.warning(
                    f"Evidence processing failed for evidenceId={eid} in runId={run_id}: "
                    f"[{error_code}] {str(ev_err)}"
                )
                evidence_processing_summary[eid_str] = {
                    "status": "FAILED",
                    "errorCode": error_code,
                    "error": str(ev_err),
                }

        duration_ms = round((time.perf_counter() - start_time) * 1000, 2)
        completed_at = datetime.now(timezone.utc)

        # If every provided evidence item failed, fail the overall run controlled
        if failed_evidence_count > 0 and successful_evidence_count == 0:
            logger.error(
                f"All {failed_evidence_count} evidence items failed processing in runId={run_id}"
            )
            raise PipelineProcessingException(
                message=f"All evidence items failed preprocessing for inspectionId={inspection_id}.",
                details={
                    "runId": str(run_id),
                    "inspectionId": str(inspection_id),
                    "evidenceErrors": evidence_processing_summary,
                    "durationMs": duration_ms,
                },
            )

        # 5. Generate Inspection-Level AI Summary
        summary = InspectionSummaryAggregator.aggregate(
            findings=all_findings,
            evidence_ids=evidence_ids,
            evidence_quality_records=evidence_quality_records,
            has_quality_warnings=has_quality_warnings,
        )

        logger.info(
            f"AI analysis completed | runId={run_id} | "
            f"successfulEvidence={successful_evidence_count} | failedEvidence={failed_evidence_count} | "
            f"findingsCount={len(all_findings)} | duration={duration_ms}ms"
        )

        error_details = (
            {"evidenceProcessing": evidence_processing_summary}
            if failed_evidence_count > 0
            else None
        )

        from app.services.finding_repository import finding_repository
        for f in all_findings:
            finding_repository.save_finding(f)

        return AIRun(
            runId=run_id,
            inspectionId=inspection_id,
            inputEvidence=evidence_ids,
            status=RunStatus.COMPLETED,
            model=meta.get("modelName", settings.model_name),
            modelVersion=meta.get("modelVersion", settings.model_version),
            findings=all_findings,
            startedAt=started_at,
            completedAt=completed_at,
            executionDurationMs=duration_ms,
            errorDetails=error_details,
            summary=summary.model_dump(),
            tenantId=tenant_id,
            modelApprovalStatus="NOT_APPROVED",
        )

