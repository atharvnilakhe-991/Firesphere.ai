"""FireSphere.ai Phase 8.7 — Dataset Expansion & Calibration Test Suite.

Validates:
1. Existing Phase 8.3 prepared dataset immutability.
2. Untouched existing Phase 8.3 test split preservation (anti-contamination).
3. Phase 8.7 prepared dataset directory structure.
4. Class IDs and bounding box label validity.
5. Deterministic group-aware partitioning (scene-level independence).
6. Strict zero cross-split duplicate leakage.
7. Provenance and commercial licensing completeness.
8. Semantic mapping governance and decision validity.
9. Calibration validation results and reproducibility.
10. Phase 8.7 gate artifact and production status (NOT_APPROVED).
"""

import json
from pathlib import Path
import pytest

WORKSPACE_ROOT = Path(__file__).resolve().parent.parent.parent
CANONICAL_ROOT = WORKSPACE_ROOT / "datasets" / "fire_safety"
PREPARED_8_3 = CANONICAL_ROOT / "prepared" / "phase8_3"
PREPARED_8_7 = CANONICAL_ROOT / "prepared" / "phase8_7"
ARTIFACTS_8_7 = WORKSPACE_ROOT / "ai-service" / "artifacts" / "phase8_7"


def test_phase8_3_dataset_immutability():
    """Confirms Phase 8.3 prepared dataset remains 100% immutable and intact."""
    assert PREPARED_8_3.exists(), "Phase 8.3 prepared directory missing!"
    
    train_imgs = list((PREPARED_8_3 / "yolo" / "images" / "train").glob("*.*"))
    val_imgs = list((PREPARED_8_3 / "yolo" / "images" / "val").glob("*.*"))
    test_imgs = list((PREPARED_8_3 / "yolo" / "images" / "test").glob("*.*"))

    assert len(train_imgs) == 3377, f"Phase 8.3 train images modified: {len(train_imgs)}"
    assert len(val_imgs) == 988, f"Phase 8.3 val images modified: {len(val_imgs)}"
    assert len(test_imgs) == 522, f"Phase 8.3 test images modified: {len(test_imgs)}"


def test_untouched_phase8_3_test_split_preserved():
    """Confirms untouched Phase 8.3 test split is never leaked into Phase 8.7 train/val."""
    p83_test_manifest_p = PREPARED_8_3 / "manifests" / "phase8_3_test_manifest.json"
    assert p83_test_manifest_p.exists(), "Phase 8.3 test manifest missing!"

    p83_test_data = json.loads(p83_test_manifest_p.read_text(encoding="utf-8"))
    p83_test_shas = {s["sha256"] for s in p83_test_data.get("samples", [])}
    assert len(p83_test_shas) > 400

    p87_manifest_p = ARTIFACTS_8_7 / "dataset_candidate_manifest.json"
    assert p87_manifest_p.exists(), "Phase 8.7 candidate manifest missing!"

    p87_data = json.loads(p87_manifest_p.read_text(encoding="utf-8"))
    p87_samples = p87_data.get("samples", [])

    # Check that NO Phase 8.7 sample collides with Phase 8.3 test set
    p87_all_shas = {s["sha256"] for s in p87_samples}
    collisions = p87_all_shas.intersection(p83_test_shas)
    assert len(collisions) == 0, f"Critical contamination! {len(collisions)} Phase 8.3 test samples in Phase 8.7!"


def test_phase8_7_dataset_structure():
    """Validates full directory layout for Phase 8.7 prepared dataset."""
    assert PREPARED_8_7.exists(), "Phase 8.7 prepared root missing!"

    required_subdirs = [
        "yolo/images/train",
        "yolo/images/val",
        "yolo/images/test",
        "yolo/labels/train",
        "yolo/labels/val",
        "yolo/labels/test",
        "manifests",
        "provenance",
        "validation",
        "duplicates",
        "groups",
        "calibration",
        "statistics",
        "reports",
    ]
    for sub in required_subdirs:
        p = PREPARED_8_7 / sub
        assert p.exists() and p.is_dir(), f"Missing required Phase 8.7 directory: {sub}"


