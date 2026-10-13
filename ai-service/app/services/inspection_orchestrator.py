"""FireSphere.ai — Phase 3 Inspection Orchestration Service.

Orchestrates multi-provider visual reasoning across Google Gemini VLM and Computer Vision
models with strict fault isolation, capability gating, evidence validation, result reconciliation,
and human-in-the-loop governance.
"""

from datetime import datetime, timezone
import time
from typing import Any, Dict, List, Optional
import uuid

from app.core.config import settings
from app.core.exceptions import (
    FireSphereAIException,
    InvalidEvidenceException,
    PipelineProcessingException,
)
from app.core.logging import logger
from app.cv.preprocessor import CVPreprocessor
from app.cv.transforms import BoundingBoxTransformer
from app.intelligence.aggregation import InspectionSummaryAggregator
from app.intelligence.evidence_quality import EvidenceQualityEvaluator
from app.intelligence.evidence_validator import EvidenceValidator, evidence_validator
from app.intelligence.finding_mapper import FindingMapper
from app.intelligence.reconciliation import ResultReconciler, result_reconciler
from app.models.cv_model_adapter import CVModelAdapter
from app.models.gemini_provider import GeminiProvider, gemini_provider
from app.models.gemini_schemas import GeminiAnalysisResult, GeminiAnalysisStatus
from app.models.model_approval_gate import model_approval_gate
from app.models.vlm_provider import BaseVLMProvider, get_vlm_provider
from app.schemas.finding import BoundingBox, Finding
from app.schemas.inspection import (
    InspectionAISummary,
    InspectionAnalysisRequest,
    InspectionAnalysisResponse,
)
from app.schemas.reconciliation import ReconciliationStatus
from app.schemas.run import AIRun, RunStatus
from app.services.finding_repository import BaseFindingRepository, finding_repository
from app.storage.evidence_client import EvidenceClient, evidence_client


