"""Comprehensive tests for AI Inspection Report Drafting and Officer Productivity (Phase 5)."""

import uuid
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from app.main import app
from app.reporting.corrective_actions import CorrectiveActionEngine
from app.reporting.deterministic_generator import DeterministicReportGenerator
from app.reporting.limitations import AILimitationsProvider
from app.reporting.review import ReviewLifecycleManager
from app.reporting.generator import BaseReportGenerator, FutureLLMReportGenerator
from app.schemas.finding import BoundingBox, Finding, FindingSeverity
from app.schemas.report import (
    InspectionReportDraft,
    ReportStatus,
    ReviewState,
)


def create_sample_finding(
    finding_type: str = "FIRE_EXTINGUISHER_MISSING",
    severity: FindingSeverity = FindingSeverity.HIGH,
    confidence: float = 0.85,
    operational_risk_score: int = 75,
    evidence_id: uuid.UUID = None,
    is_test_data: bool = False,
) -> Finding:
    """Helper creating a fully populated Finding for report drafting tests."""
    eid = evidence_id or uuid.uuid4()
    return Finding(
        findingId=uuid.uuid4(),
        evidenceId=eid,
        findingType=finding_type,
        confidence=confidence,
        confidenceLevel="HIGH" if confidence >= 0.70 else "MEDIUM",
        calibrationStatus="NOT_CALIBRATED",
        severity=severity,
        severityReason=f"Operational reasoning for {severity.value} severity.",
        operationalRiskScore=operational_risk_score,
        operationalRiskLevel="HIGH" if operational_risk_score >= 65 else "MEDIUM",
        riskFactors=[
            {"factor": "finding_severity", "contribution": 40, "description": "Base severity."},
            {"factor": "model_confidence", "contribution": 21, "description": "Model confidence."},
        ],
        explanation=f"Visual detection of potential {finding_type} condition.",
        verificationGuidance=[
            "Physically verify equipment at designated location.",
            "Inspect physical clearance perimeter.",
            "Review local maintenance collar logs.",
        ],
        evidenceQuality="GOOD",
        complianceStatus="REQUIRES_OFFICER_VERIFICATION",
        boundingBox=BoundingBox(xMin=0.1, yMin=0.2, xMax=0.4, yMax=0.5),
        model="yolo-v8-safety-mock",
        modelVersion="v1.0.0",
        isTestData=is_test_data,
    )


def test_basic_report_generation():
    """Verifies that DeterministicReportGenerator produces a structured draft with all required sections."""
    generator = DeterministicReportGenerator()
    insp_id = uuid.uuid4()
    run_id = uuid.uuid4()
    eid = uuid.uuid4()
    finding = create_sample_finding(evidence_id=eid)

    report = generator.generate_draft(
        inspection_id=insp_id,
        run_id=run_id,
        findings=[finding],
        evidence_ids=[eid],
        evidence_quality_records={str(eid): "GOOD"},
        model_name="firesphere-inspection-cv",
        model_version="0.3.0-phase3",
    )

    assert isinstance(report, InspectionReportDraft)
    assert report.inspectionId == insp_id
    assert report.runId == run_id
    assert report.status == ReportStatus.DRAFT
    assert report.reviewState == ReviewState.DRAFT
    assert report.summary.totalFindings == 1
    assert report.summary.highestSeverity == "HIGH"
    assert report.summary.requiresOfficerReview is True
    assert len(report.evidenceReviewed) == 1
    assert len(report.findings) == 1
    assert report.riskSummary.totalFindings == 1
    assert len(report.verificationActions) == 1
    assert len(report.correctiveActions) == 1
    assert len(report.aiLimitations) >= 4
    assert len(report.traceability) > 0
    assert report.auditMetadata.aiDecisionAuthority == "NONE (ADVISORY_DRAFT_ONLY)"


def test_empty_findings_honest_semantics():
    """Verifies that zero findings produces an honest statement and does NOT claim compliance."""
    generator = DeterministicReportGenerator()
    insp_id = uuid.uuid4()
    run_id = uuid.uuid4()
    eid = uuid.uuid4()

    report = generator.generate_draft(
        inspection_id=insp_id,
        run_id=run_id,
        findings=[],
        evidence_ids=[eid],
        evidence_quality_records={str(eid): "GOOD"},
    )

    assert report.summary.totalFindings == 0
    assert report.summary.highestSeverity is None
    assert report.summary.highestRiskScore is None
    assert len(report.findings) == 0

    narrative = report.summary.narrative
    assert "No AI findings were generated" in narrative
    # MUST NOT falsely claim building compliance or passing inspection
    assert "The building is compliant" not in narrative
    assert "No violations exist" not in narrative
    assert "Inspection passed" not in narrative
    assert "Mandatory human Fire Officer physical inspection is required" in narrative


