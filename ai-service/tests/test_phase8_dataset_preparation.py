"""Phase 8 Test Suite: Real Fire-Safety Dataset Acquisition, Preparation & Readiness.

Tests:
- Dataset source registry structure and license verification
- Target class feasibility and detectability categorization
- Source-to-target class mapping engine
- Image decode, dimension, and corruption validation
- Annotation syntax and bounding box coordinate validation
- SHA-256 and perceptual dHash duplicate detection
- Cleaning pipeline and rejection reporting
- Manifest generation and schema verification
- Group-aware deterministic splitting and leakage prevention
- Dataset readiness gate transitions and truthful state evaluation
"""

import json
from pathlib import Path
import shutil
import tempfile
import cv2
import numpy as np
import pytest
from app.models.model_metadata import FIRE_INSPECTION_CLASSES
from app.training.dataset_cleaner import DatasetCleaner, CleaningReport
from app.training.dataset_ingestion import DatasetIngestionEngine
from app.training.dataset_manifest import ManifestGenerator
from app.training.dataset_splitter import DatasetSplitter
from app.training.dataset_validator import DatasetValidator
from app.training.readiness_gate import DatasetReadinessGate


def create_synthetic_test_image(path: Path, width: int = 640, height: int = 640, color=(128, 128, 128)) -> Path:
    """Helper creating a valid test image file."""
    img = np.full((height, width, 3), color, dtype=np.uint8)
    cv2.imwrite(str(path), img)
    return path


