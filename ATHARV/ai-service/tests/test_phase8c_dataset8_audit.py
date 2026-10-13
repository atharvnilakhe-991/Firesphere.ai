"""Tests for Phase 8C - Dataset 8 FireSafetyNet Condition Check Full Audit."""

import hashlib
import json
import os
import pytest

PROJECT_ROOT = r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai"
DATASETS_ROOT = os.path.join(PROJECT_ROOT, "datasets")
CANONICAL_ROOT = os.path.join(DATASETS_ROOT, "fire_safety")
SOURCE_DIR = os.path.join(DATASETS_ROOT, "fire_safety_sources", "fire_safetynet_condition_check")
EXTRACTED_DIR = os.path.join(SOURCE_DIR, "extracted")
AUDIT_JSON_PATH = os.path.join(CANONICAL_ROOT, "validation", "dataset8_audit", "dataset8_audit.json")
AUDIT_MD_PATH = os.path.join(CANONICAL_ROOT, "validation", "dataset8_audit", "dataset8_audit.md")
SOURCE_MANIFEST_PATH = os.path.join(SOURCE_DIR, "dataset8_source_manifest.json")
DUPLICATE_REPORT_PATH = os.path.join(CANONICAL_ROOT, "validation", "dataset8_audit", "dataset8_duplicate_report.json")
LEAKAGE_REPORT_PATH = os.path.join(CANONICAL_ROOT, "validation", "dataset8_audit", "dataset8_split_leakage_report.json")
SEMANTIC_CSV_PATH = os.path.join(CANONICAL_ROOT, "validation", "dataset8_audit", "dataset8_semantic_mapping.csv")

EXPECTED_ARCHIVES = {
    "3_1_FSE Condition Check_modal_train_data.zip": {
        "sha256": "40e7dea5486ed55f8173f642caabce7eebadeed3720e5f3cdfd9287452d34a29",
        "size": 1467632410,
        "members": 3679
    },
    "3_1_FSE Condition Check_modal_val_data_and_weights.zip": {
        "sha256": "4b6af3367c14808e59b27d87cca77e45632e706c20d86f064becf9259a477922",
        "size": 457107575,
        "members": 1054
    },
    "4_1_FSE Condition Check_amodal_train.zip": {
        "sha256": "35c42ecc7e184d7eccd163f848aaef9e4da4a98560aebc9bb5d61f691c4bd027",
        "size": 1469230487,
        "members": 3679
    },
    "4_1_FSE Condition Check_amodal_val_data_and_weights.zip": {
        "sha256": "ed3684a81c0aacd8509494e3435195f5c46a9e111ff5541d34cba11cab4452cf",
        "size": 457549678,
        "members": 1054
    }
}


