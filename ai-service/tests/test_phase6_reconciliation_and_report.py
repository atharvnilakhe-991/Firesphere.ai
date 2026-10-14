"""FireSphere.ai — Phase 6 Multi-Model Orchestration & Reconciliation Test Suite.

Verifies:
- Cases A through W covering cross-model agreement, partial agreement, disagreement,
  unavailable components, fault isolation, evidence linkage, tenant isolation,
  advisory invariants, zero-finding compliance disclaimers, and M2 contract compatibility.
"""

from datetime import datetime, timezone
import json
from typing import Any, Dict, List
import uuid
import pytest

from app.core.exceptions import InvalidEvidenceException
from app.intelligence.reconciliation import ResultReconciler
from app.models.base_model import RawDetection
from app.models.gemini_schemas import (
    GeminiAnalysisResult,
    GeminiAnalysisStatus,
    GeminiObservation,
    parse_and_validate_bounding_box,
)
from app.reporting.deterministic_generator import DeterministicReportGenerator
from app.schemas.finding import BoundingBox, Finding, FindingSeverity
from app.schemas.inspection import InspectionAISummary, InspectionAnalysisResponse
from app.schemas.reconciliation import (
    ComponentAnalysisStatus,
    ConflictType,
    ReconciliationStatus,
    map_component_status,
)
from app.schemas.report import InspectionReportDraft, ReportModelInfo, ReviewState
from app.schemas.run import RunStatus


@pytest.fixture
def reconciler() -> ResultReconciler:
    return ResultReconciler()


@pytest.fixture
def report_generator() -> DeterministicReportGenerator:
    return DeterministicReportGenerator()


@pytest.fixture
def sample_ids() -> Dict[str, uuid.UUID]:
    return {
        "evidence_id": uuid.uuid4(),
        "inspection_id": uuid.uuid4(),
        "run_id": uuid.uuid4(),
    }


# =============================================================================
# CASE A: Both components succeed and report compatible observations
# =============================================================================
def test_case_a_both_components_succeed_and_agree(reconciler, sample_ids):
    """Case A: CV and VLM agree on equipment type and spatial location."""
    eid = sample_ids["evidence_id"]
    iid = sample_ids["inspection_id"]

    gemini_obs = GeminiObservation(
        observationId=str(uuid.uuid4()),
        imageReference=str(eid),
        evidenceId=str(eid),
        objectCategory="FIRE_EXTINGUISHER",
        potentialHazardCategory="FIRE_EXTINGUISHER",
        observationDescription="Visible wall-mounted fire extinguisher observed in good physical condition.",
        confidence=0.92,
        potentialHazardSeverity="MEDIUM",
        boundingBox=BoundingBox(xMin=0.10, yMin=0.20, xMax=0.30, yMax=0.70),
    )
    gemini_res = GeminiAnalysisResult(
        evidenceId=eid,
        model="gemini-2.5-flash",
        status=GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS,
        observations=[gemini_obs],
    )

    cv_det = RawDetection(
        className="FIRE_EXTINGUISHER",
        confidence=0.88,
        boundingBox={"xMin": 0.12, "yMin": 0.22, "xMax": 0.31, "yMax": 0.69},
        severity="MEDIUM",
    )

    findings, status, conflict = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=gemini_res,
        cv_detections=[cv_det],
        cv_status="SUCCESS",
        cv_model_info={"model": "firesphere-perception-baseline-v0.8.0", "version": "0.8.0"},
        gemini_model_info={"model": "gemini-2.5-flash"},
        is_test_mode=True,
    )

    assert status == ReconciliationStatus.AGREEMENT
    assert conflict is None
    assert len(findings) == 1
    f = findings[0]
    assert f.reconciliationStatus == ReconciliationStatus.AGREEMENT.value
    assert f.conflictType == ConflictType.NONE.value
    assert "Notice: Cross-model agreement reflects consistent visual patterns" in f.explanation
    assert f.calibrationStatus == "NOT_CALIBRATED"
    # Verify provenance retains both providers without fabricating joint scores
    assert len(f.provenance) == 2
    prov_names = [p["providerName"] for p in f.provenance]
    assert "google-gemini" in prov_names
    assert "firesphere-cv-baseline" in prov_names
    # Raw confidences preserved
    assert any(p.get("rawConfidence") == 0.92 for p in f.provenance)
    assert any(p.get("rawConfidence") == 0.88 for p in f.provenance)


