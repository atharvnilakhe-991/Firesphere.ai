"""FireSphere.ai Phase 8C - Dataset 7 Semantic Inspection & Visual Audit.

Inspects all candidate annotations for:
- Class 22: 'missing fire extinguisher'
- Class 13: 'fire extinguisher tagged as noncompliant'
- Class 14: 'fire extinguisher tagged with a white label'
- Class 12: 'fire extinguisher cabinet'

Draws overlays, checks unique physical scenes, analyzes visual content,
and generates contact sheets for representative inspection.
"""

import glob
import json
import os
import re
from collections import Counter, defaultdict
import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

BASE_DIR = r"C:\Users\athar\OneDrive\Desktop\Firesphere.ai\datasets\fire_safety\validation\dataset7_audit"
VISUAL_DIR = os.path.join(BASE_DIR, "visual_audit")
os.makedirs(VISUAL_DIR, exist_ok=True)
os.makedirs(os.path.join(VISUAL_DIR, "missing_extinguisher"), exist_ok=True)
os.makedirs(os.path.join(VISUAL_DIR, "tagged_noncompliant"), exist_ok=True)
os.makedirs(os.path.join(VISUAL_DIR, "extinguisher_cabinet"), exist_ok=True)

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


def extract_base_scene(fname):
    """Strip Roboflow UUID augmentation suffix."""
    # Pattern: <original_name>.rf.<uuid>.<ext> or <original_name>_jpeg.rf.<uuid>.<ext>
    m = re.match(r"^(.*?)(?:_jpeg|_jpg|_png)?\.rf\.[a-f0-9]{32}\.(?:jpg|jpeg|png)$", fname, re.IGNORECASE)
    if m:
        return m.group(1)
    return os.path.splitext(fname)[0]