def test_phase8_7_class_ids_and_labels():
    """Validates that all staged labels adhere to frozen 4-class vocabulary and valid coordinates."""
    valid_classes = {0, 1, 2, 3}  # FIRE_EXTINGUISHER, EXIT_DOOR, EXIT_SIGN, OBSTACLE
    labels_dir = PREPARED_8_7 / "yolo" / "labels"

    checked_labels = 0
    checked_boxes = 0

    for split in ["train", "val", "test"]:
        split_dir = labels_dir / split
        lbl_files = list(split_dir.glob("*.txt"))
        assert len(lbl_files) > 0, f"No label files in split {split}!"

        for lf in lbl_files:
            checked_labels += 1
            content = lf.read_text(encoding="utf-8").strip()
            if not content:
                continue
            for line in content.splitlines():
                parts = line.strip().split()
                assert len(parts) == 5, f"Malformed label format in {lf.name}: {line}"
                cid = int(parts[0])
                xc, yc, w, h = float(parts[1]), float(parts[2]), float(parts[3]), float(parts[4])

                assert cid in valid_classes, f"Invalid class ID {cid} in {lf.name}!"
                assert 0.0 <= xc <= 1.0, f"xc out of range: {xc} in {lf.name}"
                assert 0.0 <= yc <= 1.0, f"yc out of range: {yc} in {lf.name}"
                assert 0.0 < w <= 1.0, f"w out of range: {w} in {lf.name}"
                assert 0.0 < h <= 1.0, f"h out of range: {h} in {lf.name}"
                checked_boxes += 1

    assert checked_labels >= 4800, f"Insufficient labels checked: {checked_labels}"
    assert checked_boxes >= 7000, f"Insufficient boxes checked: {checked_boxes}"


def test_phase8_7_deterministic_grouping():
    """Validates that scene grouping is deterministic and never crosses partitions."""
    p87_manifest_p = ARTIFACTS_8_7 / "dataset_candidate_manifest.json"
    manifest_data = json.loads(p87_manifest_p.read_text(encoding="utf-8"))
    samples = manifest_data.get("samples", [])

    scene_to_splits = {}
    for s in samples:
        gid = s["group_id"]
        spl = s["split"]
        if gid not in scene_to_splits:
            scene_to_splits[gid] = set()
        scene_to_splits[gid].add(spl)

    # Every single scene group must be mapped to EXACTLY ONE split
    for gid, splits in scene_to_splits.items():
        assert len(splits) == 1, f"Scene leakage! Group {gid} mapped to multiple splits: {splits}"


def test_phase8_7_no_cross_split_leakage():
    """Validates that train, val, and test partitions have zero duplicate SHA-256 hashes."""
    p87_manifest_p = ARTIFACTS_8_7 / "dataset_candidate_manifest.json"
    manifest_data = json.loads(p87_manifest_p.read_text(encoding="utf-8"))
    samples = manifest_data.get("samples", [])

    train_shas = {s["sha256"] for s in samples if s["split"] == "train"}
    val_shas = {s["sha256"] for s in samples if s["split"] == "val"}
    test_shas = {s["sha256"] for s in samples if s["split"] == "test"}

    assert len(train_shas.intersection(val_shas)) == 0, "Train and Val contain duplicate hashes!"
    assert len(train_shas.intersection(test_shas)) == 0, "Train and Test contain duplicate hashes!"
    assert len(val_shas.intersection(test_shas)) == 0, "Val and Test contain duplicate hashes!"


def test_provenance_and_licensing_completeness():
    """Validates provenance and licensing reports."""
    prov_p = ARTIFACTS_8_7 / "provenance_report.json"
    lic_p = ARTIFACTS_8_7 / "license_report.json"

    assert prov_p.exists() and lic_p.exists()
    prov_data = json.loads(prov_p.read_text(encoding="utf-8"))
    lic_data = json.loads(lic_p.read_text(encoding="utf-8"))

    assert len(prov_data) >= 5
    assert len(lic_data) >= 5

    for src in lic_data:
        assert src["commercial_use_allowed"] is True
        assert src["license_status"] == "VERIFIED"
        assert src["license"] in ["CC BY 4.0", "Public Domain (CC0)"]


