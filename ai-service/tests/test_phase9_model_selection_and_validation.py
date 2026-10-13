"""FireSphere.ai — Phase 9 Model Selection, Calibration & Governance Tests.

Tests cover:
1. Formal Model Selection: Phase 8.4 recorded as Reference Baseline, production status NOT_APPROVED.
2. Calibration Status: Operational thresholding (tau=0.15) distinguished from probability calibration (NOT_CALIBRATED).
3. Per-Class Capability Gating: ADVISORY_ONLY for Extinguisher/Sign, UNSUPPORTED for Door/Obstacle.
4. AI Safety & Human-in-the-Loop Invariants: Prohibition of AI_APPROVED/AI_REJECTED, human verification requirement.
5. Integration Contract Readiness: Audit of Spring Boot, Postgres, Redis, MinIO compatibility.
6. Phase 9 Decision Gate: Retains baseline, closes production gate, prepares handoff for Phase 10.
7. Historical Checkpoint Immutability: Phase 8.4, 8.8, and 8.9 checkpoint hashes remain untouched.
8. Documentation Artifact Completeness: All 6 Phase 9 documents authored and mirrored.
"""

import hashlib
import json
from pathlib import Path
import pytest
from pydantic import ValidationError

from app.schemas.finding import ComplianceStatus, Finding, FindingSeverity
from app.evaluation.phase9_validation import Phase9ValidationManager

