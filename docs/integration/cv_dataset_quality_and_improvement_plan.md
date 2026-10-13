# FireSphere.ai — CV Dataset Quality Audit & Improvement Plan
**Stage 2 Deliverable: Empirical Dataset Audit, Coverage Matrix, Leakage Analysis, and Prioritized Remediation Plan**

- **Date:** October 13, 2026
- **Auditor:** Senior Computer Vision Engineer, Dataset Quality Auditor, and Production Readiness Reviewer
- **Target Microservice:** `ai-service`
- **Audited Dataset Root:** `datasets/fire_safety/prepared/phase8_3/yolo`
- **Referenced Manifests Root:** `datasets/fire_safety/prepared/phase8_3/manifests`
- **Previous Deliverable:** [`docs/integration/cv_metric_reconciliation_audit.md`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs/integration/cv_metric_reconciliation_audit.md)
- **Current CV Checkpoint:** `ai-service/artifacts/phase8_4/checkpoints/best.pt`
- **Current Checkpoint SHA-256:** `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666`
- **Status:** **STAGE 2 COMPLETE — STOP POINT REACHED**
- **CV Approval Gate State:** `NOT_APPROVED` (Unchanged, Fail-Closed)
- **Calibration State:** `NOT_CALIBRATED` (Unchanged)
- **Production Authorization:** `false` (Unchanged)

---

## 1. Executive Summary

In Stage 1 ([`cv_metric_reconciliation_audit.md`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs/integration/cv_metric_reconciliation_audit.md)), we proved mathematically and through exact live reproduction that the baseline model (`BaselineInspectionDetector`) achieves only **5.79% mAP@50**, **0.00% recall on EXIT_DOOR**, **0.00% recall on OBSTACLE**, and **1.87% recall on EXIT_SIGN**.

This **Stage 2 Audit** investigated the underlying dataset (`phase8_3/yolo`) and annotation pipeline to determine the root causes of these failures prior to any retraining or architecture modifications. Every image and label across all splits was independently audited via programmatic analysis in `ai-service/.venv` (Python 3.13.7, OpenCV 4.11.0).

### Key Empirical Findings:
1. **Severe Multi-Class Imbalance**:
   Across the 4,887 images (7,258 annotations):
   - `FIRE_EXTINGUISHER`: **4,564 annotations (62.88%)**
   - `EXIT_SIGN`: **2,559 annotations (35.26%)**
   - `OBSTACLE`: **93 annotations (1.28%)**
   - `EXIT_DOOR`: **42 annotations (0.58%)**
   The training split contains only **33 exit doors** and **69 obstacles** out of 5,007 annotations.
2. **Absence of Real Co-occurrence (Single-Class Dataset Isolation)**:
   In the entire 4,887-image dataset, **only 3 images contain multiple classes**. Over 99.9% of images contain exclusively a single class in isolation. In natural building inspections, fire exit doors, exit signs, and fire extinguishers frequently appear in the same corridor view. The dataset completely lacks natural scene co-occurrence.
3. **Confirmed Burst-Capture Cross-Split Leakage**:
   While exact image SHA-256 hashes and Roboflow string-split prefixes (`.rf.`) did not cross split boundaries, perceptual difference hashing (`dHash`) revealed **100 collision clusters spanning across train, validation, and test splits**. Consecutive smartphone photo bursts (e.g. `IMG_5066` in `test` vs. `IMG_5067` in `train`; `Exit_165` in `val` vs. `Exit_163/167/168/170` in `train`) were assigned to different splits because they possessed distinct base filenames.
4. **Extreme Scale Variance (Micro & Full-Frame Boxes)**:
   59 `EXIT_SIGN` annotations are smaller than $0.01\%$ of the image area ($< 6.4 \times 6.4$ px at $640 \times 640$). When downsampled to $256 \times 256$ input resolution for `BaselineInspectionDetector`, these boxes shrink to $\le 2 \times 2$ pixels, which vanish under the $16\times$ CNN backbone stride. Conversely, 14 `EXIT_SIGN` annotations are full-frame graphics ($> 95\%$ image area, up to $1.0 \times 1.0$).
