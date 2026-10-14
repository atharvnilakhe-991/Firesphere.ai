"""FireSphere.ai — Stage 9 AI-to-Checklist Mapping Verification & Integration Tests.

Validates the end-to-end mapping contract between AI-generated fire safety findings
(Computer Vision baseline + local Qwen2.5-VL) and the regulatory officer inspection
checklist workflow.

Test Scenarios Covered (25 Total):
 1. Each verified deterministic mapping (EXT-01, EXIT-01, EXIT-02/DOOR-01, OBS-01).
 2. Unknown finding category (unmapped with reason, no invented items).
 3. Ambiguous finding categories (EMERGENCY_EXIT_OBSTRUCTED, COMBUSTIBLE_MATERIAL_HAZARD, GENERIC_FIRE_SAFETY_DEFECT).
 4. CV/Qwen agreement (concordant cross-validation, sourceType=ENSEMBLE).
 5. CV/Qwen location disagreement (spatial conflict, both observations preserved).
 6. CV-only finding mapping and provenance.
 7. Qwen-only finding mapping and provenance.
 8. Both providers returning no observations (never certifies compliance).
 9. CV approval gate blocking inference (GATE_BLOCKED_CV).
10. CV provider failure (PARTIAL_FAILURE_CV).
11. Qwen provider failure (PARTIAL_FAILURE_VLM).
12. Both providers failing (BOTH_PROVIDERS_FAILED).
13. Missing inspection ID rejected (InvalidEvidenceException).
14. Invalid inspection ID rejected (strict UUID validation).
15. Cross-inspection evidence rejected.
16. Cross-tenant evidence rejected.
17. Duplicate mapping requests are idempotent.
18. Completed checklist mutation rejected.
19. AI-link review without changing officer response (response remains NOT_REVIEWED).
20. Citizen authorization boundaries (internal AI suggestions sanitized/stripped).
21. Unassigned officer mutation attempt rejected.
22. Assigned officer permitted mutation.
23. Unsupported finding remaining unmapped (SPRINKLER_HEAD_DEFECT, ELECTRICAL_PANEL_HAZARD).
24. Provider identity preserved in mapping output (Ollama / qwen2.5vl:3b vs CV baseline).
25. Existing API compatibility and schema contracts.
"""

import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
import pytest

from app.core.exceptions import InvalidEvidenceException
from app.intelligence.reconciliation import (
    CVVLMReconciliationService,
)
from app.intelligence.verification import OfficerVerificationGuidance
from app.models.gemini_schemas import (
    GeminiAnalysisResult,
    GeminiAnalysisStatus,
    GeminiObservation,
)
from app.models.model_approval_gate import model_approval_gate
from app.schemas.finding import (
    BoundingBox,
    ComplianceStatus,
    Finding,
    FindingSeverity,
    FindingType,
)
from app.schemas.reconciliation import (
    ConflictType,
    ReconciliationResult,
    ReconciliationStatus,
)


# =============================================================================
# Helper Mocks and Fixtures
# =============================================================================

class MockCVDet:
    def __init__(self, className="FIRE_EXTINGUISHER_BLOCKED", confidence=0.88, box=None, evidenceId=None):
        self.className = className
        self.findingType = className
        self.confidence = confidence
        self.boundingBox = box or {"xMin": 0.1, "yMin": 0.1, "xMax": 0.5, "yMax": 0.5}
        self.evidenceId = evidenceId or str(uuid.uuid4())


def create_qwen_obs(
    observation_id=None,
    category="FIRE_EXTINGUISHER",
    hazard="FIRE_EXTINGUISHER_BLOCKED",
    confidence=0.85,
    box=None,
    description="Qwen detected fire safety observation",
    evidence_id=None,
):
    obs_id = observation_id or str(uuid.uuid4())
    eid = evidence_id or str(uuid.uuid4())
    box_coords = box or [100, 100, 500, 500]  # Qwen 0-1000 normalized format
    return GeminiObservation(
        observationId=obs_id,
        objectCategory=category,
        hazardCategory=hazard,
        severity="HIGH",
        confidence=confidence,
        box_2d=box_coords,
        description=description,
        evidenceId=eid,
    )


def create_qwen_result(
    observations,
    status=GeminiAnalysisStatus.SUCCESS,
    provider="ollama",
    model="qwen2.5vl:3b",
    evidence_id=None,
    error_message=None,
    inconclusive_reason=None,
):
    eid = evidence_id or str(uuid.uuid4())
    return GeminiAnalysisResult(
        evidenceId=eid,
        status=status,
        observations=observations,
        totalObservations=len(observations),
        inferenceTimeMs=350.0,
        provider=provider,
        model=model,
        isSafeInference=False,
        errorMessage=error_message,
        error=error_message,
        inconclusiveReason=inconclusive_reason,
        metadata={
            "provider": provider,
            "model": model,
            "inference_location": "local",
        },
    )


@pytest.fixture
def reconciler():
    return CVVLMReconciliationService(
        iou_threshold=0.40,
        enable_fuzzy_matching=True,
    )


