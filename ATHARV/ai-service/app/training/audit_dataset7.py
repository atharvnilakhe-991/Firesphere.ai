"""FireSphere.ai Phase 8C - Dataset 7 Detailed Audit Script.

Performs complete structural, quality, annotation, duplicate, and semantic checks
on the isolated Dataset 7 candidate (My First Project.v6i.yolov8.zip) in:
datasets/fire_safety/validation/dataset7_audit/
"""

import hashlib
import json
import os
from collections import Counter, defaultdict
import cv2
import numpy as np
from PIL import Image

AUDIT_DIR = r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai\datasets\fire_safety\validation\dataset7_audit"
CANONICAL_IMG_DIR = r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai\datasets\fire_safety\images"
OUTPUT_REPORT_DIR = r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai\datasets\fire_safety\validation"
OUTPUT_JSON_PATH = os.path.join(OUTPUT_REPORT_DIR, "dataset7_semantic_audit.json")

SPLITS = ["train", "valid", "test"]

CLASSES = [
    "FDC",
    "air sampling smoke detector",
    "audible visible notification appliance - horn strobe",
    "audible visible notification appliance - speaker strobe",
    "ceiling mounted audible visible appliance - horn strobe",
    "double action manual fire alarm box - double pull station",
    "dry barrel sprinkler",
    "exit door",
    "exit sign",
    "exit stair",
    "fire alarm box",
    "fire alarm control panel",
    "fire extinguisher cabinet",
    "fire extinguisher tagged as noncompliant",
    "fire extinguisher tagged with a white label",
    "fire extinguisher tagged with a yellow label",
    "fire system inspection tag",
    "gas detector",
    "glue laminated heavy timber",
    "heat detector",
    "horn strobe",
    "manual fire alarm box protective cover",
    "missing fire extinguisher",
    "notification audible and visible speaker strobe",
    "pendant fire sprinkler",
    "recessed fire sprinkler",
    "semi-recessed fire sprinkler",
    "sidewall fire sprinkler",
    "single action manual fire alarm box - single pull station",
    "smoke detector with low frequency sounder base",
    "smoke detector with sounder base",
    "speaker strobe",
    "visible notification appliance",
    "wall-mounted fire alarm sounder horn speaker",
]