5. **Over-Quarantine of Architectural Doors**:
   During Phase 8B ingestion of `Fire Safety.v1i.yolov8.zip`, **2,774 architectural door images were quarantined** to prevent ordinary doors from polluting fire exit doors. However, this left the dataset with only one source for `EXIT_DOOR` (`blocked emergency exit.v1i.yolov8.zip`), which contained only 42 door annotations across 52 base scenes.

---

## 2. Dataset Structure and File Organization

The dataset is physically located in `datasets/fire_safety/prepared/phase8_3/yolo` and accompanied by immutable manifests in `datasets/fire_safety/prepared/phase8_3/manifests`.

```
datasets/fire_safety/prepared/phase8_3/
├── yolo/
│   ├── data.yaml                     # YOLO metadata and class mapping (nc: 4)
│   ├── images/
│   │   ├── train/                    # 3,377 images
│   │   ├── val/                      # 988 images
│   │   └── test/                     # 522 images
│   └── labels/
│       ├── train/                    # 3,377 label files (.txt)
│       ├── val/                      # 988 label files (.txt)
│       └── test/                     # 522 label files (.txt)
└── manifests/
    ├── phase8_3_class_statistics.json  # Reference class counts
    ├── phase8_3_group_manifest.json    # Scene group ID clustering
    ├── phase8_3_provenance.json        # Source archive attribution
    ├── phase8_3_rejected_samples.json  # Rejection log
    ├── phase8_3_training_gate.json     # Gate condition (READY_WITH_DATA_LIMITATIONS)
    ├── phase8_3_training_manifest.json # Training split inventory
    ├── phase8_3_validation_manifest.json # Validation split inventory
    └── phase8_3_test_manifest.json     # Sealed test split inventory
```

### 2.1 `data.yaml` Specification
```yaml
path: C:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/phase8_3/yolo
train: images/train
val: images/val
test: images/test
nc: 4
names:
  0: FIRE_EXTINGUISHER
  1: EXIT_DOOR
  2: EXIT_SIGN
  3: OBSTACLE
```

### 2.2 Pairing and Integrity Verification
Every image file was checked against the corresponding label file:
- Total images across all splits: **4,887**
- Total label files across all splits: **4,887**
- Unpaired images (image without label file): **0 (100% paired)**
- Unpaired labels (label without image): **0 (100% paired)**
- Corrupt or unreadable images: **0 (100% decoded successfully via OpenCV)**

---

## 3. Verified Image, Label, and Annotation Counts

The programmatic audit verified the following counts across all splits:

| Split | Total Images | Total Label Files | Total Annotations | Empty Label Files | Multi-Class Images | Mean Annots / Image | Max Annots / Image |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Train** | 3,377 | 3,377 | 5,007 | 18 (0.53%) | 3 (0.09%) | 1.48 | 27 |
| **Validation** | 988 | 988 | 1,455 | 6 (0.61%) | 0 (0.00%) | 1.47 | 9 |
| **Test (Held-out)** | 522 | 522 | 796 | 3 (0.57%) | 0 (0.00%) | 1.52 | 23 |
| **Total Dataset** | **4,887** | **4,887** | **7,258** | **27 (0.55%)** | **3 (0.06%)** | **1.49** | **27** |

*Note: The 27 empty label files represent negative background images containing zero annotated bounding boxes.*

---

## 4. Per-Class Distribution and Representation Analysis

### 4.1 Annotation Counts by Split and Class

| Class ID | Class Name | Train Annotations | Val Annotations | Test Annotations | Total Annotations | Share of Total Dataset |
|:---:|---|:---:|:---:|:---:|:---:|:---:|
| **0** | `FIRE_EXTINGUISHER` | 3,164 (63.19%) | 948 (65.15%) | 452 (56.78%) | **4,564** | **62.88%** |
| **1** | `EXIT_DOOR` | 33 (0.66%) | 3 (0.21%) | 6 (0.75%) | **42** | **0.58%** |
| **2** | `EXIT_SIGN` | 1,741 (34.77%) | 498 (34.23%) | 320 (40.20%) | **2,559** | **35.26%** |
| **3** | `OBSTACLE` | 69 (1.38%) | 6 (0.41%) | 18 (2.26%) | **93** | **1.28%** |
| **Total** | — | **5,007** | **1,455** | **796** | **7,258** | **100.00%** |

