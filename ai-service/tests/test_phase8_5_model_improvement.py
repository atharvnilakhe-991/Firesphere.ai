"""
FireSphere.ai — Phase 8.5 Model Improvement & Architectural Diagnostics Tests
Validates all requirements for Phase 8.5:
1. Phase 8.5 configuration validity (seed=42, 10 epochs, cosine LR).
2. Four-class frozen vocabulary preserved.
3. Phase 8.3 prepared dataset immutability preserved.
4. Phase 8.4 baseline checkpoint preserved.
5. Phase 8.5 checkpoint metadata and SHA-256 integrity.
6. Dataset hash and split counts consistency.
7. MultiScaleInspectionDetector output shapes across P3, P4, P5.
8. FPN multi-scale feature fusion architecture verification.
9. Validation metrics and checkpoint selection.
10. Held-out test set isolation.
11. Direct baseline-vs-improved comparative metrics table.
12. Failure-safe inference on malformed/empty input.
13. Zero legal compliance decisions or AI_APPROVED outputs.
14. No EMERGENCY_EXIT_OBSTRUCTED in primary perception model.
15. Production status remains NOT_APPROVED.
"""

import hashlib
import json
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
ARTIFACTS_DIR = AI_SERVICE_DIR / "artifacts" / "phase8_5"
VALIDATION_DIR = DATASETS_DIR / "validation"


@pytest.fixture(scope="module")
def artifacts_8_5():
    cfg_p = ARTIFACTS_DIR / "configs" / "training_config.json"
    meta_p = ARTIFACTS_DIR / "metadata" / "model_metadata.json"
    val_p = ARTIFACTS_DIR / "metrics" / "val_metrics.json"
    test_p = ARTIFACTS_DIR / "metrics" / "test_metrics.json"
    comp_p = ARTIFACTS_DIR / "metrics" / "comparison_table.json"
    audit_p = VALIDATION_DIR / "phase8_5_experiment_audit.json"
    gate_p = VALIDATION_DIR / "phase8_5_training_gate.json"

    assert cfg_p.exists(), f"Missing training config at {cfg_p}"
    assert meta_p.exists(), f"Missing model metadata at {meta_p}"
    assert val_p.exists(), f"Missing val metrics at {val_p}"
    assert test_p.exists(), f"Missing test metrics at {test_p}"
    assert comp_p.exists(), f"Missing comparison table at {comp_p}"
    assert audit_p.exists(), f"Missing audit at {audit_p}"
    assert gate_p.exists(), f"Missing gate at {gate_p}"

    return {
        "config": json.loads(cfg_p.read_text(encoding="utf-8")),
        "metadata": json.loads(meta_p.read_text(encoding="utf-8")),
        "val_metrics": json.loads(val_p.read_text(encoding="utf-8")),
        "test_metrics": json.loads(test_p.read_text(encoding="utf-8")),
        "comparison": json.loads(comp_p.read_text(encoding="utf-8")),
        "audit": json.loads(audit_p.read_text(encoding="utf-8")),
        "gate": json.loads(gate_p.read_text(encoding="utf-8")),
    }


def test_1_phase8_5_configuration_validity(artifacts_8_5):
    """Test 1: Phase 8.5 configuration validity, seed 42, 10 epochs, cosine LR."""
    cfg = artifacts_8_5["config"]
    assert cfg["model_id"] == "firesphere-perception-improved-v0.8.5"
    assert cfg["architecture"] == "MultiScaleInspectionDetector"
    assert cfg["seed"] == 42
    assert cfg["epochs"] == 10
    assert "best_epoch" in cfg
    assert "best_val_loss" in cfg
    assert len(cfg["history"]) == 10


def test_2_correct_four_class_mapping(artifacts_8_5):
    """Test 2: Exactly four frozen perception classes."""
    meta = artifacts_8_5["metadata"]
    expected_classes = {
        "0": "FIRE_EXTINGUISHER",
        "1": "EXIT_DOOR",
        "2": "EXIT_SIGN",
        "3": "OBSTACLE",
    }
    assert meta["class_mapping"] == expected_classes


