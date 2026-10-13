"""
FireSphere.ai — Phase 8.3 Training Data Preparation & Readiness Gate Tests
Validates all requirements for Phase 8.3:
- Immutable canonical baseline
- Approved perception classes only
- Decoupled compound violation ground truth
- Full provenance for every sample
- Strict zero-leakage (exact duplicate, near duplicate, physical scene group)
- Phase 8.4 gate readiness
"""

import json
from pathlib import Path
import pytest
import yaml

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
AI_SERVICE_DIR = REPO_ROOT / "ai-service"
CANONICAL_DIR = REPO_ROOT / "datasets" / "fire_safety"
PREPARED_DIR = CANONICAL_DIR / "prepared" / "phase8_3"
MANIFESTS_DIR = PREPARED_DIR / "manifests"


@pytest.fixture(scope="module")
def capability_manifest():
    p = CANONICAL_DIR / "manifests" / "phase8_2_capability_manifest.json"
    assert p.exists(), f"Phase 8.2 manifest missing at {p}"
    return json.loads(p.read_text(encoding="utf-8"))


@pytest.fixture(scope="module")
def prepared_manifests():
    train_p = MANIFESTS_DIR / "phase8_3_training_manifest.json"
    val_p = MANIFESTS_DIR / "phase8_3_validation_manifest.json"
    test_p = MANIFESTS_DIR / "phase8_3_test_manifest.json"
    prov_p = MANIFESTS_DIR / "phase8_3_provenance.json"
    gate_p = MANIFESTS_DIR / "phase8_3_training_gate.json"
    stats_p = MANIFESTS_DIR / "phase8_3_class_statistics.json"

    assert train_p.exists()
    assert val_p.exists()
    assert test_p.exists()
    assert prov_p.exists()
    assert gate_p.exists()
    assert stats_p.exists()

    return {
        "train": json.loads(train_p.read_text(encoding="utf-8")),
        "val": json.loads(val_p.read_text(encoding="utf-8")),
        "test": json.loads(test_p.read_text(encoding="utf-8")),
        "provenance": json.loads(prov_p.read_text(encoding="utf-8")),
        "gate": json.loads(gate_p.read_text(encoding="utf-8")),
        "stats": json.loads(stats_p.read_text(encoding="utf-8")),
    }


def test_1_phase8_2_manifest_readable(capability_manifest):
    """Test 1: Phase 8.2 manifest is readable and valid."""
    assert capability_manifest["manifest_version"] == "1.0.0-phase8.2"
    assert "perception_classes" in capability_manifest
    assert "primary_mvp" in capability_manifest["perception_classes"]


def test_2_only_approved_training_sources_used(prepared_manifests):
    """Test 2: Only approved training sources from Phase 8.2 candidates are used."""
    candidates_path = CANONICAL_DIR / "manifests" / "phase8_2_training_candidates.json"
    candidates_data = json.loads(candidates_path.read_text(encoding="utf-8"))
    
    approved_dataset_ids = set()
    for cand in candidates_data["approved_training_candidates"]:
        for src in cand.get("approved_sources", []):
            approved_dataset_ids.add(src["dataset_id"].lower())

    for src in prepared_manifests["provenance"]["sources"]:
        ds_id = src["dataset"].lower()
        assert any(ds_id in approved or approved in ds_id for approved in approved_dataset_ids), (
            f"Unapproved dataset in provenance: {ds_id}"
        )


def test_3_frozen_class_vocabulary_respected():
    """Test 3: Frozen class vocabulary is respected in YOLO data.yaml."""
    yaml_path = PREPARED_DIR / "yolo" / "data.yaml"
    assert yaml_path.exists(), "YOLO data.yaml missing"
    with open(yaml_path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)

    assert data["nc"] == 4
    expected_names = {
        0: "FIRE_EXTINGUISHER",
        1: "EXIT_DOOR",
        2: "EXIT_SIGN",
        3: "OBSTACLE",
    }
    assert data["names"] == expected_names