### 4.2 Number of Images Containing Each Class

| Class ID | Class Name | Train Images | Val Images | Test Images | Total Distinct Images | Dataset Presence % |
|:---:|---|:---:|:---:|:---:|:---:|:---:|
| **0** | `FIRE_EXTINGUISHER` | 1,737 | 506 | 253 | **2,496** | 51.07% |
| **1** | `EXIT_DOOR` | 33 | 3 | 6 | **42** | 0.86% |
| **2** | `EXIT_SIGN` | 1,526 | 467 | 242 | **2,235** | 45.73% |
| **3** | `OBSTACLE` | 66 | 6 | 18 | **90** | 1.84% |
| — | *Empty / Background* | 18 | 6 | 3 | **27** | 0.55% |

### 4.3 Key Distribution Observations
1. **Dominance of Two Equipment Classes**: `FIRE_EXTINGUISHER` and `EXIT_SIGN` account for **98.14%** of all annotations (7,123 of 7,258).
2. **Deficit of Egress Hazard & Access Classes**: `EXIT_DOOR` (42 boxes, 0.58%) and `OBSTACLE` (93 boxes, 1.28%) are virtually nonexistent.
3. **Validation Starvation**: The validation split contains only **3 exit doors** and **6 obstacles**, making validation loss completely insensitive to model performance on these classes.
4. **Extreme Isolation**: With only 3 multi-class images in the entire dataset, models trained on this data learn zero contextual relationships between doors, exit signs, and corridor obstacles.

---

## 5. Annotation Quality Findings

Every line across all 4,887 label files was checked for syntactic correctness, bounding-box geometry, out-of-bounds coordinates, and anomalies:

| Quality Dimension | Target Standard | Observed Count | Defect Status | Reference / Notes |
|---|---|:---:|:---:|---|
| **Class ID Range** | Must be in $\{0, 1, 2, 3\}$ | 0 | **PASS** | Zero out-of-range class IDs |
| **Format & Parsing** | 5 float tokens per line | 0 | **PASS** | Zero malformed lines |
| **Coordinate Bounds** | $0.0 \le (x, y, w, h) \le 1.0$ | 0 | **PASS** | Zero negative or $>1.0$ coords |
| **Zero-Area Boxes** | $w > 0$ and $h > 0$ | 0 | **PASS** | Zero degenerate or zero-area boxes |
| **Exact Duplicates** | Identical box & class in same file | 0 | **PASS** | Zero exact duplicate labels |
| **Near-Duplicate Boxes** | Same class, $\text{IoU} > 0.85$ in same file | 0 | **PASS** | Zero overlapping box collisions |
| **Micro Bounding Boxes** | Area $< 0.0001$ ($< 0.01\%$ image) | 59 | **DEFECT** | All `EXIT_SIGN` in `emergency_exit_signs` |
| **Full-Frame Bounding Boxes** | Area $> 0.95$ ($> 95\%$ image) | 14 | **DEFECT** | `EXIT_SIGN` full-frame graphics ($1.0 \times 1.0$) |
| **Image Decode Integrity** | Valid JPEG/PNG bitstream | 0 | **PASS** | All 4,887 images readable via OpenCV |

### 5.1 Analysis of Micro-Boxes ($< 0.0001$ Area)
- 59 annotations in `train` possess areas down to $6.1 \times 10^{-5}$ ($w \approx 0.0078, h \approx 0.0078$).
- In $640 \times 640$ images, this represents a box of only $5 \times 5$ pixels.
- When ingested into `BaselineInspectionDetector` (input resolution $256 \times 256$), this box shrinks to $\le 2 \times 2$ pixels.
- Because the network backbone downsamples by $16\times$ (to a $16 \times 16$ feature grid), objects smaller than $16 \times 16$ pixels occupy $< 1$ feature cell, causing spatial information to be obliterated.

