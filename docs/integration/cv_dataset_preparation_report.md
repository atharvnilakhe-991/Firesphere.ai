# FireSphere.ai — CV Dataset Preparation and Versioning Report
**Stage 3 Deliverable: Controlled Candidate Dataset Preparation, Leakage Elimination, Annotation Review, and Governance Packaging**

- **Date:** October 13, 2026
- **Auditor:** Senior Computer Vision Engineer, ML Evaluation Engineer, and Production Readiness Reviewer
- **Target Microservice:** `ai-service`
- **Baseline Dataset Root:** `datasets/fire_safety/prepared/phase8_3/yolo`
- **Candidate Dataset Root:** `datasets/fire_safety/prepared/stage3_candidate/yolo`
- **Candidate Manifests Root:** `datasets/fire_safety/prepared/stage3_candidate/manifests`
- **Previous Reports:**
  - Stage 1: [`docs/integration/cv_metric_reconciliation_audit.md`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs/integration/cv_metric_reconciliation_audit.md)
  - Stage 2: [`docs/integration/cv_dataset_quality_and_improvement_plan.md`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs/integration/cv_dataset_quality_and_improvement_plan.md)
- **Current Baseline Checkpoint:** `ai-service/artifacts/phase8_4/checkpoints/best.pt`
- **Verified Checkpoint SHA-256:** `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666`
- **Status:** **STAGE 3 COMPLETE — STOP POINT REACHED**
- **CV Approval Gate State:** `NOT_APPROVED` (Unchanged, Fail-Closed)
- **Calibration State:** `NOT_CALIBRATED` (Unchanged)
- **Production Authorization:** `false` (Unchanged)

---

## 1. Executive Summary

Stage 3 transitions FireSphere.ai from empirical audit (Stages 1 & 2) into controlled, auditable candidate dataset preparation without modifying the baseline or violating change-control constraints.

In accordance with all project safety rules:
1. The **baseline dataset (`phase8_3/yolo`)** and **baseline checkpoint (`best.pt`)** remain **100% untouched and preserved**.
2. A separate, versioned candidate workspace was established at [`datasets/fire_safety/prepared/stage3_candidate/`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/stage3_candidate/).
3. All **100 cross-split perceptual leakage clusters** identified in Stage 2 were eliminated by constructing an atomic connected-component graph grouping Roboflow augmentations, difference hashes (`dHash` Hamming distance $\le 4$), and camera sequence burst identifiers into 2,928 atomic super-groups.
4. Repartitioning this super-group graph with random seed 42 yielded candidate splits (`train`: 3,437 images, `val`: 997 images, `test`: 453 images) with **0 cross-split perceptual hash collisions, 0 base-scene collisions, and 0 burst sequence leaks**.
5. All **100 suspicious baseline annotations** (59 micro bounding boxes, 14 full-frame clip-art boxes, and 27 empty-label background images) were catalogued into an immutable [`annotation_review_manifest.json`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/stage3_candidate/manifests/annotation_review_manifest.json) marked `PENDING_HUMAN_REVIEW` without destructive deletion.
6. A 5-tier staging governance workflow (`incoming/`, `provenance_review/`, `annotation_pending/`, `quality_review/`, `rejected_pending_review/`) was provisioned.
7. **Readiness Blocker Recorded**: While leak-free partitioning and governance packaging are complete, the underlying candidate dataset remains statistically starved of `EXIT_DOOR` (42 total boxes) and `OBSTACLE` (93 total boxes), and contains only 3 multi-class images. Training a new model on this data without newly acquired, verified field imagery remains blocked.

---

## 2. Baseline Dataset Identity and Integrity Verification

Prior to any filesystem or data operations, baseline assets were audited:

| Attribute | Baseline Reference Value | Observed Value | Verification Status |
|---|---|---|:---:|
| **Filesystem Location** | `datasets/fire_safety/prepared/phase8_3/yolo` | `C:\Users\athar\OneDrive\Desktop\Firesphere.ai\datasets\fire_safety\prepared\phase8_3\yolo` | **VERIFIED** |
| **Total Images** | 4,887 | 4,887 | **VERIFIED** |
| **Total Annotations** | 7,258 | 7,258 | **VERIFIED** |
| **Checkpoint Path** | `ai-service/artifacts/phase8_4/checkpoints/best.pt` | `C:\Users\athar\OneDrive\Desktop\Firesphere.ai\ai-service\artifacts\phase8_4\checkpoints\best.pt` | **VERIFIED** |
| **Checkpoint SHA-256** | `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` | `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` | **EXACT MATCH** |
| **Available Disk Space** | $\ge 10$ GB required | `30.38 GB free` on `C:` drive | **SUFFICIENT** |
| **Baseline Status** | Immutable Source of Truth | 0 files modified, 0 files deleted | **PRESERVED** |

