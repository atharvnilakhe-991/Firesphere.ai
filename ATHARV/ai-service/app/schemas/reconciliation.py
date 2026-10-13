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


class ProviderProvenance(BaseModel):
    """Auditable provenance record tracking execution details for an individual AI provider."""

    providerName: str = Field(description="Name of the analysis provider (e.g. google-gemini, firesphere-cv-baseline)")
    modelId: str = Field(description="Model identifier or version string")
    modelVersion: Optional[str] = Field(default=None, description="Model semantic version if available")
    executionStatus: str = Field(
        description="Provider run status: SUCCESS, DISABLED, NOT_CONFIGURED, FAILED, INCONCLUSIVE, INVALID_RESPONSE, NOT_APPROVED"
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
