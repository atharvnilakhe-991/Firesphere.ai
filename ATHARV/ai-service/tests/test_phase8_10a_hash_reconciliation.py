"""FireSphere.ai — Phase 8.10A: Official MobileNetV2 Checkpoint Hash Reconciliation Tests.

Tests cover:
1. The local checkpoint exists at the designated path.
2. SHA-256 calculation is deterministic and matches recorded value.
3. File size is recorded correctly (14,212,972 bytes).
4. The checkpoint loads safely with weights_only=True.
5. Expected MobileNetV2 feature keys exist (312 backbone tensors).
6. No NaN values exist in floating point parameters.
7. No Inf values exist in floating point parameters.
8. Architecture compatibility with PretrainedInspectionDetector (0 missing, 0 unexpected keys).
9. The previous Phase 8.10 gate record remains unchanged (CHECKPOINT_INTEGRITY_FAILED preserved).
10. Historical checkpoint hashes remain unchanged (Phase 8.4 baseline, Phase 8.5, Phase 8.8).
11. Dataset integrity is preserved (Phase 8.3 untouched 522 test, Phase 8.8 486 test).
12. The final reconciliation gate follows documented decision rules (CHECKPOINT_HASH_REQUIRES_MANUAL_VERIFICATION).
13. An unverified official full hash cannot produce PRETRAINED_WEIGHT_READY.
14. Filename-prefix matching alone cannot produce PRETRAINED_WEIGHT_READY.
"""

import hashlib
import json
import os
from pathlib import Path
import pytest
import torch

from app.models.pretrained_detector import PretrainedInspectionDetector

