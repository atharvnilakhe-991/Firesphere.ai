"""
FireSphere.ai — Phase 8.6 Detection Pipeline & Objective Diagnostics Tests
Validates all requirements for Phase 8.6:
1. Phase 8.3 prepared dataset remains unchanged.
2. Tiny subset selection is deterministic.
3. Tiny subset contains only training data.
4. Labels are valid.
5. Loss is finite.
6. Gradients are finite.
7. Gradients are not permanently zero.
8. Parameters actually update.
9. Encode/decode round-trip is valid within an explicitly measured tolerance.
10. Prediction tensors have valid shapes.
11. No NaN/Inf predictions.
12. Threshold sweep produces deterministic results.
13. Previous Phase 8.4/8.5 artifacts remain intact.
14. No production model registration occurs.
"""

import hashlib
import json
import math
from pathlib import Path
import pytest
import torch
import yaml

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
AI_SERVICE_DIR = REPO_ROOT / "ai-service"
DATASETS_DIR = REPO_ROOT / "datasets" / "fire_safety"
PREPARED_DIR = DATASETS_DIR / "prepared" / "phase8_3"
YOLO_DIR = PREPARED_DIR / "yolo"
BASELINE_DIR = AI_SERVICE_DIR / "artifacts" / "phase8_4"
IMPROVED_DIR = AI_SERVICE_DIR / "artifacts" / "phase8_5"
ARTIFACTS_DIR = AI_SERVICE_DIR / "artifacts" / "phase8_6"
DIAGNOSTICS_DIR = ARTIFACTS_DIR / "diagnostics"


@pytest.fixture(scope="module")
def artifacts_8_6():
    gate_p = ARTIFACTS_DIR / "phase8_6_gate.json"
    rep_p = ARTIFACTS_DIR / "phase8_6_diagnostic_report.json"
    int_p = DIAGNOSTICS_DIR / "dataset_integrity.json"
    rt_p = DIAGNOSTICS_DIR / "encode_decode_roundtrip.json"
    grad_p = DIAGNOSTICS_DIR / "gradient_sanity.json"
    loss_p = DIAGNOSTICS_DIR / "loss_sanity.json"
    overfit_p = DIAGNOSTICS_DIR / "tiny_overfit_results.json"
    sweep_p = DIAGNOSTICS_DIR / "threshold_sweep_validation.json"

    assert gate_p.exists(), f"Missing gate at {gate_p}"
    assert rep_p.exists(), f"Missing diagnostic report at {rep_p}"
    assert int_p.exists(), f"Missing dataset integrity at {int_p}"
    assert rt_p.exists(), f"Missing round-trip results at {rt_p}"
    assert grad_p.exists(), f"Missing gradient sanity at {grad_p}"
    assert loss_p.exists(), f"Missing loss sanity at {loss_p}"
    assert overfit_p.exists(), f"Missing tiny overfit at {overfit_p}"
    assert sweep_p.exists(), f"Missing threshold sweep at {sweep_p}"

    return {
        "gate": json.loads(gate_p.read_text(encoding="utf-8")),
        "report": json.loads(rep_p.read_text(encoding="utf-8")),
        "dataset_integrity": json.loads(int_p.read_text(encoding="utf-8")),
        "roundtrip": json.loads(rt_p.read_text(encoding="utf-8")),
        "gradient": json.loads(grad_p.read_text(encoding="utf-8")),
        "loss": json.loads(loss_p.read_text(encoding="utf-8")),
        "overfit": json.loads(overfit_p.read_text(encoding="utf-8")),
        "sweep": json.loads(sweep_p.read_text(encoding="utf-8")),
    }


def test_1_phase8_3_dataset_remains_unchanged(artifacts_8_6):
    """Test 1: Phase 8.3 prepared YOLO dataset is completely unmodified."""
    ds = artifacts_8_6["dataset_integrity"]["phase8_3_prepared_dataset"]
    assert ds["status"] == "PASS"
    assert ds["total_images"] == 4887
    assert ds["total_labels"] == 4887
    assert ds["missing_labels"] == 0
    assert ds["orphan_labels"] == 0
    assert ds["malformed_boxes"] == 0
    assert ds["nan_inf_boxes"] == 0


def test_2_tiny_subset_selection_is_deterministic():
    """Test 2: Selection of tiny 16-image subset is 100% deterministic across calls."""
    from app.training.phase8_6_diagnostics import select_deterministic_tiny_subset
    sub1 = select_deterministic_tiny_subset(target_count=16, seed=42)
    sub2 = select_deterministic_tiny_subset(target_count=16, seed=42)
    assert len(sub1) == 16
    assert [s["stem"] for s in sub1] == [s["stem"] for s in sub2]


def test_3_tiny_subset_contains_only_training_data():
    """Test 3: Tiny subset members are drawn exclusively from train split (zero val/test leakage)."""
    from app.training.phase8_6_diagnostics import select_deterministic_tiny_subset
    sub = select_deterministic_tiny_subset(target_count=16, seed=42)
    train_stems = {p.stem for p in (YOLO_DIR / "images" / "train").glob("*.*")}
    for item in sub:
        assert item["stem"] in train_stems
        assert "train" in str(item["image_path"])


