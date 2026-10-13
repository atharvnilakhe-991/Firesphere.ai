"""Automated tests for Phase 7 Dataset Foundation, Validation, and Partitioning."""

import json
from pathlib import Path
import tempfile
import cv2
import numpy as np
import pytest
from app.training.class_balance import ClassBalanceAnalyzer
from app.training.dataset import DatasetNotAvailableException, FireSafetyDataset
from app.training.dataset_splitter import DatasetSplitter
from app.training.dataset_validator import DatasetValidator


def create_synthetic_image(output_path: Path, width: int = 640, height: int = 640) -> None:
    """Creates a valid synthetic image file for unit testing."""
    img = np.zeros((height, width, 3), dtype=np.uint8)
    cv2.rectangle(img, (50, 50), (200, 200), (0, 0, 255), -1)
    cv2.imwrite(str(output_path), img)


def test_dataset_validator_clean_dataset(tmp_path: Path):
    """Verifies that a well-formed YOLO dataset passes validation with VALID status."""
    images_dir = tmp_path / "images"
    annotations_dir = tmp_path / "annotations"
    images_dir.mkdir()
    annotations_dir.mkdir()

    # Create 3 valid pairs
    for i in range(3):
        stem = f"sample_{i:03d}"
        create_synthetic_image(images_dir / f"{stem}.jpg")
        # Class 0, centered, width 0.2, height 0.2
        with open(annotations_dir / f"{stem}.txt", "w", encoding="utf-8") as f:
            f.write("0 0.5 0.5 0.2 0.2\n1 0.3 0.3 0.1 0.1\n")

    report = DatasetValidator.validate_dataset(images_dir, annotations_dir)
    assert report.status == "VALID"
    assert report.totalImages == 3
    assert report.validImages == 3
    assert report.totalAnnotations == 6
    assert report.validAnnotations == 6
    assert report.invalidAnnotations == 0
    assert report.classDistribution.get(0) == 3
    assert report.classDistribution.get(1) == 3
    assert "VALID" in report.generate_summary_text()


def test_dataset_validator_detects_corrupted_and_missing_annotations(tmp_path: Path):
    """Verifies detection of missing annotations, corrupted images, and malformed coordinates."""
    images_dir = tmp_path / "images"
    annotations_dir = tmp_path / "annotations"
    images_dir.mkdir()
    annotations_dir.mkdir()

    # 1. Valid item
    create_synthetic_image(images_dir / "valid_001.jpg")
    with open(annotations_dir / "valid_001.txt", "w") as f:
        f.write("0 0.5 0.5 0.2 0.2\n")

    # 2. Image without annotation
    create_synthetic_image(images_dir / "orphan_img.jpg")

    # 3. Annotation without image
    with open(annotations_dir / "orphan_annot.txt", "w") as f:
        f.write("2 0.5 0.5 0.2 0.2\n")

    # 4. Corrupted image file
    with open(images_dir / "corrupt_001.jpg", "wb") as f:
        f.write(b"NOT_A_VALID_IMAGE_BYTES")
    with open(annotations_dir / "corrupt_001.txt", "w") as f:
        f.write("3 0.5 0.5 0.2 0.2\n")

    # 5. Malformed annotation (negative coordinate and invalid class ID 99)
    create_synthetic_image(images_dir / "malformed_001.jpg")
    with open(annotations_dir / "malformed_001.txt", "w") as f:
        f.write("99 0.5 0.5 0.2 0.2\n0 -0.1 0.5 0.2 0.2\n")

    report = DatasetValidator.validate_dataset(images_dir, annotations_dir)
    assert report.status == "INVALID"
    assert "orphan_img.txt" in report.missingAnnotations
    assert "orphan_annot" in report.missingImages
    assert report.invalidImages >= 1
    assert report.invalidAnnotations >= 2
    assert len(report.validationIssues) >= 4


