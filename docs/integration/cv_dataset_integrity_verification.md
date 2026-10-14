# FireSphere.ai — CV Candidate Dataset Integrity & Split Audit Report
**Stage 3.1 Deliverable: Independent Mathematical, Cryptographic, and Split-Isolation Verification**

- **Date:** October 13, 2026
- **Auditor:** Senior Computer Vision Engineer, ML Evaluation Engineer, and Production Readiness Reviewer
- **Target Microservice:** `ai-service`
- **Baseline Dataset:** `datasets/fire_safety/prepared/phase8_3/yolo`
- **Audited Candidate Dataset:** `datasets/fire_safety/prepared/stage3_candidate/yolo`
- **Audited Candidate Manifests:** `datasets/fire_safety/prepared/stage3_candidate/manifests`
- **Baseline Checkpoint:** `ai-service/artifacts/phase8_4/checkpoints/best.pt`
- **Verified Checkpoint SHA-256:** `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666`
- **Audit Execution Artifact:** [`artifacts/stage3_1_integrity_results.json`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage3_1_integrity_results.json)
- **Status:** **STAGE 3.1 COMPLETE — STOP POINT REACHED**
- **CV Approval Gate State:** `NOT_APPROVED` (Unchanged, Fail-Closed)
- **Calibration State:** `NOT_CALIBRATED` (Unchanged)
- **Production Authorization:** `false` (Unchanged)

---

## 1. Executive Verdict & Assessment

| Audit Dimension | Standard / Requirement | Empirical Result | Status |
|---|---|---|:---:|
| **Baseline Checkpoint** | SHA-256 must match `5f166c47...c666` | Verified exact match | **PASS** |
| **File Completeness** | Exactly 4,887 images & 4,887 labels | 4,887 paired files; 0 missing, 0 unexpected | **PASS** |
| **Bitstream Preservation** | 1-to-1 Image SHA-256 identical to baseline | 4,887 of 4,887 identical (0 mismatches) | **PASS** |
| **Label Preservation** | 1-to-1 Label SHA-256 & Bounding Boxes | 7,258 of 7,258 boxes identical (0 mismatches) | **PASS** |
| **Class Inventory** | Total class counts identical to baseline | Exact match: FE: 4,564, ED: 42, ES: 2,559, OB: 93 | **PASS** |
| **Split Leakage (dHash)** | Cross-split difference hash collisions $\le 4$ | **0 collisions** (Eliminated all 100 baseline leaks) | **PASS** |
| **Split Leakage (Burst)** | Camera sequence & base-scene cross-split leaks | **0 collisions** (Zero burst sequences cross splits) | **PASS** |
| **Manifest Cryptography** | Recorded vs Recalculated SHA-256 | 6 of 6 manifests match recorded checksums | **PASS** |
| **Annotation Review Registry** | 100 flagged records resolve to real files | 59 micro, 14 full-frame, 27 empty (strictly disjoint) | **PASS** |
| **Algorithm Determinism** | Seed 42 graph partitioning reproducible | 4,887 of 4,887 file assignments identical (100%) | **PASS** |

### Controlled Experiment Verdict: **CONDITIONAL GO**
- **Permitted Scope**: **CONDITIONAL GO** strictly for **Stage 4 Controlled Model Architecture Comparison** (evaluating whether multi-scale architectures such as YOLOv8-nano or class-weighted loss can improve localization over the baseline ResNet-18 detector on a leak-free benchmark).
- **Mandatory Conditions**:
  1. **Strictly Exploratory**: Stage 4 models trained on this candidate dataset must be classified as *exploratory research benchmarks*, **not** production-ready models.
  2. **Class-Weighted Loss Required**: Due to severe class starvation (30 doors vs 3,298 extinguishers in train), training without inverse-frequency weighting or $\alpha$-balanced focal loss is prohibited.
  3. **Production Gate Stays Fail-Closed**: `ModelApprovalGate` remains strictly `NOT_APPROVED`, calibration state remains `NOT_CALIBRATED`, and production inference authorization remains `false`.
  4. **Production Readiness NO-GO**: Production authorization remains strictly **BLOCKED** until external field imagery (+400 verified fire doors, +300 corridor obstacles, and +500 multi-class scenes) is acquired, annotated, and audited.

---

## 2. Exact Commands and Scripts Executed

The audit was executed entirely via non-destructive commands in `ai-service/.venv` (Python 3.13.7, OpenCV 4.11.0, NumPy 2.2.3):