def test_multiple_findings_and_evidence_traceability():
    """Verifies evidence-to-finding linkage across multiple photographs and defect categories."""
    generator = DeterministicReportGenerator()
    insp_id = uuid.uuid4()
    run_id = uuid.uuid4()
    eid1 = uuid.uuid4()
    eid2 = uuid.uuid4()

    f1 = create_sample_finding("EMERGENCY_EXIT_OBSTRUCTED", FindingSeverity.CRITICAL, 0.92, 90, eid1)
    f2 = create_sample_finding("FIRE_EXTINGUISHER_BLOCKED", FindingSeverity.HIGH, 0.78, 70, eid1)
    f3 = create_sample_finding("EXIT_SIGNAGE_MISSING", FindingSeverity.MEDIUM, 0.65, 55, eid2)

    report = generator.generate_draft(
        inspection_id=insp_id,
        run_id=run_id,
        findings=[f1, f2, f3],
        evidence_ids=[eid1, eid2],
        evidence_quality_records={str(eid1): "GOOD", str(eid2): "ACCEPTABLE"},
    )

    assert report.summary.totalFindings == 3
    assert report.summary.uniqueFindingTypes == 3
    assert report.summary.highestSeverity == "CRITICAL"
    assert report.summary.highestRiskScore == 90
    assert len(report.evidenceReviewed) == 2

    # Evidence 1 should link to f1 and f2
    e1_item = next(e for e in report.evidenceReviewed if e.evidenceId == eid1)
    assert e1_item.findingsCount == 2
    assert set(e1_item.associatedFindingIds) == {f1.findingId, f2.findingId}

    # Evidence 2 should link to f3
    e2_item = next(e for e in report.evidenceReviewed if e.evidenceId == eid2)
    assert e2_item.findingsCount == 1
    assert e2_item.associatedFindingIds == [f3.findingId]


def test_statement_traceability_links():
    """Verifies that every registered traceability statement links back to a source entity."""
    generator = DeterministicReportGenerator()
    insp_id = uuid.uuid4()
    run_id = uuid.uuid4()
    eid = uuid.uuid4()
    finding = create_sample_finding(evidence_id=eid)

    report = generator.generate_draft(
        inspection_id=insp_id,
        run_id=run_id,
        findings=[finding],
        evidence_ids=[eid],
    )

    assert len(report.traceability) >= 4
    for record in report.traceability:
        assert record.statementId.startswith("stmt-")
        assert len(record.statementText) > 5
        assert record.reportSection in (
            "EXECUTIVE_SUMMARY",
            "EVIDENCE_REVIEWED",
            "FINDINGS",
            "RISK_SUMMARY",
            "VERIFICATION_ACTIONS",
            "CORRECTIVE_ACTIONS",
        )
        assert record.sourceType in ("FINDING", "EVIDENCE", "RISK_ENGINE", "PIPELINE_METADATA")


def test_intelligence_metadata_preservation():
    """Verifies that Phase 4 intelligence fields are preserved intact in report items."""
    generator = DeterministicReportGenerator()
    insp_id = uuid.uuid4()
    run_id = uuid.uuid4()
    eid = uuid.uuid4()
    f = create_sample_finding(
        finding_type="SPRINKLER_HEAD_DEFECT",
        severity=FindingSeverity.HIGH,
        confidence=0.88,
        operational_risk_score=80,
        evidence_id=eid,
    )

    report = generator.generate_draft(
        inspection_id=insp_id,
        run_id=run_id,
        findings=[f],
        evidence_ids=[eid],
    )

    rf = report.findings[0]
    assert rf.findingId == f.findingId
    assert rf.findingType == "SPRINKLER_HEAD_DEFECT"
    assert rf.confidence == 0.88
    assert rf.confidenceLevel == "HIGH"
    assert rf.severity == FindingSeverity.HIGH
    assert rf.operationalRiskScore == 80
    assert rf.operationalRiskLevel == "HIGH"
    assert rf.calibrationStatus == "NOT_CALIBRATED"
    assert rf.severityReason == f.severityReason
    assert rf.explanation == f.explanation
    assert rf.verificationGuidance == f.verificationGuidance
    assert rf.complianceStatus == "REQUIRES_OFFICER_VERIFICATION"