def test_4_no_forbidden_violation_class_in_yolo_perception():
    """Test 4: No forbidden violation classes are added as direct YOLO perception classes."""
    yaml_path = PREPARED_DIR / "yolo" / "data.yaml"
    with open(yaml_path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)

    forbidden_classes = {
        "FIRE_EXTINGUISHER_BLOCKED",
        "FIRE_EXTINGUISHER_MISSING",
        "FIRE_EXTINGUISHER_EXPIRED",
        "EXIT_SIGNAGE_MISSING",
        "MANUAL_CALL_POINT_BLOCKED",
        "HOSE_REEL_OBSTRUCTED",
        "ELECTRICAL_PANEL_HAZARD",
        "COMBUSTIBLE_MATERIAL_HAZARD",
        "SPRINKLER_HEAD_DEFECT",
        "EMERGENCY_EXIT_OBSTRUCTED",
    }
    yolo_classes = set(data["names"].values())
    assert forbidden_classes.isdisjoint(yolo_classes), f"Forbidden class in YOLO perception: {forbidden_classes & yolo_classes}"


def test_5_every_image_has_valid_provenance(prepared_manifests):
    """Test 5: Every sample in train/val/test manifests contains complete provenance."""
    required_keys = {
        "sample_id",
        "image",
        "label",
        "source_dataset",
        "source_archive",
        "group_id",
        "split",
        "sha256",
        "license_status",
    }
    for split in ["train", "val", "test"]:
        samples = prepared_manifests[split]["samples"]
        assert len(samples) > 0
        for s in samples:
            assert required_keys.issubset(s.keys()), f"Missing provenance keys in {s.get('sample_id')}"
            assert s["split"] == split


def test_6_every_label_is_valid():
    """Test 6: Every label file in prepared YOLO dataset contains valid normalized coordinates."""
    yolo_labels_dir = PREPARED_DIR / "yolo" / "labels"
    assert yolo_labels_dir.exists()
    
    label_files = list(yolo_labels_dir.glob("*/*.txt"))
    assert len(label_files) > 0

    # Validate all label files
    for l_path in label_files:
        content = l_path.read_text(encoding="utf-8").strip()
        if not content:
            continue
        for line in content.splitlines():
            parts = line.strip().split()
            assert len(parts) == 5, f"Invalid YOLO format in {l_path}: {line}"
            cid, xc, yc, w, h = int(parts[0]), float(parts[1]), float(parts[2]), float(parts[3]), float(parts[4])
            assert cid in [0, 1, 2, 3], f"Invalid class ID {cid} in {l_path}"
            assert 0.0 <= xc <= 1.0, f"xc out of bounds in {l_path}"
            assert 0.0 <= yc <= 1.0, f"yc out of bounds in {l_path}"
            assert 0.0 < w <= 1.0, f"w out of bounds in {l_path}"
            assert 0.0 < h <= 1.0, f"h out of bounds in {l_path}"


def test_7_no_exact_duplicate_crosses_splits(prepared_manifests):
    """Test 7: No exact duplicate (SHA-256) crosses train/val/test splits."""
    train_shas = {s["sha256"] for s in prepared_manifests["train"]["samples"]}
    val_shas = {s["sha256"] for s in prepared_manifests["val"]["samples"]}
    test_shas = {s["sha256"] for s in prepared_manifests["test"]["samples"]}

    assert train_shas.isdisjoint(val_shas), "Exact SHA-256 duplicate collision between train and val"
    assert train_shas.isdisjoint(test_shas), "Exact SHA-256 duplicate collision between train and test"
    assert val_shas.isdisjoint(test_shas), "Exact SHA-256 duplicate collision between val and test"


def test_8_no_near_duplicate_crosses_splits():
    """Test 8: Near-duplicate audit verifies zero perceptual hash cross-split leakage."""
    dup_report_p = PREPARED_DIR / "duplicates" / "duplicate_report.json"
    assert dup_report_p.exists()
    dup_report = json.loads(dup_report_p.read_text(encoding="utf-8"))
    assert dup_report["exact_cross_split_duplicates"] == 0
    assert dup_report["near_cross_split_duplicates"] == 0
    assert dup_report["status"] == "PASS_ZERO_DUPLICATE_LEAKAGE"


def test_9_no_group_crosses_splits(prepared_manifests):
    """Test 9: Group-aware splitting prevents any physical scene group from crossing splits."""
    train_groups = {s["group_id"] for s in prepared_manifests["train"]["samples"]}
    val_groups = {s["group_id"] for s in prepared_manifests["val"]["samples"]}
    test_groups = {s["group_id"] for s in prepared_manifests["test"]["samples"]}

    assert train_groups.isdisjoint(val_groups), "Scene group collision between train and val"
    assert train_groups.isdisjoint(test_groups), "Scene group collision between train and test"
    assert val_groups.isdisjoint(test_groups), "Scene group collision between val and test"