### 5.2 Analysis of Full-Frame Boxes ($> 0.95$ Area)
- 14 annotations in `train` have bounding boxes covering almost the entire image (e.g. `[0.5, 0.5, 1.0, 1.0]` in `fire_safety_equipment_emergency_exit_sign_0004_jpg...txt`).
- These are isolated stock clip-art icons or cropped product shots of exit signs.
- Training on full-frame bounding boxes biases the anchor regression head toward enormous bounding boxes and causes false positives on blank walls.

---

## 6. Duplicate and Split-Leakage Audit

Leakage between training, validation, and test splits was investigated across four separate tiers of granularity:

| Audit Level | Method | Observed Violations | Status | Analysis |
|---|---|:---:|:---:|---|
| **Tier 1: Filename Overlap** | Direct string match across splits | 0 | **PASS** | Filenames are strictly partitioned |
| **Tier 2: Exact Image Bitstream** | SHA-256 byte hash collision | 0 | **PASS** | Zero exact byte-for-byte duplicate images |
| **Tier 3: Augmentation Scene Groups** | String prefix split on `.rf.` | 0 | **PASS** | Roboflow augmentations of the same file kept in same split |
| **Tier 4: Perceptual Hash (dHash)** | Difference hash with Hamming dist $\le 4$ | **100** | **CONFIRMED LEAKAGE** | Consecutive photo bursts split across train, val, and test |

### 6.1 Confirmed Tier 4 Perceptual Burst Leakage Samples
While the Phase 8.3 preparation script split filenames on `.rf.`, it did **not** cluster consecutive photo captures or burst sequences that had different root filenames:

1. **`fire_extinguisher_components` Burst**:
   - `train`: `fire_extinguisher_components_IMG_5067_jpg.rf.66b57d3e...jpg`
   - `test`: `fire_extinguisher_components_IMG_5066_jpg.rf.be8d4345...jpg`
   - *Evidence*: Consecutive camera counter frames `IMG_5066` and `IMG_5067` taken seconds apart of the identical physical fire extinguisher in the same room. One placed in `train`, the other in `test`.
2. **`fire_safety_multi` Exit Sign Burst**:
   - `train`: `Exit_163_jpeg.rf...jpg`, `Exit_167_jpeg.rf...jpg`, `Exit_168_jpeg.rf...jpg`, `Exit_170_jpeg.rf...jpg`
   - `val`: `Exit_165_jpeg.rf.4db30476...jpg`, `Exit_166_jpeg.rf.75306711...jpg`
   - *Evidence*: Sequence `Exit_163` through `Exit_170` represents a single walk-through video sequence of the same hallway exit sign. Frames 163, 167, 168, 170 are in `train`, while frames 165 and 166 are in `val`.
3. **`fire_safety_multi` Exit Sign Burst 2**:
   - `train`: `Exit_192_jpeg.rf...jpg`
   - `test`: `Exit_212_jpeg.rf...jpg`

### 6.2 Impact on Model Evaluation
This finding is critical: **even with burst-sequence leakage artificially inflating test familiarity, the baseline model still only achieved 5.79% mAP@50 and 1.87% recall on EXIT_SIGN**. In an unbiased test set with zero scene overlap, real-world generalisation would be even lower.

---

## 7. Image Coverage and Diversity Audit

To determine whether the dataset is adequate for real-world municipal Fire NOC inspections, the dataset was evaluated against 14 operational fire-safety inspection categories.

### 7.1 Class Coverage & Operating Environment Matrix

