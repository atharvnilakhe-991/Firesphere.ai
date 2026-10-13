# FireSphere.ai — Phase 8C: Dataset 7 Semantic Audit for FIRE_EXTINGUISHER_MISSING

**Prompt ID:** `73146`  
**Audit Timestamp:** `2026-10-06`  
**Target Class:** `0: FIRE_EXTINGUISHER_MISSING`  
**Evaluation Mode:** `AUDIT ONLY` (Canonical dataset untouched; no model training; no synthetic relabeling)  
**Evaluator:** Member 4 — AI/ML, Computer Vision & AI Productivity  

---

## 1. Executive Summary & Audit Context

Following the completion of **Phase 8C (Dataset 6 Verification)**, which established 120 usable images and 126 genuine violation annotations for `EMERGENCY_EXIT_OBSTRUCTED` (Class 3), this audit evaluates **Dataset 7** (`My First Project.v6i.yolov8.zip`), downloaded as a candidate source for **Target Class 0 (`FIRE_EXTINGUISHER_MISSING`)**.

### Core Governance & Truth-in-AI Rules Enforced
1. **Canonical Dataset Immutability:** The canonical dataset in `datasets/fire_safety/images/` (6,471 images), `annotations/` (6,471 files), and `splits/` was **NOT modified, altered, or polluted**.
2. **No Automatic Merge:** Dataset 7 was extracted strictly into an isolated audit directory (`datasets/fire_safety/validation/dataset7_audit/`) and is **NOT merged** into canonical data.
3. **Strict Semantic Boundaries:** An image containing a fire extinguisher (even if tagged noncompliant, damaged, or expired) is **NOT** `FIRE_EXTINGUISHER_MISSING`. Absence requires visual proof of non-presence at an expected location.
4. **No Semantic Relabeling:** Classes were audited based on visual ground truth, not class names alone.

---

## 2. Dataset Identification & Provenance (Section 2)

* **Source Filename:** `My First Project.v6i.yolov8.zip`
* **Absolute Path:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai\datasets\My First Project.v6i.yolov8.zip`
* **File Size:** `15,345,661` bytes (~14.63 MB)
* **SHA-256 Checksum:** `4bea67d2855849eecc984eb0f7b1f7af781b0f949d7004e099fa1e4374195928`
* **Source Platform:** Roboflow Universe
* **Workspace / Project:** `patyas-workspace/my-first-project-nqfzv`
* **Export Version:** Version 6 (`v6i`)
* **Export Date:** September 9, 2026
* **License:** `CC BY 4.0` (Creative Commons Attribution 4.0 International)
* **Official URL:** `https://universe.roboflow.com/patyas-workspace/my-first-project-nqfzv/dataset/6`
* **Archive Contents:** 1,735 total items (865 images, 867 text files including `data.yaml`, `README.dataset.txt`, `README.roboflow.txt`)

---

## 3. Structural & YOLO Label Audit (Section 3 & 4)

### 3.1 Split & File Counts
* **Total Discovered Images:** 865 images
* **Total Discovered Label Files:** 865 files
* **Train Split:** 698 images, 698 label files
* **Validation Split:** 94 images, 94 label files
* **Test Split:** 73 images, 73 label files
* **Image Canvas Dimensions:** Standardized to $512 \times 512$ RGB (with letterboxing/fit black edges)
* **Image Integrity:** 100% verified (0 corrupted images; 100% readable via PIL/OpenCV)
* **Coupling:** 0 images without labels; 0 labels without images

### 3.2 Annotation Format Discovery: YOLOv8 Segmentation Polygons
* **Export Format:** The downloaded archive was exported in **YOLOv8 Segmentation format** (`<class_id> <x1> <y1> <x2> <y2> ... <xn> <yn>`), consisting of polygon vertex sequences rather than standard 5-element bounding boxes (`<class_id> <xc> <yc> <w> <h>`).
* **Polygon Coordinate Validation:** All polygon coordinates satisfy $0.0 \le x_i, y_i \le 1.0$. Zero out-of-range coordinates were found.
* **Empty Label Files:** **577 files** (66.7% of the dataset) contain 0 annotations (unannotated background video frames).
* **Total Valid Annotations Across All 34 Classes:** **373 annotations** (4 bounding boxes, 369 polygon masks) distributed across 288 annotated images.

---

## 4. Class Distribution Across Entire Dataset

The dataset defines 34 classes in `data.yaml`. Actual annotations exist for 24 classes:

| Class ID | Class Name | Total Annotations | Polygon / Box Breakdown | Images | Semantic Category |
|---|---|---|---|---|---|
| **0** | `FDC` | 2 | 2 polygons | 2 | Generic equipment |
| **1** | `air sampling smoke detector` | 6 | 6 polygons | 4 | Generic equipment |
| **2** | `audible visible notification appliance - horn strobe` | 2 | 2 polygons | 2 | Generic equipment |
| **3** | `audible visible notification appliance - speaker strobe` | 2 | 2 polygons | 2 | Generic equipment |
| **4** | `ceiling mounted audible visible appliance - horn strobe` | 2 | 2 polygons | 2 | Generic equipment |
| **5** | `double action manual fire alarm box - double pull station` | 25 | 25 polygons | 25 | Generic equipment |
| **6** | `dry barrel sprinkler` | 4 | 4 polygons | 2 | Generic equipment |
| **7** | `exit door` | 2 | 2 polygons | 2 | Generic door |
| **8** | `exit sign` | 20 | 20 polygons | 20 | Generic signage |
| **9** | `exit stair` | 3 | 3 polygons | 3 | Generic architectural |
| **10** | `fire alarm box` | 2 | 2 polygons | 2 | Generic equipment |
| **11** | `fire alarm control panel` | 15 | 15 polygons | 15 | Generic equipment |
| **12** | `fire extinguisher cabinet` | 3 | 3 polygons | 3 | **Cabinet with extinguisher PRESENT** |
| **13** | `fire extinguisher tagged as noncompliant` | 158 | 4 boxes, 154 polygons | 156 | **Extinguisher PRESENT (tagged) + Noise** |
| **14** | `fire extinguisher tagged with a white label` | 11 | 11 polygons | 11 | **Extinguisher PRESENT (tagged)** |
| **15** | `fire extinguisher tagged with a yellow label` | 2 | 2 polygons | 2 | **Extinguisher PRESENT (tagged)** |
| **16** | `fire system inspection tag` | 1 | 1 polygon | 1 | Inspection tag |
| **17** | `gas detector` | 2 | 2 polygons | 2 | Generic equipment |
| **18** | `glue laminated heavy timber` | 2 | 2 polygons | 2 | Architectural beam |
| **19** | `heat detector` | 4 | 4 polygons | 4 | Generic equipment |
| **20** | `horn strobe` | 10 | 10 polygons | 4 | Generic equipment |
| **21** | `manual fire alarm box protective cover` | 2 | 2 boxes | 2 | Protective cover |
| **22** | **`missing fire extinguisher`** | **50** | **50 polygons** | **50** | **GENUINE ABSENCE (Empty red box)** |
| **23–33** | Other detectors, sounders, sprinklers | 39 | 39 polygons | 24 | Generic equipment |

---

## 5. In-Depth Semantic Audit (Section 5, 6, 7)

### 5.1 Audit of Candidate Class 22: `missing fire extinguisher`
* **Total Annotations:** 50 polygon annotations in 50 image files.
* **Visual Inspection Findings:**
  1. **Visual Evidence:** Inspection of unmasked image crops (`outline_0.jpg` and `crop_0.jpg`) proves that the annotations enclose an **empty red plastic fire extinguisher wall-mounting box/cabinet**. The fire extinguisher is physically **absent** from the cradle.
  2. **Semantic Classification:** **Category A: DIRECTLY USABLE (Genuine Physical Absence)**.
  3. **Critical Structural Limitation — Pseudo-Replication / Single Physical Scene:**
     - Further analysis revealed that **all 50 annotations originate from a single video clip**: `Sna54U5CjZHZECx9dyDf_2`.
     - The 50 images represent only **36 sequential video frames** (`mp4-0011` through `mp4-0048`) of the same inspector holding a tablet and walking toward the **exact same red wall box**.
     - Roboflow's augmentation pipeline (random rotation $\pm 15^\circ$) expanded these 36 frames into 50 images.
     - **Unique physical missing-extinguisher scenes = 1**.
     - Training an object detection model on 50 frames of a single physical box would result in severe overfitting to that specific red plastic box and lighting condition.