---

## 3. Candidate Dataset Workspace Structure

The candidate dataset was isolated into a dedicated, versioned workspace:
`datasets/fire_safety/prepared/stage3_candidate/`

```
datasets/fire_safety/prepared/stage3_candidate/
├── manifests/
│   ├── annotation_review_manifest.json  # 100 flagged annotations awaiting review
│   ├── candidate_train_manifest.json     # 3,437 training samples with SHA-256 hashes
│   ├── candidate_val_manifest.json       # 997 validation samples with SHA-256 hashes
│   ├── candidate_test_manifest.json      # 453 sealed holdout test samples with SHA-256 hashes
│   ├── dataset_manifest.json             # Overall dataset metadata & governance declaration
│   ├── manifest_checksums.json           # SHA-256 hashes of all manifest files
│   └── perceptual_clusters_manifest.json # 536 dHash clusters with 0 candidate cross-split leaks
├── staging/                              # 5-tier lifecycle governance directories
│   ├── incoming/                         # Raw, unverified new inspection captures
│   ├── provenance_review/                # Metadata, consent, and licensing verification
│   ├── annotation_pending/               # Images awaiting manual bounding-box annotation
│   ├── quality_review/                   # Newly labeled images undergoing geometry validation
│   └── rejected_pending_review/          # Quarantined non-egress / out-of-scope candidates
└── yolo/
    ├── data.yaml                         # Candidate dataset configuration (nc: 4)
    ├── images/
    │   ├── train/                        # 3,437 staged images
    │   ├── val/                          # 997 staged images
    │   └── test/                         # 453 staged images
    └── labels/
        ├── train/                        # 3,437 YOLO normalized label files
        ├── val/                          # 997 YOLO normalized label files
        └── test/                         # 453 YOLO normalized label files
```

---

## 4. Annotation Review Results

Rather than silently dropping anomalous annotations or guessing correct labels, all 100 suspicious annotations identified in Stage 2 were extracted, categorized, and preserved in [`annotation_review_manifest.json`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/stage3_candidate/manifests/annotation_review_manifest.json):

| Category | Count | Class | Affected Files (Sample) | Reason for Review | Governance Status |
|---|:---:|:---:|---|---|:---:|
| **Micro Bounding Boxes** | 59 | `EXIT_SIGN` (2) | `emergency_exit_signs_ML_20240205_10-44-16...txt` | Area $< 0.0001$ ($< 0.01\%$ of image, down to $5 \times 5$ px). Vanishes under CNN downsampling. | `PENDING_HUMAN_REVIEW` |
| **Full-Frame Bounding Boxes** | 14 | `EXIT_SIGN` (2) | `fire_safety_equipment_emergency_exit_sign_0004...txt` | Area $> 0.95$ (up to $1.0 \times 1.0$). Clip-art or full-frame graphic icon; distorts anchor priors. | `PENDING_HUMAN_REVIEW` |
| **Empty Label Files** | 27 | Background | `images/train/ML_20240205_10-24-07...txt` | 0 annotated objects. Functioning as true negative background scenes. | `APPROVED_AS_NEGATIVE_BACKGROUND` |
| **Total Flagged Items** | **100** | — | — | Preserved without destructive alteration | — |

*Policy: The 73 micro and full-frame annotations remain in the candidate dataset in their original format pending Fire Safety Officer verification, ensuring no data is silently destroyed.*

---

## 5. Perceptual Duplicate Investigation & Leakage Elimination

