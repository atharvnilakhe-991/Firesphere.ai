"""FireSphere.ai — Stage 8 Unified CV-Qwen Finding Contract & Reconciliation Tests.

Tests:
1. CV-only findings preservation and provenance
2. Qwen-only findings preservation and provenance
3. Concordant CV/Qwen equivalent observations (Case A)
4. Conflicting hazard classifications (Case B - HAZARD_MISMATCH)
5. Spatial ambiguity / ambiguous matches (Case L - AMBIGUOUS_MATCH)
6. Invalid and out-of-bounds bounding box handling
7. Explicit handling of missing provenance (no fabrication)
8. Correct Qwen provider/model identity (no false Gemini attribution)
9. Provider metadata serialization
10. Reconciliation explanation attribution
11. Successful empty CV results (Case D/E)
12. Successful empty Qwen results (Case C/E)
13. Both providers returning empty (Case E - no false compliance)
14. CV approval-gated status (Case F - GATE_BLOCKED_CV)
15. CV runtime failure handling (Case G - PARTIAL_FAILURE_CV)
16. Qwen runtime failure handling (Case H - PARTIAL_FAILURE_VLM)
17. Both providers failing (Case I - BOTH_PROVIDERS_FAILED)
18. Inconclusive image analysis (Case J - INCONCLUSIVE)
19. Invalid provider configuration rejection (fail-closed, no silent fallback)
20. Absence of silent hosted Gemini fallback
21. Shared schema compatibility (gemini_schemas.py used by Qwen)
22. Bidirectional compatibility aliases (model <-> modelId, geminiFindings <-> vlmFindings)
23. Preservation of officer review boundary (strictly advisory, no autonomous Fire NOC decisions)
"""

import math
import uuid
import pytest
from datetime import datetime, timezone

from app.core.config import Settings
from app.core.exceptions import InvalidEvidenceException
from app.models.gemini_schemas import (
    GeminiAnalysisResult,
    GeminiAnalysisStatus,
    GeminiObservation,
    parse_and_validate_bounding_box,
)
from app.models.vlm_provider import BaseVLMProvider, get_vlm_provider
from app.models.ollama_provider import OllamaQwenProvider
from app.intelligence.reconciliation import (
    CVVLMReconciliationService,
    ResultReconciler,
    validate_and_normalize_bbox,
    objects_match,
    hazards_match,
    hazards_conflict,
)
from app.schemas.finding import (
    BoundingBox,
    Finding,
    FindingSeverity,
    FindingType,
    SpatialValidationStatus,
)
from app.schemas.reconciliation import (
    ConflictDetail,
    ConflictType,
    ProviderProvenance,
    ReconciliationResult,
    ReconciliationStatus,
)


@pytest.fixture
def reconciler():
    return CVVLMReconciliationService(
        iou_threshold=0.40,
        enable_fuzzy_matching=True,
    )


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
    description="Qwen detected blocked fire extinguisher",
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


# =========================================================================
# 1. CV-Only Findings
# =========================================================================

def test_cv_only_finding_preserves_provenance(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    cv_det = MockCVDet(evidenceId=str(eid))
    qwen_res = create_qwen_result(observations=[], evidence_id=str(eid))

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[cv_det],
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.SINGLE_PROVIDER, ReconciliationStatus.SINGLE_PROVIDER_CV)
    assert len(result.findings) == 1
    reconciled = result.findings[0]
    assert reconciled.inspectionId == iid
    assert reconciled.sourceType == "CV"
    assert reconciled.modelId == "firesphere-perception-baseline-v0.8.0"
    assert reconciled.isCrossValidated is False
    assert "Ollama/Qwen VLM" in result.explanation or "qwen2.5vl:3b" in result.explanation
    assert "Gemini" not in result.explanation


# =========================================================================
# 2. Qwen-Only Findings
# =========================================================================