def test_10_train_val_test_manifests_exist(prepared_manifests):
    """Test 10: All Phase 8.3 manifests exist and have positive counts."""
    assert len(prepared_manifests["train"]["samples"]) == 3377
    assert len(prepared_manifests["val"]["samples"]) == 988
    assert len(prepared_manifests["test"]["samples"]) == 522
    total = len(prepared_manifests["train"]["samples"]) + len(prepared_manifests["val"]["samples"]) + len(prepared_manifests["test"]["samples"])
    assert total == 4887


def test_11_data_yaml_contains_only_approved_classes():
    """Test 11: data.yaml strictly contains the 4 approved perception classes."""
    yaml_path = PREPARED_DIR / "yolo" / "data.yaml"
    with open(yaml_path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)
    assert set(data["names"].values()) == {"FIRE_EXTINGUISHER", "EXIT_DOOR", "EXIT_SIGN", "OBSTACLE"}


def test_12_excluded_datasets_are_not_included(prepared_manifests):
    """Test 12: Excluded datasets are completely absent from prepared data."""
    excluded_path = CANONICAL_DIR / "manifests" / "phase8_2_excluded_data.json"
    excluded_data = json.loads(excluded_path.read_text(encoding="utf-8"))
    
    # Excluded sources that are completely excluded
    fully_excluded = {
        item["source_id"].lower()
        for item in excluded_data["excluded_items"]
        if item.get("exclusion_scope") == "COMPLETE_SOURCE_EXCLUSION"
    }

    all_sources = {src["dataset"].lower() for src in prepared_manifests["provenance"]["sources"]}
    assert fully_excluded.isdisjoint(all_sources), f"Excluded datasets present: {fully_excluded & all_sources}"


def test_13_dataset7_not_included_in_training(prepared_manifests):
    """Test 13: Dataset 7 (Zenodo SHD-Bench) is not included in training."""
    for split in ["train", "val", "test"]:
        for s in prepared_manifests[split]["samples"]:
            src = s["source_dataset"].lower()
            assert "shd" not in src and "dataset7" not in src and "synthetic" not in src


def test_14_dataset8_not_treated_as_blocked_extinguisher_ground_truth(prepared_manifests):
    """Test 14: Dataset 8 (Zenodo segmentation) is not in primary MVP training data as blocked extinguisher."""
    for split in ["train", "val", "test"]:
        for s in prepared_manifests[split]["samples"]:
            src = s["source_dataset"].lower()
            assert "dataset8" not in src and "zenodo" not in src


def test_15_no_hardcoded_statutory_clearance_in_preparation():
    """Test 15: No hardcoded statutory clearance dimensions appear in prepared config."""
    yaml_path = PREPARED_DIR / "yolo" / "data.yaml"
    yaml_text = yaml_path.read_text(encoding="utf-8")
    assert "36in" not in yaml_text
    assert "36 inches" not in yaml_text
    assert "914mm" not in yaml_text


def test_16_canonical_dataset_counts_remain_unchanged():
    """Test 16: Canonical baseline dataset remains 100% immutable."""
    images = list((CANONICAL_DIR / "images").glob("*.jpg")) + list((CANONICAL_DIR / "images").glob("*.png"))
    annotations = list((CANONICAL_DIR / "annotations").glob("*.txt"))
    assert len(images) == 6471, f"Canonical image count changed: {len(images)}"
    assert len(annotations) == 6471, f"Canonical annotation count changed: {len(annotations)}"


def test_17_training_gate_reflects_actual_validation_status(prepared_manifests):
    """Test 17: Training gate JSON reflects actual readiness status and empirical limits."""
    gate = prepared_manifests["gate"]
    assert gate["status"] == "READY_FOR_PHASE_8_4"
    assert gate["gate_condition"] == "READY_WITH_DATA_LIMITATIONS"
    assert gate["class_readiness"]["FIRE_EXTINGUISHER"] == "READY_FOR_TRAINING"
    assert gate["class_readiness"]["EXIT_SIGN"] == "READY_FOR_TRAINING"
    assert gate["class_readiness"]["EXIT_DOOR"] == "DATA_LIMITED"
    assert gate["class_readiness"]["OBSTACLE"] == "DATA_LIMITED"
    assert gate["class_readiness"]["EMERGENCY_EXIT_OBSTRUCTED"] == "DATA_LIMITED"
    assert gate["canonical_integrity"] == "UNTOUCHED"
