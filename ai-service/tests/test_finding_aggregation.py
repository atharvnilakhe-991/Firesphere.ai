"""Tests for InspectionSummaryAggregator, deduplication, executive summary, and end-to-end API integration."""

import uuid
import pytest
from fastapi.testclient import TestClient
from app.intelligence.aggregation import InspectionSummaryAggregator
from app.main import app
from app.pipelines.inspection_pipeline import InspectionPipeline
from app.schemas.finding import BoundingBox, Finding, FindingSeverity
from app.storage.evidence_client import evidence_client


def test_deduplicate_identical_findings():
    """Verifies that findings identifying identical boxes and defect classes on the same evidence are deduplicated."""
    eid = uuid.uuid4()
    box1 = BoundingBox(xMin=0.2, yMin=0.2, xMax=0.4, yMax=0.4)
    box2 = BoundingBox(xMin=0.201, yMin=0.199, xMax=0.401, yMax=0.399)  # Rounds to same coords

    f1 = Finding(
        findingId=uuid.uuid4(),
        evidenceId=eid,
        findingType="FIRE_EXTINGUISHER_MISSING",
        confidence=0.85,
        severity=FindingSeverity.HIGH,
        explanation="Finding 1",
        model="firesphere-inspection-cv",
        modelVersion="0.3.0-phase3",
        boundingBox=box1,
    )
    f2 = Finding(
        findingId=uuid.uuid4(),
        evidenceId=eid,
        findingType="FIRE_EXTINGUISHER_MISSING",
        confidence=0.84,
        severity=FindingSeverity.HIGH,
        explanation="Finding 2 duplicate",
        model="firesphere-inspection-cv",
        modelVersion="0.3.0-phase3",
        boundingBox=box2,
    )

    deduped = InspectionSummaryAggregator.deduplicate_findings([f1, f2])
    assert len(deduped) == 1


def test_aggregate_summary_metrics():
    """Verifies that InspectionSummaryAggregator produces accurate executive summary metrics."""
    eid1 = uuid.uuid4()
    eid2 = uuid.uuid4()

    f1 = Finding(
        findingId=uuid.uuid4(),
        evidenceId=eid1,
        findingType="EMERGENCY_EXIT_OBSTRUCTED",
        confidence=0.90,
        confidenceLevel="VERY_HIGH",
        severity=FindingSeverity.CRITICAL,
        operationalRiskScore=92,
        operationalRiskLevel="CRITICAL",
        explanation="Finding 1",
        model="firesphere-inspection-cv",
        modelVersion="0.3.0-phase3",
    )
    f2 = Finding(
        findingId=uuid.uuid4(),
        evidenceId=eid2,
        findingType="FIRE_EXTINGUISHER_MISSING",
        confidence=0.75,
        confidenceLevel="HIGH",
        severity=FindingSeverity.HIGH,
        operationalRiskScore=78,
        operationalRiskLevel="HIGH",
        explanation="Finding 2",
        model="firesphere-inspection-cv",
        modelVersion="0.3.0-phase3",
    )

    quality_map = {str(eid1): "GOOD", str(eid2): "ACCEPTABLE"}
    summary = InspectionSummaryAggregator.aggregate(
        findings=[f1, f2],
        evidence_ids=[eid1, eid2],
        evidence_quality_records=quality_map,
        has_quality_warnings=False,
    )

    assert summary.totalFindings == 2
    assert summary.uniqueFindingTypes == 2
    assert summary.highestSeverity == "CRITICAL"
    assert summary.highestRiskLevel == "CRITICAL"
    assert summary.highestRiskScore == 92
    assert summary.evidenceCount == 2
    assert summary.requiresOfficerReview is True
    assert summary.findingsRequiringOfficerReview == 2
    assert summary.confidenceSummary["distribution"]["VERY_HIGH"] == 1
    assert summary.confidenceSummary["distribution"]["HIGH"] == 1
    assert summary.evidenceQualitySummary["GOOD"] == 1
    assert summary.evidenceQualitySummary["ACCEPTABLE"] == 1
    assert summary.calibrationStatus == "NOT_CALIBRATED"


def test_end_to_end_api_summary_and_enriched_findings():
    """Verifies that POST /internal/ai/inspection-analysis returns enriched findings and the executive AI summary."""
    client = TestClient(app)
    eid = uuid.uuid4()

    insp_id = uuid.uuid4()
    payload = {
        "inspectionId": str(insp_id),
        "evidenceIds": [str(eid)],
        "testMode": True,
        "occupancyContext": "COMMERCIAL",
        "historicalContext": "COMPLIANT",
    }

    response = client.post("/internal/ai/inspection-analysis", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert data["status"] == "COMPLETED"
    assert "summary" in data
    summary = data["summary"]
    assert summary is not None
    assert summary["totalFindings"] > 0
    assert summary["calibrationStatus"] == "NOT_CALIBRATED"
    assert "complianceDecisionNote" in summary
    assert "Fire Officer" in summary["complianceDecisionNote"]

    # Verify enriched finding fields
    finding = data["findings"][0]
    assert "confidenceLevel" in finding
    assert finding["confidenceLevel"] in ["HIGH", "VERY_HIGH", "MEDIUM", "LOW", "VERY_LOW"]
    assert finding["calibrationStatus"] == "NOT_CALIBRATED"
    assert finding["operationalRiskScore"] is not None
    assert finding["operationalRiskLevel"] is not None
    assert isinstance(finding["riskFactors"], list)
    assert len(finding["riskFactors"]) > 0
    assert "severityReason" in finding
    assert "verificationGuidance" in finding
    assert len(finding["verificationGuidance"]) >= 3
    assert finding["complianceStatus"] == "REQUIRES_OFFICER_VERIFICATION"
    assert finding["evidenceQuality"] in ["GOOD", "ACCEPTABLE", "POOR", "UNUSABLE"]
    assert finding["occupancyContext"] == "COMMERCIAL"
    assert finding["isTestData"] is True


def test_production_mode_zero_fake_findings_and_summary():
    """Verifies that in production mode (testMode=False) with no weights, no findings are fabricated and summary is clean."""
    client = TestClient(app)
    eid = uuid.uuid4()

    insp_id = uuid.uuid4()
    payload = {
        "inspectionId": str(insp_id),
        "evidenceIds": [str(eid)],
        "testMode": False,
    }

    response = client.post("/internal/ai/inspection-analysis", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert data["status"] == "COMPLETED"
    assert len(data["findings"]) == 0
    summary = data["summary"]
    assert summary["totalFindings"] == 0
    assert summary["uniqueFindingTypes"] == 0
    assert summary["highestSeverity"] is None
    assert summary["highestRiskScore"] is None

