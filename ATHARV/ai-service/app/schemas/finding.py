"""Finding contract and schema definitions for AI detection results."""

from datetime import datetime, timezone
from enum import Enum
from typing import Optional, Dict, List, Any
import uuid
from pydantic import BaseModel, Field, field_validator


class FindingSeverity(str, Enum):
    """Standardized severity rating for fire safety non-compliance findings."""

    INFORMATIONAL = "INFORMATIONAL"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class FindingType(str, Enum):
    """Categorized safety hazard types recognized by the FireSphere AI inspection model."""

    FIRE_EXTINGUISHER_MISSING = "FIRE_EXTINGUISHER_MISSING"
    FIRE_EXTINGUISHER_BLOCKED = "FIRE_EXTINGUISHER_BLOCKED"
    FIRE_EXTINGUISHER_EXPIRED = "FIRE_EXTINGUISHER_EXPIRED"
    EMERGENCY_EXIT_OBSTRUCTED = "EMERGENCY_EXIT_OBSTRUCTED"
    EXIT_SIGNAGE_MISSING = "EXIT_SIGNAGE_MISSING"
    SPRINKLER_HEAD_DEFECT = "SPRINKLER_HEAD_DEFECT"
    ELECTRICAL_PANEL_HAZARD = "ELECTRICAL_PANEL_HAZARD"
    COMBUSTIBLE_MATERIAL_HAZARD = "COMBUSTIBLE_MATERIAL_HAZARD"
    MANUAL_CALL_POINT_BLOCKED = "MANUAL_CALL_POINT_BLOCKED"
    HOSE_REEL_OBSTRUCTED = "HOSE_REEL_OBSTRUCTED"
    GENERIC_FIRE_SAFETY_DEFECT = "GENERIC_FIRE_SAFETY_DEFECT"


class ConfidenceLevel(str, Enum):
    """Categorical interpretation of model prediction confidence."""

    VERY_LOW = "VERY_LOW"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    VERY_HIGH = "VERY_HIGH"


class ComplianceStatus(str, Enum):
    """Regulatory safety boundary: AI observation vs authorized officer decision."""

    REQUIRES_OFFICER_VERIFICATION = "REQUIRES_OFFICER_VERIFICATION"
    NOT_ASSESSED = "NOT_ASSESSED"
    AI_ASSISTED_ONLY = "AI_ASSISTED_ONLY"


class BoundingBox(BaseModel):
    """Normalized spatial coordinates for object detection on inspection evidence."""

    xMin: float = Field(ge=0.0, le=1.0, description="Normalized minimum X coordinate")
    yMin: float = Field(ge=0.0, le=1.0, description="Normalized minimum Y coordinate")
    xMax: float = Field(ge=0.0, le=1.0, description="Normalized maximum X coordinate")
    yMax: float = Field(ge=0.0, le=1.0, description="Normalized maximum Y coordinate")


