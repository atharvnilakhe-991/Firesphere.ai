"""Pydantic schemas and domain model for AI Inspection Report Drafting and Human Review."""

from datetime import datetime, timezone
from enum import Enum
from typing import Any, Dict, List, Optional
import uuid
from pydantic import BaseModel, Field, field_validator
from app.schemas.finding import BoundingBox, Finding, FindingSeverity


class ReportStatus(str, Enum):
    """Lifecycle status of the generated inspection report."""

    DRAFT = "DRAFT"
    FINAL = "FINAL"


class ReviewState(str, Enum):
    """Human-in-the-loop inspection report review state."""

    DRAFT = "DRAFT"
    UNDER_REVIEW = "UNDER_REVIEW"
    OFFICER_EDITED = "OFFICER_EDITED"
    OFFICER_ACCEPTED = "OFFICER_ACCEPTED"
    SUPERSEDED = "SUPERSEDED"


class ReportModelInfo(BaseModel):
    """Metadata regarding the computer vision and AI intelligence models used."""

    modelName: str = Field(description="Name/identifier of the model executed")
    modelVersion: str = Field(description="Semantic version string of the model executed")
    calibrationStatus: str = Field(default="NOT_CALIBRATED", description="Confidence calibration status")
    executionDurationMs: Optional[float] = Field(default=None, description="Execution duration in ms")


class ReportExecutiveSummary(BaseModel):
    """Deterministic executive summary of the inspection analysis."""

    totalFindings: int = Field(default=0, description="Total count of findings detected")
    uniqueFindingTypes: int = Field(default=0, description="Count of distinct safety defect categories")
    highestSeverity: Optional[str] = Field(default=None, description="Highest operational severity rating")
    highestRiskLevel: Optional[str] = Field(default=None, description="Highest operational risk level: LOW, MEDIUM, HIGH, CRITICAL")
    highestRiskScore: Optional[int] = Field(default=None, description="Maximum operational risk score [0 - 100]")
    evidenceCount: int = Field(default=0, description="Total photographic evidence items analyzed")
    requiresOfficerReview: bool = Field(default=True, description="Always True for AI-generated advisory drafts")
    findingsRequiringOfficerReview: int = Field(default=0, description="Count of findings requiring on-site officer review")
    narrative: str = Field(description="Deterministic human-readable executive summary text")


class ReportEvidenceItem(BaseModel):
    """Summary of an analyzed photographic evidence asset with traceability."""

    evidenceId: uuid.UUID = Field(description="Unique identifier of the photographic evidence item")
    evidenceQuality: str = Field(default="ACCEPTABLE", description="Visual quality rating: GOOD, ACCEPTABLE, POOR, UNUSABLE")
    processingStatus: str = Field(default="SUCCESS", description="Preprocessing and inference status: SUCCESS or FAILED")
    warnings: List[str] = Field(default_factory=list, description="Advisory image quality warnings")
    findingsCount: int = Field(default=0, description="Number of findings linked to this evidence item")
    associatedFindingIds: List[uuid.UUID] = Field(default_factory=list, description="List of finding UUIDs discovered in this image")


