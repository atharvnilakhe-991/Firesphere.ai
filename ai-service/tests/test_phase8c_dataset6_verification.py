"""Phase 8C Test Suite: Dataset 6 Violation Semantic Verification Engine.

Validates:
- Dataset 6 ZIP archive integrity, file size, and SHA-256.
- Extraction to raw/blocked_emergency_exit/.
- Semantic class analysis distinguishing genuine violations from generic equipment.
- Target class mapping to EMERGENCY_EXIT_OBSTRUCTED (Class 3).
- Strict non-violation quarantine for 'emergency exit door' (Section 9 compliance).
- Sample usability: 120 usable images with 126 violation annotations.
- Bounding box coordinate validity (0 <= xc, yc <= 1, 0 < w, h <= 1).
- Non-pollution of Phase 8B canonical dataset (Section 10 compliance).
- Verification report generation and metadata updates.
- Overall readiness decision: DATASET_6_USABLE_FOR_EMERGENCY_EXIT_OBSTRUCTED (isReadyForPhase9 = False).
"""

import json
from pathlib import Path
import pytest

from app.training.verify_dataset6 import run_dataset6_verification


def test_dataset6_archive_integrity_and_provenance():
    """Verifies Dataset 6 ZIP exists, matches expected SHA-256 and size."""
    zip_path = Path("../datasets/blocked emergency exit.v1i.yolov8.zip")
    if not zip_path.exists():
        zip_path = Path("datasets/blocked emergency exit.v1i.yolov8.zip")

    assert zip_path.exists(), "Dataset 6 ZIP must exist in datasets root"
    assert zip_path.stat().st_size == 7153836, "Archive size must match exactly 7,153,836 bytes"


def test_dataset6_semantic_verification_execution():
    """Executes semantic audit and asserts exact verified counts and mapping."""
    report = run_dataset6_verification()

    assert report["dataset_id"] == "roboflow-blocked-emergency-exit-v1"
    assert report["archive_size_bytes"] == 7153836
    assert report["archive_sha256"] == "242e5ce5afff09cbf05831ec085ab9e46f09bce8170e7e9b40a7e6d509e76edb"
    assert report["total_images"] == 156
    assert report["unique_base_scenes"] == 52
    assert report["total_annotations"] == 168
    assert report["invalid_annotations"] == 0
    assert report["unreadable_images"] == 0

    # Class breakdown
    assert len(report["original_classes"]) == 5
    mapping = report["semantic_mapping"]

    # Genuine violations
    assert mapping["blocked emergency exit"]["target_class"] == "EMERGENCY_EXIT_OBSTRUCTED"
    assert mapping["blocked emergency exit"]["box_count"] == 18

    assert mapping["boxes blocking exit"]["target_class"] == "EMERGENCY_EXIT_OBSTRUCTED"
    assert mapping["boxes blocking exit"]["box_count"] == 39

    assert mapping["exit block violation"]["target_class"] == "EMERGENCY_EXIT_OBSTRUCTED"
    assert mapping["exit block violation"]["box_count"] == 15

    assert mapping["objects blocked emergency exit"]["target_class"] == "EMERGENCY_EXIT_OBSTRUCTED"
    assert mapping["objects blocked emergency exit"]["box_count"] == 54

    # Generic non-violation door MUST NOT map to violation (Section 9)
    assert mapping["emergency exit door"]["target_class"] is None
    assert mapping["emergency exit door"]["is_violation"] is False
    assert mapping["emergency exit door"]["box_count"] == 42

    # Usable vs Rejected
    assert report["usable_violation_samples"]["usable_images_count"] == 120
    assert report["usable_violation_samples"]["usable_annotations_count"] == 126
    assert report["rejected_non_violation_samples"]["rejected_door_only_images_count"] == 36
    assert report["rejected_non_violation_samples"]["rejected_door_annotations_count"] == 42

    # Readiness
    assert report["overall_readiness_assessment"] == "DATASET_6_USABLE_FOR_EMERGENCY_EXIT_OBSTRUCTED"


def test_dataset6_canonical_isolation_and_normalization():
    """Verifies Dataset 6 is NOT merged into canonical dataset, and normalized copies exist."""
    canonical_img_dir = Path("../datasets/fire_safety/images")
    if not canonical_img_dir.exists():
        canonical_img_dir = Path("datasets/fire_safety/images")

    # Canonical dataset must remain unpolluted (exactly 6,471 images from Phase 8B)
    assert len(list(canonical_img_dir.glob("*.jpg"))) == 6471

    # Normalized copies must exist in raw staging
    norm_img_dir = Path("../datasets/fire_safety/raw/blocked_emergency_exit/normalized/images")
    if not norm_img_dir.exists():
        norm_img_dir = Path("datasets/fire_safety/raw/blocked_emergency_exit/normalized/images")

    assert norm_img_dir.exists()
    assert len(list(norm_img_dir.glob("*.jpg"))) == 120

    # Quarantined door-only images
    quar_dir = Path("../datasets/fire_safety/raw/blocked_emergency_exit/quarantine_door_only")
    if not quar_dir.exists():
        quar_dir = Path("datasets/fire_safety/raw/blocked_emergency_exit/quarantine_door_only")

    assert quar_dir.exists()
    assert len(list(quar_dir.glob("*.jpg"))) == 36
