"""FireSphere.ai — Phase 3 Reconciliation & Multi-Provider Provenance Schemas.

Defines deterministic reconciliation statuses, conflict types, provider provenance models,
and evidence validation schemas to coordinate Gemini VLM and Computer Vision model outputs.
"""

from datetime import datetime, timezone
from enum import Enum
from typing import Any, Dict, List, Optional, Tuple, Union
import uuid
from pydantic import BaseModel, Field, model_validator
from app.schemas.finding import BoundingBox, Finding


class ReconciliationStatus(str, Enum):
    """Deterministic status of cross-provider finding reconciliation."""

    UNRECONCILED = "UNRECONCILED"
    AGREEMENT = "AGREEMENT"
    SINGLE_PROVIDER = "SINGLE_PROVIDER"
    SINGLE_PROVIDER_CV = "SINGLE_PROVIDER_CV"
    SINGLE_PROVIDER_VLM = "SINGLE_PROVIDER_VLM"
    GATE_BLOCKED_CV = "GATE_BLOCKED_CV"
    PARTIAL_FAILURE_CV = "PARTIAL_FAILURE_CV"
    PARTIAL_FAILURE_VLM = "PARTIAL_FAILURE_VLM"
    DISCREPANCY = "DISCREPANCY"
    CONFLICT = "CONFLICT"
    PARTIAL_INCONCLUSIVE = "PARTIAL_INCONCLUSIVE"
    INCONCLUSIVE = "INCONCLUSIVE"
    AMBIGUOUS_MATCH = "AMBIGUOUS_MATCH"
    FAILED = "FAILED"
    NO_OBSERVATIONS = "NO_OBSERVATIONS"
    INVALID = "INVALID"

    # Stage 8 Semantic Aliases
    CONCORDANT_AGREEMENT = "AGREEMENT"
    CONCORDANT_EMPTY = "NO_OBSERVATIONS"
    DISCORDANT_FINDING = "CONFLICT"
    BOTH_PROVIDERS_FAILED = "FAILED"


class ConflictType(str, Enum):
    """Categorical classification of model disagreements."""

    NONE = "NONE"
    CATEGORY_MISMATCH = "CATEGORY_MISMATCH"
    HAZARD_MISMATCH = "HAZARD_MISMATCH"
    LOCATION_MISMATCH = "LOCATION_MISMATCH"
    SEVERITY_MISMATCH = "SEVERITY_MISMATCH"
    DETECTION_ABSENCE = "DETECTION_ABSENCE"
    UNRESOLVED_DISAGREEMENT = "UNRESOLVED_DISAGREEMENT"
    GATE_BLOCKED_CV = "GATE_BLOCKED_CV"
    PROVIDER_FAILURE = "PROVIDER_FAILURE"
    AMBIGUOUS_MATCH = "AMBIGUOUS_MATCH"


class ComponentAnalysisStatus(str, Enum):
    """Normalized semantic state for an individual AI component (CV or VLM)."""

    SUCCESS_WITH_OBSERVATIONS = "SUCCESS_WITH_OBSERVATIONS"
    SUCCESS_WITH_NO_OBSERVATIONS = "SUCCESS_WITH_NO_OBSERVATIONS"
    INCONCLUSIVE = "INCONCLUSIVE"
    FAILED = "FAILED"
    UNAVAILABLE = "UNAVAILABLE"


def map_component_status(
    execution_status: Any,
    observation_count: int = 0,
    error: Optional[str] = None,
) -> ComponentAnalysisStatus:
    """Deterministically maps raw provider execution status and observation count to normalized semantic state.

    Guarantees:
    - A failed or unavailable provider is NEVER converted into an empty successful result.
    - An empty result from a successful component means only that the component returned no observations
      under its current capabilities (does NOT certify building safety).
    """
    raw = str(execution_status).upper().strip() if execution_status else "UNAVAILABLE"

    # 1. Unavailable states
    if raw in (
        "DISABLED",
        "GEMINI_DISABLED",
        "NOT_CONFIGURED",
        "GEMINI_NOT_CONFIGURED",
        "UNAVAILABLE",
    ):
        return ComponentAnalysisStatus.UNAVAILABLE

    # 2. Failure states
    if raw in (
        "FAILED",
        "CHECKPOINT_LOAD_FAILED",
        "ANALYSIS_FAILED",
        "ANALYSIS_INVALID_RESPONSE",
        "ERROR",
        "EXCEPTION",
    ):
        return ComponentAnalysisStatus.FAILED

    # 3. Inconclusive states
    if raw in ("INCONCLUSIVE", "ANALYSIS_INCONCLUSIVE"):
        return ComponentAnalysisStatus.INCONCLUSIVE

    # 4. Gated or unapproved CV model in production mode (non-test execution)
    if raw in ("NOT_APPROVED_GATED", "NOT_APPROVED"):
        # Gated execution produces no authorized production detections
        if observation_count > 0:
            return ComponentAnalysisStatus.SUCCESS_WITH_OBSERVATIONS
        return ComponentAnalysisStatus.UNAVAILABLE

    # 5. Successful execution
    if raw in (
        "SUCCESS",
        "COMPLETED",
        "ANALYSIS_COMPLETED_WITH_OBSERVATIONS",
        "ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS",
    ):
        if observation_count > 0:
            return ComponentAnalysisStatus.SUCCESS_WITH_OBSERVATIONS
        return ComponentAnalysisStatus.SUCCESS_WITH_NO_OBSERVATIONS

    # Fallback based on observations and errors
    if error:
        return ComponentAnalysisStatus.FAILED
    if observation_count > 0:
        return ComponentAnalysisStatus.SUCCESS_WITH_OBSERVATIONS
    return ComponentAnalysisStatus.SUCCESS_WITH_NO_OBSERVATIONS