# =============================================================================
# CASE B: CV detects an object and VLM describes a related condition
# =============================================================================
def test_case_b_partial_agreement_object_and_condition(reconciler, sample_ids):
    """Case B: CV detects object presence; VLM notes related operational hazard."""
    eid = sample_ids["evidence_id"]
    iid = sample_ids["inspection_id"]

    gemini_obs = GeminiObservation(
        observationId=str(uuid.uuid4()),
        imageReference=str(eid),
        evidenceId=str(eid),
        objectCategory="FIRE_EXTINGUISHER",
        potentialHazardCategory="FIRE_EXTINGUISHER_BLOCKED",
        observationDescription="Storage boxes placed directly in front of the extinguisher station.",
        confidence=0.89,
        potentialHazardSeverity="HIGH",
        boundingBox=BoundingBox(xMin=0.10, yMin=0.20, xMax=0.30, yMax=0.70),
    )
    gemini_res = GeminiAnalysisResult(
        evidenceId=eid,
        model="gemini-2.5-flash",
        status=GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS,
        observations=[gemini_obs],
    )

    cv_det = RawDetection(
        className="FIRE_EXTINGUISHER",
        confidence=0.85,
        boundingBox={"xMin": 0.11, "yMin": 0.21, "xMax": 0.32, "yMax": 0.71},
        severity="MEDIUM",
    )

    findings, status, conflict = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=gemini_res,
        cv_detections=[cv_det],
        cv_status="SUCCESS",
        cv_model_info={"model": "firesphere-perception-baseline-v0.8.0", "version": "0.8.0"},
        gemini_model_info={"model": "gemini-2.5-flash"},
        is_test_mode=True,
    )

    assert len(findings) == 1
    # Severities differed (HIGH vs MEDIUM), so severity mismatch conflict type is recorded
    assert findings[0].conflictType == ConflictType.SEVERITY_MISMATCH.value
    assert findings[0].reconciliationStatus == ReconciliationStatus.AGREEMENT.value
    assert findings[0].severity == FindingSeverity.HIGH


# =============================================================================
# CASE C: Disagreements (Category Mismatch and Location Mismatch)
# =============================================================================
def test_case_c1_category_mismatch_disagreement(reconciler, sample_ids):
    """Case C1: Spatial overlap match, but conflicting defect categories."""
    eid = sample_ids["evidence_id"]
    iid = sample_ids["inspection_id"]

    gemini_obs = GeminiObservation(
        observationId=str(uuid.uuid4()),
        imageReference=str(eid),
        evidenceId=str(eid),
        objectCategory="EXIT_SIGN",
        potentialHazardCategory="EXIT_SIGNAGE_MISSING",
        observationDescription="Directional illuminated sign is dark/missing above doorway.",
        confidence=0.80,
        potentialHazardSeverity="HIGH",
        boundingBox=BoundingBox(xMin=0.40, yMin=0.10, xMax=0.60, yMax=0.30),
    )
    gemini_res = GeminiAnalysisResult(
        evidenceId=eid,
        model="gemini-2.5-flash",
        status=GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS,
        observations=[gemini_obs],
    )

    cv_det = RawDetection(
        className="SPRINKLER_HEAD_DEFECT",
        confidence=0.78,
        boundingBox={"xMin": 0.42, "yMin": 0.11, "xMax": 0.58, "yMax": 0.29},
        severity="MEDIUM",
    )

    findings, status, conflict = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=gemini_res,
        cv_detections=[cv_det],
        cv_status="SUCCESS",
        cv_model_info={"model": "firesphere-perception-baseline-v0.8.0", "version": "0.8.0"},
        gemini_model_info={"model": "gemini-2.5-flash"},
        is_test_mode=True,
    )

    assert status == ReconciliationStatus.CONFLICT
    assert len(findings) == 1
    f = findings[0]
    assert f.reconciliationStatus == ReconciliationStatus.CONFLICT.value
    assert f.conflictType == ConflictType.CATEGORY_MISMATCH.value
    assert f.conflictDetails is not None
    assert f.conflictDetails["conflictType"] == "CATEGORY_MISMATCH"
    assert "Human Fire Officer verification is required to adjudicate" in f.explanation


