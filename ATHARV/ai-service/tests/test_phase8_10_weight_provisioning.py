"""FireSphere.ai — Phase 8.10 Pretrained Weight Provisioning, Provenance & Readiness Tests.

Tests cover:
1. Downloaded checkpoint existence and size verification.
2. Actual SHA-256 verification and honest prefix vs full-hash comparison.
3. Safe deserialization with weights_only=True and state dictionary integrity.
4. Numerical integrity scan (zero NaN, zero Inf).
5. Native loading into PretrainedInspectionDetector (312/312 backbone keys, 0 missing, 0 unexpected).
6. Statistical pretraining verification against fresh random initialization.
7. Architecture compatibility, parameter counts, tensor shapes, and anchor distribution.
8. Two-stage freeze plan verification and decoupled learning rate parameter groups.
9. Cryptographic immutability of historical checkpoints (Phase 8.4 baseline, Phase 8.5, Phase 8.8).
10. Complete dataset immutability and test set isolation (Phase 8.3 and 8.8).
11. Preprocessing manifest validation.
12. Strict governance enforcement (gate decision CHECKPOINT_INTEGRITY_FAILED, production status NOT_APPROVED).
13. Zero training verification (no epochs run, no trained weights emitted).
"""

import hashlib
import json
import os
from pathlib import Path
import pytest
import torch

from app.models.baseline_detector import CLASS_NAMES
from app.models.pretrained_detector import (
    BI_SCALE_ANCHORS,
    MobileNetV2Backbone,
    PretrainedInspectionDetector,
)