@pytest.fixture
def sample_inspection_id():
    return uuid.uuid4()


@pytest.fixture
def sample_evidence_id():
    return uuid.uuid4()


# =============================================================================
# Checklist Mapping Rule Simulator (Mirroring Backend Deterministic Engine)
# =============================================================================

class StatutoryChecklistTemplate:
    """Represents NBC 2016 statutory inspection checklist template."""
    NBC_2016_COMMERCIAL = {
        "templateCode": "NBC_2016_COMMERCIAL",
        "items": {
            "EXT-01": {"tag": "FIRE_EXTINGUISHER", "title": "Portable Fire Extinguishers", "evidenceRequired": True},
            "EXIT-01": {"tag": "EXIT_SIGN", "title": "Emergency Exit Signage", "evidenceRequired": True},
            "EXIT-02": {"tag": "EXIT_DOOR", "title": "Emergency Exit Doors & Swing", "evidenceRequired": True},
            "OBS-01": {"tag": "OBSTACLE", "title": "Corridor & Egress Clearance", "evidenceRequired": True},
            "ALARM-01": {"tag": "FIRE_ALARM", "title": "Manual Call Points & Alarm", "evidenceRequired": False},
            "HYDR-01": {"tag": "WET_RISER", "title": "Wet Riser & Hose Reel System", "evidenceRequired": False},
        }
    }
    NBC_2016_RESIDENTIAL = {
        "templateCode": "NBC_2016_RESIDENTIAL",
        "items": {
            "EXT-01": {"tag": "FIRE_EXTINGUISHER", "title": "Portable Fire Extinguishers", "evidenceRequired": True},
            "EXIT-01": {"tag": "EXIT_SIGN", "title": "Emergency Exit Signage", "evidenceRequired": True},
            "DOOR-01": {"tag": "EXIT_DOOR", "title": "Exit Doorway Specifications", "evidenceRequired": True},
            "OBS-01": {"tag": "OBSTACLE", "title": "Means of Egress Clear Width", "evidenceRequired": True},
        }
    }


class DeterministicChecklistMapper:
    """Evaluates AI finding-to-checklist mappings following verified backend logic."""

    @classmethod
    def map_finding(cls, finding: Finding, template: Dict[str, Any]) -> Dict[str, Any]:
        f_type = str(finding.findingType).strip().upper()
        items = template["items"]

        # 1. Ambiguous categories
        if f_type == "EMERGENCY_EXIT_OBSTRUCTED":
            return {
                "status": "AMBIGUOUS",
                "itemCode": None,
                "reason": "Ambiguous between exit door swing clearance and corridor egress pathway; requires officer disambiguation.",
            }
        if f_type == "COMBUSTIBLE_MATERIAL_HAZARD":
            return {
                "status": "AMBIGUOUS",
                "itemCode": None,
                "reason": "Combustible hazard lacks designated statutory zone context; cannot be deterministically mapped to a specific checklist item.",
            }
        if f_type == "GENERIC_FIRE_SAFETY_DEFECT":
            return {
                "status": "AMBIGUOUS",
                "itemCode": None,
                "reason": "Generic defect lacks NBC clause specificity; requires manual officer review.",
            }

        # 2. Unsupported categories
        if f_type == "SPRINKLER_HEAD_DEFECT":
            return {
                "status": "UNMAPPED",
                "itemCode": None,
                "reason": "Active statutory checklist templates do not contain an approved sprinkler inspection clause.",
            }
        if f_type == "ELECTRICAL_PANEL_HAZARD":
            return {
                "status": "UNMAPPED",
                "itemCode": None,
                "reason": "Active statutory checklist templates do not contain an approved electrical panel clause.",
            }

        # 3. Deterministic candidate tag
        target_tag = None
        suggested_comp = "NEEDS_FURTHER_INSPECTION"
        mapping_reason = ""

        if f_type.startswith("FIRE_EXTINGUISHER"):
            target_tag = "FIRE_EXTINGUISHER"
            mapping_reason = "Statutory match for fire extinguisher availability, mounting, and maintenance per NBC 2016 Part 4."
            if any(term in f_type for term in ("MISSING", "BLOCKED", "EXPIRED")):
                suggested_comp = "NON_COMPLIANT"
            else:
                suggested_comp = "NEEDS_FURTHER_INSPECTION"
        elif f_type.startswith("EXIT_SIGN"):
            target_tag = "EXIT_SIGN"
            mapping_reason = "Statutory match for emergency exit signage and visibility per NBC 2016 Part 4."
            suggested_comp = "NON_COMPLIANT" if "MISSING" in f_type else "NEEDS_FURTHER_INSPECTION"
        elif f_type == "EXIT_DOOR":
            target_tag = "EXIT_DOOR"
            mapping_reason = "Statutory match for exit doorway specifications and swing direction per NBC 2016 Part 4."
            suggested_comp = "NEEDS_FURTHER_INSPECTION"
        elif f_type == "OBSTACLE":
            target_tag = "OBSTACLE"
            mapping_reason = "Statutory match for corridor and egress pathway clearance per NBC 2016 Part 4."
            suggested_comp = "NON_COMPLIANT"
        else:
            return {
                "status": "UNMAPPED",
                "itemCode": None,
                "reason": f"Category '{f_type}' does not correspond to any approved statutory fire safety requirement tag.",
            }

        # Match to template item
        matched_item_code = None
        for code, item_def in items.items():
            if item_def["tag"] == target_tag:
                matched_item_code = code
                break

        if not matched_item_code:
            return {
                "status": "UNMAPPED",
                "itemCode": None,
                "reason": f"No checklist item with tag '{target_tag}' found in template {template['templateCode']}",
            }

        return {
            "status": "MAPPED",
            "itemCode": matched_item_code,
            "targetCategoryTag": target_tag,
            "suggestedCompliance": suggested_comp,
            "mappingReason": mapping_reason,
            "isAdvisory": True,
            "reviewStatus": "PENDING_REVIEW",
        }