class ReportFindingItem(BaseModel):
    """Report-formatted finding item with complete intelligence metadata and traceability."""

    findingId: uuid.UUID = Field(description="Unique finding identifier")
    evidenceId: uuid.UUID = Field(description="UUID reference to the parent evidence item")
    findingType: str = Field(description="Standardized safety defect class")
    confidence: float = Field(ge=0.0, le=1.0, description="Model prediction confidence score [0.0 - 1.0]")
    confidenceLevel: str = Field(description="Categorical interpretation: VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH")
    calibrationStatus: str = Field(default="NOT_CALIBRATED", description="Calibration status")
    severity: FindingSeverity = Field(description="Assessed operational severity")
    severityReason: Optional[str] = Field(default=None, description="Operational reasoning for severity rating")
    operationalRiskScore: Optional[int] = Field(default=None, description="Operational risk prioritization score [0 - 100]")
    operationalRiskLevel: Optional[str] = Field(default=None, description="Operational risk level: LOW, MEDIUM, HIGH, CRITICAL")
    riskFactors: Optional[List[Dict[str, Any]]] = Field(default=None, description="Transparent risk factor contributions")
    description: str = Field(description="Deterministic, non-statutory visual observation statement")
    explanation: str = Field(description="Evidence-grounded explainability narrative")
    verificationGuidance: List[str] = Field(default_factory=list, description="Class-specific on-site inspection checklist")
    evidenceQuality: Optional[str] = Field(default="ACCEPTABLE", description="Visual quality rating")
    complianceStatus: str = Field(default="REQUIRES_OFFICER_VERIFICATION", description="Regulatory boundary status")
    boundingBox: Optional[BoundingBox] = Field(default=None, description="Restored original photograph coordinates")
    isTestData: bool = Field(default=False, description="Flag indicating if finding is synthetic test data")
    tenantId: Optional[str] = Field(default=None, description="Tenant identifier")
    inspectionId: Optional[uuid.UUID] = Field(default=None, description="Inspection identifier")
    analysisRunId: Optional[uuid.UUID] = Field(default=None, description="AI analysis run identifier")
    officerVerificationStatus: str = Field(default="UNREVIEWED", description="Officer review status: UNREVIEWED, AI_SUGGESTION, OFFICER_CONFIRMED, OFFICER_DISMISSED")
    knownLimitations: List[str] = Field(default_factory=list, description="Explicit known limitations")
    reconciliationStatus: str = Field(default="UNRECONCILED", description="Reconciliation status")
    conflictType: str = Field(default="NONE", description="Conflict classification")
    conflictDetails: Optional[Dict[str, Any]] = Field(default=None, description="Discrepancy details")
    provenance: List[Dict[str, Any]] = Field(default_factory=list, description="Multi-provider execution provenance")
    validationStatus: str = Field(default="VALID", description="Observation validation status")
    evidenceReviewed: bool = Field(default=False, description="Officer evidence reviewed flag")
    officerAssessment: Optional[str] = Field(default=None, description="Officer verification assessment")
    officerRemarks: Optional[str] = Field(default=None, description="Officer verification remarks")
    officerCorrections: Optional[Dict[str, Any]] = Field(default=None, description="Officer corrections")


class ReportRiskSummary(BaseModel):
    """Aggregated operational risk summary for the inspection."""

    title: str = Field(default="AI Operational Risk Summary", description="Section title")
    highestSeverity: Optional[str] = Field(default=None, description="Highest operational severity across findings")
    highestRiskLevel: Optional[str] = Field(default=None, description="Highest operational risk level")
    highestRiskScore: Optional[int] = Field(default=None, description="Highest operational risk score [0 - 100]")
    totalFindings: int = Field(default=0, description="Total findings evaluated")
    riskDistribution: Dict[str, int] = Field(default_factory=dict, description="Finding counts grouped by risk level")
    requiresOfficerVerification: bool = Field(default=True, description="Requires physical verification flag")
    narrative: str = Field(description="Deterministic narrative summary of operational risk")
    disclaimer: str = Field(
        default=(
            "AI Operational Risk Summary reflects automated workflow prioritization based on visual evidence. "
            "It is NOT a legally binding fire-risk calculation or statutory safety rating."
        ),
        description="Regulatory disclaimer",
    )


class ReportVerificationAction(BaseModel):
    """Specific on-site verification action for inspecting Fire Safety Officers."""

    actionId: str = Field(description="Deterministic action identifier (e.g. VA-001)")
    findingId: uuid.UUID = Field(description="Associated finding UUID")
    findingType: str = Field(description="Defect category requiring verification")
    action: str = Field(description="Specific physical check for the officer")
    ambiguityCheck: str = Field(description="Potential camera or perspective ambiguity to resolve on site")
    priority: str = Field(description="Operational review priority: CRITICAL, HIGH, MEDIUM, LOW")