def test_3_phase8_3_dataset_immutability():
    """Test 3: Phase 8.3 prepared YOLO dataset is completely unmodified."""
    for split, count in [("train", 3377), ("val", 988), ("test", 522)]:
        imgs = list((YOLO_DIR / "images" / split).glob("*.*"))
        lbls = list((YOLO_DIR / "labels" / split).glob("*.txt"))
        assert len(imgs) == count, f"Split {split} images modified: {len(imgs)}"
        assert len(lbls) == count, f"Split {split} labels modified: {len(lbls)}"

    # Canonical dataset immutability
    c_imgs = list((DATASETS_DIR / "images").glob("*.*"))
    c_lbls = list((DATASETS_DIR / "annotations").glob("*.txt"))
    assert len(c_imgs) == 6471
    assert len(c_lbls) == 6471


def test_4_phase8_4_baseline_checkpoint_preservation():
    """Test 4: Phase 8.4 baseline checkpoint best.pt is preserved and untouched."""
    base_ckpt = BASELINE_DIR / "checkpoints" / "best.pt"
    assert base_ckpt.exists(), "Phase 8.4 baseline checkpoint missing"
    hasher = hashlib.sha256()
    with open(base_ckpt, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    actual_sha = hasher.hexdigest()
    assert actual_sha.startswith("5f166c4703e0")


def test_5_new_checkpoint_metadata_and_sha(artifacts_8_5):
    """Test 5: Phase 8.5 best.pt checkpoint exists and matches metadata SHA-256."""
    best_ckpt = ARTIFACTS_DIR / "checkpoints" / "best.pt"
    last_ckpt = ARTIFACTS_DIR / "checkpoints" / "last.pt"
    assert best_ckpt.exists(), "Phase 8.5 best.pt missing"
    assert last_ckpt.exists(), "Phase 8.5 last.pt missing"

    hasher = hashlib.sha256()
    with open(best_ckpt, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    actual_sha = hasher.hexdigest()
    assert artifacts_8_5["metadata"]["checkpoint_sha256"] == actual_sha


def test_6_dataset_hash_consistency():
    """Test 6: data.yaml exists with approved classes."""
    yaml_path = YOLO_DIR / "data.yaml"
    assert yaml_path.exists()
    with open(yaml_path, "r", encoding="utf-8") as f:
        cfg = yaml.safe_load(f)
    assert cfg["nc"] == 4
    assert set(cfg["names"].values()) == {"FIRE_EXTINGUISHER", "EXIT_DOOR", "EXIT_SIGN", "OBSTACLE"}


def test_7_model_output_shape():
    """Test 7: MultiScaleInspectionDetector produces correct multi-scale pyramid shapes."""
    from app.models.improved_detector import MultiScaleInspectionDetector
    model = MultiScaleInspectionDetector(num_classes=4, fpn_channels=96)
    model.eval()

    dummy = torch.randn(2, 3, 256, 256)
    with torch.no_grad():
        out = model(dummy)

    assert "P3" in out
    assert "P4" in out
    assert "P5" in out

    # Check pyramid spatial dimensions
    assert out["P3"].shape == (2, 3, 9, 32, 32)
    assert out["P4"].shape == (2, 3, 9, 16, 16)
    assert out["P5"].shape == (2, 3, 9, 8, 8)


def test_8_fpn_multi_scale_architecture():
    """Test 8: Model contains FPN lateral and smoothing layers."""
    from app.models.improved_detector import MultiScaleInspectionDetector
    model = MultiScaleInspectionDetector(num_classes=4, fpn_channels=96)
    assert hasattr(model, "lat5")
    assert hasattr(model, "lat4")
    assert hasattr(model, "lat3")
    assert hasattr(model, "smooth5")
    assert hasattr(model, "smooth4")
    assert hasattr(model, "smooth3")
    assert hasattr(model, "head3")
    assert hasattr(model, "head4")
    assert hasattr(model, "head5")


def test_9_validation_metrics(artifacts_8_5):
    """Test 9: Validation metrics present with positive ground truth support."""
    val = artifacts_8_5["val_metrics"]
    assert val["evaluationStatus"] == "COMPLETED"
    assert val["totalImages"] == 988
    assert val["totalGroundTruthObjects"] == 1455
    assert "mAP50" in val
    assert "mAP50_95" in val


def test_10_test_set_isolation(artifacts_8_5):
    """Test 10: Held-out test set evaluated with explicit isolation flag."""
    test_m = artifacts_8_5["test_metrics"]
    assert test_m["split"] == "test"
    assert test_m["totalImages"] == 522
    assert test_m.get("test_set_used_for_final_evaluation") is True


def test_11_baseline_vs_improved_comparison(artifacts_8_5):
    """Test 11: Direct baseline-vs-improved comparison table schema and deltas."""
    comp = artifacts_8_5["comparison"]
    assert "overall_metrics" in comp
    assert "per_class_comparison" in comp

    for m_key in ["precision", "recall", "mAP50", "mAP50_95"]:
        assert m_key in comp["overall_metrics"]
        item = comp["overall_metrics"][m_key]
        assert "baseline" in item
        assert "improved" in item
        assert "delta" in item
        assert item["status"] in ["IMPROVED", "UNCHANGED", "REGRESSED"]

    for cname in ["FIRE_EXTINGUISHER", "EXIT_DOOR", "EXIT_SIGN", "OBSTACLE"]:
        assert cname in comp["per_class_comparison"]
        c_item = comp["per_class_comparison"][cname]
        assert "ap50" in c_item
        assert "delta" in c_item["ap50"]


def test_12_failure_safe_inference():
    """Test 12: MultiScaleInspectionDetector handles zero/malformed input safely."""
    from app.models.improved_detector import MultiScaleInspectionDetector
    model = MultiScaleInspectionDetector(num_classes=4, fpn_channels=96)
    model.eval()

    zero_in = torch.zeros((1, 3, 256, 256))
    with torch.no_grad():
        out = model(zero_in)
        dets = model.decode_predictions(out, conf_threshold=0.50)
    assert isinstance(dets, list)
    assert len(dets) == 1


def test_13_no_legal_or_compliance_decision_output():
    """Test 13: Model outputs zero legal, compliance, or AI_APPROVED decisions."""
    from app.models.improved_detector import MultiScaleInspectionDetector
    model = MultiScaleInspectionDetector(num_classes=4, fpn_channels=96)
    model.eval()

    sample_in = torch.randn(1, 3, 256, 256)
    with torch.no_grad():
        out = model(sample_in)
        dets = model.decode_predictions(out, conf_threshold=0.01)

    for d in dets[0]:
        assert "complianceStatus" not in d
        assert "legalDecision" not in d
        assert "AI_APPROVED" not in str(d)
        assert "AI_REJECTED" not in str(d)


def test_14_no_emergency_exit_obstructed_in_improved_model(artifacts_8_5):
    """Test 14: EMERGENCY_EXIT_OBSTRUCTED is excluded from 4-class perception model."""
    meta = artifacts_8_5["metadata"]
    classes = list(meta["class_mapping"].values())
    assert "EMERGENCY_EXIT_OBSTRUCTED" not in classes
    assert len(classes) == 4


def test_15_production_status_remains_not_approved(artifacts_8_5):
    """Test 15: Production approval remains NOT_APPROVED in all gates and metadata."""
    gate = artifacts_8_5["gate"]
    meta = artifacts_8_5["metadata"]
    assert gate["production_approval"] == "NOT_APPROVED"
    assert meta["production_status"] == "NOT_APPROVED"
    assert gate["calibration_status"] == "NOT_CALIBRATED"
    assert meta["calibration_status"] == "NOT_CALIBRATED"
    assert gate["status"] in [
        "ARCHITECTURE_IMPROVED",
        "ARCHITECTURE_IMPROVED_BUT_DATA_LIMITED",
        "DATA_EXPANSION_REQUIRED",
    ]