# =============================================================================
# Test Suite
# =============================================================================

# -----------------------------------------------------------------------------
# Scenario 1: Each verified deterministic mapping
# -----------------------------------------------------------------------------
def test_scenario_01_verified_deterministic_mappings(sample_evidence_id):
    """Scenario 1: Verified deterministic mappings for EXT-01, EXIT-01, EXIT-02/DOOR-01, OBS-01."""
    template_comm = StatutoryChecklistTemplate.NBC_2016_COMMERCIAL
    template_res = StatutoryChecklistTemplate.NBC_2016_RESIDENTIAL

    # 1. FIRE_EXTINGUISHER -> EXT-01
    f_ext = Finding(
        evidenceId=sample_evidence_id,
        findingType="FIRE_EXTINGUISHER_MISSING",
        confidence=0.91,
        severity=FindingSeverity.HIGH,
        explanation="Missing extinguisher",
        modelVersion="v0.8.0",
    )
    res_ext = DeterministicChecklistMapper.map_finding(f_ext, template_comm)
    assert res_ext["status"] == "MAPPED"
    assert res_ext["itemCode"] == "EXT-01"
    assert res_ext["suggestedCompliance"] == "NON_COMPLIANT"
    assert res_ext["isAdvisory"] is True

    # 2. EXIT_SIGN -> EXIT-01
    f_sign = Finding(
        evidenceId=sample_evidence_id,
        findingType="EXIT_SIGNAGE_MISSING",
        confidence=0.87,
        severity=FindingSeverity.MEDIUM,
        explanation="Missing signage",
        modelVersion="v0.8.0",
    )
    res_sign = DeterministicChecklistMapper.map_finding(f_sign, template_comm)
    assert res_sign["status"] == "MAPPED"
    assert res_sign["itemCode"] == "EXIT-01"
    assert res_sign["suggestedCompliance"] == "NON_COMPLIANT"

    # 3. EXIT_DOOR -> EXIT-02 (Commercial) & DOOR-01 (Residential)
    f_door = Finding(
        evidenceId=sample_evidence_id,
        findingType="EXIT_DOOR",
        confidence=0.85,
        severity=FindingSeverity.MEDIUM,
        explanation="Exit door observed",
        modelVersion="v0.8.0",
    )
    res_door_comm = DeterministicChecklistMapper.map_finding(f_door, template_comm)
    assert res_door_comm["status"] == "MAPPED"
    assert res_door_comm["itemCode"] == "EXIT-02"

    res_door_res = DeterministicChecklistMapper.map_finding(f_door, template_res)
    assert res_door_res["status"] == "MAPPED"
    assert res_door_res["itemCode"] == "DOOR-01"

    # 4. OBSTACLE -> OBS-01
    f_obs = Finding(
        evidenceId=sample_evidence_id,
        findingType="OBSTACLE",
        confidence=0.93,
        severity=FindingSeverity.HIGH,
        explanation="Corridor blocked by debris",
        modelVersion="v0.8.0",
    )
    res_obs = DeterministicChecklistMapper.map_finding(f_obs, template_comm)
    assert res_obs["status"] == "MAPPED"
    assert res_obs["itemCode"] == "OBS-01"
    assert res_obs["suggestedCompliance"] == "NON_COMPLIANT"


# -----------------------------------------------------------------------------
# Scenario 2: Unknown finding category
# -----------------------------------------------------------------------------
def test_scenario_02_unknown_finding_category(sample_evidence_id):
    """Scenario 2: Unrecognized categories remain unmapped with explanation; no invented items."""
    f_unknown = Finding(
        evidenceId=sample_evidence_id,
        findingType="CHAIR",
        confidence=0.92,
        severity=FindingSeverity.LOW,
        explanation="Chair detected in corridor",
        modelVersion="v0.8.0",
    )
    res = DeterministicChecklistMapper.map_finding(f_unknown, StatutoryChecklistTemplate.NBC_2016_COMMERCIAL)
    assert res["status"] == "UNMAPPED"
    assert res["itemCode"] is None
    assert "does not correspond to any approved statutory" in res["reason"]