def test_corrective_action_generic_safety():
    """Verifies that corrective action suggestions do not fabricate statutory acts, penalties, or deadlines."""
    ca = CorrectiveActionEngine.generate_suggestion(
        index=1,
        finding_id=uuid.uuid4(),
        finding_type="EMERGENCY_EXIT_OBSTRUCTED",
    )

    assert ca.suggestionId == "CA-001"
    assert "clear" in ca.suggestedAction.lower() or "remove" in ca.suggestedAction.lower()
    assert ca.actionType == "AI_SUGGESTED_CORRECTIVE_ACTION"

    # Must NOT invent specific legal sections, penalties, or deadlines
    action_text = ca.suggestedAction
    assert "Section 4." not in action_text
    assert "penalty" not in action_text.lower()
    assert "fine of" not in action_text.lower()
    assert "within 24 hours" not in action_text.lower()
    assert "within 48 hours" not in action_text.lower()


def test_test_data_flag_and_prominent_warning():
    """Verifies that reports generated with testMode=True are clearly flagged as TEST_DATA."""
    generator = DeterministicReportGenerator()
    insp_id = uuid.uuid4()
    run_id = uuid.uuid4()
    eid = uuid.uuid4()
    f = create_sample_finding(evidence_id=eid, is_test_data=True)

    report = generator.generate_draft(
        inspection_id=insp_id,
        run_id=run_id,
        findings=[f],
        evidence_ids=[eid],
        is_test_data=True,
    )

    assert report.isTestData is True
    assert report.dataClassification == "TEST_DATA"
    assert report.testDataWarning is not None
    assert "TEST DATA — NOT FOR OPERATIONAL OR COMPLIANCE USE" in report.testDataWarning
    assert report.auditMetadata.isTestData is True

    # Check that synthetic test data warning is included in unresolved items and limitations
    unres_cats = [u.category for u in report.unresolvedItems]
    assert "SYNTHETIC_TEST_DATA" in unres_cats


def test_officer_remarks_preservation():
    """Verifies that officer remarks are preserved verbatim with human-authored attribution."""
    generator = DeterministicReportGenerator()
    insp_id = uuid.uuid4()
    run_id = uuid.uuid4()
    eid = uuid.uuid4()

    remarks = "Observed movable pallet near rear fire door during routine walkthrough."
    report = generator.generate_draft(
        inspection_id=insp_id,
        run_id=run_id,
        findings=[],
        evidence_ids=[eid],
        officer_remarks=remarks,
    )

    assert report.officerRemarks is not None
    assert "[OFFICER REMARKS — HUMAN AUTHORED]:" in report.officerRemarks
    assert remarks in report.officerRemarks


def test_no_autonomous_noc_approval_or_rejection():
    """Verifies that setting forbidden compliance states on report raises validation errors."""
    with pytest.raises(ValidationError, match="forbidden"):
        InspectionReportDraft(
            inspectionId=uuid.uuid4(),
            runId=uuid.uuid4(),
            reviewState="AI_APPROVED",  # type: ignore
            modelInformation=None,  # type: ignore
            summary=None,  # type: ignore
            riskSummary=None,  # type: ignore
            auditMetadata=None,  # type: ignore
        )

    with pytest.raises(ValueError, match="forbidden"):
        ReviewLifecycleManager.validate_state("AI_REJECTED")

    with pytest.raises(ValueError, match="forbidden"):
        ReviewLifecycleManager.validate_state("AI_NOC_APPROVED")


def test_deterministic_report_generation():
    """Verifies that calling the generator with identical inputs produces identical report content."""
    generator = DeterministicReportGenerator()
    insp_id = uuid.uuid4()
    run_id = uuid.uuid4()
    eid = uuid.uuid4()
    f1 = create_sample_finding("FIRE_EXTINGUISHER_MISSING", FindingSeverity.HIGH, 0.80, 75, eid)
    f2 = create_sample_finding("EMERGENCY_EXIT_OBSTRUCTED", FindingSeverity.CRITICAL, 0.90, 88, eid)

    r1 = generator.generate_draft(
        inspection_id=insp_id,
        run_id=run_id,
        findings=[f1, f2],
        evidence_ids=[eid],
    )
    r2 = generator.generate_draft(
        inspection_id=insp_id,
        run_id=run_id,
        findings=[f1, f2],
        evidence_ids=[eid],
    )

    # Narrative, findings ordering, and corrective suggestions must be identical
    assert r1.summary.narrative == r2.summary.narrative
    assert [f.findingType for f in r1.findings] == [f.findingType for f in r2.findings]
    assert [ca.suggestedAction for ca in r1.correctiveActions] == [ca.suggestedAction for ca in r2.correctiveActions]
    assert [va.action for va in r1.verificationActions] == [va.action for va in r2.verificationActions]