def test_case_c2_location_mismatch_disagreement(reconciler, sample_ids):
    """Case C2: Same category, but spatially disjoint coordinates (IoU < 0.30)."""
    eid = sample_ids["evidence_id"]
    iid = sample_ids["inspection_id"]

    gemini_obs = GeminiObservation(
        observationId=str(uuid.uuid4()),
        imageReference=str(eid),
        evidenceId=str(eid),
        objectCategory="FIRE_EXTINGUISHER",
        potentialHazardCategory="FIRE_EXTINGUISHER",
        observationDescription="Extinguisher on the left wall.",
        confidence=0.85,
        boundingBox=BoundingBox(xMin=0.05, yMin=0.20, xMax=0.20, yMax=0.60),
    )
    gemini_res = GeminiAnalysisResult(
        evidenceId=eid,
        model="gemini-2.5-flash",
        status=GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS,
        observations=[gemini_obs],
    )

    cv_det = RawDetection(
        className="FIRE_EXTINGUISHER",
        confidence=0.82,
        boundingBox={"xMin": 0.70, "yMin": 0.20, "xMax": 0.85, "yMax": 0.60},
        severity="MEDIUM",
    )

    findings, status, conflict = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=gemini_res,
        cv_detections=[cv_det],
        cv_status="SUCCESS",
        cv_model_info={"model": "firesphere-perception-baseline-v0.8.0", "version": "0.8.0"},
        gemini_model_info={"model": "gemini-2.5-flash"},
        is_test_mode=True,
    )

    assert status == ReconciliationStatus.CONFLICT
    # Stage 8 safety contract: preserve both source observations on spatial mismatch without discarding either
    assert len(findings) == 2
    for f in findings:
        assert f.reconciliationStatus == ReconciliationStatus.CONFLICT.value
        assert f.conflictType == ConflictType.LOCATION_MISMATCH.value
    vlm_f = next(f for f in findings if f.sourceType == "VLM")
    assert vlm_f.conflictDetails["conflictType"] == "LOCATION_MISMATCH"
    assert "geminiBoundingBox" in vlm_f.conflictDetails or "vlmBoundingBox" in vlm_f.conflictDetails
    assert "cvBoundingBox" in vlm_f.conflictDetails
    cv_f = next(f for f in findings if f.sourceType == "CV")
    assert cv_f.boundingBox is not None


# =============================================================================
# CASE D: CV succeeds with no observations and VLM is unavailable
# =============================================================================
def test_case_d_cv_empty_vlm_unavailable(reconciler, sample_ids):
    """Case D: CV runs and finds 0 detections; VLM is disabled/unavailable."""
    eid = sample_ids["evidence_id"]
    iid = sample_ids["inspection_id"]

    gemini_res = GeminiAnalysisResult(
        evidenceId=eid,
        model="gemini-2.5-flash",
        status=GeminiAnalysisStatus.GEMINI_DISABLED,
        observations=[],
    )

    findings, status, conflict = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=gemini_res,
        cv_detections=[],
        cv_status="SUCCESS",
        cv_model_info={"model": "firesphere-perception-baseline-v0.8.0", "version": "0.8.0"},
        gemini_model_info={"model": "gemini-2.5-flash"},
        is_test_mode=True,
    )

    assert findings == []
    assert status == ReconciliationStatus.NO_OBSERVATIONS


# =============================================================================
# CASE E: VLM succeeds and CV is unavailable/gated
# =============================================================================
def test_case_e_vlm_succeeds_cv_gated(reconciler, sample_ids):
    """Case E: VLM returns observations; CV is gated (NOT_APPROVED_GATED)."""
    eid = sample_ids["evidence_id"]
    iid = sample_ids["inspection_id"]

    gemini_obs = GeminiObservation(
        observationId=str(uuid.uuid4()),
        imageReference=str(eid),
        evidenceId=str(eid),
        objectCategory="EMERGENCY_EXIT_OBSTRUCTED",
        potentialHazardCategory="EMERGENCY_EXIT_OBSTRUCTED",
        observationDescription="Pallet blocking exit corridor.",
        confidence=0.88,
        potentialHazardSeverity="HIGH",
        boundingBox=BoundingBox(xMin=0.30, yMin=0.40, xMax=0.60, yMax=0.80),
    )
    gemini_res = GeminiAnalysisResult(
        evidenceId=eid,
        model="gemini-2.5-flash",
        status=GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS,
        observations=[gemini_obs],
    )

    findings, status, conflict = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=gemini_res,
        cv_detections=[],
        cv_status="NOT_APPROVED_GATED",
        cv_model_info={"model": "firesphere-perception-baseline-v0.8.0", "version": "0.8.0"},
        gemini_model_info={"model": "gemini-2.5-flash"},
        is_test_mode=True,
    )

    assert status == ReconciliationStatus.SINGLE_PROVIDER
    assert len(findings) == 1
    assert findings[0].model == "google-gemini"
    assert findings[0].conflictType == ConflictType.NONE.value