class Finding(BaseModel):
    """Evidence-linked AI finding schema.

    Represents a specific observation or defect identified in an inspection evidence item.
    Explainable and reviewable by a human Fire Officer.
    """

    findingId: uuid.UUID = Field(default_factory=uuid.uuid4, description="Unique finding identifier")
    evidenceId: uuid.UUID = Field(description="UUID reference to the specific inspection evidence item")
    findingType: str = Field(description="Category or type of safety defect or observation")
    confidence: float = Field(ge=0.0, le=1.0, description="Model prediction confidence score [0.0 - 1.0]")
    severity: FindingSeverity = Field(description="Assessed severity level of the finding")
    explanation: str = Field(description="Human-readable explanation of why this finding was generated")
    model: str = Field(description="Name of the model that generated this finding")
    modelVersion: str = Field(description="Version of the model that generated this finding")
    boundingBox: Optional[BoundingBox] = Field(default=None, description="Optional bounding box for CV localization")
    isTestData: bool = Field(default=False, description="Flag indicating if this finding is synthetic/test data")
    createdAt: datetime = Field(default_factory=lambda: datetime.now(timezone.utc), description="UTC creation timestamp")

    # Phase 4 Intelligence Layer Enhancements (Fully backward-compatible with defaults)
    confidenceLevel: str = Field(
        default="MEDIUM",
        description="Categorical interpretation: VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH",
    )
    calibrationStatus: str = Field(
        default="NOT_CALIBRATED",
        description="Confidence calibration status: NOT_CALIBRATED (pending validation dataset)",
    )
    operationalRiskScore: Optional[int] = Field(
        default=None,
        ge=0,
        le=100,
        description="Operational prioritization score [0 - 100]",
    )
    operationalRiskLevel: Optional[str] = Field(
        default=None,
        description="Operational risk level: LOW, MEDIUM, HIGH, CRITICAL",
    )
    riskFactors: Optional[List[Dict[str, Any]]] = Field(
        default=None,
        description="Transparent contributing factors to operational risk score",
    )
    severityReason: Optional[str] = Field(
        default=None,
        description="Operational reasoning for the assigned severity",
    )
    verificationGuidance: Optional[List[str]] = Field(
        default_factory=list,
        description="Specific on-site checks for inspecting Fire Officer",
    )
    evidenceQuality: Optional[str] = Field(
        default="ACCEPTABLE",
        description="Visual quality rating of source evidence: GOOD, ACCEPTABLE, POOR, UNUSABLE",
    )
    complianceStatus: ComplianceStatus = Field(
        default=ComplianceStatus.REQUIRES_OFFICER_VERIFICATION,
        description="Regulatory boundary: REQUIRES_OFFICER_VERIFICATION, NOT_ASSESSED, AI_ASSISTED_ONLY",
    )
    occupancyContext: str = Field(
        default="UNKNOWN",
        description="Premises occupancy category if known, otherwise UNKNOWN",
    )
    historicalContext: str = Field(
        default="UNAVAILABLE",
        description="Prior inspection compliance record if known, otherwise UNAVAILABLE",
    )

    # Phase 10 Production Integration & Identity Linkage
    tenantId: Optional[str] = Field(
        default=None,
        description="Tenant identifier for multi-tenant isolation and auditable ownership",
    )
    inspectionId: Optional[uuid.UUID] = Field(
        default=None,
        description="Inspection session identifier connecting finding to the physical inspection",
    )
    analysisRunId: Optional[uuid.UUID] = Field(
        default=None,
        description="AI analysis run identifier connecting finding to the exact model execution",
    )
    perceptionClass: Optional[str] = Field(
        default=None,
        description="Underlying computer vision perception class name (e.g. FIRE_EXTINGUISHER)",
    )
    knownLimitations: List[str] = Field(
        default_factory=list,
        description="Documented AI limitations and edge-case caveats for this finding category",
    )
    officerVerificationStatus: str = Field(
        default="UNREVIEWED",
        description="Officer review interface status: UNREVIEWED, AI_SUGGESTION, OFFICER_CONFIRMED, OFFICER_DISMISSED",
    )
    modelApprovalStatus: str = Field(
        default="NOT_APPROVED",
        description="Model deployment approval status: NOT_APPROVED",
    )

    # Phase 3 Orchestration, Reconciliation & Officer Verification Fields
    reconciliationStatus: str = Field(
        default="UNRECONCILED",
        description="Reconciliation status: UNRECONCILED, AGREEMENT, SINGLE_PROVIDER, CONFLICT, DISCREPANCY, PARTIAL_INCONCLUSIVE, NO_OBSERVATIONS",
    )
    conflictType: str = Field(
        default="NONE",
        description="Conflict classification: NONE, CATEGORY_MISMATCH, LOCATION_MISMATCH, SEVERITY_MISMATCH, DETECTION_ABSENCE, UNRESOLVED_DISAGREEMENT",
    )
    conflictDetails: Optional[Dict[str, Any]] = Field(
        default=None,
        description="Explicit structured details on model discrepancies and disagreements",
    )
    provenance: List[Dict[str, Any]] = Field(
        default_factory=list,
        description="Traceable list of provider source observations and metadata",
    )
    validationStatus: str = Field(
        default="VALID",
        description="Evidence and observation validation status: VALID, INVALID_COORDINATES, INVALID_SCHEMA, UNVERIFIED",
    )
    evidenceReviewed: bool = Field(
        default=False,
        description="Human Fire Officer visual evidence review flag (strictly False by default; reviewing does not imply hazard confirmation)",
    )
    officerAssessment: Optional[str] = Field(
        default=None,
        description="Officer assessment: CONFIRMED_HAZARD, NOT_A_HAZARD, UNABLE_TO_DETERMINE, ADDITIONAL_INSPECTION_REQUIRED",
    )
    officerRemarks: Optional[str] = Field(
        default=None,
        description="Authorized officer remarks and site notes",
    )
    officerCorrections: Optional[Dict[str, Any]] = Field(
        default=None,
        description="Officer corrections to category, severity, or location",
    )
    reviewedByOfficerId: Optional[str] = Field(
        default=None,
        description="Identifier of the verifying Fire Officer",
    )
    reviewedAt: Optional[datetime] = Field(
        default=None,
        description="UTC timestamp when officer completed review",
    )
    auditHistory: List[Dict[str, Any]] = Field(
        default_factory=list,
        description="Full auditable history of model predictions, reconciliation steps, and officer modifications",
    )
    version: int = Field(
        default=1,
        description="Optimistic locking version counter for concurrency control",
    )
    updatedAt: Optional[datetime] = Field(
        default=None,
        description="UTC timestamp of the latest finding modification",
    )

    @field_validator("complianceStatus", mode="before")
    @classmethod
    def validate_compliance_boundary(cls, value: Any) -> Any:
        """Enforces strict AI safety boundary: AI cannot autonomously approve or reject NOC compliance."""
        if isinstance(value, str):
            normalized = value.strip().upper()
            if normalized in ("AI_APPROVED", "AI_REJECTED", "APPROVED", "REJECTED"):
                raise ValueError(
                    f"Compliance status '{value}' is forbidden. AI cannot autonomously approve or reject "
                    "Fire NOC compliance. Only authorized human Fire Officers can issue compliance decisions."
                )
        return value