def test_dataset8_archives_found_and_hashes_recorded():
    """Verify all 4 source archives exist and have recorded immutable SHA-256 checksums."""
    assert os.path.exists(SOURCE_MANIFEST_PATH), f"Source manifest not found: {SOURCE_MANIFEST_PATH}"
    with open(SOURCE_MANIFEST_PATH, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    assert len(manifest["archives"]) == 4
    for arc_info in manifest["archives"]:
        fname = arc_info["filename"]
        assert fname in EXPECTED_ARCHIVES, f"Unexpected archive: {fname}"
        expected = EXPECTED_ARCHIVES[fname]
        assert arc_info["sha256"] == expected["sha256"]
        assert arc_info["sizeBytes"] == expected["size"]
        assert arc_info["memberCount"] == expected["members"]

        # Check physical file on disk
        arc_path = os.path.join(SOURCE_DIR, fname)
        assert os.path.exists(arc_path), f"Archive file missing: {arc_path}"
        assert os.path.getsize(arc_path) == expected["size"]


def test_dataset8_canonical_dataset_remains_unchanged():
    """Verify canonical FireSphere dataset (6,471 images/labels) remains completely untouched."""
    can_imgs = os.listdir(os.path.join(CANONICAL_ROOT, "images"))
    can_annots = os.listdir(os.path.join(CANONICAL_ROOT, "annotations"))

    assert len(can_imgs) == 6471, f"Canonical images modified! Found: {len(can_imgs)}"
    assert len(can_annots) == 6471, f"Canonical labels modified! Found: {len(can_annots)}"


def test_dataset8_image_annotation_pairing_and_no_orphans():
    """Verify 100% pairing between images and labels across all 4 extracted splits with 0 orphans."""
    splits = ["modal_train", "modal_val", "amodal_train", "amodal_val"]
    for s in splits:
        img_dir = os.path.join(EXTRACTED_DIR, s, "images")
        lbl_dir = os.path.join(EXTRACTED_DIR, s, "labels")

        assert os.path.exists(img_dir), f"Missing images dir for {s}"
        assert os.path.exists(lbl_dir), f"Missing labels dir for {s}"

        imgs = {os.path.splitext(f)[0] for f in os.listdir(img_dir)}
        lbls = {os.path.splitext(f)[0] for f in os.listdir(lbl_dir)}

        assert imgs == lbls, f"Coupling failure in split {s}: orphan files detected"
        assert len(imgs) in [1838, 525], f"Unexpected image count in {s}: {len(imgs)}"


def test_dataset8_invalid_annotation_detection():
    """Verify coordinates validity across extracted polygon annotations."""
    with open(AUDIT_JSON_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    assert data["counts"]["invalidCoordinatesCount"] == 0
    assert data["counts"]["outOfBoundsPolygonsCount"] == 0
    assert data["counts"]["corruptImagesCount"] == 0
    assert data["counts"]["zeroByteImagesCount"] == 0


def test_dataset8_class_inventory():
    """Verify that only class 0 ('fire-extinguisher') is present in the source dataset."""
    with open(AUDIT_JSON_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    assert data["counts"]["sourceClasses"] == ["fire-extinguisher"]
    assert data["counts"]["sourceClassesCount"] == 1
    assert os.path.exists(SEMANTIC_CSV_PATH), f"Semantic mapping CSV missing: {SEMANTIC_CSV_PATH}"


def test_dataset8_duplicate_detection_and_cross_dataset():
    """Verify exact and near duplicate metrics, plus zero overlap with canonical dataset."""
    assert os.path.exists(DUPLICATE_REPORT_PATH), f"Duplicate report missing: {DUPLICATE_REPORT_PATH}"
    with open(DUPLICATE_REPORT_PATH, "r", encoding="utf-8") as f:
        dupes = json.load(f)

    assert dupes["internalExactDuplicates"]["count"] == 0
    assert dupes["modalVsAmodalExactPairDuplicates"] == 2363
    assert dupes["crossDatasetDuplicates"]["exactSha256Matches"] == 0
    assert dupes["crossDatasetDuplicates"]["nearPerceptualMatches"] == 0


def test_dataset8_split_leakage():
    """Verify detection of train-validation split leakage pairs."""
    assert os.path.exists(LEAKAGE_REPORT_PATH), f"Leakage report missing: {LEAKAGE_REPORT_PATH}"
    with open(LEAKAGE_REPORT_PATH, "r", encoding="utf-8") as f:
        leakage = json.load(f)

    assert leakage["trainValSplitIntegrity"]["perceptualNearDuplicateLeakagePairs"] == 138
    assert leakage["evaluatedSplits"]["sourceProvidedTest"]["images"] == 0


def test_dataset8_semantic_audit_output_and_governance():
    """Verify that zero FIRE_EXTINGUISHER_BLOCKED annotations exist and Phase 9 remains blocked."""
    assert os.path.exists(AUDIT_JSON_PATH), f"Audit JSON missing: {AUDIT_JSON_PATH}"
    assert os.path.exists(AUDIT_MD_PATH), f"Audit MD missing: {AUDIT_MD_PATH}"

    with open(AUDIT_JSON_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    # Core non-negotiable semantic constraints
    assert data["targetClass"] == "FIRE_EXTINGUISHER_BLOCKED"
    assert data["directFireExtinguisherBlockedImages"] == 0
    assert data["directFireExtinguisherBlockedAnnotations"] == 0
    assert data["decision"] == "DATASET_8_PARTIALLY_USABLE"

    # Governance gate constraints
    assert data["proceedToCanonicalMerge"] is False
    assert data["phase9Blocked"] is True
    assert data["isReadyForPhase9"] is False
    assert data["canonicalDatasetModified"] is False
    assert data["finalSummary"]["phase9Status"] == "BLOCKED"
    assert data["finalSummary"]["datasetStatus"] == "PARTIALLY USABLE"
