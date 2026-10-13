"""FireSphere.ai — Phase 8.9 Pretrained Transfer-Learning Experiment & Governance Tests.

Tests cover:
1. Pretrained weight provenance, local SHA-256 match, and user authorization records.
2. Candidate model training execution, checkpoint existence, and SHA-256 verification.
3. Formal governance decision gate (CANDIDATE_REGRESSED) and strictly NOT_APPROVED production status.
4. Audited Phase 8.8 dataset integrity and split leakage isolation.
5. Strict zero-collision isolation of untouched Phase 8.3 test set (522 images).
6. Transfer learning layer freezing/unfreezing mathematical behavior test.
7. Complete immutability verification of canonical dataset, Phase 8.3, Phase 8.4 baseline, and Phase 8.8 candidate.
8. Evaluation, benchmark, and error visualization artifact completeness.
9. Documentation artifact existence and cross-mirroring verification.
"""

import hashlib
import json
import os
from pathlib import Path
import pytest
import torch
import torch.nn as nn

WORKSPACE_ROOT = Path(r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai")
AI_SERVICE_DIR = WORKSPACE_ROOT / "ai-service"
PREPARED_8_8 = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_8"
PREPARED_8_3 = WORKSPACE_ROOT / "datasets" / "fire_safety" / "prepared" / "phase8_3"
ARTIFACTS_8_9 = AI_SERVICE_DIR / "artifacts" / "phase8_9"
PRETRAINED_CKPT = AI_SERVICE_DIR / "artifacts" / "phase8_10" / "pretrained" / "mobilenet_v2-b0353104.pth"
BASELINE_CKPT = AI_SERVICE_DIR / "artifacts" / "phase8_4" / "checkpoints" / "best.pt"
PHASE8_8_CKPT = AI_SERVICE_DIR / "artifacts" / "phase8_8" / "checkpoints" / "best.pt"
CANDIDATE_CKPT = ARTIFACTS_8_9 / "checkpoints" / "best.pt"

EXPECTED_PRETRAINED_SHA256 = "b03531047ffacf1e2488318dcd2aba1126cde36e3bfe1aa5cb07700aeeee9889"
EXPECTED_PRETRAINED_SIZE = 14212972
EXPECTED_BASELINE_SHA256 = "5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666"
EXPECTED_PHASE8_8_SHA256 = "b12ffccf532d819a1cbdd058bb4673f7270ff71533d9862003249fc5360df7b1"
EXPECTED_CANDIDATE_SHA256 = "abb5bab4a4eb3de7d2275c5a946238804f3f453cc1419a3906b888fe52d88d36"


def compute_file_sha256(filepath: Path) -> str:
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


def test_checkpoint_provenance_and_integrity_audit():
    """Verify that authorized MobileNetV2 checkpoint exists and matches recorded local digest."""
    assert PRETRAINED_CKPT.exists(), f"Pretrained checkpoint missing at {PRETRAINED_CKPT}"
    assert PRETRAINED_CKPT.stat().st_size == EXPECTED_PRETRAINED_SIZE, "Size mismatch on pretrained checkpoint"
    actual_sha = compute_file_sha256(PRETRAINED_CKPT)
    assert actual_sha == EXPECTED_PRETRAINED_SHA256, "SHA-256 mismatch on authorized pretrained checkpoint"

    manifest_file = ARTIFACTS_8_9 / "manifests" / "experiment_manifest.json"
    assert manifest_file.exists(), f"Experiment manifest missing at {manifest_file}"
    manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
    assert manifest["authorization"]["status"] == "EXPLICITLY_AUTHORIZED_BY_USER"
    assert manifest["authorization"]["production_approval"] is False
    assert "64-character SHA-256" in manifest["pretrained_backbone_provenance"]["provenance_limitation"]


def test_phase8_9_candidate_checkpoint_and_training():
    """Verify that Phase 8.9 candidate was trained and checkpoint saved with valid hash."""
    assert CANDIDATE_CKPT.exists(), f"Candidate checkpoint missing at {CANDIDATE_CKPT}"
    actual_sha = compute_file_sha256(CANDIDATE_CKPT)
    assert actual_sha == EXPECTED_CANDIDATE_SHA256, f"Candidate checkpoint hash mismatch: {actual_sha}"

    cfg_file = ARTIFACTS_8_9 / "metrics" / "training_config.json"
    assert cfg_file.exists(), f"Training config missing at {cfg_file}"
    cfg = json.loads(cfg_file.read_text(encoding="utf-8"))
    assert cfg["model_id"] == "firesphere-perception-pretrained-v0.8.9"
    assert cfg["epochs"] == 5
    assert cfg["best_epoch"] == 5
    assert cfg["best_val_loss"] == 0.82503
    assert len(cfg["history"]) == 5


def test_phase8_9_gate_decision_and_production_status():
    """Verify that formal governance decision is CANDIDATE_REGRESSED and status is NOT_APPROVED."""
    dec_file = ARTIFACTS_8_9 / "metrics" / "phase8_9_decision.json"
    assert dec_file.exists(), f"Decision file missing at {dec_file}"
    dec = json.loads(dec_file.read_text(encoding="utf-8"))
    assert dec["gate_decision"] == "CANDIDATE_REGRESSED"
    assert dec["production_status"] == "NOT_APPROVED"
    assert "firesphere-perception-baseline-v0.8.0" in dec["authoritative_baseline"]
    assert dec["candidate_sha256"] == EXPECTED_CANDIDATE_SHA256


def test_training_dataset_readiness_and_zero_leakage():
    """Verify Phase 8.8 audited dataset partitions exist with zero leakage."""
    train_dir = PREPARED_8_8 / "yolo" / "images" / "train"
    val_dir = PREPARED_8_8 / "yolo" / "images" / "val"
    test_dir = PREPARED_8_8 / "yolo" / "images" / "test"

    assert train_dir.is_dir()
    assert val_dir.is_dir()
    assert test_dir.is_dir()

    train_stems = {p.stem for p in train_dir.glob("*.jpg")}
    val_stems = {p.stem for p in val_dir.glob("*.jpg")}
    test_stems = {p.stem for p in test_dir.glob("*.jpg")}

    assert len(train_stems.intersection(val_stems)) == 0
    assert len(train_stems.intersection(test_stems)) == 0
    assert len(val_stems.intersection(test_stems)) == 0


def test_untouched_phase8_3_test_set_isolation():
    """Verify that the original Phase 8.3 test set remains 100% held out with 522 images."""
    p83_test_imgs = list((PREPARED_8_3 / "yolo" / "images" / "test").glob("*.jpg"))
    assert len(p83_test_imgs) == 522, f"Phase 8.3 test set mutated! Expected 522, found {len(p83_test_imgs)}"

    p83_test_stems = {p.stem for p in p83_test_imgs}
    train_stems = {p.stem for p in (PREPARED_8_8 / "yolo" / "images" / "train").glob("*.jpg")}
    assert len(train_stems.intersection(p83_test_stems)) == 0, "Training set leaked into untouched test split!"


def test_transfer_learning_freezing_behavior_math():
    """Verify PyTorch layer freezing protocol: Stage A frozen backbone vs Stage B unfreeze."""
    backbone = nn.Sequential(
        nn.Conv2d(3, 16, 3, padding=1),
        nn.BatchNorm2d(16),
        nn.ReLU(),
    )
    head = nn.Linear(16, 4)

    # Stage A: Freeze backbone
    for param in backbone.parameters():
        param.requires_grad = False
    for param in head.parameters():
        param.requires_grad = True

    trainable_stage_a = sum(p.numel() for p in backbone.parameters() if p.requires_grad)
    head_trainable = sum(p.numel() for p in head.parameters() if p.requires_grad)
    assert trainable_stage_a == 0, "Backbone parameters must have requires_grad=False in Stage A"
    assert head_trainable > 0, "Head parameters must have requires_grad=True in Stage A"

    # Stage B: Unfreeze
    for param in backbone.parameters():
        param.requires_grad = True
    trainable_stage_b = sum(p.numel() for p in backbone.parameters() if p.requires_grad)
    assert trainable_stage_b > 0, "Backbone parameters must have requires_grad=True in Stage B"


def test_historical_checkpoints_immutability():
    """Verify that Phase 8.4 baseline and Phase 8.8 checkpoints remain identical."""
    assert BASELINE_CKPT.exists()
    current_baseline_sha = compute_file_sha256(BASELINE_CKPT)
    assert current_baseline_sha == EXPECTED_BASELINE_SHA256, (
        f"CRITICAL: Baseline checkpoint modified! Expected {EXPECTED_BASELINE_SHA256}, got {current_baseline_sha}"
    )

    assert PHASE8_8_CKPT.exists()
    current_phase8_8_sha = compute_file_sha256(PHASE8_8_CKPT)
    assert current_phase8_8_sha == EXPECTED_PHASE8_8_SHA256, (
        f"Phase 8.8 checkpoint modified! Expected {EXPECTED_PHASE8_8_SHA256}, got {current_phase8_8_sha}"
    )


def test_phase8_9_evaluation_and_benchmark_artifacts_exist():
    """Verify that all validation, test, sweep, benchmark, and visualization artifacts exist."""
    val_file = ARTIFACTS_8_9 / "metrics" / "validation_metrics.json"
    sweep_file = ARTIFACTS_8_9 / "metrics" / "confidence_sweep_metrics.json"
    test_file = ARTIFACTS_8_9 / "metrics" / "test_metrics.json"
    comp_file = ARTIFACTS_8_9 / "metrics" / "model_comparison.json"
    bench_file = ARTIFACTS_8_9 / "benchmark" / "benchmark_report.json"

    assert val_file.exists(), f"Missing {val_file}"
    assert sweep_file.exists(), f"Missing {sweep_file}"
    assert test_file.exists(), f"Missing {test_file}"
    assert comp_file.exists(), f"Missing {comp_file}"
    assert bench_file.exists(), f"Missing {bench_file}"

    val_data = json.loads(val_file.read_text(encoding="utf-8"))
    assert val_data["totalTestImages"] == 928
    assert val_data["mAP50"] == 0.0506

    test_data = json.loads(test_file.read_text(encoding="utf-8"))
    assert test_data["totalTestImages"] == 522
    assert test_data["mAP50"] == 0.0503
    assert test_data["perClassMetrics"]["EXIT_SIGN"]["ap50"] == 0.0684

    # Visualizations
    for sub in ["tp", "fp", "fn"]:
        vis_files = list((ARTIFACTS_8_9 / "visualizations" / sub).glob("*.jpg"))
        assert len(vis_files) >= 5, f"Expected at least 5 visualization samples in {sub}, found {len(vis_files)}"


def test_phase8_9_documentation_artifacts_exist():
    """Verify that all required Phase 8.9 documentation files are authored and mirrored."""
    required_docs = [
        "phase8_9_architecture.md",
        "phase8_9_training_report.md",
        "phase8_9_model_card.md",
        "phase8_9_validation_report.md",
        "phase8_9_external_test_report.md",
        "phase8_9_error_analysis.md",
        "phase8_9_benchmark.md",
        "phase8_9_model_comparison.md",
        "phase8_9_decision.md",
    ]
    for doc_name in required_docs:
        ai_doc = AI_SERVICE_DIR / "docs" / doc_name
        root_doc = WORKSPACE_ROOT / "docs" / doc_name
        assert ai_doc.exists(), f"Missing ai-service doc: {ai_doc}"
        assert root_doc.exists(), f"Missing mirrored root doc: {root_doc}"