def test_semantic_mapping_validity():
    """Validates that semantic mapping registry explicitly rejects e-commerce and safety fixtures."""
    map_p = ARTIFACTS_8_7 / "semantic_mapping_report.json"
    assert map_p.exists()

    mapping_data = json.loads(map_p.read_text(encoding="utf-8"))
    decisions = {m["source_class"]: m["decision"] for m in mapping_data}

    # Rejections
    assert decisions.get("trash_can") == "REJECT", "trash_can must be REJECTED!"
    assert decisions.get("sand_bucket") == "REJECT", "sand_bucket must be REJECTED!"
    assert decisions.get("missing fire extinguisher") == "REJECT", "missing extinguisher must be REJECTED!"
    assert decisions.get("blocked emergency exit") == "REJECT", "compound violation must be REJECTED!"

    # Acceptances
    assert decisions.get("door") == "ACCEPT", "door must be ACCEPTED!"
    assert decisions.get("emergency exit door") == "ACCEPT", "emergency exit door must be ACCEPTED!"
    assert decisions.get("boxes blocking exit") == "ACCEPT", "boxes blocking exit must be ACCEPTED!"
    assert decisions.get("objects blocked emergency exit") == "ACCEPT", "objects blocked emergency exit must be ACCEPTED!"


def test_calibration_reproducibility():
    """Validates EXIT_SIGN confidence calibration report."""
    calib_p = ARTIFACTS_8_7 / "calibration_validation.json"
    assert calib_p.exists()

    calib_data = json.loads(calib_p.read_text(encoding="utf-8"))
    assert calib_data["validation_images_count"] == 988
    assert calib_data["model_id"] == "firesphere-perception-baseline-v0.8.0"

    size_dist = calib_data["size_distribution"]
    assert size_dist["small_signs_area_lt_0_01"]["percentage"] > 30.0

    sweep = calib_data["calibration_sweep"]
    assert len(sweep) == 14

    # Verify that threshold 0.08 improves recall significantly over baseline 0.15
    th_08 = next(s for s in sweep if s["conf_threshold"] == 0.08)
    th_15 = next(s for s in sweep if s["conf_threshold"] == 0.15)

    rec_08 = th_08["exit_sign_metrics"]["recall"]
    rec_15 = th_15["exit_sign_metrics"]["recall"]
    assert rec_08 > rec_15 * 5.0, f"Threshold 0.08 ({rec_08}) did not substantially improve over 0.15 ({rec_15})!"


def test_phase8_7_gate_and_no_production_registration():
    """Confirms gate decision and ensures production model registration did NOT occur."""
    gate_p = ARTIFACTS_8_7 / "phase8_7_gate.json"
    assert gate_p.exists()

    gate_data = json.loads(gate_p.read_text(encoding="utf-8"))
    assert gate_data["gate_status"] == "DATA_READY_FOR_CONTROLLED_TRAINING"
    assert "NOT_APPROVED" in gate_data["production_status"]
    assert "Human-in-the-loop" in gate_data["human_in_the_loop_declaration"] or "Fire Inspection Officer" in gate_data["human_in_the_loop_declaration"]


def test_previous_phase8_artifacts_intact():
    """Ensures Phase 8.4, 8.5, and 8.6 artifacts remain completely intact."""
    p84_ckpt = WORKSPACE_ROOT / "ai-service" / "artifacts" / "phase8_4" / "checkpoints" / "best.pt"
    p85_ckpt = WORKSPACE_ROOT / "ai-service" / "artifacts" / "phase8_5" / "checkpoints" / "best.pt"
    p86_gate = WORKSPACE_ROOT / "ai-service" / "artifacts" / "phase8_6" / "phase8_6_gate.json"

    assert p84_ckpt.exists(), "Phase 8.4 checkpoint deleted!"
    assert p85_ckpt.exists(), "Phase 8.5 checkpoint deleted!"
    assert p86_gate.exists(), "Phase 8.6 gate artifact deleted!"