# =============================================================================
# CASE F: Both components return successful results with no observations
# =============================================================================
def test_case_f_both_successful_no_observations(reconciler, sample_ids):
    """Case F: Both models executed successfully and detected no hazards."""
    eid = sample_ids["evidence_id"]
    iid = sample_ids["inspection_id"]

    gemini_res = GeminiAnalysisResult(
        evidenceId=eid,
        model="gemini-2.5-flash",
        status=GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS,
        observations=[],
    )

    findings, status, conflict = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=gemini_res,
        cv_detections=[],
        cv_status="SUCCESS",
        cv_model_info={"model": "firesphere-perception-baseline-v0.8.0", "version": "0.8.0"},
        gemini_model_info={"model": "gemini-2.5-flash"},
        is_test_mode=True,
    )

    assert findings == []
    assert status == ReconciliationStatus.NO_OBSERVATIONS
    assert conflict is None


# =============================================================================
# CASE G: One component returns malformed output
# =============================================================================
def test_case_g_malformed_bounding_box_handled_safely():
    """Case G: Non-finite or inverted coordinates in raw output are safely rejected."""
    # Inverted coordinates: xMin > xMax
    box = parse_and_validate_bounding_box({"ymin": 0.1, "xmin": 0.8, "ymax": 0.5, "xmax": 0.2})
    assert box is None

    # Non-finite coordinates (NaN)
    box_nan = parse_and_validate_bounding_box({"ymin": float("nan"), "xmin": 0.1, "ymax": 0.5, "xmax": 0.8})
    assert box_nan is None


# =============================================================================
# CASE H: One component fails with an exception / fault isolation
# =============================================================================
def test_case_h_component_failure_fault_isolation(reconciler, sample_ids):
    """Case H: Gemini fails; CV baseline detections are preserved with DISCREPANCY status."""
    eid = sample_ids["evidence_id"]
    iid = sample_ids["inspection_id"]

    gemini_res = GeminiAnalysisResult(
        evidenceId=eid,
        model="gemini-2.5-flash",
        status=GeminiAnalysisStatus.ANALYSIS_FAILED,
        observations=[],
        error="Network timeout to VLM gateway",
    )

    cv_det = RawDetection(
        className="FIRE_EXTINGUISHER",
        confidence=0.84,
        boundingBox={"xMin": 0.1, "yMin": 0.1, "xMax": 0.3, "yMax": 0.5},
        severity="MEDIUM",
    )

    findings, status, conflict = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=gemini_res,
        cv_detections=[cv_det],
        cv_status="SUCCESS",
        cv_model_info={"model": "firesphere-perception-baseline-v0.8.0", "version": "0.8.0"},
        gemini_model_info={"model": "gemini-2.5-flash"},
        is_test_mode=True,
    )

    assert status == ReconciliationStatus.DISCREPANCY
    assert len(findings) == 1
    assert conflict is not None
    assert conflict.conflictType == ConflictType.UNRESOLVED_DISAGREEMENT
    assert "provider failed" in conflict.description.lower()
    assert "gemini" in conflict.description.lower()


# =============================================================================
# CASE I: VLM inference is unavailable because GEMINI_ENABLED=false
# =============================================================================
def test_case_i_gemini_disabled_explicit_representation():
    """Case I: map_component_status explicitly maps GEMINI_DISABLED to UNAVAILABLE."""
    status = map_component_status("GEMINI_DISABLED", observation_count=0)
    assert status == ComponentAnalysisStatus.UNAVAILABLE

    status2 = map_component_status("DISABLED", observation_count=0)
    assert status2 == ComponentAnalysisStatus.UNAVAILABLE