WORKSPACE_ROOT = Path(r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai")
AI_SERVICE_DIR = WORKSPACE_ROOT / "ai-service"
ARTIFACTS_DIR = AI_SERVICE_DIR / "artifacts" / "phase9"

BASELINE_CKPT = AI_SERVICE_DIR / "artifacts" / "phase8_4" / "checkpoints" / "best.pt"
PHASE8_8_CKPT = AI_SERVICE_DIR / "artifacts" / "phase8_8" / "checkpoints" / "best.pt"
PHASE8_9_CKPT = AI_SERVICE_DIR / "artifacts" / "phase8_9" / "checkpoints" / "best.pt"

EXPECTED_BASELINE_SHA256 = "5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666"
EXPECTED_PHASE8_8_SHA256 = "b12ffccf532d819a1cbdd058bb4673f7270ff71533d9862003249fc5360df7b1"
EXPECTED_PHASE8_9_SHA256 = "abb5bab4a4eb3de7d2275c5a946238804f3f453cc1419a3906b888fe52d88d36"


def compute_file_sha256(filepath: Path) -> str:
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


def test_phase9_model_selection_record():
    """Verify Phase 8.4 is recorded as reference baseline and production status is NOT_APPROVED."""
    selection_file = ARTIFACTS_DIR / "model_selection.json"
    assert selection_file.exists(), f"Missing {selection_file}"

    data = json.loads(selection_file.read_text(encoding="utf-8"))
    assert "firesphere-perception-baseline-v0.8.0" in data["authoritativeReferenceBaseline"]
    assert data["baselineCheckpointSha256"] == EXPECTED_BASELINE_SHA256
    assert "firesphere-perception-pretrained-v0.8.9" in data["candidateModelEvaluated"]
    assert data["candidateHistoricalClassification"] == "CANDIDATE_REGRESSED"
    assert data["selectionDecision"] == "REFERENCE_BASELINE_RETAINED"
    assert data["productionModelApproved"] is False
    assert data["productionModelLoadedToLivePipeline"] is False


def test_phase9_calibration_status_and_threshold_audit():
    """Verify operational thresholding is audited and calibration status is NOT_CALIBRATED."""
    cal_file = ARTIFACTS_DIR / "calibration_status.json"
    assert cal_file.exists(), f"Missing {cal_file}"

    data = json.loads(cal_file.read_text(encoding="utf-8"))
    assert data["operationalThreshold"] == 0.15
    assert data["thresholdSelectionSplit"] == "phase8_8_validation_split"
    assert data["externalTestTuningPerformed"] is False
    assert data["calibrationStatus"] == "NOT_CALIBRATED"
    assert "NOT calibrated true posterior" in data["disclaimer"]


def test_phase9_per_class_capability_matrix():
    """Verify capability gating across all 4 perception classes with explicit sample sizes."""
    cap_file = ARTIFACTS_DIR / "capability_matrix.json"
    assert cap_file.exists(), f"Missing {cap_file}"

    data = json.loads(cap_file.read_text(encoding="utf-8"))
    by_name = {c["className"]: c for c in data}

    # Class 0: FIRE_EXTINGUISHER
    ext = by_name["FIRE_EXTINGUISHER"]
    assert ext["operationalStatus"] == "ADVISORY_ONLY"
    assert ext["externalTestSupport"] == 452
    assert ext["testTruePositives"] == 143
    assert ext["isProductionApproved"] is False
    assert ext["isLegalViolationClassifier"] is False

    # Class 1: EXIT_DOOR
    door = by_name["EXIT_DOOR"]
    assert door["operationalStatus"] == "UNSUPPORTED"
    assert door["externalTestSupport"] == 6
    assert door["testTruePositives"] == 0
    assert door["isProductionApproved"] is False

    # Class 2: EXIT_SIGN
    sign = by_name["EXIT_SIGN"]
    assert sign["operationalStatus"] == "ADVISORY_ONLY"
    assert sign["externalTestSupport"] == 320
    assert sign["testTruePositives"] == 52
    assert sign["isProductionApproved"] is False
    assert sign["isLegalViolationClassifier"] is False

    # Class 3: OBSTACLE
    obst = by_name["OBSTACLE"]
    assert obst["operationalStatus"] == "UNSUPPORTED"
    assert obst["externalTestSupport"] == 18
    assert obst["testTruePositives"] == 0
    assert obst["isProductionApproved"] is False


def test_phase9_ai_safety_and_human_review_invariants():
    """Enforce that AI findings cannot autonomously approve or reject fire NOCs."""
    import uuid

    # 1. Default complianceStatus must be REQUIRES_OFFICER_VERIFICATION
    finding = Finding(
        evidenceId=uuid.uuid4(),
        findingType="FIRE_EXTINGUISHER_MISSING",
        confidence=0.85,
        severity=FindingSeverity.HIGH,
        explanation="Test finding",
        model="firesphere-perception-baseline-v0.8.0",
        modelVersion="0.8.0",
    )
    assert finding.complianceStatus == ComplianceStatus.REQUIRES_OFFICER_VERIFICATION

    # 2. Autonomous certification states are strictly rejected
    for forbidden in ["AI_APPROVED", "AI_REJECTED", "APPROVED", "REJECTED"]:
        with pytest.raises(ValidationError):
            Finding(
                evidenceId=uuid.uuid4(),
                findingType="FIRE_EXTINGUISHER_MISSING",
                confidence=0.85,
                severity=FindingSeverity.HIGH,
                explanation="Test finding",
                model="firesphere-perception-baseline-v0.8.0",
                modelVersion="0.8.0",
                complianceStatus=forbidden,
            )


def test_phase9_integration_readiness_audit():
    """Verify integration contracts are audited for Spring Boot, Postgres, Redis, MinIO."""
    integ_file = ARTIFACTS_DIR / "integration_readiness.json"
    assert integ_file.exists(), f"Missing {integ_file}"

    data = json.loads(integ_file.read_text(encoding="utf-8"))
    assert data["readinessStatus"] == "READY_FOR_PHASE_10_INTEGRATION"
    assert data["springBootCompatibility"]["status"] == "COMPATIBLE"
    assert data["persistencePostgresCompatibility"]["status"] == "COMPATIBLE"
    assert data["redisAsyncJobCompatibility"]["status"] == "COMPATIBLE"
    assert data["minioS3StorageCompatibility"]["status"] == "COMPATIBLE"
    assert data["humanReviewAndSafetyBoundary"]["status"] == "COMPATIBLE"


def test_phase9_decision_gate():
    """Verify final Phase 9 governance decision record."""
    dec_file = ARTIFACTS_DIR / "phase9_decision.json"
    assert dec_file.exists(), f"Missing {dec_file}"

    data = json.loads(dec_file.read_text(encoding="utf-8"))
    assert data["gateVerdict"] == "PHASE_9_COMPLETED_BASELINE_RETAINED_PRODUCTION_NOT_APPROVED"
    assert data["productionStatus"] == "NOT_APPROVED"
    assert data["calibrationStatus"] == "NOT_CALIBRATED"
    assert data["nextRoadmapMilestone"] == "Phase 10: Production AI Integration"
    assert "firesphere-perception-baseline-v0.8.0" in data["referenceBaselineModel"]


def test_historical_checkpoints_immutability():
    """Verify historical checkpoints remain strictly identical."""
    assert BASELINE_CKPT.exists()
    assert compute_file_sha256(BASELINE_CKPT) == EXPECTED_BASELINE_SHA256

    assert PHASE8_8_CKPT.exists()
    assert compute_file_sha256(PHASE8_8_CKPT) == EXPECTED_PHASE8_8_SHA256

    assert PHASE8_9_CKPT.exists()
    assert compute_file_sha256(PHASE8_9_CKPT) == EXPECTED_PHASE8_9_SHA256


def test_phase9_documentation_mirroring():
    """Verify all 6 Phase 9 documentation files exist in both ai-service/docs and docs."""
    required_docs = [
        "phase9_model_selection.md",
        "phase9_calibration_and_thresholds.md",
        "phase9_class_capability_matrix.md",
        "phase9_ai_safety_and_human_review.md",
        "phase9_integration_readiness.md",
        "phase9_decision.md",
    ]
    for doc_name in required_docs:
        ai_doc = AI_SERVICE_DIR / "docs" / doc_name
        root_doc = WORKSPACE_ROOT / "docs" / doc_name
        assert ai_doc.exists(), f"Missing ai-service doc: {ai_doc}"
        assert root_doc.exists(), f"Missing mirrored root doc: {root_doc}"