WORKSPACE_ROOT = Path(r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai")
AI_SERVICE_DIR = WORKSPACE_ROOT / "ai-service"
ARTIFACTS_DIR = AI_SERVICE_DIR / "artifacts"
PHASE8_10_DIR = ARTIFACTS_DIR / "phase8_10"
DOWNLOADED_CKPT = PHASE8_10_DIR / "pretrained" / "mobilenet_v2-b0353104.pth"

PREPARED_8_3 = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_3"
PREPARED_8_8 = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_8"

BASELINE_CKPT = ARTIFACTS_DIR / "phase8_4" / "checkpoints" / "best.pt"
PHASE8_5_CKPT = ARTIFACTS_DIR / "phase8_5" / "checkpoints" / "best.pt"
PHASE8_8_CKPT = ARTIFACTS_DIR / "phase8_8" / "checkpoints" / "best.pt"

EXPECTED_BASELINE_SHA256 = "5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666"
EXPECTED_PHASE8_5_SHA256 = "54ea8d2a2a124e38a5354913e0818514ea534d0d56ddb087c1e96cb03c20e89a"
EXPECTED_PHASE8_8_SHA256 = "b12ffccf532d819a1cbdd058bb4673f7270ff71533d9862003249fc5360df7b1"

OFFICIAL_PYTORCH_ACTUAL_SHA256 = "b03531047ffacf1e2488318dcd2aba1126cde36e3bfe1aa5cb07700aeeee9889"
PROMPT_AUTHORIZED_SHA256 = "b0353104d49a37c02b37ba4ef5dd3f3d1b827756f7b9492e73703eb402773cbb"


def compute_sha256(filepath: Path) -> str:
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


def test_downloaded_checkpoint_exists_and_size():
    """Verify that the authorized MobileNetV2 checkpoint exists and matches expected size."""
    assert DOWNLOADED_CKPT.exists(), f"Checkpoint missing at {DOWNLOADED_CKPT}"
    file_size = DOWNLOADED_CKPT.stat().st_size
    assert file_size == 14212972, f"Expected 14212972 bytes, got {file_size}"


def test_downloaded_checkpoint_sha256_analysis():
    """Verify actual SHA-256 against official prefix and prompt expected hash."""
    actual_sha = compute_sha256(DOWNLOADED_CKPT)
    assert actual_sha == OFFICIAL_PYTORCH_ACTUAL_SHA256

    # Verify official 8-character prefix match
    assert actual_sha.startswith("b0353104")
    assert "b0353104" in DOWNLOADED_CKPT.name

    # Honest verification: Full hash differs from prompt-authorized expected string
    assert actual_sha != PROMPT_AUTHORIZED_SHA256


def test_checkpoint_safe_load_and_state_dict_structure():
    """Verify safe deserialization with weights_only=True and state dict keys."""
    state_dict = torch.load(DOWNLOADED_CKPT, map_location="cpu", weights_only=True)
    assert isinstance(state_dict, dict)
    assert len(state_dict) == 314

    feature_keys = [k for k in state_dict.keys() if k.startswith("features.")]
    classifier_keys = [k for k in state_dict.keys() if k.startswith("classifier.")]

    assert len(feature_keys) == 312
    assert len(classifier_keys) == 2


def test_checkpoint_numerical_integrity_scan():
    """Verify zero NaN and zero Inf values in the downloaded checkpoint."""
    state_dict = torch.load(DOWNLOADED_CKPT, map_location="cpu", weights_only=True)
    for k, v in state_dict.items():
        if torch.is_tensor(v) and v.is_floating_point():
            assert not torch.isnan(v).any(), f"NaN detected in {k}"
            assert not torch.isinf(v).any(), f"Inf detected in {k}"


def test_checkpoint_loads_into_pretrained_detector():
    """Verify downloaded checkpoint loads cleanly into PretrainedInspectionDetector with 0 missing/unexpected."""
    state_dict = torch.load(DOWNLOADED_CKPT, map_location="cpu", weights_only=True)
    detector = PretrainedInspectionDetector()
    load_res = detector.load_pretrained_backbone(state_dict, strict=False)

    assert load_res["loaded_tensors"] == 312
    assert load_res["missing_keys"] == 0
    assert load_res["unexpected_keys"] == 0


def test_checkpoint_statistical_pretraining_divergence():
    """Verify downloaded weights diverge statistically from fresh random initialization."""
    state_dict = torch.load(DOWNLOADED_CKPT, map_location="cpu", weights_only=True)
    fresh_backbone = MobileNetV2Backbone()

    pt_w = state_dict["features.0.0.weight"]
    fresh_w = fresh_backbone.features[0][0].weight

    l2_dist = torch.norm(pt_w - fresh_w).item()
    assert l2_dist > 0.5, f"Weights too close to random init: {l2_dist}"
    assert l2_dist > 5.0  # Confirms genuine pretraining


def test_architecture_compatibility_and_parameters():
    """Verify PretrainedInspectionDetector structure, parameters, shapes, and vocabulary."""
    detector = PretrainedInspectionDetector(num_classes=4)

    assert detector.num_classes == 4
    assert CLASS_NAMES[0] == "FIRE_EXTINGUISHER"
    assert CLASS_NAMES[1] == "EXIT_DOOR"
    assert CLASS_NAMES[2] == "EXIT_SIGN"
    assert CLASS_NAMES[3] == "OBSTACLE"

    bb_params = sum(p.numel() for p in detector.backbone.parameters())
    neck_params = sum(p.numel() for p in detector.neck.parameters())
    head_p3_params = sum(p.numel() for p in detector.head_p3.parameters())
    head_p4_params = sum(p.numel() for p in detector.head_p4.parameters())
    total_params = sum(p.numel() for p in detector.parameters())

    assert bb_params == 2223872
    assert neck_params == 82432
    assert head_p3_params == 1170
    assert head_p4_params == 1170
    assert total_params == 2308644

    dummy_input = torch.zeros(1, 3, 256, 256)
    out = detector(dummy_input)

    assert out["P3"].shape == (1, 2, 9, 32, 32)
    assert out["P4"].shape == (1, 2, 9, 16, 16)
    total_anchors = (2 * 32 * 32) + (2 * 16 * 16)
    assert total_anchors == 2560


def test_freeze_plan_behavior_and_parameter_groups():
    """Verify Stage A and Stage B parameter freezing behavior and optimizer groups."""
    detector = PretrainedInspectionDetector(num_classes=4)

    res_a = detector.set_freeze_stage("stage_a")
    assert res_a["frozen"] == 2223872
    assert res_a["trainable"] == 84772
    assert all(not p.requires_grad for p in detector.backbone.parameters())
    assert all(p.requires_grad for p in detector.neck.parameters())

    res_b = detector.set_freeze_stage("stage_b")
    assert res_b["frozen"] == 55488
    assert res_b["trainable"] == 2253156
    for i in range(7):
        assert all(not p.requires_grad for p in detector.backbone.features[i].parameters())
    for i in range(7, len(detector.backbone.features)):
        assert all(p.requires_grad for p in detector.backbone.features[i].parameters())

    groups = detector.get_parameter_groups(lr_head=1e-3, lr_backbone=1e-4)
    assert len(groups) == 2
    assert groups[0]["name"] == "backbone"
    assert groups[0]["lr"] == 1e-4
    assert groups[1]["name"] == "heads_neck"
    assert groups[1]["lr"] == 1e-3


def test_historical_checkpoints_immutability():
    """Verify Phase 8.4, Phase 8.5, and Phase 8.8 checkpoints are 100% unchanged."""
    assert BASELINE_CKPT.exists()
    assert PHASE8_5_CKPT.exists()
    assert PHASE8_8_CKPT.exists()

    assert compute_sha256(BASELINE_CKPT) == EXPECTED_BASELINE_SHA256
    assert compute_sha256(PHASE8_5_CKPT) == EXPECTED_PHASE8_5_SHA256
    assert compute_sha256(PHASE8_8_CKPT) == EXPECTED_PHASE8_8_SHA256


def test_dataset_immutability_and_test_set_isolation():
    """Verify Phase 8.3 and Phase 8.8 image counts and complete isolation of 522-image test set."""
    p83_test_imgs = list((PREPARED_8_3 / "yolo" / "images" / "test").glob("*.jpg"))
    assert len(p83_test_imgs) == 522

    p83_train = len(list((PREPARED_8_3 / "yolo" / "images" / "train").glob("*.jpg")))
    p83_val = len(list((PREPARED_8_3 / "yolo" / "images" / "val").glob("*.jpg")))
    assert p83_train == 3377
    assert p83_val == 988
    assert p83_train + p83_val + len(p83_test_imgs) == 4887

    p88_train = len(list((PREPARED_8_8 / "yolo" / "images" / "train").glob("*.jpg")))
    p88_val = len(list((PREPARED_8_8 / "yolo" / "images" / "val").glob("*.jpg")))
    p88_test = len(list((PREPARED_8_8 / "yolo" / "images" / "test").glob("*.jpg")))
    assert p88_train == 3427
    assert p88_val == 928
    assert p88_test == 486
    assert p88_train + p88_val + p88_test == 4841


def test_preprocessing_configuration():
    """Verify preprocessing manifest specifications."""
    prep_file = PHASE8_10_DIR / "manifests" / "pretrained_preprocessing_manifest.json"
    assert prep_file.exists()

    data = json.loads(prep_file.read_text(encoding="utf-8"))
    assert data["preprocessing_pipeline"]["target_resolution"] == [256, 256]
    assert data["preprocessing_pipeline"]["channel_order"] == "RGB"
    assert "imagenet_pretrained_standard" in data["preprocessing_pipeline"]["normalization_options"]
    assert data["dataset_immutability_guarantee"]["source_dataset_overwriting"] == "STRICTLY_PROHIBITED"


def test_governance_and_gate_decisions():
    """Verify Phase 8.10 gate decision reflects CHECKPOINT_INTEGRITY_FAILED and NOT_APPROVED."""
    gate_file = PHASE8_10_DIR / "phase8_10_gate.json"
    assert gate_file.exists()

    data = json.loads(gate_file.read_text(encoding="utf-8"))
    assert data["phase"] == "8.10"
    assert data["gate_decision"] == "CHECKPOINT_INTEGRITY_FAILED"
    assert data["production_status"] == "NOT_APPROVED"
    assert data["gate_decision"] not in ["PRODUCTION_APPROVED", "MODEL_APPROVED", "DEPLOYMENT_READY"]
    assert data["functional_readiness"]["detector_backbone_compatibility"] == "PASSED (312/312 tensors loaded, 0 missing, 0 unexpected)"


def test_zero_training_executed():
    """Verify that no training was performed in Phase 8.10."""
    ckpts_dir = PHASE8_10_DIR / "checkpoints"
    if ckpts_dir.exists():
        pt_files = list(ckpts_dir.glob("*.pt"))
        assert len(pt_files) == 0, f"Found unexpected trained checkpoints: {pt_files}"
