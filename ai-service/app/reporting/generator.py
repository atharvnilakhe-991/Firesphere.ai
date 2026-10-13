"""Abstract base class for report generators and future LLM abstraction."""

from abc import ABC, abstractmethod
from typing import Any, Dict, List, Optional
import uuid
from app.schemas.finding import Finding
from app.schemas.report import InspectionReportDraft


class BaseReportGenerator(ABC):
    """Abstract base class for inspection report draft generators."""

    generator_version: str = "base-v1"

    @abstractmethod
    def generate_draft(
        self,
        inspection_id: uuid.UUID,
        run_id: uuid.UUID,
        findings: List[Finding],
        evidence_ids: List[uuid.UUID],
        evidence_quality_records: Optional[Dict[str, str]] = None,
        evidence_warnings: Optional[Dict[str, List[str]]] = None,
        model_name: str = "firesphere-inspection-cv",
        model_version: str = "0.3.0-phase3",
        calibration_status: str = "NOT_CALIBRATED",
        execution_duration_ms: Optional[float] = None,
        occupancy_context: str = "UNKNOWN",
        historical_context: str = "UNAVAILABLE",
        officer_remarks: Optional[str] = None,
        checklist_data: Optional[Dict[str, Any]] = None,
        is_test_data: bool = False,
    ) -> InspectionReportDraft:
        """Generates an inspection report draft from structured inputs."""
        pass


class FutureLLMReportGenerator(BaseReportGenerator):
    """Interface placeholder for future LLM-assisted report narrative generation.

    NOTE: In Phase 5, all report generation is strictly deterministic and performed by
    DeterministicReportGenerator. No external LLM (OpenAI, Gemini, Anthropic, Ollama, etc.)
    is invoked, ensuring zero hallucination, strict auditability, and deterministic reproducibility.
    """

    generator_version: str = "future-llm-v1-interface"

    def generate_draft(
        self,
        inspection_id: uuid.UUID,
        run_id: uuid.UUID,
        findings: List[Finding],
        evidence_ids: List[uuid.UUID],
        evidence_quality_records: Optional[Dict[str, str]] = None,
        evidence_warnings: Optional[Dict[str, List[str]]] = None,
        model_name: str = "firesphere-inspection-cv",
        model_version: str = "0.3.0-phase3",
        calibration_status: str = "NOT_CALIBRATED",
        execution_duration_ms: Optional[float] = None,
        occupancy_context: str = "UNKNOWN",
        historical_context: str = "UNAVAILABLE",
        officer_remarks: Optional[str] = None,
        checklist_data: Optional[Dict[str, Any]] = None,
        is_test_data: bool = False,
    ) -> InspectionReportDraft:
        raise NotImplementedError(
            "FutureLLMReportGenerator is a future architectural interface. "
            "Phase 5 operates exclusively with DeterministicReportGenerator without external LLM dependencies."
        )
