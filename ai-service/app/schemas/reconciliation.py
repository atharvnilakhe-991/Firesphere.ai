"""FireSphere.ai — Phase 3 Reconciliation & Multi-Provider Provenance Schemas.

Defines deterministic reconciliation statuses, conflict types, provider provenance models,
and evidence validation schemas to coordinate Gemini VLM and Computer Vision model outputs.
"""

from datetime import datetime, timezone
from enum import Enum
from typing import Any, Dict, List, Optional, Tuple
import uuid
from pydantic import BaseModel, Field
from app.schemas.finding import BoundingBox


class ReconciliationStatus(str, Enum):
    """Deterministic status of cross-provider finding reconciliation."""

    UNRECONCILED = "UNRECONCILED"
    AGREEMENT = "AGREEMENT"
    SINGLE_PROVIDER = "SINGLE_PROVIDER"
    DISCREPANCY = "DISCREPANCY"
    CONFLICT = "CONFLICT"
    PARTIAL_INCONCLUSIVE = "PARTIAL_INCONCLUSIVE"
    FAILED = "FAILED"
    NO_OBSERVATIONS = "NO_OBSERVATIONS"
    INVALID = "INVALID"


class ConflictType(str, Enum):
    """Categorical classification of model disagreements."""

    NONE = "NONE"
    CATEGORY_MISMATCH = "CATEGORY_MISMATCH"
    LOCATION_MISMATCH = "LOCATION_MISMATCH"
    SEVERITY_MISMATCH = "SEVERITY_MISMATCH"
    DETECTION_ABSENCE = "DETECTION_ABSENCE"
    UNRESOLVED_DISAGREEMENT = "UNRESOLVED_DISAGREEMENT"


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

    providerName: str = Field(description="Name of the analysis provider (e.g. google-gemini, firesphere-cv-baseline)")
    modelId: str = Field(description="Model identifier or version string")
    modelVersion: Optional[str] = Field(default=None, description="Model semantic version if available")
    executionStatus: str = Field(
        description="Provider run status: SUCCESS, DISABLED, NOT_CONFIGURED, FAILED, INCONCLUSIVE, INVALID_RESPONSE, NOT_APPROVED"
    )
    componentStatus: Optional[ComponentAnalysisStatus] = Field(
        default=None,
        description="Normalized component semantic status (SUCCESS_WITH_OBSERVATIONS, SUCCESS_WITH_NO_OBSERVATIONS, INCONCLUSIVE, FAILED, UNAVAILABLE)",
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


class ConflictDetail(BaseModel):
    """Structured description of an identified cross-provider conflict or disagreement."""

    conflictType: ConflictType = Field(description="Classification of the discrepancy")
    description: str = Field(description="Clear explanation of the disagreement for the reviewing officer")
    geminiObservation: Optional[Dict[str, Any]] = Field(default=None, description="Snapshot of Gemini observation data")
    cvObservation: Optional[Dict[str, Any]] = Field(default=None, description="Snapshot of CV observation data")
    requiresOfficerAttention: bool = Field(default=True, description="Always True for unresolved discrepancies")


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