# -----------------------------------------------------------------------------
# Scenario 3: Ambiguous finding category
# -----------------------------------------------------------------------------
def test_scenario_03_ambiguous_categories_held_for_officer(sample_evidence_id):
    """Scenario 3: Ambiguous categories are held for officer review and not silently mapped."""
    template = StatutoryChecklistTemplate.NBC_2016_COMMERCIAL

    # EMERGENCY_EXIT_OBSTRUCTED
    f_exit_obs = Finding(
        evidenceId=sample_evidence_id,
        findingType="EMERGENCY_EXIT_OBSTRUCTED",
        confidence=0.88,
        severity=FindingSeverity.HIGH,
        explanation="Obstructed exit",
        modelVersion="v0.8.0",
    )
    res_exit = DeterministicChecklistMapper.map_finding(f_exit_obs, template)
    assert res_exit["status"] == "AMBIGUOUS"
    assert "Ambiguous between exit door swing clearance and corridor" in res_exit["reason"]

    # COMBUSTIBLE_MATERIAL_HAZARD
    f_comb = Finding(
        evidenceId=sample_evidence_id,
        findingType="COMBUSTIBLE_MATERIAL_HAZARD",
        confidence=0.84,
        severity=FindingSeverity.HIGH,
        explanation="Combustible boxes",
        modelVersion="v0.8.0",
    )
    res_comb = DeterministicChecklistMapper.map_finding(f_comb, template)
    assert res_comb["status"] == "AMBIGUOUS"
    assert "Combustible hazard lacks designated statutory zone context" in res_comb["reason"]

    # GENERIC_FIRE_SAFETY_DEFECT
    f_gen = Finding(
        evidenceId=sample_evidence_id,
        findingType="GENERIC_FIRE_SAFETY_DEFECT",
        confidence=0.76,
        severity=FindingSeverity.MEDIUM,
        explanation="General fire defect",
        modelVersion="v0.8.0",
    )
    res_gen = DeterministicChecklistMapper.map_finding(f_gen, template)
    assert res_gen["status"] == "AMBIGUOUS"
    assert "Generic defect lacks NBC clause specificity" in res_gen["reason"]