### 5.2 Audit of Class 13: `fire extinguisher tagged as noncompliant`
* **Total Annotations:** 158 annotations across 156 images.
* **Visual Inspection Findings:**
  1. **Equipment Presence:** Representative visual review (`noncomp_02`) confirms that a red fire extinguisher is physically **PRESENT** inside the cabinet with an orange inspection/maintenance tag attached to the cylinder collar.
  2. **Semantic Rule Enforcement:** A tagged, present fire extinguisher is **NOT** `FIRE_EXTINGUISHER_MISSING`. Mapping this class to Class 0 would train the model to falsely detect present extinguishers as missing.
  3. **Severe Annotation Noise:** Inspection of image `PXL_20260312_202120291` revealed that a ceiling structural wooden roof beam (`glue laminated heavy timber`) was also erroneously annotated as Class 13.
  4. **Semantic Classification:** **Category C: NOT USABLE FOR FIRE_EXTINGUISHER_MISSING**.

### 5.3 Audit of Class 12: `fire extinguisher cabinet`
* **Visual Inspection Findings:** Visual review (`cabinet_0` and `cabinet_2`) confirms that the cabinets depicted contain fire extinguishers **present** inside the glass door. They are **NOT** missing.
* **Semantic Classification:** **Category C: NOT USABLE FOR FIRE_EXTINGUISHER_MISSING**.

### 5.4 Audit of Class 14 & 15: `tagged with white/yellow label`
* **Visual Inspection Findings:** Depict physically present extinguishers with inspection tags.
* **Semantic Classification:** **Category C: NOT USABLE FOR FIRE_EXTINGUISHER_MISSING**.

---

## 6. Duplicate & Data Leakage Audit (Section 8 & 9)

* **Cross-Dataset Leakage (vs. Canonical Dataset):**
  - All 865 images were checked by SHA-256 against the 6,471 canonical images.
  - **Result: 0 duplicate images found.** Zero cross-dataset leakage.
* **Internal Exact Duplicates:** 13 SHA-256 duplicate image pairs within Dataset 7 (identical frames exported across splits or augmentations).
* **Scene Redundancy:** 100% of the Class 22 violation samples derive from a single video clip (`Sna54U5CjZHZECx9dyDf_2`).

---

## 7. Metrics & Final Evaluation (Section 10)

Per Prompt Section 10 requirements:

| Metric | Value | Verification Notes |
|---|---|---|
| **targetClass** | `FIRE_EXTINGUISHER_MISSING` | Target Class 0 |
| **verifiedDirectViolationImages** | **50** | 50 images contain genuine empty wall box |
| **verifiedDirectViolationAnnotations** | **50** | 50 polygon annotations of empty wall box |
| **ambiguousImages** | **0** | Verified: absence is genuine in 50; presence is genuine in remainder |
| **nonTargetImages** | **815** | 156 tagged present, 16 other present, 66 generic equipment, 577 empty |
| **usablePercentage** | **5.78%** | $50 / 865 = 5.78\%$ (or $\frac{1 \text{ unique scene}}{519} \approx 0.19\%$) |
| **Unique Physical Violation Scenes** | **1** | Single video clip (`Sna54U5CjZHZECx9dyDf_2`) |

---

## 8. Final Decision & Gate Assessment (Section 10 & 11)

### Official Dataset 7 Decision:
```text
DATASET_7_PARTIALLY_USABLE
```

### Justification:
1. **Why partially usable (and not NOT_USABLE):**
   The 50 images in Class 22 genuinely depict an authentic empty fire extinguisher wall cabinet (physical non-compliance). The annotations are accurate, clean, and represent real-world absence.
2. **Why NOT fully usable:**
   - Yield is only 5.78% (50 of 865 images).
   - Zero scene diversity: All 50 images originate from **1 single video recording** (pseudo-replication).
   - Annotation format is YOLOv8 Segmentation polygons, requiring polygon-to-bounding-box conversion before YOLO object detection ingestion.
   - It cannot serve as a viable standalone training dataset for `FIRE_EXTINGUISHER_MISSING`.

### Phase 9 Gate Status:
* **May Dataset 7 proceed to canonical merge?** **NO.** (Merging 50 frames of a single video scene would bias the canonical dataset).
* **Does Dataset 7 change the Phase 9 gate?** **NO.**
* **Overall Pipeline Status:** **`DATASET_PARTIALLY_READY`** (`isReadyForPhase9 = false`).
* **Phase 9 Model Training:** **STRICTLY BLOCKED.**

---

## 9. Test Safety & Environment Validation (Section 13)

* **Canonical Dataset Protection:** Verified that `datasets/fire_safety/images/` contains exactly **6,471 images** and `annotations/` contains exactly **6,471 files**. Zero files added, modified, or deleted.
* **Test Suite:** Executed [`tests/test_phase8c_dataset6_verification.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/tests/test_phase8c_dataset6_verification.py) — **3 passed, 0 failed**.