class ReportCorrectiveAction(BaseModel):
    """Deterministic, generic operational recommendation for mitigating detected hazard."""

    suggestionId: str = Field(description="Deterministic suggestion identifier (e.g. CA-001)")
    findingId: uuid.UUID = Field(description="Associated finding UUID")
    findingType: str = Field(description="Defect category")
    suggestedAction: str = Field(description="Generic operational corrective suggestion without fabricated legal clauses")
    actionType: str = Field(default="AI_SUGGESTED_CORRECTIVE_ACTION", description="Distinguishes advisory from official orders")
    scope: str = Field(description="Physical scope of the recommendation")
    disclaimer: str = Field(
        default="Advisory recommendation only. Official statutory orders must be determined by authorized Fire Officers.",
        description="Non-statutory disclaimer",
    )


class ReportUnresolvedItem(BaseModel):
    """Documented uncertainty or item requiring physical investigation."""

    itemId: str = Field(description="Deterministic item identifier (e.g. UNRES-001)")
    category: str = Field(description="Category of uncertainty (e.g. LOW_CONFIDENCE, POOR_EVIDENCE_QUALITY, UNCALIBRATED_MODEL)")
    description: str = Field(description="Clear explanation of the unresolved condition or uncertainty")
    recommendedAction: str = Field(description="Recommended follow-up for the reviewing officer")


class TraceabilityRecord(BaseModel):
    """Audit link connecting a report statement to its underlying structured source."""

    statementId: str = Field(description="Unique statement identifier (e.g. stmt-001)")
    reportSection: str = Field(description="Section of the report (e.g. EXECUTIVE_SUMMARY, FINDINGS, CORRECTIVE_ACTIONS)")
    statementText: str = Field(description="Exact report text or statement")
    sourceType: str = Field(description="Type of source data: FINDING, EVIDENCE, RISK_ENGINE, AI_METADATA")
    sourceId: str = Field(description="Identifier of source record")
    evidenceId: Optional[uuid.UUID] = Field(default=None, description="Linked photographic evidence UUID if applicable")
    findingId: Optional[uuid.UUID] = Field(default=None, description="Linked finding UUID if applicable")
    sourceField: str = Field(description="Field providing data (e.g. explanation, severityReason, verificationGuidance)")


class ReportAuditMetadata(BaseModel):
    """Comprehensive audit trail establishing complete provenance for the report draft."""

    reportId: uuid.UUID = Field(description="Unique report draft identifier")
    inspectionId: uuid.UUID = Field(description="Linked inspection record UUID")
    runId: uuid.UUID = Field(description="Linked AI analysis run UUID")
    generatedAt: datetime = Field(description="UTC timestamp when report draft was generated")
    generatorVersion: str = Field(description="Version of report generator engine")
    modelName: str = Field(description="Active CV model name")
    modelVersion: str = Field(description="Active CV model version")
    calibrationStatus: str = Field(default="NOT_CALIBRATED", description="Calibration state of CV model")
    isTestData: bool = Field(default=False, description="Flag indicating if report contains synthetic test data")
    aiDecisionAuthority: str = Field(
        default="NONE (ADVISORY_DRAFT_ONLY)",
        description="Explicit limitation confirming AI possesses no autonomous NOC authority",
    )