# =============================================================================
# CASE J: Test-mode/mock result cannot be silently accepted as real production inference
# =============================================================================
def test_case_j_test_mode_flagged_prominently(report_generator, sample_ids):
    """Case J: Reports generated in testMode carry prominent synthetic warnings."""
    eid = sample_ids["evidence_id"]
    iid = sample_ids["inspection_id"]
    rid = sample_ids["run_id"]

    draft = report_generator.generate_draft(
        inspection_id=iid,
        run_id=rid,
        findings=[],
        evidence_ids=[eid],
        is_test_data=True,
    )

    assert draft.isTestData is True
    assert draft.dataClassification == "TEST_DATA"
    assert draft.testDataWarning is not None
    assert "TEST DATA" in draft.testDataWarning
    assert any(u.category == "SYNTHETIC_TEST_DATA" for u in draft.unresolvedItems)


# =============================================================================
# CASE K & L: Evidence UUID and Inspection UUID mismatch rejections
# =============================================================================
def test_case_k_evidence_uuid_mismatch_rejected(reconciler, sample_ids):
    """Case K: Reject observation that points to a mismatched evidence UUID."""
    eid1 = sample_ids["evidence_id"]
    eid2 = uuid.uuid4()
    iid = sample_ids["inspection_id"]

    gemini_res = GeminiAnalysisResult(
        evidenceId=eid2,  # Mismatched evidenceId
        model="gemini-2.5-flash",
        status=GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS,
        observations=[],
    )

    with pytest.raises(InvalidEvidenceException, match="Evidence UUID mismatch"):
        reconciler.reconcile(
            evidence_id=eid1,
            inspection_id=iid,
            gemini_result=gemini_res,
            cv_detections=[],
            cv_status="SUCCESS",
            cv_model_info={"model": "cv", "version": "0.8.0"},
            gemini_model_info={"model": "gemini"},
        )


def test_case_l_inspection_uuid_empty_rejected(reconciler, sample_ids):
    """Case L: Null or empty inspection ID is strictly rejected."""
    eid = sample_ids["evidence_id"]

    with pytest.raises(InvalidEvidenceException, match="Inspection identifier cannot be empty"):
        reconciler.reconcile(
            evidence_id=eid,
            inspection_id=None,
            gemini_result=None,
            cv_detections=[],
            cv_status="SUCCESS",
            cv_model_info={"model": "cv", "version": "0.8.0"},
            gemini_model_info={"model": "gemini"},
        )


# =============================================================================
# CASE M: Cross-tenant result rejection
# =============================================================================
def test_case_m_cross_tenant_isolation(sample_ids):
    """Case M: Cross-tenant evidence requests and unauthorized evidence references are blocked."""
    from app.core.config import settings
    from app.intelligence.evidence_validator import EvidenceValidator
    from app.storage.evidence_client import EvidenceClient

    eid = sample_ids["evidence_id"]
    unauthorized_id = uuid.uuid4()

    # 1. Provider evidence reference validation blocks unauthorized evidence
    is_valid, err = EvidenceValidator.validate_provider_evidence_reference(
        observation_evidence_id=unauthorized_id,
        authorized_evidence_ids={str(eid)},
    )
    assert is_valid is False
    assert "unauthorized evidence identifier" in err

    # 2. Storage lookup when mock loader is disabled rejects unknown cross-tenant evidence
    original_mock = settings.enable_mock_evidence_loader
    try:
        settings.enable_mock_evidence_loader = False
        client = EvidenceClient()
        validator = EvidenceValidator(client=client)
        res = validator.validate_requested_evidence(
            inspection_id=sample_ids["inspection_id"],
            evidence_ids=[unauthorized_id],
            tenant_id="tenant-beta",
        )
        assert res[str(unauthorized_id)].isValid is False
    finally:
        settings.enable_mock_evidence_loader = original_mock


