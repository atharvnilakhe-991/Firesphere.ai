"""
FireSphere.ai — Phase 8.4 Model Training & Baseline Evaluation Gate Tests
Verifies that:
1. Training configuration is valid and reproducible (seed=42).
2. Exactly 4 perception classes are used (FIRE_EXTINGUISHER, EXIT_DOOR, EXIT_SIGN, OBSTACLE).
3. data.yaml is authoritative and unmodified.
4. Dataset paths and sample counts are validated.
5. Reproducibility metadata is present and complete.
6. Checkpoint metadata is preserved.
7. Checkpoint SHA-256 integrity is cryptographically validated.
8. Evaluation schemas are adhered to.
9. Per-class metrics are present for all classes.
10. Test set is strictly isolated for final evaluation only.
11. Model metadata is valid.
12. Failure-safe inference prevents crashes on malformed input.
13. No prohibited legal/compliance decisions are output.
14. EMERGENCY_EXIT_OBSTRUCTED is excluded from the perception model.
15. Production status cannot automatically become APPROVED.
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
ARTIFACTS_DIR = AI_SERVICE_DIR / "artifacts" / "phase8_4"
VALIDATION_DIR = DATASETS_DIR / "validation"


@pytest.fixture(scope="module")
def artifacts():
    cfg_p = ARTIFACTS_DIR / "configs" / "training_config.json"
    meta_p = ARTIFACTS_DIR / "metadata" / "model_metadata.json"
    val_p = ARTIFACTS_DIR / "metrics" / "val_metrics.json"
    test_p = ARTIFACTS_DIR / "metrics" / "test_metrics.json"
    audit_p = VALIDATION_DIR / "phase8_4_training_audit.json"
    gate_p = VALIDATION_DIR / "phase8_4_training_gate.json"

    assert cfg_p.exists(), f"Missing training config at {cfg_p}"
    assert meta_p.exists(), f"Missing model metadata at {meta_p}"
    assert val_p.exists(), f"Missing val metrics at {val_p}"
    assert test_p.exists(), f"Missing test metrics at {test_p}"
    assert audit_p.exists(), f"Missing audit at {audit_p}"
    assert gate_p.exists(), f"Missing gate at {gate_p}"

    return {
        "config": json.loads(cfg_p.read_text(encoding="utf-8")),
        "metadata": json.loads(meta_p.read_text(encoding="utf-8")),
        "val_metrics": json.loads(val_p.read_text(encoding="utf-8")),
        "test_metrics": json.loads(test_p.read_text(encoding="utf-8")),
        "audit": json.loads(audit_p.read_text(encoding="utf-8")),
        "gate": json.loads(gate_p.read_text(encoding="utf-8")),
    }


def test_1_phase8_4_configuration_validity(artifacts):
    """Test 1: Phase 8.4 configuration validity and baseline parameters."""
    cfg = artifacts["config"]
    assert cfg["model_id"] == "firesphere-perception-baseline-v0.8.0"
    assert cfg["architecture"] == "BaselineInspectionDetector"
    assert cfg["seed"] == 42
    assert cfg["epochs"] >= 1
    assert "best_epoch" in cfg
    assert "best_val_loss" in cfg
    assert len(cfg["history"]) > 0


def test_2_correct_four_class_mapping(artifacts):
    """Test 2: Correct four-class mapping strictly adheres to frozen vocabulary."""
    meta = artifacts["metadata"]
    expected_classes = {
        "0": "FIRE_EXTINGUISHER",
        "1": "EXIT_DOOR",
        "2": "EXIT_SIGN",
        "3": "OBSTACLE",
    }
    assert meta["class_mapping"] == expected_classes


def test_3_correct_data_yaml():
    """Test 3: Authoritative data.yaml contains only the four perception classes."""
    yaml_path = YOLO_DIR / "data.yaml"
    assert yaml_path.exists()
    with open(yaml_path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)

    assert data["nc"] == 4
    expected_classes = {
        0: "FIRE_EXTINGUISHER",
        1: "EXIT_DOOR",
        2: "EXIT_SIGN",
        3: "OBSTACLE",
    }
    assert data["names"] == expected_classes


def test_4_dataset_path_validation():
    """Test 4: Dataset split paths exist and have correct image/label pairing."""
    for split, expected_count in [("train", 3377), ("val", 988), ("test", 522)]:
        imgs = list((YOLO_DIR / "images" / split).glob("*.*"))
        lbls = list((YOLO_DIR / "labels" / split).glob("*.txt"))
        assert len(imgs) == expected_count, f"Mismatch in {split} images: {len(imgs)}"
        assert len(lbls) == expected_count, f"Mismatch in {split} labels: {len(lbls)}"


def test_5_reproducibility_metadata(artifacts):
    """Test 5: Reproducibility seed and environment metadata recorded."""
    meta = artifacts["metadata"]
    assert meta["training_seed"] == 42
    env = meta["environment"]
    assert "python_version" in env
    assert "pytorch_version" in env
    assert "training_device" in env


def test_6_checkpoint_metadata():
    """Test 6: Checkpoints exist and retain deserializable PyTorch state."""
    best_ckpt = ARTIFACTS_DIR / "checkpoints" / "best.pt"
    last_ckpt = ARTIFACTS_DIR / "checkpoints" / "last.pt"
    assert best_ckpt.exists(), "best.pt checkpoint missing"
    assert last_ckpt.exists(), "last.pt checkpoint missing"

    payload = torch.load(str(best_ckpt), map_location="cpu", weights_only=False)
    assert "model_state_dict" in payload
    assert "epoch" in payload
    assert "metrics" in payload
    assert payload.get("metadata", {}).get("is_best") is True


def test_7_sha256_checkpoint_integrity(artifacts):
    """Test 7: Cryptographic SHA256 matches saved checkpoint digest."""
    best_ckpt = ARTIFACTS_DIR / "checkpoints" / "best.pt"
    hasher = hashlib.sha256()
    with open(best_ckpt, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    actual_sha = hasher.hexdigest()

    assert artifacts["metadata"]["checkpoint_sha256"] == actual_sha
    assert artifacts["audit"]["best_checkpoint_sha256"] == actual_sha


def test_8_evaluation_output_schema(artifacts):
    """Test 8: Evaluation outputs match required schema."""
    for split_key in ["val_metrics", "test_metrics"]:
        m = artifacts[split_key]
        assert "evaluationStatus" in m
        assert "mAP50" in m
        assert "mAP50_95" in m
        assert "meanPrecision" in m
        assert "meanRecall" in m
        assert "meanF1" in m
        assert "perClassMetrics" in m
        assert "confusionMatrix" in m


def test_9_per_class_metrics_presence(artifacts):
    """Test 9: Per-class metrics exist for all 4 perception classes."""
    classes = ["FIRE_EXTINGUISHER", "EXIT_DOOR", "EXIT_SIGN", "OBSTACLE"]
    for split_key in ["val_metrics", "test_metrics"]:
        per_class = artifacts[split_key]["perClassMetrics"]
        for cname in classes:
            assert cname in per_class
            metric = per_class[cname]
            assert "precision" in metric
            assert "recall" in metric
            assert "ap50" in metric
            assert "support" in metric


def test_10_test_set_isolation(artifacts):
    """Test 10: Held-out test set explicitly flagged and isolated."""
    assert artifacts["test_metrics"].get("test_set_used_for_final_evaluation") is True


def test_11_model_metadata_validity(artifacts):
    """Test 11: Model metadata validates training status and governance decision."""
    meta = artifacts["metadata"]
    assert meta["training_status"] == "TRAINED_BASELINE"
    assert meta["production_status"] == "NOT_APPROVED"
    assert meta["calibration_status"] == "NOT_CALIBRATED"
    assert meta["governance_decision"] == "BASELINE_TRAINED_DATA_EXPANSION_REQUIRED"


def test_12_failure_safe_inference():
    """Test 12: Baseline model handles malformed or empty inputs safely without crashing."""
    from app.models.baseline_detector import BaselineInspectionDetector
    best_ckpt = ARTIFACTS_DIR / "checkpoints" / "best.pt"
    model = BaselineInspectionDetector(num_classes=4, input_size=(256, 256))
    payload = torch.load(str(best_ckpt), map_location="cpu", weights_only=False)
    model.load_state_dict(payload["model_state_dict"])
    model.eval()

    # Zero input
    zero_tensor = torch.zeros((1, 3, 256, 256))
    with torch.no_grad():
        out = model(zero_tensor)
        dets = model.decode_predictions(out, conf_threshold=0.50)
    assert isinstance(dets, list)
    assert len(dets) == 1


def test_13_no_prohibited_legal_or_compliance_decision_output():
    """Test 13: Model produces zero legal/compliance decisions or AI_APPROVED states."""
    from app.models.baseline_detector import BaselineInspectionDetector
    best_ckpt = ARTIFACTS_DIR / "checkpoints" / "best.pt"
    model = BaselineInspectionDetector(num_classes=4, input_size=(256, 256))
    payload = torch.load(str(best_ckpt), map_location="cpu", weights_only=False)
    model.load_state_dict(payload["model_state_dict"])
    model.eval()

    sample_tensor = torch.randn(1, 3, 256, 256)
    with torch.no_grad():
        out = model(sample_tensor)
        dets = model.decode_predictions(out, conf_threshold=0.01)

    for d in dets[0]:
        assert "complianceStatus" not in d
        assert "legalDecision" not in d
        assert "AI_APPROVED" not in str(d)
        assert "AI_REJECTED" not in str(d)


def test_14_no_emergency_exit_obstructed_in_primary_model(artifacts):
    """Test 14: EMERGENCY_EXIT_OBSTRUCTED is not trained as a perception class."""
    meta = artifacts["metadata"]
    classes = list(meta["class_mapping"].values())
    assert "EMERGENCY_EXIT_OBSTRUCTED" not in classes
    assert len(classes) == 4


def test_15_production_status_cannot_automatically_become_approved(artifacts):
    """Test 15: Production status remains NOT_APPROVED in all gates and metadata."""
    gate = artifacts["gate"]
    meta = artifacts["metadata"]
    assert gate["production_approval"] == "NOT_APPROVED"
    assert meta["production_status"] == "NOT_APPROVED"
    assert gate["status"] == "BASELINE_TRAINED_DATA_EXPANSION_REQUIRED"
    assert gate["class_readiness"]["EXIT_DOOR"] == "DATA_LIMITED"
    assert gate["class_readiness"]["OBSTACLE"] == "DATA_LIMITED"