WORKSPACE_ROOT = Path(r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai")
AI_SERVICE_DIR = WORKSPACE_ROOT / "ai-service"
ARTIFACTS_DIR = AI_SERVICE_DIR / "artifacts"
PHASE8_10_DIR = ARTIFACTS_DIR / "phase8_10"
RECONCILIATION_JSON = PHASE8_10_DIR / "phase8_10a_hash_reconciliation.json"
CHECKPOINT_PATH = PHASE8_10_DIR / "pretrained" / "mobilenet_v2-b0353104.pth"

PREPARED_8_3 = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_3"
PREPARED_8_8 = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_8"

BASELINE_CKPT = ARTIFACTS_DIR / "phase8_4" / "checkpoints" / "best.pt"
PHASE8_5_CKPT = ARTIFACTS_DIR / "phase8_5" / "checkpoints" / "best.pt"
PHASE8_8_CKPT = ARTIFACTS_DIR / "phase8_8" / "checkpoints" / "best.pt"

EXPECTED_LOCAL_SHA256 = "b03531047ffacf1e2488318dcd2aba1126cde36e3bfe1aa5cb07700aeeee9889"
EXPECTED_LOCAL_SIZE = 14212972
EXPECTED_BASELINE_SHA256 = "5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666"
EXPECTED_PHASE8_5_SHA256 = "54ea8d2a2a124e38a5354913e0818514ea534d0d56ddb087c1e96cb03c20e89a"
EXPECTED_PHASE8_8_SHA256 = "b12ffccf532d819a1cbdd058bb4673f7270ff71533d9862003249fc5360df7b1"


def compute_sha256(filepath: Path) -> str:
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


def test_1_local_checkpoint_exists():
    """Verify local checkpoint file exists at the expected path."""
    assert CHECKPOINT_PATH.exists(), f"Checkpoint missing at {CHECKPOINT_PATH}"
    assert CHECKPOINT_PATH.is_file()


def test_2_sha256_calculation_is_deterministic():
    """Verify SHA-256 calculation is deterministic across multiple evaluations."""
    h1 = compute_sha256(CHECKPOINT_PATH)
    h2 = compute_sha256(CHECKPOINT_PATH)
    assert h1 == h2
    assert h1 == EXPECTED_LOCAL_SHA256


def test_3_file_size_recorded_correctly():
    """Verify file size strictly matches recorded 14,212,972 bytes."""
    size = CHECKPOINT_PATH.stat().st_size
    assert size == EXPECTED_LOCAL_SIZE


def test_4_checkpoint_loads_safely():
    """Verify checkpoint loads cleanly using weights_only=True."""
    state_dict = torch.load(CHECKPOINT_PATH, map_location="cpu", weights_only=True)
    assert isinstance(state_dict, dict)
    assert len(state_dict) == 314


def test_5_expected_mobilenet_v2_keys_exist():
    """Verify all expected MobileNetV2 feature keys exist."""
    state_dict = torch.load(CHECKPOINT_PATH, map_location="cpu", weights_only=True)
    features = [k for k in state_dict.keys() if k.startswith("features.")]
    assert len(features) == 312
    assert "features.0.0.weight" in state_dict
    assert "features.18.1.num_batches_tracked" in state_dict


def test_6_no_nan_values_exist():
    """Verify floating point parameters contain zero NaN values."""
    state_dict = torch.load(CHECKPOINT_PATH, map_location="cpu", weights_only=True)
    nan_count = sum(
        torch.isnan(v).sum().item()
        for v in state_dict.values()
        if torch.is_tensor(v) and v.is_floating_point()
    )
    assert nan_count == 0


def test_7_no_inf_values_exist():
    """Verify floating point parameters contain zero Inf values."""
    state_dict = torch.load(CHECKPOINT_PATH, map_location="cpu", weights_only=True)
    inf_count = sum(
        torch.isinf(v).sum().item()
        for v in state_dict.values()
        if torch.is_tensor(v) and v.is_floating_point()
    )
    assert inf_count == 0


def test_8_architecture_compatibility_verified():
    """Verify checkpoint loads into PretrainedInspectionDetector with 0 missing and 0 unexpected keys."""
    state_dict = torch.load(CHECKPOINT_PATH, map_location="cpu", weights_only=True)
    detector = PretrainedInspectionDetector(num_classes=4)
    res = detector.load_pretrained_backbone(state_dict, strict=False)
    assert res["loaded_tensors"] == 312
    assert res["missing_keys"] == 0
    assert res["unexpected_keys"] == 0


def test_9_previous_phase8_10_gate_remains_unchanged():
    """Verify that historical Phase 8.10 records preserve CHECKPOINT_INTEGRITY_FAILED."""
    p810_gate_file = PHASE8_10_DIR / "phase8_10_gate.json"
    assert p810_gate_file.exists()
    data = json.loads(p810_gate_file.read_text(encoding="utf-8"))
    assert data["phase"] == "8.10"
    assert data["gate_decision"] == "CHECKPOINT_INTEGRITY_FAILED"
    assert data["production_status"] == "NOT_APPROVED"


def test_10_historical_checkpoint_hashes_unchanged():
    """Verify Phase 8.4, 8.5, and 8.8 checkpoint hashes remain unchanged."""
    assert compute_sha256(BASELINE_CKPT) == EXPECTED_BASELINE_SHA256
    assert compute_sha256(PHASE8_5_CKPT) == EXPECTED_PHASE8_5_SHA256
    assert compute_sha256(PHASE8_8_CKPT) == EXPECTED_PHASE8_8_SHA256


def test_11_dataset_integrity_manifests_unchanged():
    """Verify Phase 8.3 and 8.8 dataset counts and test set isolation."""
    p83_test_imgs = list((PREPARED_8_3 / "yolo" / "images" / "test").glob("*.jpg"))
    assert len(p83_test_imgs) == 522

    p83_train = len(list((PREPARED_8_3 / "yolo" / "images" / "train").glob("*.jpg")))
    p83_val = len(list((PREPARED_8_3 / "yolo" / "images" / "val").glob("*.jpg")))
    assert p83_train + p83_val + len(p83_test_imgs) == 4887

    p88_train = len(list((PREPARED_8_8 / "yolo" / "images" / "train").glob("*.jpg")))
    p88_val = len(list((PREPARED_8_8 / "yolo" / "images" / "val").glob("*.jpg")))
    p88_test = len(list((PREPARED_8_8 / "yolo" / "images" / "test").glob("*.jpg")))
    assert p88_train + p88_val + p88_test == 4841


def test_12_reconciliation_gate_decision_rule():
    """Verify Phase 8.10A decision reflects CHECKPOINT_HASH_REQUIRES_MANUAL_VERIFICATION."""
    assert RECONCILIATION_JSON.exists()
    data = json.loads(RECONCILIATION_JSON.read_text(encoding="utf-8"))
    assert data["phase"] == "8.10A"
    assert data["governance_and_controls"]["final_gate"] == "CHECKPOINT_HASH_REQUIRES_MANUAL_VERIFICATION"
    assert data["governance_and_controls"]["production_status"] == "NOT_APPROVED"
    assert data["governance_and_controls"]["training_performed"] is False


def test_13_unverified_official_hash_cannot_produce_ready_gate():
    """Safety test: An unverified or unpublished official hash must never yield PRETRAINED_WEIGHT_READY."""
    data = json.loads(RECONCILIATION_JSON.read_text(encoding="utf-8"))
    official_hash = data["official_metadata_audit"]["official_full_sha256_published_by_pytorch"]
    gate = data["governance_and_controls"]["final_gate"]
    if official_hash is None or official_hash == "NOT_OFFICIALLY_PUBLISHED_BY_PYTORCH":
        assert gate != "PRETRAINED_WEIGHT_READY"


def test_14_filename_prefix_matching_alone_cannot_produce_ready_gate():
    """Safety test: Filename prefix matching alone must NOT trigger PRETRAINED_WEIGHT_READY."""
    data = json.loads(RECONCILIATION_JSON.read_text(encoding="utf-8"))
    prefix_match = data["official_metadata_audit"]["official_published_hash_prefix"] == "b0353104"
    matches_val = data["hash_reconciliation_table"]["D_officially_published_sha256"]["matches_current_local"]
    full_match = matches_val is True
    gate = data["governance_and_controls"]["final_gate"]
    if prefix_match and not full_match:
        assert gate != "PRETRAINED_WEIGHT_READY"