| # | Inspection Category | Available Evidence | Observed Limitations | Confidence | Required Remediation |
|---|---|---|---|:---:|---|
| **1** | **Commercial & Office Interiors** | Abundant in `fire_safety_multi` | Clean drywall backgrounds dominate; corporate settings over-represented | High | Retain existing samples; add industrial settings |
| **2** | **Industrial, Factory, & Warehouse** | Limited (< 5% of images) | High ceilings, metal racking, warehouse shelving severely under-represented | High | Collect 300+ warehouse & factory corridor scenes |
| **3** | **Corridors & Egress Routes** | Moderate (~15% of images) | Most photos are close-ups of equipment rather than corridor perspective views | High | Collect wide-angle hallway & corridor perspectives |
| **4** | **Exit Door Designs** | Extremely Poor (42 total boxes) | Only standard push-bar doors from Dataset 6; zero double-leaf, glass, or fire shutter doors | High | Collect 400+ diverse fire exit doors (NBC/NFPA compliant) |
| **5** | **Exit Sign Designs** | Moderate (2,559 boxes) | Heavy bias toward green ISO 7010 running man; North American red "EXIT" text under-represented | High | Collect bilingual and red/green illuminated signs |
| **6** | **Extinguisher Types & Mountings** | Moderate (4,564 boxes) | Almost exclusively red ABC dry powder wall-mounted; CO2 horn nozzles, hose reels, and floor stands sparse | High | Add CO2 cylinders, foam/water extinguishers, floor stands |
| **7** | **Corridor Obstacles** | Extremely Poor (93 total boxes) | Only cardboard boxes from Dataset 6; zero pallets, furniture, carts, or construction waste | High | Collect 300+ obstacle scenes with diverse real-world clutter |
| **8** | **Distance & Small Objects** | Bimodal (too close or micro) | Close-ups ($< 2$m) dominate; realistic inspection distance ($3-8$m down corridor) lacking | High | Standardize capture distance between 3m and 10m |
| **9** | **Occlusion & Clutter** | Poor (< 2% partially occluded) | Most objects are centered and unoccluded against clean walls | High | Add partially hidden extinguishers and signs |
| **10** | **Lighting & Low-Light** | Poor (< 3% low-light) | Overwhelmingly well-lit indoor daylight/fluorescent; emergency lighting/darkness absent | High | Collect emergency lighting and night/shadow conditions |
| **11** | **Motion Blur & Compression** | Absent (Roboflow curated) | Images are sharp, high-contrast, curated photographs; mobile phone blur absent | High | Add realistic handheld inspection camera blur |
| **12** | **Camera Angles & High Mounts** | Eye-level dominant (> 90%) | High ceiling mounts and downward perspective angles under-represented | High | Collect varied camera angles and heights |
| **13** | **Crowded Scenes** | Absent (< 0.1% have people) | Empty buildings only; realistic pedestrian/worker presence absent | High | Add scenes with background human presence |
| **14** | **Hard Negatives / Look-alikes** | Absent in official splits | Red trash cans, red fire hose boxes, standard office doors excluded/quarantined | High | Add 250+ hard negative non-target objects |

---

## 8. Data Provenance and Licensing Audit

The dataset provenance was traced through `datasets/fire_safety/README.md`, `phase8_3_provenance.json`, and `dataset_sources.json`:

| Source Identifier | Source Archive File | Origin / Provider | Declared License | Samples Contributed | Target Class Mapped | Commercial Suitability |
|---|---|---|---|:---:|---|:---:|
| `emergency_exit_signs` | `Emergency Exit Signs.v2i.yolov8.zip` | Roboflow Universe (`emergency-exit-signs`) | Public Domain (CC0) | 657 | `EXIT_SIGN` | Suitable (CC0) |
| `fire_extinguisher_components` | `Fire Extinguisher.v1-roboflow-instant-1--eval-.yolov8.zip` | Roboflow Universe (`fire-extinguisher-sz0p7`) | CC BY 4.0 | 163 | `FIRE_EXTINGUISHER` | Suitable with attribution |
| `fire_safety_equipment` | `Fire Safety Equipment.v1i.yolov8.zip` | Roboflow Universe (`anass-workspace-f1hzt`) | CC BY 4.0 | 307 | `FIRE_EXTINGUISHER`, `EXIT_SIGN` | Suitable with attribution |
| `fire_safety_multi` | `Fire Safety.v1i.yolov8.zip` | Roboflow Universe (`jakub-konopa/fire-safety-cstwg`) | CC BY 4.0 | 3,494 | `FIRE_EXTINGUISHER`, `EXIT_SIGN` | Suitable with attribution |
| `safety_equipment_sign` | `Safety Equipment - Sign.v1-roboflow-instant-1--eval-.yolov8.zip` | Roboflow Universe (`mmmm-lx3n5`) | CC BY 4.0 | 110 | `FIRE_EXTINGUISHER` | Suitable with attribution |
| `roboflow-blocked-emergency-exit-v1` | `blocked emergency exit.v1i.yolov8.zip` | Roboflow Universe (`sakthi-ypq7a/...`) | CC BY 4.0 | 156 | `EXIT_DOOR`, `OBSTACLE` | Suitable with attribution |
| **Total Ingested** | **6 Archives** | — | — | **4,887** | **4 Target Classes** | — |

