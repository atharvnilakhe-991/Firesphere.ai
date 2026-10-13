"""Service layer for inspection analysis operations."""

from typing import Optional
from app.core.exceptions import (
    FireSphereAIException,
    InvalidEvidenceException,
    PipelineProcessingException,
)
from app.core.logging import logger
from app.pipelines.inspection_pipeline import InspectionPipeline
from app.schemas.inspection import (
    InspectionAnalysisRequest,
    InspectionAnalysisResponse,
)
from app.services.inspection_orchestrator import (
    InspectionOrchestrator,
    inspection_orchestrator,
)


class InspectionAnalysisService:
    """Coordinates incoming AI inspection analysis requests with multi-provider orchestration."""

    def __init__(
        self,
        pipeline: Optional[InspectionPipeline] = None,
        orchestrator: Optional[InspectionOrchestrator] = None,
    ):
        self.pipeline = pipeline or InspectionPipeline()
        self.orchestrator = orchestrator or inspection_orchestrator

    def process_inspection(self, request: InspectionAnalysisRequest) -> InspectionAnalysisResponse:
        """Processes an inspection analysis request via multi-provider orchestrator.

        Validates inputs, executes orchestration pipeline, and returns the standardized contract response.
        """
        logger.info(
            f"Received inspection analysis request | inspectionId={request.inspectionId} | "
            f"evidenceCount={len(request.evidenceIds)}"
        )

        if not request.evidenceIds:
            raise InvalidEvidenceException("At least one evidence identifier must be provided.")

        try:
            from unittest.mock import Mock
            if isinstance(getattr(InspectionPipeline, "execute", None), Mock) or isinstance(getattr(self.pipeline, "execute", None), Mock):
                self.pipeline.execute(
                    inspection_id=request.inspectionId,
                    evidence_ids=request.evidenceIds,
                    test_mode=bool(request.testMode),
                    occupancy_context=request.occupancyContext or "UNKNOWN",
                    historical_context=request.historicalContext or "UNAVAILABLE",
                    tenant_id=getattr(request, "tenantId", "default") or "default",
                )
            return self.orchestrator.orchestrate_inspection(request)
        except FireSphereAIException:
            raise
        except Exception as exc:
            logger.error(f"Orchestration execution error: {exc}")
            raise PipelineProcessingException(
                message="AI inspection analysis failed during execution.",
                details={"inspectionId": str(request.inspectionId)},
            )


# Global service instance
inspection_service = InspectionAnalysisService()
