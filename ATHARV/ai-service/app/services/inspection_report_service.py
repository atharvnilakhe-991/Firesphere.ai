"""Inspection report drafting service coordinating analysis outputs and report generation."""

from typing import Dict, List, Optional
import uuid
from app.core.exceptions import (
    FireSphereAIException,
    InvalidEvidenceException,
    PipelineProcessingException,
)
from app.core.logging import logger
from app.pipelines.inspection_pipeline import InspectionPipeline
from app.reporting.deterministic_generator import DeterministicReportGenerator
from app.schemas.report import (
    InspectionReportDraft,
    InspectionReportDraftRequest,
)


class InspectionReportService:
    """Coordinates the generation of AI-assisted inspection report drafts."""

    def __init__(
        self,
        pipeline: Optional[InspectionPipeline] = None,
        generator: Optional[DeterministicReportGenerator] = None,
    ):
        self.pipeline = pipeline or InspectionPipeline()
        self.generator = generator or DeterministicReportGenerator()

    def generate_report_draft(
        self,
        request: InspectionReportDraftRequest,
    ) -> InspectionReportDraft:
        """Generates a structured, evidence-traceable inspection report draft from request parameters.

        Supports two flexible operational workflows:
        1. Direct drafting: If findings and runId are supplied, drafts the report directly from existing analysis.
        2. Full execution: If findings are not provided, executes the inspection pipeline on evidenceIds, then drafts the report.
        """
        logger.info(
            f"Generating inspection report draft | inspectionId={request.inspectionId} | "
            f"runId={request.runId} | testMode={request.testMode}"
        )

        findings = request.findings
        run_id = request.runId or uuid.uuid4()
        evidence_ids = request.evidenceIds or []
        evidence_qual_map: Dict[str, str] = {}
        evidence_warn_map: Dict[str, List[str]] = {}
        duration_ms = None
        cal_status = "NOT_CALIBRATED"

        # Workflow 2: If findings were not pre-supplied, execute the pipeline
        if findings is None:
            if not evidence_ids:
                raise InvalidEvidenceException("Either 'findings' or 'evidenceIds' must be supplied to generate a report.")

            try:
                ai_run = self.pipeline.execute(
                    inspection_id=request.inspectionId,
                    evidence_ids=evidence_ids,
                    test_mode=bool(request.testMode),
                    occupancy_context=request.occupancyContext or "UNKNOWN",
                    historical_context=request.historicalContext or "UNAVAILABLE",
                )
                run_id = ai_run.runId
                findings = ai_run.findings
                duration_ms = ai_run.executionDurationMs

                # Extract quality ratings and warnings from error/processing details if available
                if ai_run.summary and "evidenceQualitySummary" in ai_run.summary:
                    cal_status = ai_run.summary.get("calibrationStatus", "NOT_CALIBRATED")

            except FireSphereAIException:
                raise
            except Exception as exc:
                logger.error(f"Error executing inspection pipeline for report draft: {exc}")
                raise PipelineProcessingException(
                    message="Pipeline analysis failed during report generation.",
                    details={"inspectionId": str(request.inspectionId)},
                )
        else:
            # Workflow 1: Pre-supplied findings
            if not evidence_ids:
                evidence_ids = list({f.evidenceId for f in findings})

        return self.generator.generate_draft(
            inspection_id=request.inspectionId,
            run_id=run_id,
            findings=findings,
            evidence_ids=evidence_ids,
            evidence_quality_records=evidence_qual_map,
            evidence_warnings=evidence_warn_map,
            model_name="firesphere-inspection-cv",
            model_version="0.3.0-phase3",
            calibration_status=cal_status,
            execution_duration_ms=duration_ms,
            occupancy_context=request.occupancyContext or "UNKNOWN",
            historical_context=request.historicalContext or "UNAVAILABLE",
            officer_remarks=request.officerRemarks,
            checklist_data=request.checklistData,
            is_test_data=bool(request.testMode),
        )


# Global service instance
inspection_report_service = InspectionReportService()