# =============================================================================
# CASE N & O: Duplicate observation deduplication vs unrelated separation
# =============================================================================
def test_case_n_duplicate_observation_deduplication(reconciler, sample_ids):
    """Case N: Duplicate observations for the same object are merged with AGREEMENT."""
    eid = sample_ids["evidence_id"]
    iid = sample_ids["inspection_id"]

    gemini_obs = GeminiObservation(
        observationId="gem-obs-101",
        imageReference=str(eid),
        evidenceId=str(eid),
        objectCategory="FIRE_EXTINGUISHER",
        potentialHazardCategory="FIRE_EXTINGUISHER",
        confidence=0.91,
        boundingBox=BoundingBox(xMin=0.2, yMin=0.3, xMax=0.4, yMax=0.8),
    )
    gemini_res = GeminiAnalysisResult(
        evidenceId=eid,
        model="gemini-2.5-flash",
        status=GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS,
        observations=[gemini_obs],
    )

    cv_det = RawDetection(
        className="FIRE_EXTINGUISHER",
        confidence=0.89,
        boundingBox={"xMin": 0.21, "yMin": 0.31, "xMax": 0.41, "yMax": 0.79},
        severity="MEDIUM",
    )

    findings, status, conflict = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=gemini_res,
        cv_detections=[cv_det],
        cv_status="SUCCESS",
        cv_model_info={"model": "firesphere-perception-baseline-v0.8.0", "version": "0.8.0"},
        gemini_model_info={"model": "gemini-2.5-flash"},
        is_test_mode=True,
    )

    assert len(findings) == 1
    assert findings[0].reconciliationStatus == ReconciliationStatus.AGREEMENT.value
    # Provenance tracks source observationId
    assert any(p.get("observationId") == "gem-obs-101" for p in findings[0].provenance)


def test_case_o_unrelated_observations_not_merged(reconciler, sample_ids):
    """Case O: Spatially distinct observations are preserved as separate findings."""
    eid = sample_ids["evidence_id"]
    iid = sample_ids["inspection_id"]

    gemini_obs = GeminiObservation(
        observationId="gem-exit-sign",
        imageReference=str(eid),
        evidenceId=str(eid),
        objectCategory="EXIT_SIGN",
        potentialHazardCategory="EXIT_SIGNAGE_MISSING",
        confidence=0.85,
        boundingBox=BoundingBox(xMin=0.1, yMin=0.1, xMax=0.3, yMax=0.3),
    )
    gemini_res = GeminiAnalysisResult(
        evidenceId=eid,
        model="gemini-2.5-flash",
        status=GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_OBSERVATIONS,
        observations=[gemini_obs],
    )

    cv_det = RawDetection(
        className="FIRE_EXTINGUISHER",
        confidence=0.88,
        boundingBox={"xMin": 0.7, "yMin": 0.5, "xMax": 0.9, "yMax": 0.9},
        severity="MEDIUM",
    )

    findings, status, conflict = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=gemini_res,
        cv_detections=[cv_det],
        cv_status="SUCCESS",
        cv_model_info={"model": "firesphere-perception-baseline-v0.8.0", "version": "0.8.0"},
        gemini_model_info={"model": "gemini-2.5-flash"},
        is_test_mode=True,
    )

    # Must preserve BOTH distinct observations without forcing merge
    assert len(findings) == 2
    types = {f.findingType for f in findings}
    assert "EXIT_SIGNAGE_MISSING" in types
    assert "FIRE_EXTINGUISHER" in types


# =============================================================================
# CASE P & Q: Model Provenance & Confidence Interpretation
# =============================================================================
def test_case_p_and_q_provenance_and_calibration_invariants(reconciler, sample_ids):
    """Cases P & Q: Provenance fields preserved and calibration marked NOT_CALIBRATED."""
    eid = sample_ids["evidence_id"]
    iid = sample_ids["inspection_id"]

    cv_det = RawDetection(
        className="FIRE_EXTINGUISHER",
        confidence=0.76,
        boundingBox={"xMin": 0.2, "yMin": 0.2, "xMax": 0.4, "yMax": 0.6},
        severity="MEDIUM",
    )

    findings, status, _ = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=None,
        cv_detections=[cv_det],
        cv_status="SUCCESS",
        cv_model_info={"model": "firesphere-perception-baseline-v0.8.0", "version": "0.8.0"},
        gemini_model_info={"model": "gemini-2.5-flash"},
        is_test_mode=True,
    )

    assert len(findings) == 1
    f = findings[0]
    assert f.calibrationStatus == "NOT_CALIBRATED"
    assert f.modelApprovalStatus == "NOT_APPROVED"
    assert f.confidence == 0.76