def test_qwen_only_finding_preserves_provenance(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    obs = create_qwen_obs(hazard="FIRE_EXTINGUISHER_MISSING", evidence_id=str(eid))
    qwen_res = create_qwen_result(observations=[obs], evidence_id=str(eid))

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[],
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.SINGLE_PROVIDER, ReconciliationStatus.SINGLE_PROVIDER_VLM)
    assert len(result.findings) == 1
    reconciled = result.findings[0]
    assert reconciled.inspectionId == iid
    assert reconciled.sourceType == "VLM"
    assert reconciled.modelId == "qwen2.5vl:3b"
    assert reconciled.isCrossValidated is False
    assert "Ollama/Qwen VLM" in result.explanation
    assert "Gemini" not in result.explanation


# =========================================================================
# 3. Concordant CV/Qwen Equivalent Observations (Case A)
# =========================================================================

def test_concordant_cv_qwen_agreement(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    cv_det = MockCVDet(
        className="FIRE_EXTINGUISHER_BLOCKED",
        box={"xMin": 0.1, "yMin": 0.1, "xMax": 0.5, "yMax": 0.5},
        evidenceId=str(eid),
    )
    obs = create_qwen_obs(
        hazard="FIRE_EXTINGUISHER_BLOCKED",
        box=[100, 100, 500, 500],
        evidence_id=str(eid),
    )
    qwen_res = create_qwen_result(observations=[obs], evidence_id=str(eid))

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[cv_det],
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.AGREEMENT, ReconciliationStatus.CONCORDANT_AGREEMENT)
    assert len(result.findings) == 1
    reconciled = result.findings[0]
    assert reconciled.inspectionId == iid
    assert reconciled.isCrossValidated is True
    assert set(reconciled.contributingProviders) == {"CV", "VLM"}
    assert reconciled.confidence >= 0.88


# =========================================================================
# 4. Conflicting Hazard Classifications (Case B - HAZARD_MISMATCH)
# =========================================================================

def test_conflicting_hazard_mismatch_preserves_both_viewpoints(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    cv_det = MockCVDet(
        className="FIRE_EXTINGUISHER_BLOCKED",
        box={"xMin": 0.2, "yMin": 0.2, "xMax": 0.6, "yMax": 0.6},
        evidenceId=str(eid),
    )
    obs = create_qwen_obs(
        category="FIRE_EXTINGUISHER",
        hazard="FIRE_EXTINGUISHER_MISSING",
        box=[200, 200, 600, 600],
        description="Missing extinguisher bracket empty",
        evidence_id=str(eid),
    )
    qwen_res = create_qwen_result(observations=[obs], evidence_id=str(eid))

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[cv_det],
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.CONFLICT, ReconciliationStatus.DISCORDANT_FINDING, ReconciliationStatus.DISCREPANCY)
    assert len(result.conflicts) >= 1
    conflict = result.conflicts[0]
    assert conflict.conflictType == ConflictType.HAZARD_MISMATCH
    assert conflict.cvFinding is not None
    assert conflict.vlmObservation is not None
    assert len(result.findings) == 2
    for f in result.findings:
        assert f.inspectionId == iid
    sources = {f.sourceType for f in result.findings}
    assert sources == {"CV", "VLM"}


# =========================================================================
# 5. Spatial Ambiguity / Ambiguous Matches (Case L - AMBIGUOUS_MATCH)
# =========================================================================