def test_dataset_source_registry_schema_and_licenses():
    """Verifies that dataset_sources.json is well-formed and candidate licenses are documented."""
    sources_path = Path("ai-service/datasets/fire_safety/dataset_sources.json")
    if not sources_path.exists():
        sources_path = Path("datasets/fire_safety/dataset_sources.json")

    assert sources_path.exists(), "dataset_sources.json must exist"

    with open(sources_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    assert "sources" in data
    sources = data["sources"]
    assert len(sources) >= 5, "Must document at least 5 candidate datasets"

    valid_statuses = {"VERIFIED_PUBLIC_OPEN_ACCESS", "RESTRICTED_NON_COMMERCIAL", "ACQUISITION_IN_PROGRESS"}
    valid_license_statuses = {"VERIFIED", "UNVERIFIED"}

    for s in sources:
        assert "datasetId" in s
        assert "datasetName" in s
        assert "source" in s
        assert "sourceUrl" in s
        assert "license" in s
        assert s["licenseStatus"] in valid_license_statuses
        assert s["provenanceStatus"] in valid_statuses
        assert "relevantClasses" in s


def test_target_class_feasibility_analysis():
    """Verifies all 10 target classes have a validated feasibility rating in dataset_class_mapping.json."""
    mapping_path = Path("ai-service/datasets/fire_safety/dataset_class_mapping.json")
    if not mapping_path.exists():
        mapping_path = Path("datasets/fire_safety/dataset_class_mapping.json")

    assert mapping_path.exists(), "dataset_class_mapping.json must exist"

    with open(mapping_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    feasibility_list = data["targetClassFeasibility"]
    assert len(feasibility_list) == 10, "Must cover all 10 target classes"

    allowed_ratings = {
        "DIRECTLY_VISUALLY_DETECTABLE",
        "PARTIALLY_VISUALLY_DETECTABLE",
        "NOT_RELIABLY_VISUALLY_DETECTABLE",
    }

    class_ids_found = set()
    for item in feasibility_list:
        cid = item["classId"]
        class_ids_found.add(cid)
        assert cid in FIRE_INSPECTION_CLASSES
        assert item["visualDetectability"] in allowed_ratings
        assert len(item["visualEvidenceRequired"]) > 10
        assert isinstance(item["contextualInfoRequired"], bool)
        assert isinstance(item["ocrOrDocumentMetadataRequired"], bool)
        assert isinstance(item["humanVerificationRequired"], bool)

    assert class_ids_found == set(range(10))

    # Verify specific safety findings:
    # Class 2 (FIRE_EXTINGUISHER_EXPIRED) must NOT be claimed as directly visually detectable
    expired_meta = next(item for item in feasibility_list if item["classId"] == 2)
    assert expired_meta["visualDetectability"] == "NOT_RELIABLY_VISUALLY_DETECTABLE"
    assert expired_meta["ocrOrDocumentMetadataRequired"] is True


def test_dataset_ingestion_and_class_mapping_engine(tmp_path):
    """Tests DatasetIngestionEngine mapping source classes to FireSphere classes."""
    src_img_dir = tmp_path / "src_images"
    src_annot_dir = tmp_path / "src_annotations"
    tgt_img_dir = tmp_path / "tgt_images"
    tgt_annot_dir = tmp_path / "tgt_annotations"

    src_img_dir.mkdir()
    src_annot_dir.mkdir()

    # Create source sample image
    img_path = src_img_dir / "sample_001.jpg"
    create_synthetic_test_image(img_path)

    # Source dataset with classes: 0 -> obstructed_door, 1 -> clear_exit
    # obstructed_door maps to EMERGENCY_EXIT_OBSTRUCTED (3)
    # clear_exit maps to UNSUPPORTED (None)
    annot_path = src_annot_dir / "sample_001.txt"
    annot_path.write_text("0 0.5 0.5 0.4 0.6\n1 0.2 0.2 0.1 0.1\n")

    mapping_cfg = Path("ai-service/datasets/fire_safety/dataset_class_mapping.json")
    if not mapping_cfg.exists():
        mapping_cfg = Path("datasets/fire_safety/dataset_class_mapping.json")

    engine = DatasetIngestionEngine(class_mapping_config_path=mapping_cfg)
    report = engine.ingest_yolo_dataset(
        source_dataset_id="roboflow-emergency-exit-obstruction-v2",
        source_images_dir=src_img_dir,
        source_annotations_dir=src_annot_dir,
        target_images_dir=tgt_img_dir,
        target_annotations_dir=tgt_annot_dir,
        source_class_names=["obstructed_door", "clear_exit"],
    )

    assert report.ingestedImages == 1
    assert report.totalSourceAnnotations == 2
    assert report.totalMappedAnnotations == 1
    assert report.totalUnsupportedAnnotations == 1
    assert report.classInstanceCounts.get(3) == 1

    # Verify target annotation content
    out_annot = tgt_annot_dir / "sample_001.txt"
    assert out_annot.exists()
    lines = out_annot.read_text().strip().split("\n")
    assert len(lines) == 1
    assert lines[0].startswith("3 ")  # Class 3: EMERGENCY_EXIT_OBSTRUCTED


def test_dataset_cleaner_and_rejection_report(tmp_path):
    """Tests DatasetCleaner detecting corruptions, invalid boxes, duplicates, and writing report."""
    img_dir = tmp_path / "images"
    annot_dir = tmp_path / "annots"
    img_dir.mkdir()
    annot_dir.mkdir()

    # 1. Valid image and annotation
    valid_img = img_dir / "valid_001.jpg"
    create_synthetic_test_image(valid_img, 640, 640)
    (annot_dir / "valid_001.txt").write_text("0 0.5 0.5 0.2 0.4\n")

    # 2. Corrupt image file (invalid bytes)
    corrupt_img = img_dir / "corrupt_002.jpg"
    corrupt_img.write_bytes(b"NOT_A_REAL_IMAGE_BYTES_12345")
    (annot_dir / "corrupt_002.txt").write_text("1 0.5 0.5 0.2 0.4\n")

    # 3. Tiny resolution image (< 300x300)
    tiny_img = img_dir / "tiny_003.jpg"
    create_synthetic_test_image(tiny_img, 150, 150)
    (annot_dir / "tiny_003.txt").write_text("3 0.5 0.5 0.2 0.4\n")

    # 4. Out-of-bounds bounding box annotation
    oob_img = img_dir / "oob_004.jpg"
    create_synthetic_test_image(oob_img, 640, 640, color=(100, 100, 100))
    (annot_dir / "oob_004.txt").write_text("3 1.5 0.5 0.2 0.4\n")  # xc = 1.5 out of bounds

    # 5. Exact duplicate of valid_001.jpg
    dup_img = img_dir / "dup_005.jpg"
    shutil.copy2(valid_img, dup_img)
    (annot_dir / "dup_005.txt").write_text("0 0.5 0.5 0.2 0.4\n")

    # 6. Orphan annotation (no image)
    (annot_dir / "orphan_006.txt").write_text("0 0.5 0.5 0.2 0.4\n")

    report_path = tmp_path / "rejection_report.json"
    cleaner = DatasetCleaner(min_width=300, min_height=300)
    report = cleaner.clean_dataset(
        images_dir=img_dir,
        annotations_dir=annot_dir,
        rejection_report_path=report_path,
    )

    assert report.usableImages == 1
    assert report.rejectedImagesCount >= 3
    assert report.exactDuplicatesCount == 1
    assert report_path.exists()

    with open(report_path, "r", encoding="utf-8") as f:
        saved_report = json.load(f)
    assert saved_report["usableImages"] == 1
    assert len(saved_report["rejections"]) >= 4


def test_perceptual_dhash_duplicate_detection(tmp_path):
    """Verifies that difference hash (dHash) detects near-identical images."""
    img1 = tmp_path / "base.jpg"
    img2 = tmp_path / "slightly_different.jpg"

    mat1 = np.full((640, 640, 3), 128, dtype=np.uint8)
    # Add a horizontal gradient
    for x in range(640):
        mat1[:, x] = int(x / 640.0 * 255)
    cv2.imwrite(str(img1), mat1)

    # mat2 has 1 pixel noise added, visually identical
    mat2 = mat1.copy()
    mat2[100, 100, 0] = np.uint8((int(mat2[100, 100, 0]) + 2) % 250)
    cv2.imwrite(str(img2), mat2)

    dh1 = DatasetCleaner.compute_dhash(img1)
    dh2 = DatasetCleaner.compute_dhash(img2)

    assert dh1 is not None and dh2 is not None
    dist = DatasetCleaner.hamming_distance(dh1, dh2)
    assert dist <= 2, f"Expected near zero Hamming distance for near-duplicate (got {dist})"


def test_dataset_manifest_generator(tmp_path):
    """Verifies ManifestGenerator generates valid train, val, and test manifests."""
    img_dir = tmp_path / "images"
    annot_dir = tmp_path / "annots"
    splits_dir = tmp_path / "splits"
    manifests_dir = tmp_path / "manifests"

    img_dir.mkdir()
    annot_dir.mkdir()
    splits_dir.mkdir()

    for idx in range(3):
        stem = f"sample_{idx:03d}"
        create_synthetic_test_image(img_dir / f"{stem}.jpg")
        (annot_dir / f"{stem}.txt").write_text(f"{idx} 0.5 0.5 0.2 0.3\n")

    (splits_dir / "train.txt").write_text("sample_000\nsample_001\n")
    (splits_dir / "val.txt").write_text("sample_002\n")
    (splits_dir / "test.txt").write_text("")

    manifests = ManifestGenerator.generate_all_manifests(
        splits_dir=splits_dir,
        images_dir=img_dir,
        annotations_dir=annot_dir,
        manifests_dir=manifests_dir,
    )

    assert "train" in manifests and "val" in manifests and "test" in manifests
    assert manifests["train"].totalRecords == 2
    assert manifests["val"].totalRecords == 1
    assert manifests["test"].totalRecords == 0

    assert (manifests_dir / "train.json").exists()
    assert (manifests_dir / "val.json").exists()

    record0 = manifests["train"].records[0]
    assert record0.width == 640
    assert record0.height == 640
    assert len(record0.sha256) == 64
    assert record0.targetClasses == [0]


def test_dataset_splitter_group_aware_leakage_prevention():
    """Verifies DatasetSplitter prevents split leakage across grouped inspections."""
    # 10 samples belonging to 3 inspections:
    # inspection_A: s1, s2, s3, s4
    # inspection_B: s5, s6, s7
    # inspection_C: s8, s9, s10
    items = [f"sample_{i}" for i in range(1, 11)]
    groups = {
        "sample_1": "insp_A", "sample_2": "insp_A", "sample_3": "insp_A", "sample_4": "insp_A",
        "sample_5": "insp_B", "sample_6": "insp_B", "sample_7": "insp_B",
        "sample_8": "insp_C", "sample_9": "insp_C", "sample_10": "insp_C",
    }

    res = DatasetSplitter.split(
        items=items,
        train_ratio=0.70,
        val_ratio=0.20,
        test_ratio=0.10,
        seed=42,
        groups=groups,
    )

    train_groups = {groups[f] for f in res.trainFiles}
    val_groups = {groups[f] for f in res.valFiles}
    test_groups = {groups[f] for f in res.testFiles}

    # Zero group leakage across partitions
    assert len(train_groups.intersection(val_groups)) == 0
    assert len(train_groups.intersection(test_groups)) == 0
    assert len(val_groups.intersection(test_groups)) == 0


def test_dataset_readiness_gate_evaluation():
    """Verifies DatasetReadinessGate truthfully evaluates state without fabricating readiness."""
    ds_root = Path("ai-service/datasets/fire_safety")
    if not ds_root.exists():
        ds_root = Path("datasets/fire_safety")

    gate = DatasetReadinessGate(dataset_root=ds_root)
    evaluation = gate.evaluate_readiness()

    # The actual repository status is DATASET_ACQUISITION_IN_PROGRESS because candidate sources
    # are registered, but full annotated field data has not been provided.
    assert evaluation.status == DatasetReadinessGate.STATUS_ACQUISITION_IN_PROGRESS
    assert evaluation.isReadyForPhase9 is False
    assert len(evaluation.blockers) > 0
    assert "actively underway" in evaluation.blockers[0] or "Candidate sources" in evaluation.blockers[0]

    # Verify that gate checks G1 (source registry) and G2 (class feasibility) PASS
    g1 = next(c for c in evaluation.checks if c.checkId == "G1_SOURCE_REGISTRY")
    g2 = next(c for c in evaluation.checks if c.checkId == "G2_CLASS_FEASIBILITY")
    assert g1.passed is True
    assert g2.passed is True


def test_dataset_readiness_gate_synthetic_progression(tmp_path):
    """Verifies DatasetReadinessGate correctly transitions across all states."""
    ds_root = tmp_path / "ds"
    ds_root.mkdir()

    # State 1: DATASET_NOT_AVAILABLE
    gate = DatasetReadinessGate(dataset_root=ds_root)
    e1 = gate.evaluate_readiness()
    assert e1.status == DatasetReadinessGate.STATUS_NOT_AVAILABLE
    assert e1.isReadyForPhase9 is False

    # State 2: Add sources and mapping -> DATASET_ACQUISITION_IN_PROGRESS
    real_ds = Path("ai-service/datasets/fire_safety")
    if not real_ds.exists():
        real_ds = Path("datasets/fire_safety")

    shutil.copy2(real_ds / "dataset_sources.json", ds_root / "dataset_sources.json")
    shutil.copy2(real_ds / "dataset_class_mapping.json", ds_root / "dataset_class_mapping.json")

    e2 = gate.evaluate_readiness()
    assert e2.status == DatasetReadinessGate.STATUS_ACQUISITION_IN_PROGRESS

    # State 3: Add invalid/corrupt data -> DATASET_VALIDATION_FAILED
    img_dir = ds_root / "images"
    annot_dir = ds_root / "annotations"
    img_dir.mkdir()
    annot_dir.mkdir()

    (img_dir / "bad.jpg").write_bytes(b"CORRUPT")
    (annot_dir / "bad.txt").write_text("0 0.5 0.5 0.2 0.2\n")

    e3 = gate.evaluate_readiness()
    assert e3.status == DatasetReadinessGate.STATUS_VALIDATION_FAILED

    # State 4: Fix corruption, but too few samples/classes -> DATASET_PARTIALLY_READY
    create_synthetic_test_image(img_dir / "bad.jpg")  # now valid image
    e4 = gate.evaluate_readiness()
    assert e4.status == DatasetReadinessGate.STATUS_PARTIALLY_READY
    assert e4.isReadyForPhase9 is False