class ComponentResult(BaseModel):
    """Auditable component output tracking metadata, observations, and status."""

    componentName: str = Field(description="Component identifier: 'CV' or 'VLM'")
    modelName: str = Field(description="Model identifier")
    modelVersion: Optional[str] = Field(default=None, description="Model semantic version")
    evidenceId: uuid.UUID = Field(description="Evidence identifier")
    analysisStatus: ComponentAnalysisStatus = Field(description="Normalized component semantic status")
    rawExecutionStatus: str = Field(description="Underlying provider execution status")
    observations: List[Dict[str, Any]] = Field(default_factory=list, description="Raw observations or detections")
    executionDurationMs: Optional[float] = Field(default=None, description="Execution duration in milliseconds")
    timestamp: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="Execution timestamp",
    )
    modelApprovalStatus: str = Field(default="NOT_APPROVED", description="Model production approval status")
    calibrationStatus: str = Field(default="NOT_CALIBRATED", description="Calibration status")
    isTestData: bool = Field(default=False, description="Flag indicating if output is synthetic test data")
    error: Optional[str] = Field(default=None, description="Sanitized error description if failed")


class ProviderProvenance(BaseModel):
    """Auditable provenance record tracking execution details for an individual AI provider."""

    providerName: str = Field(default="ollama", description="Name of the analysis provider (e.g. ollama, firesphere-cv-baseline)")
    modelId: str = Field(default="qwen2.5vl:3b", description="Model identifier or version string")
    provider: Optional[str] = Field(default=None, description="Bidirectional alias for providerName")
    model: Optional[str] = Field(default=None, description="Bidirectional alias for modelId")
    modelVersion: Optional[str] = Field(default=None, description="Model semantic version if available")
    executionStatus: str = Field(
        default="SUCCESS",
        description="Provider run status: SUCCESS, DISABLED, NOT_CONFIGURED, FAILED, INCONCLUSIVE, INVALID_RESPONSE, NOT_APPROVED"
    )
    componentStatus: Optional[ComponentAnalysisStatus] = Field(
        default=None,
        description="Normalized component semantic status (SUCCESS_WITH_OBSERVATIONS, SUCCESS_WITH_NO_OBSERVATIONS, INCONCLUSIVE, FAILED, UNAVAILABLE)",
    )
    sourceType: Optional[str] = Field(
        default=None,
        description="Modality or source component type: 'CV' or 'VLM'",
    )
    evidenceId: Optional[str] = Field(
        default=None,
        description="Associated inspection evidence item identifier",
    )
    checkpointMetadata: Optional[Union[Dict[str, Any], str]] = Field(
        default=None,
        description="Cryptographic checksum and release metadata for model checkpoint",
    )
    rawObjectCategory: Optional[str] = Field(
        default=None,
        description="Original physical object category emitted by provider before normalization",
    )
    rawHazardCategory: Optional[str] = Field(
        default=None,
        description="Original defect or hazard category emitted by provider before normalization",
    )
    calibrationStatus: str = Field(
        default="NOT_CALIBRATED",
        description="Confidence calibration status: strictly NOT_CALIBRATED pending validated dataset",
    )
    coordinateConvention: Optional[str] = Field(
        default=None,
        description="Coordinate convention: 'gemini_0_1000', 'normalized', or 'absolute_pixels'",
    )
    observationDescription: Optional[str] = Field(
        default=None,
        description="Original descriptive reasoning or detection summary from provider",
    )
    uncertaintyMetadata: Optional[Dict[str, Any]] = Field(
        default=None,
        description="Documented optical caveats, visual limitations, or uncertainty indicators",
    )
    observationId: Optional[str] = Field(default=None, description="Original observation identifier from source provider")
    rawCategory: Optional[str] = Field(default=None, description="Unmodified category or class emitted by provider")
    rawSeverity: Optional[str] = Field(default=None, description="Raw severity rating proposed by provider")
    rawConfidence: Optional[float] = Field(default=None, description="Raw confidence score if provided [0.0 - 1.0]")
    boundingBox: Optional[BoundingBox] = Field(default=None, description="Spatial bounding box if localized by provider")
    error: Optional[str] = Field(default=None, description="Sanitized error description if provider encountered a failure")
    timestamp: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="UTC timestamp of provider execution",
    )

    @model_validator(mode="before")
    @classmethod
    def normalize_provider_provenance_aliases(cls, data: Any) -> Any:
        if isinstance(data, dict):
            if "providerName" not in data and "provider" in data:
                data["providerName"] = data["provider"]
            if "provider" not in data and "providerName" in data:
                data["provider"] = data["providerName"]
            if "modelId" not in data and "model" in data:
                data["modelId"] = data["model"]
            if "model" not in data and "modelId" in data:
                data["model"] = data["modelId"]
            if "executionStatus" not in data:
                data["executionStatus"] = "SUCCESS"
        return data

    @model_validator(mode="after")
    def sync_aliases_after(self) -> "ProviderProvenance":
        if not self.provider and self.providerName:
            self.provider = self.providerName
        if not self.model and self.modelId:
            self.model = self.modelId
        return self