def test_spatial_ambiguity_borderline_iou_preserves_distinct_findings(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    cv_det = MockCVDet(
        className="FIRE_EXTINGUISHER_BLOCKED",
        box={"xMin": 0.1, "yMin": 0.1, "xMax": 0.5, "yMax": 0.5},
        evidenceId=str(eid),
    )
    obs = create_qwen_obs(
        category="FIRE_EXTINGUISHER",
        hazard="FIRE_EXTINGUISHER_BLOCKED",
        box=[450, 450, 850, 850],  # Minimal corner overlap: IoU ~ 0.015
        evidence_id=str(eid),
    )
    qwen_res = create_qwen_result(observations=[obs], evidence_id=str(eid))

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[cv_det],
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert len(result.findings) == 2
    for f in result.findings:
        assert f.inspectionId == iid
        assert f.isCrossValidated is False


# =========================================================================
# 6. Invalid and Out-of-Bounds Bounding Box Handling
# =========================================================================

def test_validate_and_normalize_bbox_handles_invalids():
    inv_box = [600, 600, 200, 200]
    res_inv = validate_and_normalize_bbox(inv_box)
    assert res_inv["validationStatus"] == SpatialValidationStatus.INVALID_COORDINATES
    assert res_inv["box"] is None

    oob_box = [-200, 100, 1500, 500]
    res_oob = validate_and_normalize_bbox(oob_box)
    assert res_oob["validationStatus"] == SpatialValidationStatus.INVALID_COORDINATES

    valid_box = [150, 250, 650, 750]
    res_valid = validate_and_normalize_bbox(valid_box)
    assert res_valid["validationStatus"] == SpatialValidationStatus.NORMALIZED
    assert res_valid["box"] == (0.15, 0.25, 0.65, 0.75)


# =========================================================================
# 7. Explicit Missing Provenance Handling (No Fabrication)
# =========================================================================

def test_missing_provenance_is_explicit_not_fabricated():
    prov = ProviderProvenance(
        provider="ollama",
        model="qwen2.5vl:3b",
        modelVersion=None,
        sourceType="VLM",
        evidenceId="ev-explicit-none",
        checkpointMetadata=None,
        calibrationStatus="NOT_CALIBRATED",
    )
    dumped = prov.model_dump()
    assert dumped["modelVersion"] is None
    assert dumped["checkpointMetadata"] is None
    assert dumped["calibrationStatus"] == "NOT_CALIBRATED"
    assert dumped["provider"] == "ollama"


# =========================================================================
# 8. Qwen Provider/Model Identity (No False Gemini Attribution)
# =========================================================================

def test_qwen_provider_identity_correctness(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    obs = create_qwen_obs(description="Detected extinguisher", evidence_id=str(eid))
    qwen_res = create_qwen_result(
        observations=[obs],
        provider="ollama",
        model="qwen2.5vl:3b",
        evidence_id=str(eid),
    )

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[],
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    finding = result.findings[0]
    assert finding.inspectionId == iid
    assert finding.modelId == "qwen2.5vl:3b"
    assert finding.sourceType == "VLM"
    assert "gemini" not in finding.modelId.lower()


# =========================================================================
# 9. Provider Metadata Serialization
# =========================================================================

def test_provider_provenance_serialization():
    prov = ProviderProvenance(
        provider="ollama",
        model="qwen2.5vl:3b",
        modelVersion="3b-instruct",
        sourceType="VLM",
        evidenceId="ev-stage8-test",
        checkpointMetadata="local-ollama",
        rawObjectCategory="FIRE_EXTINGUISHER",
        rawHazardCategory="FIRE_EXTINGUISHER_BLOCKED",
        rawConfidence=0.89,
        calibrationStatus="NOT_CALIBRATED",
        coordinateConvention="NORMALIZED_1000",
        observationDescription="Clear view of blocked extinguisher",
        uncertaintyMetadata={"iouUncertainty": 0.05},
        executionStatus="SUCCESS",
    )
    serialized = prov.model_dump()
    assert serialized["sourceType"] == "VLM"
    assert serialized["coordinateConvention"] == "NORMALIZED_1000"
    assert serialized["uncertaintyMetadata"]["iouUncertainty"] == 0.05


# =========================================================================
# 10. Reconciliation Attribution Wording
# =========================================================================

def test_reconciliation_explanation_cites_ollama_qwen(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    obs = create_qwen_obs(evidence_id=str(eid))
    qwen_res = create_qwen_result(observations=[obs], evidence_id=str(eid))

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[],
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert "Ollama/Qwen VLM" in result.explanation
    assert "Google Gemini" not in result.explanation
    assert "Gemini" not in result.explanation


# =========================================================================
# 11. Successful Empty CV Results (Case D/E)
# =========================================================================

def test_successful_empty_cv_with_qwen_finding(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    obs = create_qwen_obs(evidence_id=str(eid))
    qwen_res = create_qwen_result(observations=[obs], evidence_id=str(eid))

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[],
        cv_status="SUCCESS",
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.SINGLE_PROVIDER, ReconciliationStatus.SINGLE_PROVIDER_VLM)
    assert len(result.findings) == 1
    assert result.findings[0].inspectionId == iid
    assert "CV detector reported no observations" in result.explanation


# =========================================================================
# 12. Successful Empty Qwen Results (Case C/E)
# =========================================================================

def test_successful_empty_qwen_with_cv_finding(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    cv_det = MockCVDet(evidenceId=str(eid))
    qwen_res = create_qwen_result(
        observations=[],
        status=GeminiAnalysisStatus.ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS,
        evidence_id=str(eid),
    )

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[cv_det],
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.SINGLE_PROVIDER, ReconciliationStatus.SINGLE_PROVIDER_CV)
    assert len(result.findings) == 1
    assert result.findings[0].inspectionId == iid
    assert "Ollama/Qwen VLM reported no observations" in result.explanation


# =========================================================================
# 13. Both Providers Empty (Case E - No False Compliance)
# =========================================================================

def test_both_providers_empty_never_claims_compliance(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    qwen_res = create_qwen_result(observations=[], evidence_id=str(eid))

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[],
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.NO_OBSERVATIONS, ReconciliationStatus.CONCORDANT_EMPTY)
    assert len(result.findings) == 0
    assert "Safe condition cannot be certified" in result.explanation or "no observations" in result.explanation
    assert result.isSafeInference is False


# =========================================================================
# 14. CV Approval-Gated Status (Case F - GATE_BLOCKED_CV)
# =========================================================================

def test_cv_approval_gated_status_handling(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    obs = create_qwen_obs(evidence_id=str(eid))
    qwen_res = create_qwen_result(observations=[obs], evidence_id=str(eid))

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[],
        cv_status="NOT_APPROVED_GATED",
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.GATE_BLOCKED_CV, ReconciliationStatus.SINGLE_PROVIDER)
    assert len(result.findings) == 1
    assert result.findings[0].inspectionId == iid
    assert "CV execution was blocked by the Model Approval Gate" in result.explanation


# =========================================================================
# 15. CV Runtime Failure (Case G - PARTIAL_FAILURE_CV)
# =========================================================================

def test_cv_runtime_failure_handling(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    obs = create_qwen_obs(evidence_id=str(eid))
    qwen_res = create_qwen_result(observations=[obs], evidence_id=str(eid))

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[],
        cv_status="FAILED",
        cv_error="Runtime CUDA out of memory in CV detector",
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.PARTIAL_FAILURE_CV, ReconciliationStatus.DISCREPANCY, ReconciliationStatus.SINGLE_PROVIDER)
    assert len(result.findings) == 1
    assert result.findings[0].inspectionId == iid
    assert "CV pipeline failed" in result.explanation


# =========================================================================
# 16. Qwen Runtime Failure (Case H - PARTIAL_FAILURE_VLM)
# =========================================================================

def test_qwen_runtime_failure_handling(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    cv_det = MockCVDet(evidenceId=str(eid))
    qwen_res = create_qwen_result(
        observations=[],
        status=GeminiAnalysisStatus.ANALYSIS_FAILED,
        error_message="Ollama daemon connection refused on port 11434",
        evidence_id=str(eid),
    )

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[cv_det],
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.PARTIAL_FAILURE_VLM, ReconciliationStatus.DISCREPANCY)
    assert len(result.findings) == 1
    assert result.findings[0].inspectionId == iid
    assert "Ollama/Qwen VLM provider failed" in result.explanation or "Ollama/Qwen VLM pipeline failed" in result.explanation


# =========================================================================
# 17. Both Providers Failing (Case I - BOTH_PROVIDERS_FAILED)
# =========================================================================

def test_both_providers_failing_preserves_error_details(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    qwen_res = create_qwen_result(
        observations=[],
        status=GeminiAnalysisStatus.ANALYSIS_FAILED,
        error_message="Ollama socket timeout",
        evidence_id=str(eid),
    )

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[],
        cv_status="FAILED",
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.BOTH_PROVIDERS_FAILED, ReconciliationStatus.FAILED)
    assert len(result.findings) == 0
    assert "Both visual analysis providers failed" in result.explanation or "Both CV and Ollama/Qwen VLM" in result.explanation


# =========================================================================
# 18. Inconclusive Image Analysis (Case J - INCONCLUSIVE)
# =========================================================================

def test_inconclusive_image_analysis_preserves_limitations(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    cv_det = MockCVDet(evidenceId=str(eid))
    qwen_res = create_qwen_result(
        observations=[],
        status=GeminiAnalysisStatus.ANALYSIS_INCONCLUSIVE,
        inconclusive_reason="Image severely underexposed and blurry",
        evidence_id=str(eid),
    )

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[cv_det],
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    assert result.status in (ReconciliationStatus.INCONCLUSIVE, ReconciliationStatus.PARTIAL_INCONCLUSIVE)
    assert len(result.findings) == 1
    assert result.findings[0].inspectionId == iid
    assert len(result.findings) == 1
    inconclusive_finding = result.findings[0]
    assert "Inconclusive Ollama/Qwen evaluation" in inconclusive_finding.knownLimitations[0]
    assert "underexposed" in inconclusive_finding.knownLimitations[0]


# =========================================================================
# 19. Invalid Provider Configuration Rejection (Fail-Closed)
# =========================================================================

def test_invalid_provider_configuration_raises_error():
    with pytest.raises(ValueError, match="Unsupported or invalid VLM provider"):
        get_vlm_provider(provider_name="invalid_hosted_provider")


# =========================================================================
# 20. Absence of Silent Hosted Gemini Fallback
# =========================================================================

def test_default_provider_is_ollama_not_gemini():
    prov = get_vlm_provider()
    assert prov.provider_name == "ollama"
    assert "gemini" not in prov.provider_name.lower()


# =========================================================================
# 21. Shared Schema Compatibility (gemini_schemas.py used by Qwen)
# =========================================================================

def test_shared_schema_compatibility_with_qwen():
    obs = GeminiObservation(
        observationId="qwen-obs-001",
        objectCategory="FIRE_EXTINGUISHER",
        hazardCategory="FIRE_EXTINGUISHER_MISSING",
        severity="CRITICAL",
        confidence=0.92,
        box_2d=[100, 200, 800, 900],
        description="Missing extinguisher bracket empty",
        evidenceId="ev-shared-schema-001",
    )
    result = GeminiAnalysisResult(
        evidenceId="ev-shared-schema-001",
        status=GeminiAnalysisStatus.SUCCESS,
        observations=[obs],
        totalObservations=1,
        inferenceTimeMs=420.0,
        provider="ollama",
        model="qwen2.5vl:3b",
        isSafeInference=False,
    )

    data = result.model_dump()
    assert data["provider"] == "ollama"
    assert data["model"] == "qwen2.5vl:3b"
    assert data["observations"][0]["hazardCategory"] == "FIRE_EXTINGUISHER_MISSING"


# =========================================================================
# 22. Bidirectional Compatibility Aliases
# =========================================================================

def test_bidirectional_finding_model_id_alias():
    eid = uuid.uuid4()
    f1 = Finding(
        findingId=uuid.uuid4(),
        evidenceId=eid,
        findingType=FindingType.FIRE_EXTINGUISHER_BLOCKED.value,
        severity=FindingSeverity.HIGH,
        explanation="Test finding 1",
        modelVersion="1.0",
        confidence=0.8,
        modelId="qwen2.5vl:3b",
    )
    assert f1.model == "qwen2.5vl:3b"
    assert f1.modelId == "qwen2.5vl:3b"

    f2 = Finding(
        findingId=uuid.uuid4(),
        evidenceId=eid,
        findingType=FindingType.FIRE_EXTINGUISHER_BLOCKED.value,
        severity=FindingSeverity.HIGH,
        explanation="Test finding 2",
        modelVersion="1.0",
        confidence=0.8,
        model="firesphere-cv-v0.8.0",
    )
    assert f2.model == "firesphere-cv-v0.8.0"
    assert f2.modelId == "firesphere-cv-v0.8.0"


def test_bidirectional_reconciliation_result_aliases():
    eid = uuid.uuid4()
    f = Finding(
        findingId=uuid.uuid4(),
        evidenceId=eid,
        findingType=FindingType.FIRE_EXTINGUISHER_BLOCKED.value,
        severity=FindingSeverity.HIGH,
        explanation="Test finding",
        modelVersion="1.0",
        confidence=0.8,
        model="firesphere-cv-v0.8.0",
    )

    res1 = ReconciliationResult(
        evidenceId=eid,
        status=ReconciliationStatus.SINGLE_PROVIDER_VLM,
        findings=[f],
        vlmFindings=[f],
    )
    assert len(res1.geminiFindings) == 1
    assert len(res1.vlmFindings) == 1

    obs = create_qwen_obs(evidence_id=str(eid))
    conflict = ConflictDetail(
        evidenceId=str(eid),
        conflictType=ConflictType.HAZARD_MISMATCH,
        cvFinding=f,
        vlmObservation=obs,
        explanation="Conflict detail test",
    )
    assert conflict.geminiObservation is not None
    assert conflict.vlmObservation is not None
    assert conflict.vlmObservation.observationId == obs.observationId


# =========================================================================
# 23. Officer Verification Boundary & Absence of Autonomous Decisions
# =========================================================================

def test_findings_are_strictly_advisory_no_autonomous_compliance(reconciler):
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    cv_det = MockCVDet(evidenceId=str(eid))
    obs = create_qwen_obs(evidence_id=str(eid))
    qwen_res = create_qwen_result(observations=[obs], evidence_id=str(eid))

    result = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[cv_det],
        vlm_result=qwen_res,
        vlm_provider_name="ollama",
        vlm_model_name="qwen2.5vl:3b",
    )

    for f in result.findings:
        assert f.inspectionId == iid
        assert f.isAdvisory is True
        assert getattr(f, "status", None) not in ("AI_APPROVED", "AI_REJECTED", "APPROVED", "REJECTED")


# =========================================================================
# 24. Inspection ID Contract Tests (Strict Validation, No Silent Fallback)
# =========================================================================

def test_reconciliation_rejects_none_inspection_id(reconciler):
    """Reconciliation strictly rejects None inspection_id without fabricating a replacement."""
    eid = uuid.uuid4()
    with pytest.raises(InvalidEvidenceException, match="Inspection identifier cannot be empty or null"):
        reconciler.reconcile(
            evidence_id=eid,
            inspection_id=None,
            cv_detections=[],
            vlm_result=None,
        )


def test_reconciliation_rejects_empty_string_inspection_id(reconciler):
    """Reconciliation strictly rejects empty or whitespace-only inspection_id strings."""
    eid = uuid.uuid4()
    with pytest.raises(InvalidEvidenceException, match="Inspection identifier cannot be empty or null"):
        reconciler.reconcile(
            evidence_id=eid,
            inspection_id="",
            cv_detections=[],
            vlm_result=None,
        )
    with pytest.raises(InvalidEvidenceException, match="Inspection identifier cannot be empty or null"):
        reconciler.reconcile(
            evidence_id=eid,
            inspection_id="   ",
            cv_detections=[],
            vlm_result=None,
        )


def test_reconciliation_rejects_invalid_uuid_inspection_id(reconciler):
    """Reconciliation strictly rejects malformed UUID strings for inspection_id."""
    eid = uuid.uuid4()
    with pytest.raises(InvalidEvidenceException, match="Invalid inspection UUID"):
        reconciler.reconcile(
            evidence_id=eid,
            inspection_id="not-a-valid-uuid-string",
            cv_detections=[],
            vlm_result=None,
        )


def test_reconciliation_accepts_valid_uuid_and_string_uuid(reconciler):
    """Reconciliation accepts valid UUID objects and string UUIDs, propagating them to findings."""
    eid = uuid.uuid4()
    iid = uuid.uuid4()
    cv_det = MockCVDet(evidenceId=str(eid))

    # 1. UUID object
    result1 = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=iid,
        cv_detections=[cv_det],
        vlm_result=None,
    )
    assert len(result1.findings) == 1
    assert result1.findings[0].inspectionId == iid
    assert isinstance(result1.findings[0].inspectionId, uuid.UUID)

    # 2. String UUID
    result2 = reconciler.reconcile(
        evidence_id=eid,
        inspection_id=str(iid),
        cv_detections=[cv_det],
        vlm_result=None,
    )
    assert len(result2.findings) == 1
    assert result2.findings[0].inspectionId == iid
    assert isinstance(result2.findings[0].inspectionId, uuid.UUID)