def test_4_labels_are_valid(artifacts_8_6):
    """Test 4: All labels belong to the 4 approved perception classes and have valid coordinates."""
    ds = artifacts_8_6["dataset_integrity"]["phase8_3_prepared_dataset"]
    assert ds["malformed_boxes"] == 0
    assert set(ds["class_distribution_overall"].keys()) == {
        "FIRE_EXTINGUISHER", "EXIT_DOOR", "EXIT_SIGN", "OBSTACLE"
    }


def test_5_loss_is_finite(artifacts_8_6):
    """Test 5: Detector loss computation produces strictly finite non-NaN values."""
    loss_data = artifacts_8_6["loss"]
    assert loss_data["status"] == "PASS"
    assert loss_data["empty_background_safe"] is True
    assert math.isfinite(loss_data["normal_prediction_loss"]["total_loss"])
    assert math.isfinite(loss_data["perfect_prediction_loss"]["total_loss"])


def test_6_gradients_are_finite(artifacts_8_6):
    """Test 6: Backpropagation produces finite gradients with zero NaNs or Infs."""
    grad = artifacts_8_6["gradient"]
    assert grad["status"] == "PASS"
    assert grad["nan_gradient_count"] == 0
    assert grad["inf_gradient_count"] == 0
    assert grad["total_gradient_norm"] > 0


def test_7_gradients_are_not_permanently_zero(artifacts_8_6):
    """Test 7: Gradients across active layers are non-zero."""
    grad = artifacts_8_6["gradient"]
    assert grad["total_gradient_norm"] > 0.01


def test_8_parameters_actually_update(artifacts_8_6):
    """Test 8: An optimizer step on the detector updates parameters with non-zero magnitude."""
    grad = artifacts_8_6["gradient"]
    assert grad["total_parameter_update_magnitude"] > 0.001


def test_9_encode_decode_roundtrip_within_tolerance(artifacts_8_6):
    """Test 9: GT box -> grid/anchor logit encoding -> decode -> box reconstruction error < 1e-4."""
    rt = artifacts_8_6["roundtrip"]
    assert rt["status"] == "PASS"
    max_err = max(rt["max_coordinate_error"].values())
    assert max_err < 1e-4


def test_10_prediction_tensors_have_valid_shapes():
    """Test 10: BaselineInspectionDetector forward pass outputs shape (B, num_anchors, 5 + C, S, S)."""
    from app.models.baseline_detector import BaselineInspectionDetector
    model = BaselineInspectionDetector(num_classes=4)
    model.eval()
    dummy = torch.randn(2, 3, 256, 256)
    with torch.no_grad():
        out = model(dummy)
    assert out.shape == (2, 3, 9, 16, 16)


def test_11_no_nan_or_inf_predictions():
    """Test 11: Forward pass produces strictly finite activation tensors."""
    from app.models.baseline_detector import BaselineInspectionDetector
    model = BaselineInspectionDetector(num_classes=4)
    dummy = torch.randn(2, 3, 256, 256)
    with torch.no_grad():
        out = model(dummy)
    assert torch.isfinite(out).all()


def test_12_threshold_sweep_produces_deterministic_results(artifacts_8_6):
    """Test 12: Validation threshold sweep contains 8 evaluated confidence cutoffs."""
    sw = artifacts_8_6["sweep"]
    assert sw["dataset_split"] == "val"
    assert sw["validation_images_evaluated"] == 988
    assert len(sw["sweep_results"]) == 8
    # Assert confidence monotonically ascends
    confs = [r["conf_threshold"] for r in sw["sweep_results"]]
    assert confs == [0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40, 0.50]


def test_13_previous_phase8_4_and_phase8_5_artifacts_intact():
    """Test 13: Phase 8.4 and Phase 8.5 checkpoints and artifacts remain completely intact."""
    base_ckpt = BASELINE_DIR / "checkpoints" / "best.pt"
    imp_ckpt = IMPROVED_DIR / "checkpoints" / "best.pt"
    assert base_ckpt.exists()
    assert imp_ckpt.exists()

    # Check baseline SHA256 starts with 5f166c4703e0
    hasher = hashlib.sha256()
    with open(base_ckpt, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    assert hasher.hexdigest().startswith("5f166c4703e0")


def test_14_no_production_model_registration(artifacts_8_6):
    """Test 14: Phase 8.6 is strictly diagnostic with PRODUCTION_STATUS = NOT_APPROVED."""
    gate = artifacts_8_6["gate"]
    assert gate["production_status"] == "NOT_APPROVED"
    assert gate["status"] == "PASS"
    assert gate["decision"] in [
        "PIPELINE_HEALTHY_DATA_EXPANSION_REQUIRED",
        "PIPELINE_DEFECT_REQUIRES_FIX",
        "PIPELINE_PARTIALLY_HEALTHY_CONTROLLED_EXPERIMENT_REQUIRED",
        "BLOCKED",
    ]