def inspect_semantics():
    print("=" * 70)
    print("INSPECTING DATASET 7 SEMANTICS")
    print("=" * 70)

    # 1. Collect all annotations
    all_annotations = []
    for split in ["train", "valid", "test"]:
        lbl_dir = os.path.join(BASE_DIR, split, "labels")
        img_dir = os.path.join(BASE_DIR, split, "images")
        if not os.path.exists(lbl_dir):
            continue
        for lfile in sorted(os.listdir(lbl_dir)):
            if not lfile.endswith(".txt"):
                continue
            lpath = os.path.join(lbl_dir, lfile)
            stem = os.path.splitext(lfile)[0]
            img_matches = glob.glob(os.path.join(img_dir, stem + ".*"))
            if not img_matches:
                continue
            img_path = img_matches[0]

            with open(lpath, "r", encoding="utf-8") as f:
                for line_idx, line in enumerate(f):
                    parts = line.strip().split()
                    if not parts:
                        continue
                    cid = int(parts[0])
                    coords = [float(x) for x in parts[1:]]
                    is_poly = len(coords) > 4
                    if is_poly:
                        xs = coords[0::2]
                        ys = coords[1::2]
                        bbox = [min(xs), min(ys), max(xs), max(ys)]
                    else:
                        xc, yc, w, h = coords
                        bbox = [xc - w / 2, yc - h / 2, xc + w / 2, yc + h / 2]

                    all_annotations.append({
                        "split": split,
                        "label_file": lfile,
                        "img_path": img_path,
                        "img_filename": os.path.basename(img_path),
                        "base_scene": extract_base_scene(os.path.basename(img_path)),
                        "class_id": cid,
                        "class_name": CLASSES[cid] if cid < len(CLASSES) else f"unknown_{cid}",
                        "is_polygon": is_poly,
                        "coords": coords,
                        "bbox": bbox,
                        "line_idx": line_idx,
                    })

    # Group by class
    by_class = defaultdict(list)
    for ann in all_annotations:
        by_class[ann["class_id"]].append(ann)

    print("\nAnnotation counts by class:")
    for cid in sorted(by_class.keys()):
        print(f"  Class {cid:2d} ({CLASSES[cid]}): {len(by_class[cid])} annotations in {len(set(a['img_filename'] for a in by_class[cid]))} images")

    # 2. Detailed audit of Class 22: 'missing fire extinguisher'
    missing_anns = by_class[22]
    missing_images = sorted(list(set(a["img_filename"] for a in missing_anns)))
    missing_base_scenes = sorted(list(set(a["base_scene"] for a in missing_anns)))

    print("\n" + "=" * 70)
    print("AUDIT OF CLASS 22: 'missing fire extinguisher'")
    print("=" * 70)
    print(f"Total annotations: {len(missing_anns)}")
    print(f"Total images containing class 22: {len(missing_images)}")
    print(f"Total UNIQUE physical scenes (base images): {len(missing_base_scenes)}")
    print("Base physical scenes:", missing_base_scenes)

    # Render visualizations of Class 22
    rendered_missing = []
    for idx, ann in enumerate(missing_anns):
        img = cv2.imread(ann["img_path"])
        if img is None:
            continue
        h_img, w_img = img.shape[:2]
        vis = img.copy()

        # Draw polygon or box
        coords = ann["coords"]
        if ann["is_polygon"]:
            pts = np.array([[int(x * w_img), int(y * h_img)] for x, y in zip(coords[0::2], coords[1::2])], np.int32)
            pts = pts.reshape((-1, 1, 2))
            cv2.polylines(vis, [pts], isClosed=True, color=(0, 0, 255), thickness=2)
            cv2.fillPoly(vis, [pts], color=(0, 0, 255, 60))
        else:
            x1 = int(ann["bbox"][0] * w_img)
            y1 = int(ann["bbox"][1] * h_img)
            x2 = int(ann["bbox"][2] * w_img)
            y2 = int(ann["bbox"][3] * h_img)
            cv2.rectangle(vis, (x1, y1), (x2, y2), (0, 0, 255), 2)

        # Draw label
        cv2.putText(
            vis,
            f"MISSING_EXT: {ann['base_scene']}",
            (10, 25),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            (0, 0, 255),
            2,
        )

        out_name = f"missing_{idx:02d}_{ann['base_scene']}_{ann['split']}.jpg"
        out_path = os.path.join(VISUAL_DIR, "missing_extinguisher", out_name)
        cv2.imwrite(out_path, vis)
        rendered_missing.append(out_path)

    print(f"Rendered {len(rendered_missing)} visual audit images to: {VISUAL_DIR}/missing_extinguisher/")

    # 3. Detailed audit of Class 13: 'fire extinguisher tagged as noncompliant'
    noncomp_anns = by_class[13]
    noncomp_images = sorted(list(set(a["img_filename"] for a in noncomp_anns)))
    noncomp_base_scenes = sorted(list(set(a["base_scene"] for a in noncomp_anns)))

    print("\n" + "=" * 70)
    print("AUDIT OF CLASS 13: 'fire extinguisher tagged as noncompliant'")
    print("=" * 70)
    print(f"Total annotations: {len(noncomp_anns)}")
    print(f"Total images containing class 13: {len(noncomp_images)}")
    print(f"Total UNIQUE physical scenes (base images): {len(noncomp_base_scenes)}")

    # Render 10 representative samples of Class 13
    rendered_noncomp = []
    for idx, ann in enumerate(noncomp_anns[:15]):
        img = cv2.imread(ann["img_path"])
        if img is None:
            continue
        h_img, w_img = img.shape[:2]
        vis = img.copy()

        coords = ann["coords"]
        if ann["is_polygon"]:
            pts = np.array([[int(x * w_img), int(y * h_img)] for x, y in zip(coords[0::2], coords[1::2])], np.int32)
            pts = pts.reshape((-1, 1, 2))
            cv2.polylines(vis, [pts], isClosed=True, color=(0, 255, 255), thickness=2)
        else:
            x1 = int(ann["bbox"][0] * w_img)
            y1 = int(ann["bbox"][1] * h_img)
            x2 = int(ann["bbox"][2] * w_img)
            y2 = int(ann["bbox"][3] * h_img)
            cv2.rectangle(vis, (x1, y1), (x2, y2), (0, 255, 255), 2)

        cv2.putText(
            vis,
            f"TAGGED_NONCOMPLIANT: {ann['base_scene']}",
            (10, 25),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            (0, 255, 255),
            2,
        )

        out_name = f"noncomp_{idx:02d}_{ann['base_scene']}_{ann['split']}.jpg"
        out_path = os.path.join(VISUAL_DIR, "tagged_noncompliant", out_name)
        cv2.imwrite(out_path, vis)
        rendered_noncomp.append(out_path)

    print(f"Rendered {len(rendered_noncomp)} visual audit images to: {VISUAL_DIR}/tagged_noncompliant/")

    # 4. Create Contact Sheet of Class 22 samples
    # Grid of up to 16 samples (4x4)
    sample_imgs = []
    for p in rendered_missing[:16]:
        im = cv2.imread(p)
        if im is not None:
            im = cv2.resize(im, (256, 256))
            sample_imgs.append(im)

    if sample_imgs:
        # Pad to 16 if needed
        while len(sample_imgs) < 16:
            sample_imgs.append(np.zeros((256, 256, 3), dtype=np.uint8))
        row1 = np.hstack(sample_imgs[0:4])
        row2 = np.hstack(sample_imgs[4:8])
        row3 = np.hstack(sample_imgs[8:12])
        row4 = np.hstack(sample_imgs[12:16])
        grid = np.vstack([row1, row2, row3, row4])
        grid_path = os.path.join(VISUAL_DIR, "contact_sheet_missing_extinguisher.jpg")
        cv2.imwrite(grid_path, grid)
        print(f"Generated 4x4 contact sheet: {grid_path}")

    return {
        "missing_annotations_count": len(missing_anns),
        "missing_images_count": len(missing_images),
        "missing_base_scenes": missing_base_scenes,
        "noncompliant_annotations_count": len(noncomp_anns),
        "noncompliant_images_count": len(noncomp_images),
        "noncompliant_base_scenes_count": len(noncomp_base_scenes),
    }


if __name__ == "__main__":
    inspect_semantics()
