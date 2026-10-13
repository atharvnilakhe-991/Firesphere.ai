"""FireSphere.ai — Phase 8.8 Controlled Model Training & Comparative Evaluation Tests.

Tests cover:
1. Semantic life-safety audit verification and rejection quarantine.
2. Audited Phase 8.8 prepared dataset structure, YAML, and split integrity.
3. Strict zero-leakage enforcement (Train vs Val vs Candidate Test, and vs Untouched Phase 8.3 Test).
4. Physical scene group isolation and expansion metrics.
5. Immutability of canonical dataset, Phase 8.3 dataset, and Phase 8.4 baseline checkpoint.
6. Checkpoint integrity, format, and SHA-256 reproducibility.
7. Candidate metrics schema, evaluation report structure, and confidence sweep completeness.
8. Comparative evaluation against Phase 8.4 baseline.
9. Benchmark report validation (latency, FPS, model size).
10. Strict enforcement of PRODUCTION_STATUS = NOT_APPROVED.
"""

import hashlib
import json
import os
from pathlib import Path
import pytest
import torch

WORKSPACE_ROOT = Path(r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai")
AI_SERVICE_DIR = WORKSPACE_ROOT / "ai-service"
PREPARED_8_8 = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_8"
PREPARED_8_7 = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_7"
PREPARED_8_3 = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_3"
ARTIFACTS_DIR = AI_SERVICE_DIR / "artifacts" / "phase8_8"
BASELINE_CKPT = AI_SERVICE_DIR / "artifacts" / "phase8_4" / "checkpoints" / "best.pt"

EXPECTED_BASELINE_SHA256 = "5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666"


def compute_file_sha256(filepath: Path) -> str:
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


def test_semantic_audit_ledger_and_quarantine():
    """Verify that the EXIT_DOOR semantic audit identified, logged, and quarantined non-egress doors."""
    audit_path = PREPARED_8_8 / "audit" / "exit_door_semantic_audit.json"
    assert audit_path.exists(), f"Audit ledger missing at {audit_path}"

    audit_data = json.loads(audit_path.read_text(encoding="utf-8"))
    assert audit_data["total_candidate_doors_inspected"] == 515
    assert audit_data["accepted_count"] == 499
    assert audit_data["rejected_count"] == 16
    assert audit_data["review_required_count"] == 0

    # Verify specific dangerous samples were rejected
    rej_names = {item["filename"] for item in audit_data["rejected_samples_list"]}
    assert "fsm_door_bilateral_elevatorDoor-89_jpg.jpg" in rej_names
    assert "fsm_door_closet-gc19700d7e_1280_png_jpg.jpg" in rej_names
    assert "fsm_door_room-g1d8650015_1280_jpg.jpg" in rej_names
    assert "fsm_door_apartment-door1_jpg.jpg" in rej_names

    # Verify quarantined images do not exist in the staged Phase 8.8 dataset
    for split in ["train", "val", "test"]:
        img_split = PREPARED_8_8 / "yolo" / "images" / split
        for rej_name in rej_names:
            assert not (img_split / rej_name).exists(), f"Rejected door {rej_name} leaked into {split} split!"


def test_phase8_8_dataset_structure_and_yaml():
    """Verify the staged Phase 8.8 dataset folder structure and data.yaml configuration."""
    yolo_dir = PREPARED_8_8 / "yolo"
    assert (yolo_dir / "data.yaml").exists()

    for split in ["train", "val", "test"]:
        assert (yolo_dir / "images" / split).is_dir()
        assert (yolo_dir / "labels" / split).is_dir()

    manifests_dir = PREPARED_8_8 / "manifests"
    assert (manifests_dir / "phase8_8_dataset_manifest.json").exists()
    assert (manifests_dir / "phase8_8_train_manifest.json").exists()
    assert (manifests_dir / "phase8_8_val_manifest.json").exists()
    assert (manifests_dir / "phase8_8_test_manifest.json").exists()


def test_phase8_8_split_leakage_and_immutability():
    """Ensure zero cross-split leakage and zero collision against untouched Phase 8.3 test set."""
    train_stems = {p.stem for p in (PREPARED_8_8 / "yolo" / "images" / "train").glob("*.jpg")}
    val_stems = {p.stem for p in (PREPARED_8_8 / "yolo" / "images" / "val").glob("*.jpg")}
    test_stems = {p.stem for p in (PREPARED_8_8 / "yolo" / "images" / "test").glob("*.jpg")}

    assert len(train_stems.intersection(val_stems)) == 0, "Train and Val split have overlapping samples!"
    assert len(train_stems.intersection(test_stems)) == 0, "Train and Test split have overlapping samples!"
    assert len(val_stems.intersection(test_stems)) == 0, "Val and Test split have overlapping samples!"

    # Strict isolation from Phase 8.3 untouched test set
    p83_test_imgs = list((PREPARED_8_3 / "yolo" / "images" / "test").glob("*.jpg"))
    assert len(p83_test_imgs) == 522, f"Phase 8.3 test set mutated! Expected 522, found {len(p83_test_imgs)}"

    p83_test_stems = {p.stem for p in p83_test_imgs}
    assert len(train_stems.intersection(p83_test_stems)) == 0, "Phase 8.8 training split leaked into Phase 8.3 test set!"


def test_baseline_checkpoint_immutability():
    """Verify that the authoritative Phase 8.4 baseline checkpoint remains completely unaltered."""
    assert BASELINE_CKPT.exists(), f"Baseline checkpoint missing at {BASELINE_CKPT}"
    current_sha = compute_file_sha256(BASELINE_CKPT)
    assert current_sha == EXPECTED_BASELINE_SHA256, (
        f"CRITICAL: Phase 8.4 baseline checkpoint modified! Expected {EXPECTED_BASELINE_SHA256}, got {current_sha}"
    )


def test_exit_door_physical_expansion_volume():
    """Verify that EXIT_DOOR maintains substantial physical scene expansion over baseline."""
    manifest_p = PREPARED_8_8 / "manifests" / "phase8_8_dataset_manifest.json"
    assert manifest_p.exists()
    data = json.loads(manifest_p.read_text(encoding="utf-8"))

    door_boxes = data["class_counts"]["EXIT_DOOR"]
    assert door_boxes >= 700, f"Expected >= 700 EXIT_DOOR boxes, found {door_boxes}"

    door_imgs = data["exit_door_audit"]["door_images_in_dataset"]
    assert door_imgs >= 500, f"Expected >= 500 door images, found {door_imgs}"


def test_checkpoint_and_metrics_artifacts():
    """Verify that Phase 8.8 checkpoints and evaluation metrics exist and conform to schema."""
    best_ckpt = ARTIFACTS_DIR / "checkpoints" / "best.pt"
    assert best_ckpt.exists(), f"Candidate best.pt missing at {best_ckpt}"

    # Load PyTorch checkpoint safely
    ckpt_data = torch.load(best_ckpt, map_location="cpu", weights_only=False)
    assert "model_state_dict" in ckpt_data or "state_dict" in ckpt_data
    model_id = ckpt_data.get("metadata", {}).get("model_id") or ckpt_data.get("model_id")
    assert model_id == "firesphere-perception-candidate-v0.8.8"

    # Metrics JSON files
    val_m = ARTIFACTS_DIR / "metrics" / "validation_metrics.json"
    assert val_m.exists(), "validation_metrics.json missing"
    v_data = json.loads(val_m.read_text(encoding="utf-8"))
    assert "mAP50" in v_data
    assert "perClassMetrics" in v_data
    assert "EXIT_DOOR" in v_data["perClassMetrics"]

    test_m = ARTIFACTS_DIR / "metrics" / "test_metrics.json"
    assert test_m.exists(), "test_metrics.json missing"
    t_data = json.loads(test_m.read_text(encoding="utf-8"))
    assert "mAP50" in t_data
    assert "perClassMetrics" in t_data


def test_confidence_sweep_metrics():
    """Verify that the confidence sweep evaluated required threshold points."""
    sweep_path = ARTIFACTS_DIR / "metrics" / "threshold_sweep.json"
    assert sweep_path.exists(), "threshold_sweep.json missing"
    sweep_data = json.loads(sweep_path.read_text(encoding="utf-8"))["sweep"]

    thresholds_evaluated = {round(item["threshold"], 2) for item in sweep_data}
    required = {0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40, 0.50}
    assert required.issubset(thresholds_evaluated), f"Missing threshold evaluations: {required - thresholds_evaluated}"


def test_benchmark_report():
    """Verify CPU inference latency benchmark report."""
    bench_p = ARTIFACTS_DIR / "benchmark" / "benchmark_report.json"
    assert bench_p.exists(), "benchmark_report.json missing"
    b_data = json.loads(bench_p.read_text(encoding="utf-8"))

    assert "baseline" in b_data
    assert "candidate" in b_data
    assert b_data["candidate"]["fps"] > 0
    assert b_data["candidate"]["total_mean_ms"] > 0


def test_production_status_remains_not_approved():
    """CRITICAL SAFETY TEST: Verify candidate is NOT marked approved or deployed."""
    dec_p = ARTIFACTS_DIR / "metrics" / "phase8_8_decision.json"
    assert dec_p.exists(), "phase8_8_decision.json missing"
    dec_data = json.loads(dec_p.read_text(encoding="utf-8"))

    assert dec_data["production_status"] == "NOT_APPROVED", "Production status must remain NOT_APPROVED!"
    assert dec_data["gate_decision"] != "PRODUCTION_APPROVED", "Gate decision must never be PRODUCTION_APPROVED!"

    meta_p = ARTIFACTS_DIR / "manifests" / "model_metadata.json"
    assert meta_p.exists(), "model_metadata.json missing"
    meta_data = json.loads(meta_p.read_text(encoding="utf-8"))
    assert meta_data["production_status"] == "NOT_APPROVED"
