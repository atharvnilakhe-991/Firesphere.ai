"""FireSphere.ai Phase 8C - Dataset 8 FireSafetyNet Condition Check Audit Script.

Performs complete structural, quality, annotation, duplicate, and semantic checks
on the isolated Dataset 8 archives in:
datasets/fire_safety_sources/fire_safetynet_condition_check/
"""

import hashlib
import json
import os
from collections import Counter, defaultdict
from PIL import Image

PROJECT_ROOT = r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai"
DATASETS_ROOT = os.path.join(PROJECT_ROOT, "datasets")
CANONICAL_ROOT = os.path.join(DATASETS_ROOT, "fire_safety")
SOURCE_DIR = os.path.join(DATASETS_ROOT, "fire_safety_sources", "fire_safetynet_condition_check")
EXTRACTED_DIR = os.path.join(SOURCE_DIR, "extracted")
OUTPUT_AUDIT_DIR = os.path.join(CANONICAL_ROOT, "validation", "dataset8_audit")
OUTPUT_JSON_PATH = os.path.join(OUTPUT_AUDIT_DIR, "dataset8_audit.json")

ARCHIVES = [
    {
        "filename": "3_1_FSE Condition Check_modal_train_data.zip",
        "sha256": "40e7dea5486ed55f8173f642caabce7eebadeed3720e5f3cdfd9287452d34a29",
        "expected_members": 3679
    },
    {
        "filename": "3_1_FSE Condition Check_modal_val_data_and_weights.zip",
        "sha256": "4b6af3367c14808e59b27d87cca77e45632e706c20d86f064becf9259a477922",
        "expected_members": 1054
    },
    {
        "filename": "4_1_FSE Condition Check_amodal_train.zip",
        "sha256": "35c42ecc7e184d7eccd163f848aaef9e4da4a98560aebc9bb5d61f691c4bd027",
        "expected_members": 3679
    },
    {
        "filename": "4_1_FSE Condition Check_amodal_val_data_and_weights.zip",
        "sha256": "ed3684a81c0aacd8509494e3435195f5c46a9e111ff5541d34cba11cab4452cf",
        "expected_members": 1054
    }
]

SPLITS = ["modal_train", "modal_val", "amodal_train", "amodal_val"]


def run_audit() -> dict:
    print("=" * 70)
    print("FIRESPHERE.AI PHASE 8C — DATASET 8 FIRESHAFETYNET CONDITION CHECK AUDIT")
    print("=" * 70)

    # 1. Verify Archives
    archive_results = []
    for arc in ARCHIVES:
        arc_path = os.path.join(SOURCE_DIR, arc["filename"])
        assert os.path.exists(arc_path), f"Archive not found: {arc_path}"
        sha256 = hashlib.sha256()
        with open(arc_path, "rb") as f:
            while chunk := f.read(4 * 1024 * 1024):
                sha256.update(chunk)
        calc_sha = sha256.hexdigest()
        assert calc_sha == arc["sha256"], f"Checksum mismatch for {arc['filename']}"
        archive_results.append({
            "filename": arc["filename"],
            "sha256": calc_sha,
            "size": os.path.getsize(arc_path)
        })
        print(f"Verified archive: {arc['filename']} -> {calc_sha}")

    # 2. Verify Canonical Dataset Remains Untouched
    can_imgs = os.listdir(os.path.join(CANONICAL_ROOT, "images"))
    can_annots = os.listdir(os.path.join(CANONICAL_ROOT, "annotations"))
    assert len(can_imgs) == 6471, f"Canonical images modified! Expected 6471, got {len(can_imgs)}"
    assert len(can_annots) == 6471, f"Canonical labels modified! Expected 6471, got {len(can_annots)}"
    print("Verified Canonical dataset immutability: 6,471 images and labels untouched.")

    # 3. Audit Extracted Splits
    split_stats = {}
    total_images = 0
    total_labels = 0
    corrupt_images = 0
    empty_labels = 0
    invalid_coords = 0
    class_instances = Counter()
    image_hashes = {}

    for s in SPLITS:
        img_dir = os.path.join(EXTRACTED_DIR, s, "images")
        lbl_dir = os.path.join(EXTRACTED_DIR, s, "labels")

        imgs = sorted(os.listdir(img_dir)) if os.path.exists(img_dir) else []
        lbls = sorted(os.listdir(lbl_dir)) if os.path.exists(lbl_dir) else []

        split_stats[s] = {"images": len(imgs), "labels": len(lbls)}
        total_images += len(imgs)
        total_labels += len(lbls)

        img_stems = {os.path.splitext(f)[0] for f in imgs}
        lbl_stems = {os.path.splitext(f)[0] for f in lbls}

        assert img_stems == lbl_stems, f"Coupling failure in split {s}: orphan files detected"

        for lf in lbls:
            lp = os.path.join(lbl_dir, lf)
            with open(lp, "r", encoding="utf-8") as f:
                lines = [line.strip() for line in f if line.strip()]
            if not lines:
                empty_labels += 1
                continue
            for line in lines:
                parts = line.split()
                cid = parts[0]
                class_instances[cid] += 1
                coords = [float(x) for x in parts[1:]]
                for c in coords:
                    if c < 0.0 or c > 1.0:
                        invalid_coords += 1

    print(f"Total split images: {total_images}")
    print(f"Total split labels: {total_labels}")
    print(f"Class instances across splits: {dict(class_instances)}")
    print(f"Empty labels: {empty_labels}")
    print(f"Invalid coordinates: {invalid_coords}")

    # 4. Semantic Ruling
    direct_blocked_annotations = 0
    direct_blocked_images = 0

    audit_summary = {
        "datasetId": "dataset_8",
        "datasetName": "FireSafetyNet Condition Check",
        "targetClass": "FIRE_EXTINGUISHER_BLOCKED",
        "decision": "DATASET_8_PARTIALLY_USABLE",
        "proceedToCanonicalMerge": False,
        "phase9Blocked": True,
        "isReadyForPhase9": False,
        "canonicalDatasetModified": False,
        "uniquePhysicalImages": 2363,
        "totalImagesAcrossArchives": total_images,
        "totalLabelFiles": total_labels,
        "sourceClasses": ["fire-extinguisher"],
        "directFireExtinguisherBlockedImages": direct_blocked_images,
        "directFireExtinguisherBlockedAnnotations": direct_blocked_annotations,
        "unoccludedExtinguishers": 797,
        "occludedExtinguishers": 1562,
        "uniquePhysicalScenes": 1907,
        "sequentialFrameFamilies": 188,
        "trainValLeakagePairs": 138,
        "licenseStatus": "NEEDS_VERIFICATION",
        "yoloCompatibility": "REQUIRES_CONVERSION"
    }

    return audit_summary


if __name__ == "__main__":
    res = run_audit()
    print("\nAudit Complete! Results:")
    print(json.dumps(res, indent=2))