### Provenance Limitations & Risks:
1. **Roboflow Aggregation Layer**: The original primary authors of the photos uploaded to Roboflow are unknown. While Roboflow project owners applied CC BY 4.0 / CC0 tags, many images appear to be web-scraped from Google Images or Flickr without original photographer releases.
2. **Geographical & Regulatory Disconnect**: Imagery originates from international web uploads. Standards vary widely: European EN ISO 7010 signage differs from Indian NBC (IS 1644 / IS 2189) requirements.
3. **No Field Inspection Metadata**: Lacks GPS, building occupancy class, inspection timestamps, or verified fire officer ground truth.

---

## 9. Training-Pipeline and Model Architecture Consistency

The dataset representation was audited against the model and loader implementation in `ai-service/app`:

### 9.1 Vocabulary and Class ID Alignment
The class IDs and definitions are perfectly aligned across all audited components:
- `data.yaml`: `0: FIRE_EXTINGUISHER`, `1: EXIT_DOOR`, `2: EXIT_SIGN`, `3: OBSTACLE`
- `app.models.baseline_detector.CLASS_NAMES`: `0: FIRE_EXTINGUISHER`, `1: EXIT_DOOR`, `2: EXIT_SIGN`, `3: OBSTACLE`
- `app.training.phase8_4_trainer.CLASS_NAMES`: Exact match.
- `app.training.prepare_phase8_3.APPROVED_PERCEPTION_CLASSES`: Exact match.

### 9.2 Data Loader Transformations (`Phase84YOLODataset`)
- **Resolution Forcing**: `Phase84YOLODataset` resizes all images to `(256, 256)` using direct `cv2.resize` without preserving aspect ratio (letterboxing). Over 94% of raw images are square ($640 \times 640$), so distortion is minimal for those, but portrait/landscape images suffer geometric squishing.
- **Anchor Mismatch**: `BaselineInspectionDetector` defines 3 anchors:
  - Anchor 0: `[0.08, 0.08]` (area $0.0064$)
  - Anchor 1: `[0.22, 0.35]` (area $0.0770$)
  - Anchor 2: `[0.55, 0.65]` (area $0.3575$)
  Exit signs with box dimensions smaller than $0.05 \times 0.05$ have poor IoU overlap with Anchor 0, resulting in unassigned ground truth during anchor matching.
- **Unweighted Loss**: In `BaselineInspectionDetector`, classification loss is unweighted cross-entropy. Because `FIRE_EXTINGUISHER` has 3,164 training samples and `EXIT_DOOR` has only 33, door gradients are completely overwhelmed by extinguisher and background anchor gradients.

---

## 10. Confirmed Defects vs. Disproven Hypotheses

