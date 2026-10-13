"""Request and response schemas for AI Inspection Analysis."""

from typing import List, Optional, Dict, Any
import uuid
from pydantic import BaseModel, Field
from app.schemas.finding import Finding
from app.schemas.run import RunStatus


class InspectionAISummary(BaseModel):
    """Aggregated inspection-level AI summary for human Fire Safety Officer review."""

    totalFindings: int = Field(default=0, description="Total count of detected findings")
    uniqueFindingTypes: int = Field(default=0, description="Number of distinct defect categories detected")
    highestSeverity: Optional[str] = Field(default=None, description="Highest operational severity among findings")
    highestRiskLevel: Optional[str] = Field(default=None, description="Highest operational risk level: LOW, MEDIUM, HIGH, CRITICAL")
    highestRiskScore: Optional[int] = Field(default=None, description="Maximum operational risk score across findings [0 - 100]")
    evidenceCount: int = Field(default=0, description="Total evidence photographs evaluated")
    requiresOfficerReview: bool = Field(default=False, description="Flag indicating if any finding or warning warrants manual officer review")
    findingsRequiringOfficerReview: int = Field(default=0, description="Number of findings requiring on-site officer verification")
    confidenceSummary: Dict[str, Any] = Field(default_factory=dict, description="Distribution of confidence levels and average confidence")
    evidenceQualitySummary: Dict[str, int] = Field(default_factory=dict, description="Count of evidence items by visual quality level")
    calibrationStatus: str = Field(default="NOT_CALIBRATED", description="Calibration status: NOT_CALIBRATED (pending validation dataset)")
    cvStatus: Optional[str] = Field(default=None, description="CV component execution/semantic status")
    vlmStatus: Optional[str] = Field(default=None, description="VLM component execution/semantic status")
    reconciliationStatus: Optional[str] = Field(default=None, description="Overall reconciliation status across evidence")
    complianceDecisionNote: str = Field(
        default=(
            "All AI findings are advisory visual observations. The AI system does NOT approve or reject "
            "Fire NOC applications. Authorized Fire Officer on-site verification is required."
        ),
        description="Explicit regulatory governance boundary notice",
    )


class InspectionAnalysisRequest(BaseModel):
    """Payload for POST /internal/ai/inspection-analysis."""

    inspectionId: uuid.UUID = Field(
        ...,
        description="Unique identifier of the inspection record",
        examples=["9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"],
    )
    evidenceIds: List[uuid.UUID] = Field(
        ...,
        min_length=1,
        description="Non-empty list of evidence file UUIDs to be analyzed",
        examples=[["c3b88b22-8239-4d69-a1b9-87cfa90f1d3e"]],
    )
    testMode: Optional[bool] = Field(
        default=False,
        description="Internal test flag for generating synthetic test findings during integration tests",
    )
    occupancyContext: Optional[str] = Field(
        default="UNKNOWN",
        description="Premises occupancy category if provided by backend (e.g. RESIDENTIAL, COMMERCIAL, ASSEMBLY, HAZARDOUS)",
    )
    historicalContext: Optional[str] = Field(
        default="UNAVAILABLE",
        description="Prior inspection compliance record if provided by backend",
    )
    tenantId: Optional[str] = Field(
        default="default",
        description="Tenant identifier for multi-tenant isolation",
    )


class InspectionAnalysisResponse(BaseModel):
    """Response payload for POST /internal/ai/inspection-analysis."""

    runId: uuid.UUID = Field(
        description="Unique identifier for this AI analysis run",
        examples=["4fa85f64-5717-4562-b3fc-2c963f66afa6"],
    )
    status: RunStatus = Field(
        description="Overall processing status of the analysis run",
        examples=["COMPLETED"],
    )
    findings: List[Finding] = Field(
        default_factory=list,
        description="List of detected safety non-compliance findings",
    )
    summary: Optional[InspectionAISummary] = Field(
        default=None,
        description="Inspection-level aggregated AI summary for the reviewing Fire Officer",
    )
    model: Optional[str] = Field(
        default=None,
        description="Model identifier responsible for the analysis",
    )
    modelVersion: Optional[str] = Field(
        default=None,
        description="Semantic version of the model executed",
    )
    executionDurationMs: Optional[float] = Field(
        default=None,
        description="Total execution time in milliseconds",
    )
    tenantId: Optional[str] = Field(
        default=None,
        description="Tenant identifier for multi-tenant isolation",
    )
    modelApprovalStatus: str = Field(
        default="NOT_APPROVED",
        description="Model production approval status: NOT_APPROVED",
    )
    calibrationStatus: str = Field(
        default="NOT_CALIBRATED",
        description="Statistical calibration status: NOT_CALIBRATED",
    )