def run_audit():
    print("=" * 70)
    print("FIRESPHERE.AI PHASE 8C — DATASET 7 DETAILED AUDIT")
    print("=" * 70)

    total_images = 0
    total_labels = 0
    corrupted_images = 0
    empty_label_files = 0
    images_without_labels = 0
    labels_without_images = 0

    class_box_counts = Counter()
    class_image_sets = defaultdict(set)
    split_stats = defaultdict(lambda: {"images": 0, "labels": 0, "boxes": 0})
    invalid_boxes = []

    image_resolutions = Counter()
    image_sha256_map = {}  # sha256 -> list of relative paths
    image_metadata = {}  # rel_path -> metadata

    for s in SPLITS:
        img_dir = os.path.join(AUDIT_DIR, s, "images")
        lbl_dir = os.path.join(AUDIT_DIR, s, "labels")

        imgs = sorted(os.listdir(img_dir)) if os.path.exists(img_dir) else []
        lbls = sorted(os.listdir(lbl_dir)) if os.path.exists(lbl_dir) else []

        split_stats[s]["images"] = len(imgs)
        split_stats[s]["labels"] = len(lbls)
        total_images += len(imgs)
        total_labels += len(lbls)

        img_stems = {os.path.splitext(f)[0]: f for f in imgs}
        lbl_stems = {os.path.splitext(f)[0]: f for f in lbls}

        # Check for uncoupled files
        for stem, img_fname in img_stems.items():
            if stem not in lbl_stems:
                images_without_labels += 1

        for stem, lbl_fname in lbl_stems.items():
            if stem not in img_stems:
                labels_without_images += 1

        # Check images
        for img_fname in imgs:
            img_path = os.path.join(img_dir, img_fname)
            rel_path = f"{s}/images/{img_fname}"
            try:
                with open(img_path, "rb") as f:
                    img_bytes = f.read()
                h = hashlib.sha256(img_bytes).hexdigest()
                image_sha256_map.setdefault(h, []).append(rel_path)

                with Image.open(img_path) as im:
                    im.verify()

                # Reopen to get size/mode
                with Image.open(img_path) as im:
                    w, h_px = im.size
                    mode = im.mode
                    image_resolutions[f"{w}x{h_px}"] += 1
                    image_metadata[rel_path] = {
                        "width": w,
                        "height": h_px,
                        "mode": mode,
                        "sha256": h,
                        "split": s,
                        "filename": img_fname,
                    }
            except Exception as e:
                corrupted_images += 1
                print(f"CORRUPT IMAGE: {rel_path} - {e}")

        # Check labels and boxes
        for lbl_fname in lbls:
            lbl_path = os.path.join(lbl_dir, lbl_fname)
            rel_path = f"{s}/labels/{lbl_fname}"
            stem = os.path.splitext(lbl_fname)[0]
            matching_img = img_stems.get(stem)

            with open(lbl_path, "r", encoding="utf-8") as f:
                lines = [line.strip() for line in f if line.strip()]

            if not lines:
                empty_label_files += 1
                continue

            for line_no, line in enumerate(lines, 1):
                parts = line.split()
                if len(parts) != 5:
                    invalid_boxes.append({
                        "file": rel_path,
                        "line": line_no,
                        "error": "wrong_part_count",
                        "content": line,
                    })
                    continue
                try:
                    cls_id = int(parts[0])
                    xc, yc, w, h_box = map(float, parts[1:])
                except ValueError:
                    invalid_boxes.append({
                        "file": rel_path,
                        "line": line_no,
                        "error": "non_numeric",
                        "content": line,
                    })
                    continue

                if not (0.0 <= xc <= 1.0 and 0.0 <= yc <= 1.0 and 0.0 < w <= 1.0 and 0.0 < h_box <= 1.0):
                    invalid_boxes.append({
                        "file": rel_path,
                        "line": line_no,
                        "error": "out_of_bounds",
                        "content": line,
                    })

                class_box_counts[cls_id] += 1
                split_stats[s]["boxes"] += 1
                if matching_img:
                    class_image_sets[cls_id].add(f"{s}/images/{matching_img}")

    # Canonical dataset duplicate check
    canonical_duplicates = []
    canonical_sha256 = {}
    if os.path.exists(CANONICAL_IMG_DIR):
        print("Checking duplicates against canonical dataset...")
        for c_fname in os.listdir(CANONICAL_IMG_DIR):
            c_path = os.path.join(CANONICAL_IMG_DIR, c_fname)
            try:
                with open(c_path, "rb") as f:
                    c_hash = hashlib.sha256(f.read()).hexdigest()
                    canonical_sha256[c_hash] = c_fname
            except Exception:
                pass

        for d7_hash, d7_paths in image_sha256_map.items():
            if d7_hash in canonical_sha256:
                canonical_duplicates.append({
                    "d7_paths": d7_paths,
                    "canonical_file": canonical_sha256[d7_hash],
                    "sha256": d7_hash,
                })

    # Within-dataset duplicates
    within_duplicates = {h: paths for h, paths in image_sha256_map.items() if len(paths) > 1}

    print("\n--- QUANTITATIVE SUMMARY ---")
    print(f"Total images: {total_images}")
    print(f"Total label files: {total_labels}")
    print(f"Total bounding boxes: {sum(class_box_counts.values())}")
    print(f"Empty label files: {empty_label_files}")
    print(f"Corrupted images: {corrupted_images}")
    print(f"Images without labels: {images_without_labels}")
    print(f"Labels without images: {labels_without_images}")
    print(f"Invalid bounding boxes: {len(invalid_boxes)}")
    print(f"Exact internal duplicates (SHA-256): {len(within_duplicates)}")
    print(f"Duplicates vs Canonical: {len(canonical_duplicates)}")
    print(f"Resolutions: {dict(image_resolutions)}")

    print("\n--- SPLIT STATISTICS ---")
    for s in SPLITS:
        st = split_stats[s]
        print(f"  {s:5s}: images={st['images']:3d}, labels={st['labels']:3d}, boxes={st['boxes']:4d}")

    print("\n--- ALL CLASSES AND BOX COUNTS ---")
    class_summary = []
    for cid in range(len(CLASSES)):
        cname = CLASSES[cid]
        cnt = class_box_counts[cid]
        img_count = len(class_image_sets[cid])
        class_summary.append({
            "class_id": cid,
            "class_name": cname,
            "boxes": cnt,
            "images": img_count,
        })
        if cnt > 0:
            print(f"  Class {cid:2d}: {cname:<52} -> {cnt:4d} boxes in {img_count:3d} images")

    # Specific Target Class 22 analysis
    missing_cid = 22
    missing_boxes = class_box_counts[missing_cid]
    missing_images = sorted(list(class_image_sets[missing_cid]))
    print("\n--- TARGET CANDIDATE CLASS (Class 22: 'missing fire extinguisher') ---")
    print(f"Total bounding boxes: {missing_boxes}")
    print(f"Total unique images: {len(missing_images)}")
    print(f"Images containing missing fire extinguisher: {missing_images}")

    # Class 13 analysis (tagged as noncompliant)
    noncompliant_cid = 13
    noncompliant_boxes = class_box_counts[noncompliant_cid]
    noncompliant_images = sorted(list(class_image_sets[noncompliant_cid]))
    print("\n--- CLASS 13 ('fire extinguisher tagged as noncompliant') ---")
    print(f"Total bounding boxes: {noncompliant_boxes}")
    print(f"Total unique images: {len(noncompliant_images)}")

    return {
        "total_images": total_images,
        "total_labels": total_labels,
        "total_boxes": sum(class_box_counts.values()),
        "corrupted_images": corrupted_images,
        "empty_label_files": empty_label_files,
        "invalid_boxes": invalid_boxes,
        "split_stats": dict(split_stats),
        "class_summary": class_summary,
        "missing_class": {
            "class_id": missing_cid,
            "class_name": CLASSES[missing_cid],
            "boxes": missing_boxes,
            "images_count": len(missing_images),
            "images": missing_images,
        },
        "noncompliant_class": {
            "class_id": noncompliant_cid,
            "class_name": CLASSES[noncompliant_cid],
            "boxes": noncompliant_boxes,
            "images_count": len(noncompliant_images),
            "images": noncompliant_images,
        },
        "within_duplicates_count": len(within_duplicates),
        "canonical_duplicates_count": len(canonical_duplicates),
        "image_resolutions": dict(image_resolutions),
    }


if __name__ == "__main__":
    res = run_audit()
    with open(OUTPUT_JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(res, f, indent=2)
    print(f"\nSaved audit summary JSON to: {OUTPUT_JSON_PATH}")