def test_unresolved_items_and_ai_limitations():
    """Verifies that uncertainties, uncalibrated models, and low-confidence detections are recorded."""
    generator = DeterministicReportGenerator()
    insp_id = uuid.uuid4()
    run_id = uuid.uuid4()
    eid = uuid.uuid4()
    low_conf_f = create_sample_finding(
        finding_type="COMBUSTIBLE_MATERIAL_HAZARD",
        severity=FindingSeverity.MEDIUM,
        confidence=0.52,  # Low/marginal confidence
        operational_risk_score=50,
        evidence_id=eid,
    )

    report = generator.generate_draft(
        inspection_id=insp_id,
        run_id=run_id,
        findings=[low_conf_f],
        evidence_ids=[eid],
        evidence_quality_records={str(eid): "POOR"},
        calibration_status="NOT_CALIBRATED",
        occupancy_context="UNKNOWN",
    )

    unres_cats = {u.category for u in report.unresolvedItems}
    assert "UNCALIBRATED_MODEL" in unres_cats
    assert "LOW_CONFIDENCE_DETECTION" in unres_cats
    assert "DEGRADED_EVIDENCE_QUALITY" in unres_cats
    assert "MISSING_OCCUPANCY_CONTEXT" in unres_cats

    assert len(report.aiLimitations) >= 5


