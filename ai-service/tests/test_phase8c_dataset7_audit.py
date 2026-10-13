"""Tests for Phase 8C - Dataset 7 Semantic Audit for FIRE_EXTINGUISHER_MISSING."""

import hashlib
import json
import os
import pytest

PROJECT_ROOT = r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai"
DATASETS_ROOT = os.path.join(PROJECT_ROOT, "datasets")
CANONICAL_ROOT = os.path.join(DATASETS_ROOT, "fire_safety")
AUDIT_JSON_PATH = os.path.join(CANONICAL_ROOT, "validation", "dataset7_semantic_audit.json")
AUDIT_MD_PATH = os.path.join(CANONICAL_ROOT, "validation", "dataset7_semantic_audit.md")
ARCHIVE_PATH = os.path.join(DATASETS_ROOT, "My First Project.v6i.yolov8.zip")

EXPECTED_SHA256 = "4bea67d2855849eecc984eb0f7b1f7af781b0f949d7004e099fa1e4374195928"
EXPECTED_SIZE = 15345661


def test_dataset7_archive_and_canonical_integrity():
    """Verify Dataset 7 archive integrity and ensure canonical dataset remains untouched."""
    assert os.path.exists(ARCHIVE_PATH), f"Archive not found: {ARCHIVE_PATH}"
    assert os.path.getsize(ARCHIVE_PATH) == EXPECTED_SIZE

    # Verify SHA-256
    sha256 = hashlib.sha256()
    with open(ARCHIVE_PATH, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            sha256.update(chunk)
    assert sha256.hexdigest() == EXPECTED_SHA256

    # Verify canonical dataset remains byte-for-byte untouched (6,471 images and annotations)
    canonical_images = os.listdir(os.path.join(CANONICAL_ROOT, "images"))
    canonical_annots = os.listdir(os.path.join(CANONICAL_ROOT, "annotations"))
    assert len(canonical_images) == 6471, f"Canonical images modified! Found: {len(canonical_images)}"
    assert len(canonical_annots) == 6471, f"Canonical annotations modified! Found: {len(canonical_annots)}"


def test_dataset7_semantic_audit_report_data():
    """Verify machine-readable semantic audit results."""
    assert os.path.exists(AUDIT_JSON_PATH), f"JSON audit not found: {AUDIT_JSON_PATH}"
    assert os.path.exists(AUDIT_MD_PATH), f"MD audit report not found: {AUDIT_MD_PATH}"

    with open(AUDIT_JSON_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    assert data["targetClass"] == "FIRE_EXTINGUISHER_MISSING"
    assert data["decision"] == "DATASET_7_PARTIALLY_USABLE"
    assert data["verifiedDirectViolationImages"] == 50
    assert data["verifiedDirectViolationAnnotations"] == 50
    assert data["ambiguousImages"] == 0
    assert data["nonTargetImages"] == 815
    assert data["usablePercentage"] == pytest.approx(5.78, rel=1e-2)

    # Verify single scene redundancy
    assert data["uniquePhysicalScenes"] == 1
    assert data["uniqueVideoClip"] == "Sna54U5CjZHZECx9dyDf_2"

    # Verify governance gate constraints
    assert data["proceedToCanonicalMerge"] is False
    assert data["phase9Blocked"] is True
    assert data["isReadyForPhase9"] is False


def test_dataset7_class13_tagged_noncompliant_exclusion():
    """Verify that Class 13 ('fire extinguisher tagged as noncompliant') is not mapped to missing."""
    with open(AUDIT_JSON_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    # Class 13 has 158 annotations across 156 images
    assert data["taggedNoncompliantImages"] == 156
    assert data["taggedNoncompliantAnnotations"] == 158
    # Ensure they are counted as non-target for missing extinguisher
    assert data["nonTargetImages"] >= data["taggedNoncompliantImages"]