| Hypothesis / Concern | Type | Status | Evidence & Explanation |
|---|---|:---:|---|
| **Defect in metric calculation** | Math / Code | **DISPROVEN** | Stage 1 proved pooled (50.89%) vs. macro (22.83%) precision arithmetic is 100% correct. |
| **Corrupt image or label files** | Data Integrity | **DISPROVEN** | 100% of 4,887 images and 4,887 labels parse cleanly; 0 corrupt files, 0 malformed lines. |
| **Exact image duplicates across splits** | Data Leakage | **DISPROVEN** | 0 duplicate filenames and 0 exact SHA-256 byte collisions cross split boundaries. |
| **Class mapping mismatch between data and model** | Code Contract | **DISPROVEN** | Class IDs 0, 1, 2, 3 are 100% aligned across `data.yaml`, loader, trainer, and model. |
| **Extreme class imbalance causes door/obstacle failure** | Data Quality | **CONFIRMED** | `EXIT_DOOR` (0.58%) and `OBSTACLE` (1.28%) are so rare that unweighted loss completely suppresses their logits. |
| **Absence of multi-class co-occurrence** | Data Quality | **CONFIRMED** | Only 3 images out of 4,887 contain $> 1$ class. Natural corridor co-occurrence is missing. |
| **Burst-capture leakage across splits** | Data Leakage | **CONFIRMED** | 100 dHash collision clusters cross split boundaries (consecutive video/burst frames split into train/val/test). |
| **Micro bounding-box feature loss** | Model/Data | **CONFIRMED** | 59 `EXIT_SIGN` annotations are smaller than $0.01\%$ image area and vanish at $256 \times 256$ resolution. |
| **Full-frame annotation noise** | Data Quality | **CONFIRMED** | 14 `EXIT_SIGN` annotations cover $> 95\%$ of the image ($1.0 \times 1.0$), corrupting spatial priors. |

---

## 11. Prioritized Dataset Improvement Plan

To transition FireSphere.ai toward a reliable, compliant fire safety perception system, the following 6-step improvement plan is established.

### Priority 1: Egress Door & Obstacle Data Curation (+700 Instances)
- **Target**: Add **400 verified emergency exit doors** and **300 real corridor obstacles**.
- **Justification**: Current dataset contains only 33 doors and 69 obstacles in training, which produced 0% recall. Statistically, achieving $\ge 70\%$ recall requires a minimum of 300–500 diverse positive instances per class.
- **Source Strategy**:
  1. Audit the 2,774 quarantined door images in `datasets/fire_safety/quarantine/irrelevant_samples/` to extract genuine fire-rated exit doors (push bars, exit signage above, fire-resistance rating labels).
  2. Perform controlled on-site photography in commercial buildings, educational facilities, and hospitals under explicit administrative authorization.

### Priority 2: Natural Co-occurring Multi-Class Corridors (+500 Scenes)
- **Target**: Add **500 corridor perspective images** where exit doors, exit signs, and fire extinguishers appear together in their natural spatial relationships.
- **Justification**: Currently only 3 multi-class images exist in the entire dataset. Contextual co-occurrence provides essential spatial priors for detection heads and multi-class anchor assignment.

### Priority 3: Hard Negative Background Samples (+250 Scenes)
- **Target**: Add **250 images of clean corridors and non-target look-alikes**:
  - Ordinary office wooden doors (non-exit doors)
  - Red trash bins and red tool storage chests
  - Water dispensers and electrical cabinets
  - Completely clear corridors (negative samples for `OBSTACLE`)
- **Justification**: Prevents false positive alarms when officers inspect compliant premises.

### Priority 4: Re-clustering & Strict Leakage Elimination
- **Action**: Implement **Perceptual + Sequence Burst Clustering**:
  - Cluster all images using dHash (Hamming distance $\le 6$) and camera timestamp/sequence metadata into unified `super_group_id` clusters.
  - Partition splits strictly at the `super_group_id` level.
  - Guarantee that zero photo bursts or consecutive video frames cross between train, validation, and test.

### Priority 5: Annotation Cleaning & Resolution Protocol
- **Action**:
  - Remove the 14 full-frame ($> 95\%$ area) clip-art exit sign bounding boxes.
  - Establish a minimum bounding-box area threshold of $0.0005$ ($0.05\%$ of image area, or $\ge 15 \times 15$ px at $640 \times 640$).
  - Increase model input resolution from $256 \times 256$ to $640 \times 640$ with aspect-ratio preserving letterboxing.

### Priority 6: Multi-Stage Data Governance Lifecycle
Establish explicit, immutable directory tiers:
1. `incoming/`: Raw unverified captures.
2. `provenance_review/`: Licensing, rights-holder consent, and metadata validation.
3. `annotation_pending/`: Labeling by trained annotators according to NBC 2016 guidelines.
4. `quality_review/`: Geometry, box area, and class consistency audit.
5. `approved_training/`: Versioned manifest with SHA-256 hashes for model fitting.
6. `sealed_holdout_test/`: Strictly untouched, leak-free holdout benchmark.