def test_dataset_validator_detects_content_duplicates(tmp_path: Path):
    """Verifies duplicate image detection using SHA-256 cryptographic hashing."""
    images_dir = tmp_path / "images"
    annotations_dir = tmp_path / "annotations"
    images_dir.mkdir()
    annotations_dir.mkdir()

    img1 = images_dir / "img1.jpg"
    img2 = images_dir / "img2.jpg"  # Exact byte duplicate
    create_synthetic_image(img1)
    with open(img1, "rb") as f_in, open(img2, "wb") as f_out:
        f_out.write(f_in.read())

    with open(annotations_dir / "img1.txt", "w") as f:
        f.write("0 0.5 0.5 0.2 0.2\n")
    with open(annotations_dir / "img2.txt", "w") as f:
        f.write("0 0.5 0.5 0.2 0.2\n")

    report = DatasetValidator.validate_dataset(images_dir, annotations_dir)
    assert len(report.duplicateImages) == 1
    assert report.duplicateImages[0]["original"] == "img1.jpg"
    assert report.duplicateImages[0]["duplicate"] == "img2.jpg"


def test_dataset_splitter_uniform_and_deterministic():
    """Verifies deterministic 70/20/10 partitioning with reproducible seed."""
    samples = [f"sample_{i:03d}" for i in range(100)]

    res1 = DatasetSplitter.split(samples, train_ratio=0.7, val_ratio=0.2, test_ratio=0.1, seed=42)
    res2 = DatasetSplitter.split(samples, train_ratio=0.7, val_ratio=0.2, test_ratio=0.1, seed=42)

    assert res1.trainCount == 70
    assert res1.valCount == 20
    assert res1.testCount == 10
    assert res1.trainFiles == res2.trainFiles
    assert res1.valFiles == res2.valFiles
    assert res1.testFiles == res2.testFiles

    # Assert mutual exclusivity (no leakage)
    s_train = set(res1.trainFiles)
    s_val = set(res1.valFiles)
    s_test = set(res1.testFiles)
    assert len(s_train.intersection(s_val)) == 0
    assert len(s_train.intersection(s_test)) == 0
    assert len(s_val.intersection(s_test)) == 0


def test_dataset_splitter_group_leakage_prevention():
    """Verifies that items belonging to the same inspectionId group remain in the same split."""
    samples = [f"photo_{i}" for i in range(20)]
    # Group items into 4 inspection groups of 5 photos each
    groups = {f"photo_{i}": f"inspection_group_{i // 5}" for i in range(20)}

    res = DatasetSplitter.split(
        samples, train_ratio=0.5, val_ratio=0.25, test_ratio=0.25, seed=123, groups=groups
    )

    # For each group, all its photos must be in exactly one split
    group_splits = {}
    for item in res.trainFiles:
        gid = groups[item]
        group_splits.setdefault(gid, set()).add("train")
    for item in res.valFiles:
        gid = groups[item]
        group_splits.setdefault(gid, set()).add("val")
    for item in res.testFiles:
        gid = groups[item]
        group_splits.setdefault(gid, set()).add("test")

    for gid, split_names in group_splits.items():
        assert len(split_names) == 1, f"Group {gid} leaked across multiple splits: {split_names}"


def test_class_balance_analyzer(tmp_path: Path):
    """Verifies class frequency counting and inverse-frequency loss weighting."""
    annot_dir = tmp_path / "annotations"
    annot_dir.mkdir()

    # 10 instances of class 0, 2 instances of class 1, 0 instances of other classes
    with open(annot_dir / "img1.txt", "w") as f:
        f.write("\n".join(["0 0.5 0.5 0.1 0.1"] * 10) + "\n")
    with open(annot_dir / "img2.txt", "w") as f:
        f.write("1 0.5 0.5 0.1 0.1\n1 0.6 0.6 0.1 0.1\n")

    profile = ClassBalanceAnalyzer.analyze_from_annotations(annot_dir)
    assert profile.totalAnnotations == 12
    assert profile.totalImages == 2
    assert profile.classInstanceCounts[0] == 10
    assert profile.classInstanceCounts[1] == 2
    assert 1 in profile.underrepresentedClasses or profile.classPercentages[1] < 20.0
    assert 2 in profile.emptyClasses
    assert profile.recommendedWeights[1] > profile.recommendedWeights[0]
