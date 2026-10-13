"""FireSphere.ai — Phase 3 Officer Verification Schemas.

Defines the contract for authorized Fire Safety Officer finding reviews,
assessments, manual corrections, optimistic concurrency control, and audit history.
"""

from datetime import datetime, timezone
from enum import Enum
from typing import Any, Dict, List, Optional
import uuid
from pydantic import BaseModel, Field, field_validator
from app.schemas.finding import Finding, FindingSeverity


class OfficerAssessmentType(str, Enum):
    """Authorized officer assessments for AI-proposed findings."""

    CONFIRMED_HAZARD = "CONFIRMED_HAZARD"
    NOT_A_HAZARD = "NOT_A_HAZARD"
    UNABLE_TO_DETERMINE = "UNABLE_TO_DETERMINE"
    ADDITIONAL_INSPECTION_REQUIRED = "ADDITIONAL_INSPECTION_REQUIRED"


class OfficerFindingVerificationRequest(BaseModel):
    """Payload for submitting an authorized Fire Officer finding verification."""

    findingId: uuid.UUID = Field(
        ...,
        description="UUID of the AI finding being verified",
    )
    evidenceReviewed: bool = Field(
        default=False,
        description=(
            "Explicit human verification that the evidence photo has been inspected. "
            "STRICT SAFETY INVARIANT: Defaults to False. Setting True does NOT confirm a hazard."
        ),
    )
    assessment: OfficerAssessmentType = Field(
        ...,
        description="Mandatory officer determination: CONFIRMED_HAZARD, NOT_A_HAZARD, UNABLE_TO_DETERMINE, ADDITIONAL_INSPECTION_REQUIRED",
    )
    officerId: str = Field(
        ...,
        min_length=1,
        description="Unique identifier of the verifying Fire Officer",
    )
    officerRole: str = Field(
        ...,
        min_length=1,
        description="Claimed role of verifying user (must be verified server-side against authorized roles)",
    )
    remarks: Optional[str] = Field(
        default=None,
        description="Officer remarks, explanations, or physical inspection notes",
    )
    categoryOverride: Optional[str] = Field(
        default=None,
        description="Optional officer correction of the hazard defect category",
    )
    severityOverride: Optional[FindingSeverity] = Field(
        default=None,
        description="Optional officer correction of the operational severity level",
    )
    additionalEvidenceIds: Optional[List[uuid.UUID]] = Field(
        default=None,
        description="Optional additional evidence references supplied during on-site inspection",
    )
    inspectionId: Optional[uuid.UUID] = Field(
        default=None,
        description="Optional client-supplied inspection correlation ID. Server authoritatively validates against URL inspection_id.",
    )
    tenantId: Optional[str] = Field(
        default=None,
        description="Optional client-supplied tenant boundary ID. Server authoritatively validates against trusted context.",
    )
    expectedVersion: Optional[int] = Field(
        default=None,
        description="Optimistic locking version for concurrency control",
    )

    @field_validator("officerId")
    @classmethod
    def validate_officer_id(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("officerId must not be empty or whitespace.")
        return v.strip()

    @field_validator("officerRole")
    @classmethod
    def validate_officer_role(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("officerRole must not be empty or whitespace.")
        return v.strip().upper()


class OfficerFindingVerificationResponse(BaseModel):
    """Response payload returned upon successfully recording an officer verification."""

    findingId: uuid.UUID = Field(description="UUID of the verified finding")
    inspectionId: Optional[uuid.UUID] = Field(default=None, description="Linked inspection identifier")
    tenantId: Optional[str] = Field(default=None, description="Linked tenant identifier")
    verificationStatus: str = Field(description="Updated status: OFFICER_CONFIRMED, OFFICER_DISMISSED, UNDER_REVIEW")
    officerAssessment: OfficerAssessmentType = Field(description="Recorded officer assessment")
    evidenceReviewed: bool = Field(description="Evidence reviewed flag state")
    updatedFinding: Finding = Field(description="Updated finding object with complete audit details")
    version: int = Field(description="New entity version after update")
    auditEntry: Dict[str, Any] = Field(description="Audit trail record appended during this action")
    verifiedAt: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="UTC timestamp of verification",
    )
    persistenceDurable: bool = Field(
        default=False,
        description="Explicit persistence indicator: False in AI-service in-memory cache; True once committed to Spring Boot PostgreSQL.",
    )
    persistenceStatus: str = Field(
        default="IN_MEMORY_EPHEMERAL_PENDING_SPRING_BOOT_PERSISTENCE",
        description="Authoritative storage durability lifecycle status.",
    )
    trustedIdentityAssertion: bool = Field(
        default=False,
        description="Explicit indicator whether officer identity was verified via trusted gateway assertion headers.",
    )
    identitySource: str = Field(
        default="CALLER_SUPPLIED_BODY_PENDING_SPRING_BOOT_VERIFICATION",
        description="Identity provenance: TRUSTED_FORWARDED_ASSERTION or CALLER_SUPPLIED_BODY_PENDING_SPRING_BOOT_VERIFICATION.",
    )