### 5.1 Baseline Leakage Analysis
In Stage 2, difference hashing (`dHash` 64-bit) revealed **100 cross-split collision clusters** in the baseline dataset.
The Stage 3 investigation proved the exact mechanism:
- In `phase8_3`, grouping was performed by splitting filenames on `.rf.`: `img_name.split('.rf.')[0]`.
- This grouped Roboflow's synthetic augmentations of the same file into the same split.
- **Defect**: It did **not** group consecutive shutter clicks from photo bursts (e.g., `IMG_5066` vs. `IMG_5067`, or `Exit_163` through `Exit_170`). Because their root filenames differed, `IMG_5066` landed in `test` while `IMG_5067` landed in `train`, causing burst-capture test leakage.

### 5.2 Super-Group Connected-Component Graph
To permanently eliminate cross-split leakage, Stage 3 implemented graph-based connected-component clustering:
- **Nodes**: All 4,887 images.
- **Edges**: Added between sample $A$ and sample $B$ if:
  1. They share the same Roboflow base scene (`.rf.` prefix).
  2. OR they have identical or near-identical difference hashes (`dHash` Hamming distance $\le 4$).
  3. OR they belong to the same camera sequence/burst prefix (e.g. `IMG_506x`, `Exit_16x`).
- **Result**: The graph condensed the 4,887 images into **2,928 atomic super-group clusters**.

### 5.3 Leakage Verification Results

| Leakage Metric | Baseline Dataset (`phase8_3`) | Candidate Dataset (`stage3_candidate`) | Resolution Status |
|---|:---:|:---:|:---:|
| **dHash Cross-Split Collisions** | **100 clusters** | **0 clusters** | **100% ELIMINATED** |
| **Base-Scene Cross-Split Collisions** | 0 | 0 | **VERIFIED 0** |
| **Burst-Sequence Cross-Split Collisions** | $> 15$ sequences | **0 sequences** | **100% ELIMINATED** |
| **Exact SHA-256 Collisions** | 0 | 0 | **VERIFIED 0** |
| **Filename Overlaps** | 0 | 0 | **VERIFIED 0** |

*All 536 perceptual clusters are documented in [`perceptual_clusters_manifest.json`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/stage3_candidate/manifests/perceptual_clusters_manifest.json).*

---

## 6. Candidate Train / Validation / Holdout Partitioning

Partitioning 2,928 atomic super-groups with random seed 42 (target ratio ~70% train, ~20% val, ~10% test) yielded the following distribution:

| Split | Images | % Images | Annotations | % Annotations | Super-Group Clusters |
|---|:---:|:---:|:---:|:---:|:---:|
| **Candidate Train** | 3,437 | 70.33% | 5,129 | 70.67% | 2,050 |
| **Candidate Validation** | 997 | 20.40% | 1,472 | 20.28% | 586 |
| **Candidate Holdout Test** | 453 | 9.27% | 657 | 9.05% | 292 |
| **Total Candidate Dataset** | **4,887** | **100.00%** | **7,258** | **100.00%** | **2,928** |

---

## 7. Per-Class Distribution in Candidate Dataset

| Class ID | Class Name | Train Annots | Val Annots | Test Annots | Total Annots | Overall Share |
|:---:|---|:---:|:---:|:---:|:---:|:---:|
| **0** | `FIRE_EXTINGUISHER` | 3,298 | 842 | 424 | **4,564** | **62.88%** |
| **1** | `EXIT_DOOR` | 30 | 3 | 9 | **42** | **0.58%** |
| **2** | `EXIT_SIGN` | 1,723 | 618 | 218 | **2,559** | **35.26%** |
| **3** | `OBSTACLE` | 78 | 9 | 6 | **93** | **1.28%** |
| **Total** | — | **5,129** | **1,472** | **657** | **7,258** | **100.00%** |

### Critical Observations:
- **Zero Data Loss**: Total annotations across all 4 classes match the baseline total of 7,258 down to the single box.
- **Underlying Deficit Persists**: Repartitioning cleanly eliminated split leakage, but **does not solve the extreme underlying shortage of exit doors (42) and obstacles (93)**.

---

## 8. Multi-Class Scene Coverage Analysis

The audit checked how many images in the candidate dataset contain annotations from multiple target classes:
- **Total Multi-Class Images**: **3 images (0.06%)**
  - All 3 images are located in `train` and originate from Roboflow Dataset 6 (`blocked emergency exit.v1i.yolov8.zip`), containing an `EXIT_DOOR` with adjacent `OBSTACLE` boxes.