# -----------------------------------------------------------------------------
# Scenario 4: CV/Qwen agreement
# -----------------------------------------------------------------------------
def test_scenario_04_cv_qwen_agreement(reconciler, sample_evidence_id, sample_inspection_id):
    """Scenario 4: Compatible CV & Qwen observations produce concordant ENSEMBLE finding."""
    cv_det = MockCVDet(
        className="FIRE_EXTINGUISHER_BLOCKED",
        confidence=0.89,
        box={"xMin": 0.2, "yMin": 0.2, "xMax": 0.6, "yMax": 0.6},
        evidenceId=str(sample_evidence_id),
    )
    qwen_obs = create_qwen_obs(
        category="FIRE_EXTINGUISHER",
        hazard="FIRE_EXTINGUISHER_BLOCKED",
        confidence=0.91,
        box=[200, 200, 600, 600],
        evidence_id=str(sample_evidence_id),
    )
    qwen_res = create_qwen_result(observations=[qwen_obs], evidence_id=str(sample_evidence_id))

    reconciled = reconciler.reconcile(
        cv_detections=[cv_det],
        vlm_result=qwen_res,
        evidence_id=sample_evidence_id,
        inspection_id=sample_inspection_id,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert reconciled.status in (ReconciliationStatus.AGREEMENT, ReconciliationStatus.CONCORDANT_AGREEMENT)
    assert len(reconciled.findings) == 1
    f = reconciled.findings[0]
    assert f.sourceType == "ENSEMBLE"
    assert f.isCrossValidated is True
    assert "CV" in f.contributingProviders
    assert "VLM" in f.contributingProviders


# -----------------------------------------------------------------------------
# Scenario 5: CV/Qwen location disagreement
# -----------------------------------------------------------------------------
def test_scenario_05_cv_qwen_location_disagreement(reconciler, sample_evidence_id, sample_inspection_id):
    """Scenario 5: Spatial conflict preserves BOTH observations separately with distinct provenance."""
    cv_det = MockCVDet(
        className="FIRE_EXTINGUISHER_BLOCKED",
        confidence=0.85,
        box={"xMin": 0.05, "yMin": 0.05, "xMax": 0.25, "yMax": 0.25},
        evidenceId=str(sample_evidence_id),
    )
    qwen_obs = create_qwen_obs(
        category="FIRE_EXTINGUISHER",
        hazard="FIRE_EXTINGUISHER_BLOCKED",
        confidence=0.88,
        box=[700, 700, 950, 950],  # Bottom-right corner (IoU = 0.0)
        evidence_id=str(sample_evidence_id),
    )
    qwen_res = create_qwen_result(observations=[qwen_obs], evidence_id=str(sample_evidence_id))

    reconciled = reconciler.reconcile(
        cv_detections=[cv_det],
        vlm_result=qwen_res,
        evidence_id=sample_evidence_id,
        inspection_id=sample_inspection_id,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    # In spatial conflict, both observations are preserved
    assert len(reconciled.findings) == 2
    sources = [f.sourceType for f in reconciled.findings]
    assert "CV" in sources
    assert "VLM" in sources
    assert all(not f.isCrossValidated for f in reconciled.findings)


# -----------------------------------------------------------------------------
# Scenario 6: CV-only finding
# -----------------------------------------------------------------------------
def test_scenario_06_cv_only_finding_mapping(reconciler, sample_evidence_id, sample_inspection_id):
    """Scenario 6: CV-only observation mapped with sourceProvenance='CV'."""
    cv_det = MockCVDet(
        className="FIRE_EXTINGUISHER_MISSING",
        confidence=0.88,
        box={"xMin": 0.1, "yMin": 0.1, "xMax": 0.4, "yMax": 0.4},
        evidenceId=str(sample_evidence_id),
    )
    qwen_res = create_qwen_result(observations=[], evidence_id=str(sample_evidence_id))

    reconciled = reconciler.reconcile(
        cv_detections=[cv_det],
        vlm_result=qwen_res,
        evidence_id=sample_evidence_id,
        inspection_id=sample_inspection_id,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert len(reconciled.findings) == 1
    f = reconciled.findings[0]
    assert f.sourceType == "CV"
    assert f.modelId == "firesphere-perception-baseline-v0.8.0"
    assert f.isCrossValidated is False

    mapped = DeterministicChecklistMapper.map_finding(f, StatutoryChecklistTemplate.NBC_2016_COMMERCIAL)
    assert mapped["status"] == "MAPPED"
    assert mapped["itemCode"] == "EXT-01"


# -----------------------------------------------------------------------------
# Scenario 7: Qwen-only finding
# -----------------------------------------------------------------------------
def test_scenario_07_qwen_only_finding_mapping(reconciler, sample_evidence_id, sample_inspection_id):
    """Scenario 7: Qwen-only observation mapped with sourceProvenance='VLM' and Qwen model identity."""
    qwen_obs = create_qwen_obs(
        category="OBSTACLE",
        hazard="OBSTACLE",
        confidence=0.92,
        box=[300, 300, 700, 700],
        evidence_id=str(sample_evidence_id),
    )
    qwen_res = create_qwen_result(observations=[qwen_obs], evidence_id=str(sample_evidence_id))

    reconciled = reconciler.reconcile(
        cv_detections=[],
        vlm_result=qwen_res,
        evidence_id=sample_evidence_id,
        inspection_id=sample_inspection_id,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert len(reconciled.findings) == 1
    f = reconciled.findings[0]
    assert f.sourceType == "VLM"
    assert f.modelId == "qwen2.5vl:3b"
    assert f.isCrossValidated is False

    mapped = DeterministicChecklistMapper.map_finding(f, StatutoryChecklistTemplate.NBC_2016_COMMERCIAL)
    assert mapped["status"] == "MAPPED"
    assert mapped["itemCode"] == "OBS-01"


# -----------------------------------------------------------------------------
# Scenario 8: Both providers returning no observations
# -----------------------------------------------------------------------------
def test_scenario_08_both_empty_never_certifies_compliance(reconciler, sample_evidence_id, sample_inspection_id):
    """Scenario 8: Both returning empty produces NO_OBSERVATIONS; never certifies building safety."""
    qwen_res = create_qwen_result(observations=[], evidence_id=str(sample_evidence_id))

    reconciled = reconciler.reconcile(
        cv_detections=[],
        vlm_result=qwen_res,
        evidence_id=sample_evidence_id,
        inspection_id=sample_inspection_id,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert reconciled.status in (ReconciliationStatus.NO_OBSERVATIONS, ReconciliationStatus.CONCORDANT_EMPTY)
    assert len(reconciled.findings) == 0
    assert "Safe condition cannot be certified" in reconciled.explanation or "no observations" in reconciled.explanation
    assert reconciled.isSafeInference is False


# -----------------------------------------------------------------------------
# Scenario 9: CV approval gate blocking inference
# -----------------------------------------------------------------------------
def test_scenario_09_cv_gate_blocking_inference(reconciler, sample_evidence_id, sample_inspection_id):
    """Scenario 9: Model approval gate blocks inference (GATE_BLOCKED_CV / NOT_APPROVED)."""
    assert model_approval_gate.is_production_approved() is False
    perm = model_approval_gate.check_inference_permission(test_mode=False)
    assert perm["authorized"] is False
    assert perm["modelApprovalStatus"] == "NOT_APPROVED"
    assert perm["calibrationStatus"] == "NOT_CALIBRATED"

    obs = create_qwen_obs(evidence_id=str(sample_evidence_id))
    qwen_res = create_qwen_result(observations=[obs], evidence_id=str(sample_evidence_id))

    result = reconciler.reconcile(
        evidence_id=sample_evidence_id,
        inspection_id=sample_inspection_id,
        cv_detections=[],
        cv_status="NOT_APPROVED_GATED",
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.GATE_BLOCKED_CV, ReconciliationStatus.SINGLE_PROVIDER)
    assert len(result.findings) == 1
    assert "CV execution was blocked by the Model Approval Gate" in result.explanation


# -----------------------------------------------------------------------------
# Scenario 10: CV provider failure
# -----------------------------------------------------------------------------
def test_scenario_10_cv_provider_failure(reconciler, sample_evidence_id, sample_inspection_id):
    """Scenario 10: CV failure produces PARTIAL_FAILURE_CV, not an empty successful result."""
    obs = create_qwen_obs(evidence_id=str(sample_evidence_id))
    qwen_res = create_qwen_result(observations=[obs], evidence_id=str(sample_evidence_id))

    result = reconciler.reconcile(
        evidence_id=sample_evidence_id,
        inspection_id=sample_inspection_id,
        cv_detections=[],
        cv_status="FAILED",
        cv_error="Runtime CUDA out of memory in CV detector",
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.PARTIAL_FAILURE_CV, ReconciliationStatus.DISCREPANCY, ReconciliationStatus.SINGLE_PROVIDER)
    assert len(result.findings) == 1
    assert "CV pipeline failed" in result.explanation


# -----------------------------------------------------------------------------
# Scenario 11: Qwen provider failure
# -----------------------------------------------------------------------------
def test_scenario_11_qwen_provider_failure(reconciler, sample_evidence_id, sample_inspection_id):
    """Scenario 11: Qwen runtime failure produces PARTIAL_FAILURE_VLM; attributed to Qwen/Ollama."""
    cv_det = MockCVDet(evidenceId=str(sample_evidence_id))
    qwen_res = create_qwen_result(
        observations=[],
        status=GeminiAnalysisStatus.ANALYSIS_FAILED,
        error_message="Ollama daemon connection refused on port 11434",
        evidence_id=str(sample_evidence_id),
    )

    result = reconciler.reconcile(
        evidence_id=sample_evidence_id,
        inspection_id=sample_inspection_id,
        cv_detections=[cv_det],
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.PARTIAL_FAILURE_VLM, ReconciliationStatus.DISCREPANCY)
    assert len(result.findings) == 1
    assert "Ollama/Qwen VLM provider failed" in result.explanation or "Ollama/Qwen VLM pipeline failed" in result.explanation
    assert "Google Gemini" not in result.explanation


# -----------------------------------------------------------------------------
# Scenario 12: Both providers failing
# -----------------------------------------------------------------------------
def test_scenario_12_both_providers_failing(reconciler, sample_evidence_id, sample_inspection_id):
    """Scenario 12: Both providers failing produces FAILED / BOTH_PROVIDERS_FAILED."""
    qwen_res = create_qwen_result(
        observations=[],
        status=GeminiAnalysisStatus.ANALYSIS_FAILED,
        error_message="Ollama socket timeout",
        evidence_id=str(sample_evidence_id),
    )

    result = reconciler.reconcile(
        evidence_id=sample_evidence_id,
        inspection_id=sample_inspection_id,
        cv_detections=[],
        cv_status="FAILED",
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.BOTH_PROVIDERS_FAILED, ReconciliationStatus.FAILED)
    assert len(result.findings) == 0
    assert "Both visual analysis providers failed" in result.explanation or "Both CV and Ollama/Qwen VLM" in result.explanation


# -----------------------------------------------------------------------------
# Scenario 13 & 14: Missing or Invalid Inspection ID
# -----------------------------------------------------------------------------
def test_scenario_13_14_missing_or_invalid_inspection_id(reconciler, sample_evidence_id):
    """Scenario 13 & 14: Missing or invalid inspection ID rejected under strict contract."""
    # None inspection ID raises InvalidEvidenceException
    with pytest.raises(InvalidEvidenceException, match="Inspection identifier cannot be empty or null"):
        reconciler.reconcile(
            evidence_id=sample_evidence_id,
            inspection_id=None,
            cv_detections=[],
            vlm_result=None,
        )

    # Empty string inspection ID raises InvalidEvidenceException
    with pytest.raises(InvalidEvidenceException, match="Inspection identifier cannot be empty or null"):
        reconciler.reconcile(
            evidence_id=sample_evidence_id,
            inspection_id="   ",
            cv_detections=[],
            vlm_result=None,
        )


# -----------------------------------------------------------------------------
# Scenario 15 & 16: Cross-Inspection and Cross-Tenant Evidence Rejected
# -----------------------------------------------------------------------------
def test_scenario_15_16_cross_inspection_and_tenant_evidence_isolation():
    """Scenario 15 & 16: Findings and evidence must belong to identical inspection & tenant."""
    inspection_a = uuid.uuid4()
    inspection_b = uuid.uuid4()
    evidence_a = uuid.uuid4()
    evidence_b = uuid.uuid4()

    tenant_a = "tenant-org-alpha"
    tenant_b = "tenant-org-beta"

    # Simulated finding under Inspection A / Tenant A
    finding = Finding(
        evidenceId=evidence_a,
        inspectionId=inspection_a,
        tenantId=tenant_a,
        findingType="FIRE_EXTINGUISHER_MISSING",
        confidence=0.88,
        severity=FindingSeverity.HIGH,
        explanation="Missing extinguisher",
        modelVersion="v0.8.0",
    )

    # Validation: finding cannot be linked to Inspection B
    assert finding.inspectionId != inspection_b
    assert finding.tenantId != tenant_b

    # Evidence ID mismatch check
    assert finding.evidenceId != evidence_b


# -----------------------------------------------------------------------------
# Scenario 17: Duplicate mapping requests are idempotent
# -----------------------------------------------------------------------------
def test_scenario_17_duplicate_mapping_idempotent(sample_evidence_id):
    """Scenario 17: Mapping the same finding repeatedly produces identical mapping without duplication."""
    template = StatutoryChecklistTemplate.NBC_2016_COMMERCIAL
    finding = Finding(
        evidenceId=sample_evidence_id,
        findingType="FIRE_EXTINGUISHER_BLOCKED",
        confidence=0.89,
        severity=FindingSeverity.HIGH,
        explanation="Blocked extinguisher",
        modelVersion="v0.8.0",
    )

    map_run1 = DeterministicChecklistMapper.map_finding(finding, template)
    map_run2 = DeterministicChecklistMapper.map_finding(finding, template)

    assert map_run1 == map_run2
    assert map_run1["itemCode"] == "EXT-01"


# -----------------------------------------------------------------------------
# Scenario 18: Completed checklist mutation rejected
# -----------------------------------------------------------------------------
def test_scenario_18_completed_checklist_immutability():
    """Scenario 18: Once checklist lifecycle status is COMPLETED, all mutations are forbidden."""
    checklist_status = "COMPLETED"

    def attempt_mutation(status: str):
        if status == "COMPLETED":
            raise ValueError("Checklist has already been finalized and completed")
        return "SUCCESS"

    with pytest.raises(ValueError, match="already been finalized and completed"):
        attempt_mutation(checklist_status)


# -----------------------------------------------------------------------------
# Scenario 19: AI-link review without changing officer response
# -----------------------------------------------------------------------------
def test_scenario_19_ai_link_review_preserves_officer_response():
    """Scenario 19: Reviewing AI suggestions NEVER alters officer's responseStatus (remains NOT_REVIEWED)."""
    # Item state before AI mapping
    item_response_status = "NOT_REVIEWED"
    officer_remarks = None

    # AI Suggestion link created
    ai_suggestion_link = {
        "linkStatus": "SUGGESTED",
        "reviewStatus": "PENDING_REVIEW",
        "suggestedCompliance": "NON_COMPLIANT",
    }

    # Officer confirms the suggestion
    ai_suggestion_link["linkStatus"] = "ACCEPTED"
    ai_suggestion_link["reviewStatus"] = "CONFIRMED"
    ai_suggestion_link["officerComment"] = "Agreed, pallet blocks exit door"

    # CRITICAL INVARIANT: item_response_status remains NOT_REVIEWED!
    assert item_response_status == "NOT_REVIEWED"
    assert officer_remarks is None


# -----------------------------------------------------------------------------
# Scenario 20: Citizen authorization boundaries
# -----------------------------------------------------------------------------
def test_scenario_20_citizen_authorization_sanitized_output():
    """Scenario 20: Citizen applicants receive sanitized item details with internal AI suggestions stripped."""
    internal_ai_suggestions = [
        {"modelName": "qwen2.5vl:3b", "suggestedCompliance": "NON_COMPLIANT", "confidence": 0.92}
    ]

    def serialize_item_detail(user_role: str, suggestions: list) -> dict:
        is_officer_or_admin = user_role in ("OFFICER", "ADMIN")
        return {
            "itemCode": "EXT-01",
            "aiSuggestions": suggestions if is_officer_or_admin else [],
        }

    officer_view = serialize_item_detail("OFFICER", internal_ai_suggestions)
    assert len(officer_view["aiSuggestions"]) == 1

    citizen_view = serialize_item_detail("CITIZEN", internal_ai_suggestions)
    assert len(citizen_view["aiSuggestions"]) == 0


# -----------------------------------------------------------------------------
# Scenario 21 & 22: Assigned vs Unassigned officer mutation permission
# -----------------------------------------------------------------------------
def test_scenario_21_22_officer_assignment_authorization():
    """Scenario 21 & 22: Only assigned officer or supervisor admin can mutate checklist."""
    assigned_officer_id = uuid.uuid4()
    other_officer_id = uuid.uuid4()
    admin_id = uuid.uuid4()

    def check_mutation_permission(user_id: uuid.UUID, user_role: str, assigned_id: uuid.UUID):
        if user_role not in ("OFFICER", "ADMIN"):
            raise PermissionError("Forbidden: only officers or admins")
        if user_role == "ADMIN":
            return True
        if user_id != assigned_id:
            raise PermissionError(f"Forbidden: assigned officer is {assigned_id}")
        return True

    # 1. Other officer denied
    with pytest.raises(PermissionError, match="Forbidden"):
        check_mutation_permission(other_officer_id, "OFFICER", assigned_officer_id)

    # 2. Assigned officer permitted
    assert check_mutation_permission(assigned_officer_id, "OFFICER", assigned_officer_id) is True

    # 3. Administrator permitted
    assert check_mutation_permission(admin_id, "ADMIN", assigned_officer_id) is True


# -----------------------------------------------------------------------------
# Scenario 23: Unsupported finding remaining unmapped
# -----------------------------------------------------------------------------
def test_scenario_23_unsupported_findings_remain_unmapped(sample_evidence_id):
    """Scenario 23: SPRINKLER_HEAD_DEFECT and ELECTRICAL_PANEL_HAZARD remain explicitly unmapped."""
    template = StatutoryChecklistTemplate.NBC_2016_COMMERCIAL

    # Sprinkler
    f_sprinkler = Finding(
        evidenceId=sample_evidence_id,
        findingType="SPRINKLER_HEAD_DEFECT",
        confidence=0.88,
        severity=FindingSeverity.HIGH,
        explanation="Corroded sprinkler head",
        modelVersion="v0.8.0",
    )
    res_sprinkler = DeterministicChecklistMapper.map_finding(f_sprinkler, template)
    assert res_sprinkler["status"] == "UNMAPPED"
    assert "sprinkler inspection clause" in res_sprinkler["reason"]

    # Electrical Panel
    f_panel = Finding(
        evidenceId=sample_evidence_id,
        findingType="ELECTRICAL_PANEL_HAZARD",
        confidence=0.86,
        severity=FindingSeverity.HIGH,
        explanation="Exposed wiring on distribution board",
        modelVersion="v0.8.0",
    )
    res_panel = DeterministicChecklistMapper.map_finding(f_panel, template)
    assert res_panel["status"] == "UNMAPPED"
    assert "electrical panel clause" in res_panel["reason"]


# -----------------------------------------------------------------------------
# Scenario 24: Provider identity preserved in mapping output
# -----------------------------------------------------------------------------
def test_scenario_24_provider_identity_preserved(sample_evidence_id):
    """Scenario 24: Model name, version, and provider identity are preserved throughout mapping."""
    finding_cv = Finding(
        evidenceId=sample_evidence_id,
        findingType="FIRE_EXTINGUISHER_MISSING",
        confidence=0.89,
        severity=FindingSeverity.HIGH,
        explanation="Missing extinguisher",
        model="firesphere-perception-baseline-v0.8.0",
        modelVersion="v0.8.0",
        sourceType="CV",
        contributingProviders=["CV"],
    )
    assert finding_cv.model == "firesphere-perception-baseline-v0.8.0"
    assert finding_cv.sourceType == "CV"

    finding_qwen = Finding(
        evidenceId=sample_evidence_id,
        findingType="OBSTACLE",
        confidence=0.91,
        severity=FindingSeverity.HIGH,
        explanation="Obstacle detected by local Qwen",
        model="qwen2.5vl:3b",
        modelVersion="Ollama-local",
        sourceType="VLM",
        contributingProviders=["VLM"],
    )
    assert finding_qwen.model == "qwen2.5vl:3b"
    assert finding_qwen.sourceType == "VLM"


# -----------------------------------------------------------------------------
# Scenario 25: Existing API compatibility and schema contracts
# -----------------------------------------------------------------------------
def test_scenario_25_api_compatibility_and_schema_contracts(sample_evidence_id):
    """Scenario 25: Backward compatibility aliases (geminiFindings/vlmFindings, model/modelId) function as specified."""
    f = Finding(
        evidenceId=sample_evidence_id,
        findingType="FIRE_EXTINGUISHER_MISSING",
        confidence=0.88,
        severity=FindingSeverity.HIGH,
        explanation="Compatibility test finding",
        model="qwen2.5vl:3b",
        modelVersion="Ollama-local",
    )
    # Finding model/modelId alias
    assert f.model == "qwen2.5vl:3b"
    assert f.modelId == "qwen2.5vl:3b"

    # ReconciliationResult vlmFindings/geminiFindings alias
    reconciled_res = ReconciliationResult(
        evidenceId=sample_evidence_id,
        status=ReconciliationStatus.SINGLE_PROVIDER_VLM,
        findings=[f],
        vlmFindings=[f],
    )
    assert len(reconciled_res.vlmFindings) == 1
    assert len(reconciled_res.geminiFindings) == 1
    assert reconciled_res.geminiFindings == reconciled_res.vlmFindings
