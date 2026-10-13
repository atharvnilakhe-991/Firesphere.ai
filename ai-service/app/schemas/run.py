"""Internal representation and schema for an AI analysis run."""

from datetime import datetime, timezone
from enum import Enum
from typing import List, Optional, Dict, Any
import uuid
from pydantic import BaseModel, Field
from app.schemas.finding import Finding


class RunStatus(str, Enum):
    """Execution status for an AI analysis run."""

    PENDING = "PENDING"
    IN_PROGRESS = "IN_PROGRESS"
    COMPLETED = "COMPLETED"
    PARTIAL = "PARTIAL"
    INCONCLUSIVE = "INCONCLUSIVE"
    FAILED = "FAILED"


class AIRun(BaseModel):
    """Internal model representing an end-to-end AI analysis run on inspection evidence.

    Ensures complete traceability linking the input inspection ID, evidence items,
    model metadata, execution duration, and generated findings or errors.
    """

    runId: uuid.UUID = Field(default_factory=uuid.uuid4, description="Unique analysis run identifier")
    inspectionId: uuid.UUID = Field(description="UUID of the inspection being evaluated")
    inputEvidence: List[uuid.UUID] = Field(description="List of evidence item UUIDs analyzed during this run")
    status: RunStatus = Field(default=RunStatus.PENDING, description="Current run lifecycle status")
    model: str = Field(description="Name/identifier of the model executed")
    modelVersion: str = Field(description="Version string of the model executed")
    findings: List[Finding] = Field(default_factory=list, description="Findings generated during the run")
    startedAt: datetime = Field(default_factory=lambda: datetime.now(timezone.utc), description="UTC timestamp when run was initiated")
    completedAt: Optional[datetime] = Field(default=None, description="UTC timestamp when run finished")
    executionDurationMs: Optional[float] = Field(default=None, description="Total execution duration in milliseconds")
    errorMessage: Optional[str] = Field(default=None, description="Error message if run status is FAILED")
    errorDetails: Optional[Dict[str, Any]] = Field(default=None, description="Sanitized diagnostic details for failures")
    summary: Optional[Dict[str, Any]] = Field(default=None, description="Inspection-level aggregated AI summary")
    tenantId: Optional[str] = Field(default=None, description="Tenant identifier for multi-tenant isolation")
    modelApprovalStatus: str = Field(default="NOT_APPROVED", description="Model production approval status: NOT_APPROVED")