1. **Audit Execution Script**:
   [`scratch/stage3_1_integrity_audit.py`](file:///C:/Users/athar/.gemini/antigravity-ide/brain/726731a4-418a-4d5c-8361-74aa1b715be6/scratch/stage3_1_integrity_audit.py)
2. **Terminal Execution Command**:
   ```powershell
   .venv\Scripts\python C:\Users\athar\.gemini\antigravity-ide\brain\726731a4-418a-4d5c-8361-74aa1b715be6\scratch\stage3_1_integrity_audit.py
   ```
3. **Execution Runtime**:
   - Background Task ID: `task-1901`
   - Exit Code: `0` (Clean termination)
   - Runtime: ~68 seconds
   - Output Verification Artifact: [`artifacts/stage3_1_integrity_results.json`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage3_1_integrity_results.json)

---

## 3. Dataset and Annotation Inventory

### 3.1 Split File Inventory Comparison

| Split | Baseline Images (`phase8_3`) | Candidate Images (`stage3_candidate`) | Baseline Labels | Candidate Labels | Empty Labels |
|---|:---:|:---:|:---:|:---:|:---:|
| **Train** | 3,377 | **3,437** | 3,377 | **3,437** | 18 |
| **Validation** | 988 | **997** | 988 | **997** | 6 |
| **Test (Held-out)** | 522 | **453** | 522 | **453** | 3 |
| **Total** | **4,887** | **4,887** | **4,887** | **4,887** | **27** |

*Variance Explanation: Candidate split counts shifted (+60 in train, +9 in val, -69 in test) because atomic super-groups were repartitioned as unbreakable units to permanently eliminate cross-split perceptual collisions.*

---

## 4. Annotation-Preservation Comparison (1-to-1 Verification)

Every image and label file was mapped across baseline and candidate datasets using filename and SHA-256 bitstream hashes:

```
[4] Comparing Baseline vs Candidate 1-to-1 Preservation...
    Missing files in candidate:         0
    Unexpected files in candidate:      0
    Image SHA-256 bitstream mismatches: 0
    Label SHA-256 bitstream mismatches: 0
    Bounding-box coordinate mismatches: 0
    1-to-1 Exact Preservation Status:   TRUE
```

### Detailed Verification Findings:
1. **Bit-for-Bit Image Identity**: For all 4,887 images, `SHA256(candidate_image) == SHA256(baseline_image)` with zero byte alterations or re-compression.
2. **Bit-for-Bit Label Identity**: For all 4,887 label files, `SHA256(candidate_label) == SHA256(baseline_label)` with zero coordinate drift or formatting changes.
3. **Zero Coordinate Drift**: Float coordinates `(xc, yc, w, h)` across all 7,258 bounding boxes match baseline values to machine precision.

---

## 5. Group-Based Split Integrity & Leakage Elimination

### 5.1 Recomputed Split-Isolation Metrics

| Leakage Dimension | Audit Method & Rule | Baseline (`phase8_3`) | Candidate (`stage3_candidate`) | Resolution |
|---|---|:---:|:---:|:---:|
| **Exact Bitstream Overlap** | Image SHA-256 collision across splits | 0 | **0** | Clean |
| **Perceptual Near-Duplicates** | `dHash` 64-bit Hamming distance $\le 4$ | **100 clusters** | **0 clusters** | **100% Eliminated** |
| **Base-Scene Groups** | Roboflow prefix split on `.rf.` | 0 | **0** | Clean |
| **Camera Burst Sequences** | Timestamp/counter prefix (`IMG_506x`, `Exit_16x`) | $> 15$ sequences | **0 sequences** | **100% Eliminated** |
| **Split Assignment Completeness** | Every image in exactly one split | 100% | **100%** | Clean |

### 5.2 Super-Group Connected-Component Integrity
- Total images: 4,887
- Atomic connected-component super-groups: **2,928**
- **Component Containment**: Every single super-group was verified to reside 100% within a single split (`train`, `val`, or `test`). Zero super-groups span across split boundaries.

---

## 6. Manifest Checksum Verification and Reproducibility

### 6.1 Cryptographic Hash Verification

| Manifest File | Recorded SHA-256 in `manifest_checksums.json` | Recalculated SHA-256 | Verification Status |
|---|---|---|:---:|
| `candidate_train_manifest.json` | `98d7ba565a8009a032475081d0289ee5a4564391a55eb80ab327184de6bc5dbe` | `98d7ba565a8009a032475081d0289ee5a4564391a55eb80ab327184de6bc5dbe` | **EXACT MATCH** |
| `candidate_val_manifest.json` | `b9f2b5674c7730a8316fd4cae953aabb2d6f46ef70f24619921ab4fd5e7f4db2` | `b9f2b5674c7730a8316fd4cae953aabb2d6f46ef70f24619921ab4fd5e7f4db2` | **EXACT MATCH** |
| `candidate_test_manifest.json` | `d795f13ba164bad7d5a35d45aa54c69b4aa91d0f6d4c4d96b36c5dfee911802c` | `d795f13ba164bad7d5a35d45aa54c69b4aa91d0f6d4c4d96b36c5dfee911802c` | **EXACT MATCH** |
| `annotation_review_manifest.json` | `036b728ca6e261aecfff3aa631743de4da620cbc3d804514686a61d3c029589a` | `036b728ca6e261aecfff3aa631743de4da620cbc3d804514686a61d3c029589a` | **EXACT MATCH** |
| `perceptual_clusters_manifest.json` | `9dee514a1f09d515893e22e752ae8b93cf9c1484a62457f789474ec25797c4b6` | `9dee514a1f09d515893e22e752ae8b93cf9c1484a62457f789474ec25797c4b6` | **EXACT MATCH** |
| `dataset_manifest.json` | `c95fcbf6ae6134e536e2e358b7cd3c121c378da1bf09bcae2f57d9fe6557aa56` | `c95fcbf6ae6134e536e2e358b7cd3c121c378da1bf09bcae2f57d9fe6557aa56` | **EXACT MATCH** |

### 6.2 Deterministic Reproducibility
The split generation graph algorithm was independently re-executed in-memory from baseline inputs using random seed 42:
- Total files tested: 4,887
- Split assignment matches: **4,887 of 4,887 (100.0% match)**
- **Conclusion**: The split generation is 100% deterministic, seed-stable, and reproducible.

---

## 7. Annotation Review Registry Audit

[`annotation_review_manifest.json`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/stage3_candidate/manifests/annotation_review_manifest.json) was audited line-by-line:
- **Total Flagged Records**: 100
- **File Existence**: 100 of 100 records resolve to verified physical image and label files on disk.

### 7.1 Category Disjointness & Verification
- **Micro-Boxes ($< 0.0001$ area)**: **59 records**
  - All 59 are `EXIT_SIGN` (class ID 2) from Roboflow `emergency_exit_signs`.
  - Box dimensions range from $0.0078 \times 0.0078$ down to $5 \times 5$ px at $640 \times 640$.
- **Full-Frame Boxes ($> 0.95$ area)**: **14 records**
  - All 14 are `EXIT_SIGN` (class ID 2) covering $\ge 96\%$ of the frame (up to $1.0 \times 1.0$).
  - Confirmed as clip-art icons or cropped sign photographs.
- **Empty Background Images**: **27 records**
  - All 27 contain 0 bounding boxes. Confirmed as clean negative background scenes.
- **Disjointness Test**: $59 + 14 + 27 = 100$. Categories are **100% strictly disjoint** with zero overlapping or misclassified records.

---

## 8. Dataset Class Inventory Comparison

| Class ID | Target Class Name | Baseline Total | Candidate Total | Delta | Candidate Train | Candidate Val | Candidate Test |
|:---:|---|:---:|:---:|:---:|:---:|:---:|:---:|
| **0** | `FIRE_EXTINGUISHER` | 4,564 | **4,564** | 0 | 3,298 | 842 | 424 |
| **1** | `EXIT_DOOR` | 42 | **42** | 0 | 30 | 3 | 9 |
| **2** | `EXIT_SIGN` | 2,559 | **2,559** | 0 | 1,723 | 618 | 218 |
| **3** | `OBSTACLE` | 93 | **93** | 0 | 78 | 9 | 6 |
| **Total** | — | **7,258** | **7,258** | **0** | **5,129** | **1,472** | **657** |

---

## 9. Confirmed Defects and Unresolved Risks

1. **Persistent Rare-Class Deficit (Data Volume)**:
   - While leak-free partitioning was completely achieved, repartitioning **cannot fabricate missing data**.
   - The training set contains only **30 exit doors** and **78 obstacles** out of 5,129 annotations.
   - Without class-weighted loss, any model trained on this dataset will suffer the same near-zero recall defect observed in Stage 1.
2. **Contextual Co-occurrence Deficit**:
   - Only **3 images in the entire dataset** contain multiple classes.
   - The dataset remains bimodal: single extinguishers against drywall, or isolated exit signs against ceilings.
3. **Micro-Scale Vanishing**:
   - The 59 micro exit signs ($< 5 \times 5$ px) cannot be resolved by standard $256 \times 256$ input resolution detectors. High-resolution input ($640 \times 640$) with multiscale feature pyramid networks is mandatory.

---

## 10. Recommended Next Steps (Stage 4 Roadmap)

1. **Stage 4 Controlled Model Architecture Comparison**:
   - Evaluate a candidate multi-scale detector (e.g. YOLOv8-nano / FPN) at $640 \times 640$ resolution against the baseline `BaselineInspectionDetector` ($256 \times 256$).
   - Implement **Class-Weighted Focal Loss** ($\alpha$-weighting based on inverse class frequency) during training to prevent rare class suppression.
   - Evaluate on both the candidate leak-free holdout test set (453 images) and the baseline holdout test set (522 images) to measure true generalisation.
2. **Field Data Acquisition Requirement (Parallel Track)**:
   - Formally schedule the collection of +400 real fire exit doors and +300 corridor obstacles under building administration authorization before any production release review.

---

## 11. Explicit STOP POINT

```
================================================================================
                               STOP POINT REACHED
================================================================================
STAGE 3.1 (Independent Candidate Dataset Integrity & Split Audit) is COMPLETE.
All files, bitstreams, manifests, checksums, and isolation boundaries are VERIFIED.

Safety Confirmation:
- CV Approval Gate: NOT_APPROVED (Fail-Closed)
- Calibration State: NOT_CALIBRATED
- Production Inference Authorization: FALSE
- Baseline Checkpoint best.pt SHA-256: 5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666 (UNTOUCHED)
- Baseline Dataset phase8_3/yolo: 100% UNTOUCHED
- Candidate Dataset stage3_candidate/yolo: 100% READ-ONLY VERIFIED

DO NOT begin Stage 4 model training automatically.
Awaiting user review and authorization before proceeding.
================================================================================
```