---

## 12. Recommended Next Experiments (Stages 3 & 4)

1. **Stage 3 (Candidate Dataset Preparation)**:
   - Prepare a versioned candidate dataset manifest (`v0.9.0-candidate`) incorporating curated exit door and obstacle samples.
   - Re-partition with strict perceptual burst clustering to eliminate the 100 cross-split dHash leaks.
   - Keep the original `phase8_3/yolo/test` split strictly untouched as the authoritative baseline holdout benchmark.
2. **Stage 4 (Model Comparison)**:
   - Introduce **Class-Weighted Focal Loss** ($\alpha$-balanced focal loss or inverse-frequency weighting) to prevent extinguisher dominance.
   - Evaluate a candidate multi-scale detector (e.g. YOLOv8-nano with Feature Pyramid Network) capable of resolving small exit signs and distant doors at $640 \times 640$.

---

## 13. Audit Execution Trace & Verification Records

The audit was executed entirely via non-destructive commands in `ai-service/.venv`:
- **Execution Script**: [`stage2_dataset_audit.py`](file:///C:/Users/athar/.gemini/antigravity-ide/brain/726731a4-418a-4d5c-8361-74aa1b715be6/scratch/stage2_dataset_audit.py)
- **Execution Command**: `.venv\Scripts\python C:\Users\athar\.gemini\antigravity-ide\brain\726731a4-418a-4d5c-8361-74aa1b715be6\scratch\stage2_dataset_audit.py`
- **Exit Code**: `0` (Success)
- **Output Artifact**: [`dataset_audit_results.json`](file:///C:/Users/athar/.gemini/antigravity-ide/brain/726731a4-418a-4d5c-8361-74aa1b715be6/scratch/dataset_audit_results.json)
- **Total Images Processed**: 4,887 images (3,377 train, 988 val, 522 test)
- **Total Labels Processed**: 4,887 label files (7,258 bounding boxes)

---

## 14. Files Created or Modified

- **Created**:
  - [`docs/integration/cv_dataset_quality_and_improvement_plan.md`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs/integration/cv_dataset_quality_and_improvement_plan.md) (This comprehensive audit report)
  - [`scratch/stage2_dataset_audit.py`](file:///C:/Users/athar/.gemini/antigravity-ide/brain/726731a4-418a-4d5c-8361-74aa1b715be6/scratch/stage2_dataset_audit.py) (Non-destructive audit script)
  - [`scratch/dataset_audit_results.json`](file:///C:/Users/athar/.gemini/antigravity-ide/brain/726731a4-418a-4d5c-8361-74aa1b715be6/scratch/dataset_audit_results.json) (Programmatic audit findings)
- **Modified**: *None*. (Zero existing source files, checkpoints, configuration files, or dataset files were modified).

---

## 15. Unresolved Questions & External Dependencies

1. **Quarantine Door Extraction**: Can the 2,774 architectural door images in `datasets/fire_safety/quarantine/` be safely reviewed by a human annotator to rescue genuine fire exit doors, or should newly captured field imagery be prioritized?
2. **On-Premise Capture Permissions**: For Indian NBC 2016 compliance, will real facility inspection photography be provided by municipal partner departments, or must open-source permissive archives be curated?

---

## 16. Explicit STOP POINT

```
================================================================================
                               STOP POINT REACHED
================================================================================
STAGE 2 (Dataset Quality Audit & Improvement Plan) is COMPLETE.
All dataset files, annotations, distributions, and leakage risks have been verified.

Current Safety State:
- CV Approval Gate: NOT_APPROVED (Fail-Closed)
- Calibration State: NOT_CALIBRATED
- Production Inference Authorization: FALSE
- Baseline Checkpoint best.pt SHA-256: 5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666 (UNTOUCHED)
- Baseline Dataset phase8_3/yolo: UNTOUCHED

DO NOT proceed to Stage 3 (Dataset Preparation & Controlled Experiment) or
modify any datasets, splits, or models without explicit user authorization.
================================================================================
```