class InspectionOrchestrator:
    """Central orchestration service coordinating multi-provider AI visual inspection analysis."""

    def __init__(
        self,
        gemini: Optional[BaseVLMProvider] = None,
        vlm_provider: Optional[BaseVLMProvider] = None,
        cv_model: Optional[CVModelAdapter] = None,
        preprocessor: Optional[CVPreprocessor] = None,
        storage: Optional[EvidenceClient] = None,
        validator: Optional[EvidenceValidator] = None,
        reconciler: Optional[ResultReconciler] = None,
        repository: Optional[BaseFindingRepository] = None,
    ):
        self.vlm = vlm_provider or gemini or get_vlm_provider()
        self.gemini = self.vlm  # Backward-compatibility alias
        self.cv_model = cv_model or CVModelAdapter(
            model_name=settings.model_name,
            model_version=settings.model_version,
        )
        self.preprocessor = preprocessor or CVPreprocessor()
        self.storage = storage or evidence_client
        self.validator = validator or evidence_validator
        self.reconciler = reconciler or result_reconciler
        self.repository = repository or finding_repository


    def orchestrate_inspection(
        self,
        request: InspectionAnalysisRequest,
    ) -> InspectionAnalysisResponse:
        """Executes full multi-provider orchestration from an API request."""
        ai_run = self.execute_run(
            inspection_id=request.inspectionId,
            evidence_ids=request.evidenceIds,
            test_mode=bool(request.testMode),
            occupancy_context=request.occupancyContext or "UNKNOWN",
            historical_context=request.historicalContext or "UNAVAILABLE",
            tenant_id=getattr(request, "tenantId", "default") or "default",
        )

        summary_obj = InspectionAISummary(**ai_run.summary) if ai_run.summary else None

        return InspectionAnalysisResponse(
            runId=ai_run.runId,
            status=ai_run.status,
            findings=ai_run.findings,
            summary=summary_obj,
            model=ai_run.model,
            modelVersion=ai_run.modelVersion,
            executionDurationMs=ai_run.executionDurationMs,
            tenantId=getattr(ai_run, "tenantId", None),
            modelApprovalStatus=getattr(ai_run, "modelApprovalStatus", "NOT_APPROVED"),
            calibrationStatus="NOT_CALIBRATED",
        )

    def execute_run(
        self,
        inspection_id: uuid.UUID,
        evidence_ids: List[uuid.UUID],
        test_mode: bool = False,
        occupancy_context: str = "UNKNOWN",
        historical_context: str = "UNAVAILABLE",
        tenant_id: Optional[str] = "default",
    ) -> AIRun:
        """Executes multi-provider analysis with fault isolation and deterministic reconciliation."""
        run_id = uuid.uuid4()
        start_time = time.perf_counter()
        started_at = datetime.now(timezone.utc)

        logger.info(
            f"ORCHESTRATOR_RUN_STARTED | runId={run_id} | inspectionId={inspection_id} | "
            f"evidenceCount={len(evidence_ids)} | testMode={test_mode} | tenantId={tenant_id}"
        )

        # 1. Validate requested evidence references
        self.validator.validate_requested_evidence(
            inspection_id=inspection_id,
            evidence_ids=evidence_ids,
            tenant_id=tenant_id,
        )

        all_findings: List[Finding] = []
        evidence_quality_records: Dict[str, str] = {}
        evidence_processing_summary: Dict[str, Any] = {}
        has_quality_warnings = False
        successful_evidence_count = 0
        failed_evidence_count = 0
        has_inconclusive = False
        provider_failures = 0

        cv_info = {
            "model": settings.model_name,
            "version": settings.model_version,
        }
        prov_name = getattr(self.gemini, "provider_name", "google-gemini")
        gemini_info = {
            "provider": prov_name,
            "model": self.gemini.get_model_id() if hasattr(self.gemini, "get_model_id") else "gemini-2.5-flash",
            "enabled": str(self.gemini.is_enabled()),
        }


        # Check CV gate once for run context
        cv_gate = model_approval_gate.check_inference_permission(
            test_mode=test_mode,
            model_id=settings.model_name,
            model_version=settings.model_version,
        )

        for idx, eid in enumerate(evidence_ids, 1):
            eid_str = str(eid)
            logger.debug(f"Orchestrating evidence [{idx}/{len(evidence_ids)}] evidenceId={eid}")

            try:
                # A. Fetch Evidence Bytes & Metadata
                media_bytes = self.storage.fetch_evidence_bytes(eid, tenant_id=tenant_id)

                # B. Execute OpenCV Preprocessing (for CV model and quality metrics)
                prep_result = self.preprocessor.preprocess(
                    raw_bytes=media_bytes,
                    evidence_id=eid_str,
                )
                evidence_quality = EvidenceQualityEvaluator.evaluate(prep_result.quality)
                evidence_quality_records[eid_str] = evidence_quality
                if prep_result.quality.warnings:
                    has_quality_warnings = True

                # C. Primary Provider: Gemini VLM Vision Analysis
                gemini_res: Optional[GeminiAnalysisResult] = None
                if self.gemini.is_enabled():
                    try:
                        gemini_res = self.gemini.analyze_image(
                            evidence_id=eid,
                            image_data=media_bytes,
                        )
                        if gemini_res.status == GeminiAnalysisStatus.ANALYSIS_INCONCLUSIVE:
                            has_inconclusive = True
                        elif gemini_res.status in (
                            GeminiAnalysisStatus.ANALYSIS_FAILED,
                            GeminiAnalysisStatus.ANALYSIS_INVALID_RESPONSE,
                        ):
                            provider_failures += 1
                    except Exception as g_err:
                        logger.error(f"Gemini execution exception for evidenceId={eid}: {g_err}")
                        gemini_res = GeminiAnalysisResult(
                            evidenceId=eid,
                            model=self.gemini.get_model_id(),
                            status=GeminiAnalysisStatus.ANALYSIS_FAILED,
                            observations=[],
                            error=str(g_err),
                        )
                        provider_failures += 1
                else:
                    gemini_res = GeminiAnalysisResult(
                        evidenceId=eid,
                        model=self.gemini.get_model_id(),
                        status=GeminiAnalysisStatus.GEMINI_DISABLED,
                        observations=[],
                    )

                # D. Secondary Provider: CV Model Adapter with Governance Gate
                raw_cv_detections = []
                cv_status = "NOT_APPROVED_GATED"

                if cv_gate["authorized"]:
                    try:
                        raw_cv_detections = self.cv_model.predict(
                            evidence_id=eid_str,
                            preprocessed_image=prep_result.preprocessed_array,
                            test_mode=test_mode,
                        )
                        cv_status = "SUCCESS"
                    except Exception as cv_err:
                        logger.error(f"CV prediction failed for evidenceId={eid}: {cv_err}")
                        if "weights" in str(cv_err).lower() or "checkpoint" in str(cv_err).lower():
                            cv_status = "CHECKPOINT_LOAD_FAILED"
                        else:
                            cv_status = "FAILED"
                        provider_failures += 1
                else:
                    cv_status = "NOT_APPROVED_GATED"

                # Filter and Gate CV detections
                actionable_cv_dets, _ = model_approval_gate.filter_and_gate_detections(
                    raw_cv_detections,
                    is_test_mode=test_mode,
                )

                # E. Deterministic Multi-Provider Reconciliation (12 Cases)
                reconciled_findings, recon_status, conflict_detail = self.reconciler.reconcile(
                    evidence_id=eid,
                    inspection_id=inspection_id,
                    gemini_result=gemini_res,
                    cv_detections=actionable_cv_dets,
                    cv_status=cv_status,
                    cv_model_info=cv_info,
                    gemini_model_info=gemini_info,
                    is_test_mode=test_mode,
                    tenant_id=tenant_id,
                    analysis_run_id=run_id,
                )

                # Enrich findings with quality, context, and guidance
                from app.intelligence.confidence import ConfidenceInterpreter
                from app.intelligence.risk import RiskScoringEngine
                from app.intelligence.severity import SeverityEngine
                from app.intelligence.verification import OfficerVerificationGuidance

                for f in reconciled_findings:
                    f.occupancyContext = occupancy_context
                    f.historicalContext = historical_context
                    f.evidenceQuality = evidence_quality
                    if not f.confidenceLevel or f.confidenceLevel == "MEDIUM":
                        f.confidenceLevel = ConfidenceInterpreter.interpret(f.confidence)
                    if not f.severityReason:
                        f.severityReason = SeverityEngine.generate_severity_reason(f.findingType, f.severity)
                    if f.operationalRiskScore is None:
                        risk_assessment = RiskScoringEngine.calculate_risk(
                            severity=f.severity,
                            confidence=f.confidence,
                            evidence_quality=evidence_quality,
                            occupancy_context=occupancy_context,
                            historical_context=historical_context,
                        )
                        f.operationalRiskScore = risk_assessment.riskScore
                        f.operationalRiskLevel = risk_assessment.riskLevel
                        f.riskFactors = [rf.model_dump() for rf in risk_assessment.factors]
                    if not f.verificationGuidance:
                        f.verificationGuidance = OfficerVerificationGuidance.get_guidance(f.findingType)
                    all_findings.append(f)
                    # Persist finding in repository for subsequent officer verification
                    self.repository.save_finding(f)

                successful_evidence_count += 1
                evidence_processing_summary[eid_str] = {
                    "status": "SUCCESS",
                    "reconciliationStatus": recon_status.value if hasattr(recon_status, "value") else str(recon_status),
                    "geminiStatus": gemini_res.status.value if gemini_res else "DISABLED",
                    "cvStatus": cv_status,
                    "findingsCount": len(reconciled_findings),
                    "evidenceQuality": evidence_quality,
                }

            except Exception as ev_err:
                failed_evidence_count += 1
                logger.error(f"Evidence processing failed for evidenceId={eid}: {ev_err}")
                evidence_processing_summary[eid_str] = {
                    "status": "FAILED",
                    "error": str(ev_err),
                }

        duration_ms = round((time.perf_counter() - start_time) * 1000, 2)
        completed_at = datetime.now(timezone.utc)

        # Failure boundary: if all evidence items completely failed
        if failed_evidence_count > 0 and successful_evidence_count == 0:
            logger.error(f"All {failed_evidence_count} evidence items failed in runId={run_id}")
            raise PipelineProcessingException(
                message=f"All evidence items failed during orchestration for inspectionId={inspection_id}.",
                details={"runId": str(run_id), "errors": evidence_processing_summary},
            )

        # Determine overall run status
        if failed_evidence_count > 0 and successful_evidence_count > 0:
            # Per-evidence fault isolation: valid evidence was successfully analyzed
            overall_status = RunStatus.COMPLETED
        elif provider_failures > 0:
            overall_status = RunStatus.PARTIAL
        elif has_inconclusive and not all_findings:
            overall_status = RunStatus.INCONCLUSIVE
        else:
            overall_status = RunStatus.COMPLETED

        # Generate Inspection-Level AI Summary
        summary = InspectionSummaryAggregator.aggregate(
            findings=all_findings,
            evidence_ids=evidence_ids,
            evidence_quality_records=evidence_quality_records,
            has_quality_warnings=has_quality_warnings,
        )

        recon_statuses = [
            item.get("reconciliationStatus")
            for item in evidence_processing_summary.values()
            if isinstance(item, dict) and "reconciliationStatus" in item
        ]
        overall_recon = (
            "AGREEMENT"
            if recon_statuses and all(s == "AGREEMENT" for s in recon_statuses)
            else (
                "CONFLICT"
                if any(s == "CONFLICT" for s in recon_statuses)
                else (recon_statuses[0] if len(recon_statuses) == 1 else "DISCREPANCY")
            )
        )
        summary.cvStatus = "SUCCESS" if cv_gate.get("authorized") else "NOT_APPROVED_GATED"
        summary.vlmStatus = "OPERATIONAL" if self.gemini.is_enabled() else "UNAVAILABLE"
        summary.reconciliationStatus = overall_recon

        logger.info(
            f"ORCHESTRATION_COMPLETED | runId={run_id} | status={overall_status} | "
            f"findings={len(all_findings)} | duration={duration_ms}ms"
        )

        return AIRun(
            runId=run_id,
            inspectionId=inspection_id,
            inputEvidence=evidence_ids,
            status=overall_status,
            model="firesphere-orchestrator-ensemble",
            modelVersion="0.3.0",
            findings=all_findings,
            startedAt=started_at,
            completedAt=completed_at,
            executionDurationMs=duration_ms,
            errorDetails={"evidenceSummary": evidence_processing_summary} if failed_evidence_count > 0 else None,
            summary=summary.model_dump(),
            tenantId=tenant_id,
            modelApprovalStatus="NOT_APPROVED",
        )


# Global inspection orchestrator singleton
inspection_orchestrator = InspectionOrchestrator()