class InspectionReportDraft(BaseModel):
    """Structured, evidence-traceable, officer-reviewable AI Inspection Report Draft."""

    reportId: uuid.UUID = Field(default_factory=uuid.uuid4, description="Unique report draft identifier")
    inspectionId: uuid.UUID = Field(description="Inspection record UUID")
    runId: uuid.UUID = Field(description="AI analysis run UUID")
    generatedAt: datetime = Field(default_factory=lambda: datetime.now(timezone.utc), description="UTC timestamp of generation")
    reportVersion: str = Field(default="0.5.0-phase5", description="Report schema and generator version")
    status: ReportStatus = Field(default=ReportStatus.DRAFT, description="Report status: DRAFT")
    reviewState: ReviewState = Field(default=ReviewState.DRAFT, description="Human review lifecycle state")
    generatedBy: str = Field(default="FireSphere-AI-DeterministicReportGenerator-v0.5.0", description="Generator signature")
    dataClassification: str = Field(default="PRODUCTION_ADVISORY", description="'PRODUCTION_ADVISORY' or 'TEST_DATA'")
    isTestData: bool = Field(default=False, description="True if report is derived from synthetic test fixtures")
    testDataWarning: Optional[str] = Field(default=None, description="Prominent warning banner if report contains test data")
    modelInformation: ReportModelInfo = Field(description="Model provenance metadata")
    summary: ReportExecutiveSummary = Field(description="Executive summary section")
    evidenceReviewed: List[ReportEvidenceItem] = Field(default_factory=list, description="Traceable list of evidence analyzed")
    findings: List[ReportFindingItem] = Field(default_factory=list, description="Structured finding items")
    riskSummary: ReportRiskSummary = Field(description="Operational risk synthesis")
    verificationActions: List[ReportVerificationAction] = Field(default_factory=list, description="Actionable officer verification checks")
    correctiveActions: List[ReportCorrectiveAction] = Field(default_factory=list, description="Safe generic operational recommendations")
    unresolvedItems: List[ReportUnresolvedItem] = Field(default_factory=list, description="Explicit uncertainties and ambiguities")
    aiLimitations: List[str] = Field(default_factory=list, description="Tailored AI limitations")
    traceability: List[TraceabilityRecord] = Field(default_factory=list, description="Statement-to-source audit links")
    auditMetadata: ReportAuditMetadata = Field(description="Audit provenance records")
    officerRemarks: Optional[str] = Field(default=None, description="Officer-provided remarks (clearly marked as human-authored)")
    checklistSummary: str = Field(
        default="Checklist data was not provided to the AI report generator.",
        description="Structured checklist observations if supplied",
    )
    regulatoryNotice: str = Field(
        default=(
            "ADVISORY REPORT DRAFT — HUMAN OFFICER REVIEW REQUIRED. "
            "This document is an AI-generated inspection report draft prepared to assist an authorized Fire Safety Officer. "
            "The AI system does NOT make legal, statutory, or NOC approval/rejection decisions."
        ),
        description="Mandatory statutory notice",
    )

    @field_validator("reviewState", mode="before")
    @classmethod
    def validate_review_state_safety(cls, value: Any) -> Any:
        """Enforces that AI can never set an approved or rejected compliance state."""
        if isinstance(value, str):
            normalized = value.strip().upper()
            if normalized in ("AI_APPROVED", "AI_REJECTED", "AI_NOC_APPROVED", "AI_NOC_REJECTED", "APPROVED", "REJECTED"):
                raise ValueError(
                    f"Review state '{value}' is forbidden. AI cannot autonomously issue NOC approvals or rejections. "
                    "Report review state must be DRAFT, UNDER_REVIEW, OFFICER_EDITED, OFFICER_ACCEPTED, or SUPERSEDED."
                )
        return value


class InspectionReportDraftRequest(BaseModel):
    """Payload for POST /internal/ai/inspection-report-draft."""

    inspectionId: uuid.UUID = Field(..., description="Unique inspection identifier")
    runId: Optional[uuid.UUID] = Field(default=None, description="Existing AI analysis run UUID if already executed")
    evidenceIds: Optional[List[uuid.UUID]] = Field(default=None, description="List of evidence UUIDs to analyze if runId not provided")
    findings: Optional[List[Finding]] = Field(default=None, description="Existing findings list if already analyzed")
    officerRemarks: Optional[str] = Field(default=None, description="Optional notes or remarks from reviewing officer")
    checklistData: Optional[Dict[str, Any]] = Field(default=None, description="Optional structured inspection checklist data")
    occupancyContext: Optional[str] = Field(default="UNKNOWN", description="Premises occupancy category (e.g. COMMERCIAL, ASSEMBLY)")
    historicalContext: Optional[str] = Field(default="UNAVAILABLE", description="Prior compliance history if available")
    testMode: Optional[bool] = Field(default=False, description="Flag for synthetic test data generation")