# =============================================================================
# CASE R, S, T: Advisory-only, No Autonomous Decisions, Officer Verification
# =============================================================================
def test_case_r_s_t_governance_invariants(report_generator, sample_ids):
    """Cases R, S, T: Report is strictly advisory, requires officer review, forbids AI approvals."""
    eid = sample_ids["evidence_id"]
    iid = sample_ids["inspection_id"]
    rid = sample_ids["run_id"]

    draft = report_generator.generate_draft(
        inspection_id=iid,
        run_id=rid,
        findings=[],
        evidence_ids=[eid],
    )

    # Case R: Regulatory notice
    assert "ADVISORY REPORT DRAFT" in draft.regulatoryNotice
    assert draft.auditMetadata.aiDecisionAuthority == "NONE (ADVISORY_DRAFT_ONLY)"

    # Case T: Officer review required
    assert draft.summary.requiresOfficerReview is True

    # Case S: Forbidden review states raise ValueError
    with pytest.raises(ValueError, match="forbidden"):
        InspectionReportDraft(
            inspectionId=iid,
            runId=rid,
            modelInformation=draft.modelInformation,
            summary=draft.summary,
            auditMetadata=draft.auditMetadata,
            riskSummary=draft.riskSummary,
            reviewState="AI_APPROVED",  # FORBIDDEN
        )


# =============================================================================
# CASE U: Failed component is never converted into empty successful result
# =============================================================================
def test_case_u_both_failed_never_empty_success(reconciler, sample_ids):
    """Case U: When providers fail, status is FAILED, not NO_OBSERVATIONS."""
    eid = sample_ids["evidence_id"]
    iid = sample_ids["inspection_id"]

    gemini_res = GeminiAnalysisResult(
        evidenceId=eid,
        model="gemini-2.5-flash",
        status=GeminiAnalysisStatus.ANALYSIS_FAILED,
        observations=[],
        error="Gateway timeout",
    )

    findings, status, conflict = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        gemini_result=gemini_res,
        cv_detections=[],
        cv_status="FAILED",
        cv_model_info={"model": "cv", "version": "0.8.0"},
        gemini_model_info={"model": "gemini"},
    )

    assert findings == []
    assert status == ReconciliationStatus.FAILED
    assert conflict is not None


# =============================================================================
# CASE V: Empty observations do not claim safety or compliance
# =============================================================================
def test_case_v_empty_observations_do_not_certify_compliance(report_generator, sample_ids):
    """Case V: Zero detections narrative explicitly warns that lack of detections != compliance."""
    eid = sample_ids["evidence_id"]
    iid = sample_ids["inspection_id"]
    rid = sample_ids["run_id"]

    draft = report_generator.generate_draft(
        inspection_id=iid,
        run_id=rid,
        findings=[],
        evidence_ids=[eid],
    )

    narrative = draft.summary.narrative
    assert "No AI findings were generated" in narrative
    assert "does NOT certify that the building is compliant" in narrative
    assert "Mandatory human Fire Officer physical inspection is required" in narrative


# =============================================================================
# CASE W: Existing M2/M4 contract compatibility
# =============================================================================
def test_case_w_m2_external_ai_response_compatibility(sample_ids):
    """Case W: Response matches Jackson deserialization contract expected by M2 backend."""
    rid = sample_ids["run_id"]
    summary = InspectionAISummary(
        totalFindings=0,
        uniqueFindingTypes=0,
        evidenceCount=1,
        requiresOfficerReview=True,
    )
    resp = InspectionAnalysisResponse(
        runId=rid,
        status=RunStatus.COMPLETED,
        findings=[],
        summary=summary,
        model="firesphere-orchestrator-ensemble",
        modelVersion="0.3.0",
        executionDurationMs=42.5,
        tenantId="default",
        modelApprovalStatus="NOT_APPROVED",
        calibrationStatus="NOT_CALIBRATED",
    )

    payload = resp.model_dump(mode="json")
    # Verify mandatory M2 ExternalAiResponse fields exist
    assert "runId" in payload
    assert "status" in payload
    assert "findings" in payload
    assert "summary" in payload
    assert "model" in payload
    assert "modelVersion" in payload
    assert "modelApprovalStatus" in payload
    assert "calibrationStatus" in payload
    assert payload["modelApprovalStatus"] == "NOT_APPROVED"
    assert payload["calibrationStatus"] == "NOT_CALIBRATED"