def test_api_report_draft_with_pre_supplied_findings(client: TestClient):
    """Verifies that POST /internal/ai/inspection-report-draft accepts pre-supplied findings and returns 200."""
    insp_id = uuid.uuid4()
    run_id = uuid.uuid4()
    eid = uuid.uuid4()
    sample_f = create_sample_finding(evidence_id=eid)

    payload = {
        "inspectionId": str(insp_id),
        "runId": str(run_id),
        "findings": [sample_f.model_dump(mode="json")],
        "evidenceIds": [str(eid)],
        "officerRemarks": "Front corridor clear, rear storage requires cleanup.",
        "testMode": True,
    }

    response = client.post("/internal/ai/inspection-report-draft", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert data["inspectionId"] == str(insp_id)
    assert data["runId"] == str(run_id)
    assert data["status"] == "DRAFT"
    assert data["reviewState"] == "DRAFT"
    assert data["summary"]["totalFindings"] == 1
    assert len(data["findings"]) == 1
    assert len(data["verificationActions"]) == 1
    assert len(data["correctiveActions"]) == 1
    assert len(data["traceability"]) > 0
    assert "[OFFICER REMARKS — HUMAN AUTHORED]:" in data["officerRemarks"]


def test_api_report_draft_with_evidence_pipeline_execution(client: TestClient):
    """Verifies that POST /internal/ai/inspection-report-draft executes pipeline when findings are not pre-supplied."""
    insp_id = uuid.uuid4()
    eid = uuid.uuid4()

    payload = {
        "inspectionId": str(insp_id),
        "evidenceIds": [str(eid)],
        "testMode": True,
        "occupancyContext": "COMMERCIAL",
    }

    response = client.post("/internal/ai/inspection-report-draft", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert data["inspectionId"] == str(insp_id)
    assert data["status"] == "DRAFT"
    assert data["isTestData"] is True
    assert data["summary"]["totalFindings"] > 0
    assert len(data["evidenceReviewed"]) == 1
    assert data["evidenceReviewed"][0]["evidenceId"] == str(eid)


def test_api_report_draft_missing_required_inputs(client: TestClient):
    """Verifies that calling report drafting endpoint without findings or evidenceIds raises 422."""
    payload = {
        "inspectionId": str(uuid.uuid4()),
        # Neither findings nor evidenceIds provided
    }
    response = client.post("/internal/ai/inspection-report-draft", json=payload)
    # Controlled rejection
    assert response.status_code in (400, 422)


def test_missing_optional_fields_and_honest_representation():
    """Verifies that report generation succeeds honestly when findings lack optional Phase 4 fields."""
    generator = DeterministicReportGenerator()
    insp_id = uuid.uuid4()
    run_id = uuid.uuid4()
    eid = uuid.uuid4()

    # Minimal finding without optional risk score, without reasoning, without bounding box
    bare_finding = Finding(
        findingId=uuid.uuid4(),
        evidenceId=eid,
        findingType="EXIT_SIGNAGE_MISSING",
        confidence=0.72,
        severity=FindingSeverity.HIGH,
        explanation="Detected possible missing exit signage.",
        model="yolo-v8-safety-mock",
        modelVersion="v1.0.0",
        operationalRiskScore=None,
        operationalRiskLevel=None,
        riskFactors=None,
        severityReason=None,
        verificationGuidance=[],
        evidenceQuality=None,
        boundingBox=None,
    )

    report = generator.generate_draft(
        inspection_id=insp_id,
        run_id=run_id,
        findings=[bare_finding],
        evidence_ids=[eid],
        evidence_quality_records=None,
        occupancy_context="UNKNOWN",
        historical_context="UNAVAILABLE",
    )

    assert report.summary.totalFindings == 1
    assert report.summary.highestRiskScore is None
    assert report.summary.highestRiskLevel is None
    assert report.findings[0].severityReason is None
    assert report.findings[0].operationalRiskScore is None
    assert report.findings[0].verificationGuidance == []
    assert len(report.verificationActions) >= 1
    assert "Checklist data was not provided" in report.checklistSummary


def test_unusable_evidence_handling():
    """Verifies that UNUSABLE evidence triggers an explicit unresolved item."""
    generator = DeterministicReportGenerator()
    insp_id = uuid.uuid4()
    run_id = uuid.uuid4()
    eid = uuid.uuid4()

    report = generator.generate_draft(
        inspection_id=insp_id,
        run_id=run_id,
        findings=[],
        evidence_ids=[eid],
        evidence_quality_records={str(eid): "UNUSABLE"},
    )

    unres_cats = [u.category for u in report.unresolvedItems]
    assert "UNUSABLE_EVIDENCE" in unres_cats
    unusable_item = next(u for u in report.unresolvedItems if u.category == "UNUSABLE_EVIDENCE")
    assert "severe degradation" in unusable_item.description.lower() or "unusable" in unusable_item.description.lower()


def test_no_hallucinated_building_information_or_regulations():
    """Verifies safeguards against hallucinated legal citations, penalties, and building specifications."""
    generator = DeterministicReportGenerator()
    insp_id = uuid.uuid4()
    run_id = uuid.uuid4()
    eid = uuid.uuid4()
    f = create_sample_finding("FIRE_EXTINGUISHER_MISSING", FindingSeverity.HIGH, 0.85, 75, eid)

    report = generator.generate_draft(
        inspection_id=insp_id,
        run_id=run_id,
        findings=[f],
        evidence_ids=[eid],
    )

    report_dump_str = report.model_dump_json()

    # Forbidden hallucinated concepts
    forbidden_terms = [
        "section 144",
        "rupees",
        "penalty of",
        "court of law",
        "building is compliant",
        "building complies",
        "water tank capacity",
        "hydrant pressure",
        "square meters",
        "fine of",
        "mandatory imprisonment",
    ]
    for term in forbidden_terms:
        assert term not in report_dump_str.lower(), f"Forbidden hallucinated term '{term}' found in report!"


def test_future_llm_generator_abstraction():
    """Verifies that FutureLLMReportGenerator implements the BaseReportGenerator interface."""
    llm_gen = FutureLLMReportGenerator()
    assert isinstance(llm_gen, BaseReportGenerator)
    assert llm_gen.generator_version == "future-llm-v1-interface"

    with pytest.raises(NotImplementedError, match="future architectural interface"):
        llm_gen.generate_draft(
            inspection_id=uuid.uuid4(),
            run_id=uuid.uuid4(),
            findings=[],
            evidence_ids=[],
        )


def test_backward_compatibility_inspection_analysis_endpoint(client: TestClient):
    """Verifies that POST /internal/ai/inspection-analysis continues to work identically for Phase 1-4 callers."""
    insp_id = uuid.uuid4()
    eid = uuid.uuid4()
    payload = {
        "inspectionId": str(insp_id),
        "evidenceIds": [str(eid)],
        "testMode": True,
        "occupancyContext": "EDUCATIONAL",
    }
    response = client.post("/internal/ai/inspection-analysis", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert "runId" in data
    assert uuid.UUID(data["runId"])
    assert "findings" in data
    assert "summary" in data
    assert data["status"] == "COMPLETED"