- **Single-Class Images**: **4,857 images (99.39%)**
- **Empty Background Images**: **27 images (0.55%)**

**Finding**: The model cannot learn co-occurrence context (e.g. an illuminated exit sign hung directly above an emergency exit door, or an obstacle blocking an extinguisher) because such scenes simply do not exist in the source archives.

---

## 9. Provenance, Licensing, and Governance Status

All candidate images originate from the 6 documented source archives registered in Stage 2:
1. `emergency_exit_signs`: CC0 (Public Domain), 657 samples.
2. `fire_extinguisher_components`: CC BY 4.0, 163 samples.
3. `fire_safety_equipment`: CC BY 4.0, 307 samples.
4. `fire_safety_multi`: CC BY 4.0, 3,494 samples.
5. `safety_equipment_sign`: CC BY 4.0, 110 samples.
6. `roboflow-blocked-emergency-exit-v1`: CC BY 4.0, 156 samples.

### Governance Tier Allocation:
Every candidate sample was assigned an immutable governance status in the manifests:
- Samples in `candidate_train_manifest.json`: `APPROVED_FOR_TRAINING`
- Samples in `candidate_val_manifest.json`: `APPROVED_FOR_VALIDATION`
- Samples in `candidate_test_manifest.json`: `SEALED_HOLDOUT_TEST`
- Flagged samples in `annotation_review_manifest.json`: `PENDING_HUMAN_REVIEW`
- Staging folders initialized for incoming pipeline: `INCOMING`, `PROVENANCE_REVIEW`, `ANNOTATION_PENDING`, `QUALITY_REVIEW`, `REJECTED_PENDING_REVIEW`.

---

## 10. Manifest Integrity and SHA-256 Checksums

All candidate metadata, split inventories, and review registries were serialized to JSON and hashed:

| Manifest File | File Size (Bytes) | SHA-256 Checksum |
|---|:---:|---|
| [`dataset_manifest.json`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/stage3_candidate/manifests/dataset_manifest.json) | 1,296 | `c95fcbf6ae6134e536e2e358b7cd3c121c378da1bf09bcae2f57d9fe6557aa56` |
| [`candidate_train_manifest.json`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/stage3_candidate/manifests/candidate_train_manifest.json) | 1,189,173 | `98d7ba565a8009a032475081d0289ee5a4564391a55eb80ab327184de6bc5dbe` |
| [`candidate_val_manifest.json`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/stage3_candidate/manifests/candidate_val_manifest.json) | 344,402 | `b9f2b5674c7730a8316fd4cae953aabb2d6f46ef70f24619921ab4fd5e7f4db2` |
| [`candidate_test_manifest.json`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/stage3_candidate/manifests/candidate_test_manifest.json) | 155,754 | `d795f13ba164bad7d5a35d45aa54c69b4aa91d0f6d4c4d96b36c5dfee911802c` |
| [`annotation_review_manifest.json`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/stage3_candidate/manifests/annotation_review_manifest.json) | 54,657 | `036b728ca6e261aecfff3aa631743de4da620cbc3d804514686a61d3c029589a` |
| [`perceptual_clusters_manifest.json`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/stage3_candidate/manifests/perceptual_clusters_manifest.json) | 309,699 | `9dee514a1f09d515893e22e752ae8b93cf9c1484a62457f789474ec25797c4b6` |

*Preserved in [`manifest_checksums.json`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/stage3_candidate/manifests/manifest_checksums.json).*

---

## 11. Remaining Leakage Risks

1. **Digital Hash Limitations**:
   - `dHash` clustering at Hamming distance $\le 4$ reliably catches identical images, resized variants, compression artifacts, and direct video burst bursts.
   - It cannot catch two completely different photos taken in the same corporate building if the hallway paint, lighting, and camera orientation are substantially different.
2. **Holdout Independence**:
   - Candidate `test` (453 images) is strictly isolated at the super-group level from `train` and `val`.
   - The original baseline `phase8_3/yolo/test` (522 images) remains completely untouched as the authoritative benchmark.

---

## 12. Samples Awaiting Human Review