class ConflictDetail(BaseModel):
    """Structured description of an identified cross-provider conflict or disagreement."""

    conflictType: ConflictType = Field(description="Classification of the discrepancy")
    description: str = Field(default="", description="Clear explanation of the disagreement for the reviewing officer")
    explanation: Optional[str] = Field(default=None, description="Human-readable explanation alias")
    evidenceId: Optional[str] = Field(default=None, description="Associated evidence identifier")
    geminiObservation: Optional[Any] = Field(
        default=None,
        description="Snapshot of VLM observation data (retained for backward compatibility)",
    )
    vlmObservation: Optional[Any] = Field(
        default=None,
        description="Snapshot of VLM observation data (provider-neutral)",
    )
    cvObservation: Optional[Any] = Field(
        default=None,
        description="Snapshot of CV observation data",
    )
    cvFinding: Optional[Any] = Field(
        default=None,
        description="Snapshot of CV finding data",
    )
    requiresOfficerAttention: bool = Field(
        default=True,
        description="Always True for unresolved discrepancies",
    )

    @model_validator(mode="before")
    @classmethod
    def normalize_conflict_inputs(cls, data: Any) -> Any:
        if isinstance(data, dict):
            if "description" not in data and "explanation" in data:
                data["description"] = data["explanation"]
            if "explanation" not in data and "description" in data:
                data["explanation"] = data["description"]
            if "cvObservation" not in data and "cvFinding" in data:
                data["cvObservation"] = data["cvFinding"]
            if "cvFinding" not in data and "cvObservation" in data:
                data["cvFinding"] = data["cvObservation"]
            if "geminiObservation" not in data and "vlmObservation" in data:
                data["geminiObservation"] = data["vlmObservation"]
            if "vlmObservation" not in data and "geminiObservation" in data:
                data["vlmObservation"] = data["geminiObservation"]
        return data

    @model_validator(mode="after")
    def sync_conflict_fields_after(self) -> "ConflictDetail":
        if not self.description and self.explanation:
            self.description = self.explanation
        if not self.explanation and self.description:
            self.explanation = self.description
        if self.geminiObservation is not None and self.vlmObservation is None:
            self.vlmObservation = self.geminiObservation
        elif self.vlmObservation is not None and self.geminiObservation is None:
            self.geminiObservation = self.vlmObservation
        if self.cvFinding is not None and self.cvObservation is None:
            self.cvObservation = self.cvFinding
        elif self.cvObservation is not None and self.cvFinding is None:
            self.cvFinding = self.cvObservation
        return self


class EvidenceValidationResult(BaseModel):
    """Result of evidence reference, dimension, and integrity validation."""

    evidenceId: uuid.UUID = Field(description="Validated evidence identifier")
    inspectionId: uuid.UUID = Field(description="Associated inspection identifier")
    isValid: bool = Field(description="True if evidence passes all verification gates")
    dimensions: Optional[Tuple[int, int]] = Field(default=None, description="Image (width, height) in pixels")
    validationErrors: List[str] = Field(default_factory=list, description="List of validation rejection reasons")
    checkedAt: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="UTC timestamp of validation check",
    )


class ReconciliationResult(BaseModel):
    """Complete reconciliation result model for inspection evidence."""

    evidenceId: uuid.UUID = Field(description="Evidence item identifier")
    status: ReconciliationStatus = Field(description="Overall reconciliation status")
    findings: List[Finding] = Field(default_factory=list, description="Reconciled findings")
    conflicts: List[ConflictDetail] = Field(default_factory=list, description="Recorded conflict details")
    geminiFindings: List[Finding] = Field(default_factory=list, description="VLM findings (backward compatibility alias)")
    vlmFindings: List[Finding] = Field(default_factory=list, description="VLM findings")
    cvFindings: List[Finding] = Field(default_factory=list, description="CV findings")
    explanation: Optional[str] = Field(default=None, description="Human-readable reconciliation narrative")
    isSafeInference: bool = Field(default=False, description="Strict safety invariant: False")

    @model_validator(mode="after")
    def sync_vlm_finding_aliases(self) -> "ReconciliationResult":
        if self.geminiFindings and not self.vlmFindings:
            self.vlmFindings = self.geminiFindings
        elif self.vlmFindings and not self.geminiFindings:
            self.geminiFindings = self.vlmFindings
        return self