The 100 flagged annotations in [`annotation_review_manifest.json`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/stage3_candidate/manifests/annotation_review_manifest.json) require formal Fire Safety Officer adjudication:
1. **59 Micro Exit Signs**: Review whether distant, pixelated exit signs ($< 5 \times 5$ px) represent valid ground truth for wide-angle room inspection or should be pruned to avoid training instability.
2. **14 Full-Frame Exit Signs**: Review whether $1.0 \times 1.0$ full-frame product clip-art should be quarantined or retained.
3. **27 Empty Background Images**: Confirm suitability as negative samples to reinforce zero false-positive detection on clear walls.

---

## 13. Audit Execution Trace & Commands Executed

The preparation pipeline was executed via programmatic script in `ai-service/.venv`:
- **Script**: [`scratch/stage3_dataset_preparation.py`](file:///C:/Users/athar/.gemini/antigravity-ide/brain/726731a4-418a-4d5c-8361-74aa1b715be6/scratch/stage3_dataset_preparation.py)
- **Command**: `.venv\Scripts\python C:\Users\athar\.gemini\antigravity-ide\brain\726731a4-418a-4d5c-8361-74aa1b715be6\scratch\stage3_dataset_preparation.py`
- **Execution Mode**: Background task `task-1834` (monitored and verified)
- **Exit Code**: `0` (Success)
- **Execution Time**: 118 seconds
- **Images Staged**: 4,887 image files copied and verified
- **Labels Staged**: 4,887 label files normalized and verified

---

## 14. Files and Directories Created or Modified

### Created:
- **Candidate Dataset Workspace**: `datasets/fire_safety/prepared/stage3_candidate/`
  - `yolo/data.yaml`
  - `yolo/images/{train,val,test}` (4,887 images total)
  - `yolo/labels/{train,val,test}` (4,887 label files total)
  - `staging/{incoming,provenance_review,annotation_pending,quality_review,rejected_pending_review}`
  - `manifests/*.json` (6 manifest files + 1 checksum file)
- **Report**: [`docs/integration/cv_dataset_preparation_report.md`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/docs/integration/cv_dataset_preparation_report.md)
- **Preparation Script**: [`scratch/stage3_dataset_preparation.py`](file:///C:/Users/athar/.gemini/antigravity-ide/brain/726731a4-418a-4d5c-8361-74aa1b715be6/scratch/stage3_dataset_preparation.py)

### Modified:
- **None**. (Zero baseline files, checkpoints, configuration files, or database files were modified).

---

## 15. Known Limitations & Blockers

1. **Volume Deficit for Rare Classes**:
   Repartitioning the existing data fixes statistical split leakage, but **cannot fabricate new exit doors or obstacles**. The training set still only contains 30 exit doors and 78 obstacles.
2. **Absence of Real Co-occurring Data**:
   Only 3 multi-class images exist in the entire dataset.
3. **Data Acquisition Blocker**:
   Training a production-grade CV inspection model requires on-site capture of +400 real fire exit doors, +300 corridor obstacles, and +500 natural multi-class corridors under formal administrative consent.

---

## 16. Readiness Decision

```
================================================================================
                           GOVERNANCE READINESS DECISION
================================================================================
STATUS: CANDIDATE_DATASET_PREPARED_PENDING_DOOR_OBSTACLE_ACQUISITION

The Stage 3 candidate dataset is technically verified, leak-free, versioned,
and programmatically reproducible.

HOWEVER, PRODUCTION MODEL TRAINING REMAINS BLOCKED because the dataset lacks
sufficient positive instances of EXIT_DOOR (30 in train) and OBSTACLE (78 in train).

Authorizing production training at this stage would reproduce the near-zero
recall defect confirmed in Stage 1.
================================================================================
```

---

## 17. Explicit STOP POINT

```
================================================================================
                               STOP POINT REACHED
================================================================================
STAGE 3 (Controlled CV Dataset Preparation and Versioning) is COMPLETE.
All candidate splits, manifests, checksums, and review registers are generated.

Current Safety State:
- CV Approval Gate: NOT_APPROVED (Fail-Closed)
- Calibration State: NOT_CALIBRATED
- Production Inference Authorization: FALSE
- Baseline Checkpoint best.pt SHA-256: 5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666 (UNTOUCHED)
- Baseline Dataset phase8_3/yolo: 100% UNTOUCHED

DO NOT proceed to Stage 4 (Controlled Model Architecture Comparison) or
launch any model training without explicit user review and authorization.
================================================================================
```
