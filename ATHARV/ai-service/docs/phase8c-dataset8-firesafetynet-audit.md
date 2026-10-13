# FireSphere.ai — Phase 8C: Dataset 8 FireSafetyNet Condition Check Full Audit

**Prompt ID:** `84627`  
**Audit Timestamp:** `2026-10-07T11:42:00+00:00`  
**Role:** Dataset Engineering and AI/ML Validation Agent (Member 4)  
**Target Class:** `1: FIRE_EXTINGUISHER_BLOCKED`  
**Evaluation Mode:** `AUDIT ONLY` (Canonical dataset untouched; no model training; no synthetic relabeling)  
**Dataset 8 Status:** `DATASET_8_PARTIALLY_USABLE`  
**Phase 9 Training Status:** `PHASE_9_STATUS = BLOCKED`  
**Canonical Merge Decision:** `DO NOT MERGE (0 direct violation annotations)`

---

## 1. Executive Summary & Governance Compliance

Following the Phase 8C evaluation of earlier candidate datasets, this audit evaluates **Dataset 8: FireSafetyNet Condition Check (FSE Condition Check)**, sourced from the Ruhr University Bochum research publication (*Journal of Computing in Civil Engineering*, ASCE, June 2025).

### Core Governance Rules Enforced:
1. **Canonical Dataset Immutability:** The canonical dataset at `datasets/fire_safety/` (6,471 images, 6,471 annotation files, 9,077 bounding boxes) remains **completely untouched and unmodified**.
2. **Safe Workspace Isolation:** All archives were extracted into `datasets/fire_safety_sources/fire_safetynet_condition_check/extracted/`.
3. **Non-Negotiable Semantic Rule:** Detections of fire extinguishers, partially visible extinguishers, or optical occlusions **MUST NEVER** be coercively relabeled as `FIRE_EXTINGUISHER_BLOCKED`.
4. **No Semantic Relabeling:** Classes were audited based on ground-truth visual semantics rather than assuming file or project names imply violation status.
5. **Phase 9 Training Barrier:** Dataset 8 does **NOT** unblock Phase 9. `PHASE_9_STATUS` remains strictly `BLOCKED`.

---

## 2. Immutable Source Archives & Cryptographic Verification

The four source archives in `datasets/fire_safety_sources/fire_safetynet_condition_check/` were hashed and verified:

| Archive Filename | Size (Bytes) | Size (MB) | SHA-256 Checksum | Members | Content |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `3_1_FSE Condition Check_modal_train_data.zip` | 1,467,632,410 | 1,399.64 | `40e7dea5486ed55f8173f642caabce7eebadeed3720e5f3cdfd9287452d34a29` | 3,679 | Modal Train Images & Labels |
| `3_1_FSE Condition Check_modal_val_data_and_weights.zip` | 457,107,575 | 435.93 | `4b6af3367c14808e59b27d87cca77e45632e706c20d86f064becf9259a477922` | 1,054 | Modal Val Images, Labels, Weights (`best.pt`) |
| `4_1_FSE Condition Check_amodal_train.zip` | 1,469,230,487 | 1,401.17 | `35c42ecc7e184d7eccd163f848aaef9e4da4a98560aebc9bb5d61f691c4bd027` | 3,679 | Amodal Train Images & Labels |
| `4_1_FSE Condition Check_amodal_val_data_and_weights.zip` | 457,549,678 | 436.35 | `ed3684a81c0aacd8509494e3435195f5c46a9e111ff5541d34cba11cab4452cf` | 1,054 | Amodal Val Images, Labels, Weights (`best.pt`) |

* **Total Raw Archive Size:** `3,851,520,150` bytes (~3.67 GB)
* **Total Archive Members:** `9,466`
* **Source Manifest Location:** `datasets/fire_safety_sources/fire_safetynet_condition_check/dataset8_source_manifest.json`

---

## 3. Structural Inventory & Integrity Validation

### 3.1 Image Corpus Metrics
* **Total Image Instances in Archives:** 4,726 (2,363 modal, 2,363 amodal).
* **Unique Physical Images:** **2,363** (1,838 in train, 525 in val).
* **Decodability & Health:** 100% valid (0 corrupt, 0 zero-byte, 0 unsupported formats).
* **Dimensions:** High-resolution photographs (predominantly $2560 \times 1440$ [61.4%], $2250 \times 4000$ [7.8%], $4000 \times 2250$ [4.9%]).

### 3.2 Annotation Format & Distribution
* **Format:** YOLOv8 Instance Segmentation Polygons.
* **Source Classes:** Only one class is annotated: `names: {0: fire-extinguisher}`.
* **Total Polygon Instances:**
  - `modal_train`: 3,136 polygons
  - `modal_val`: 885 polygons
  - `amodal_train`: 2,506 polygons
  - `amodal_val`: 724 polygons
  - **Total Modal Instances:** 4,021 polygons
  - **Total Amodal Instances:** 3,230 polygons
* **Coupling & Validity:** 0 orphan labels; 0 uncoupled images; 0 invalid coordinates; 0 out-of-bound vertices.

---

## 4. Semantic Audit for FIRE_EXTINGUISHER_BLOCKED

### 4.1 Ground Truth Visual Reality
1. **The Target Class Violation:**
   - Under NFPA 10 and OSHA 1910.157, `FIRE_EXTINGUISHER_BLOCKED` requires an extinguisher whose immediate 36-inch clearance envelope, physical pathway, or operating access is obstructed by objects (pallets, boxes, furniture, carts).
2. **What FireSafetyNet Actually Annotates:**
   - The authors developed an algorithm to compare **visible area** against **full extent** of fire extinguishers.
   - Every annotation in the dataset surrounds the **fire extinguisher itself**.
   - The occluding object (e.g. table, railing, chair, door frame) is **never annotated**.
   - No label denotes "obstruction" or "violation".
3. **The Unoccluded Baseline:**
   - 797 images (33.7% of the dataset) depict completely unobstructed fire extinguishers where modal polygon == amodal polygon.
4. **Direct Violation Count:**
   - **DIRECT_FIRE_EXTINGUISHER_BLOCKED_IMAGES = 0**
   - **DIRECT_FIRE_EXTINGUISHER_BLOCKED_ANNOTATIONS = 0**

---

## 5. Visual Audit & Representative Evidence

A stratified visual sample of 30 images was generated with side-by-side overlays and metadata banners, stored in:
`datasets/fire_safety/validation/dataset8_audit/visual_audit/`

* **Unobstructed Normals (`sample_01` to `sample_06`):** Clean, accessible wall-mounted extinguishers. Clear negative non-violation samples.
* **Partially Occluded (`sample_07` to `sample_14`):** Slight foreground occlusions (e.g. edge of a cabinet or base obscured).
* **Heavily Occluded (`sample_15` to `sample_22`):** Central bar, pillar, or object crossing the extinguisher, causing split modal fragments.
* **Fully Hidden in Modal (`sample_23` to `sample_25`):** Extinguisher completely obscured (empty modal file, full amodal polygon).
* **Ambiguous / Unusual (`sample_26` to `sample_30`):** Distant captures, complex perspectives.

---

## 6. Physical Scene, Duplicate & Leakage Analysis

1. **Unique Physical Scenes:** Estimated **1,907** independent physical scenes based on dHash connected components clustering (threshold $\le 4$).
2. **Video / Sequential Frame Families:** **188 multi-frame clusters** (up to 19 images per cluster), representing handheld camera walks around single extinguisher locations.
3. **Internal Exact Duplicates:** 0 duplicates within the 2,363 images.
4. **Cross-Dataset Duplicates vs Canonical Dataset:** **0 exact or near matches** against canonical FireSphere images (6,471 images).
5. **Train-Validation Split Leakage:** **138 image pairs** between the source train and val splits share high perceptual similarity (Hamming distance $\le 2$), proving that consecutive frames from the same physical extinguisher setups were split across train and validation sets.

---

## 7. Modal vs. Amodal Comparative Analysis

* **Modal:** Segments strictly visible pixels. Splits into disconnected polygons when occluded.
* **Amodal:** Segments the complete physical envelope including the occluded regions behind foreground barriers.
* **Leakage Warning:** Modal and amodal packages contain **100% duplicate images**. Combining them in standard detector training would cause severe training instability and boundary contradiction.
* **Utility:** Modal is ideal for line-of-sight visual detection; Amodal is valuable for spatial footprint localization. Neither provides obstruction violation bounding boxes.

---

## 8. Expired Extinguisher Decoupling Rule

Under FireSphere architecture:
- Inspection tag dates and maintenance compliance for `FIRE_EXTINGUISHER_EXPIRED` (Class 2) are decoupled into a dedicated high-resolution OCR pipeline:
  `Extinguisher Detection -> Inspection Tag Crop -> OCR / Date Extraction -> Validation Engine`
- Therefore, no condition labels in this dataset may be mapped to `FIRE_EXTINGUISHER_EXPIRED`.

---

## 9. FireSphere Class Mapping Decision Table

| Source Class | Proposed Target Class | Decision | Eligible Instances | Rationale |
| :--- | :--- | :--- | :--- | :--- |
| `0: fire-extinguisher (modal)` | `FIRE_EXTINGUISHER` (Generic Equipment) | `SUPPORTING_DATA_ONLY` | 4,021 | High-quality visible polygon segmentation; not a violation |
| `0: fire-extinguisher (amodal)` | `FIRE_EXTINGUISHER` (Generic Equipment) | `SUPPORTING_DATA_ONLY` | 3,230 | High-quality full extent segmentation; not a violation |
| `0: fire-extinguisher (unoccluded)` | `FIRE_EXTINGUISHER` (Negative Baseline) | `REJECT_AS_VIOLATION` | 797 | Fully compliant equipment; negative example for blocked |
| `0: fire-extinguisher (occluded)` | `FIRE_EXTINGUISHER_OCCLUDED` | `REVIEW_REQUIRED` | 3,224 (modal) | Optical occlusion $\ne$ physical access obstruction |
| `FIRE_EXTINGUISHER_BLOCKED` | `1: FIRE_EXTINGUISHER_BLOCKED` | `REJECT` | **0** | **Zero direct violation annotations exist** |
| `FIRE_EXTINGUISHER_EXPIRED` | `2: FIRE_EXTINGUISHER_EXPIRED` | `REJECT` | **0** | Handled via downstream OCR pipeline |

---

## 10. Multi-Dimensional Dataset Quality Assessment

| Assessment Dimension | Rating | Technical Summary |
| :--- | :--- | :--- |
| 1. Semantic Correctness (Target Class 1) | **LOW** | 0 genuine violation annotations for `FIRE_EXTINGUISHER_BLOCKED` |
| 2. Annotation Quality | **HIGH** | Precise multi-vertex polygons; 0 coordinate errors |
| 3. Image Quality | **HIGH** | 100% valid RGB high-resolution photographs; 0 corrupt files |
| 4. Scene Diversity | **MEDIUM** | Diverse German public buildings, universities, dorms |
| 5. Physical Scene Independence | **MEDIUM** | 1,907 unique physical scenes across 2,363 images; 188 video families |
| 6. Train/Val/Test Integrity | **LOW** | 138 train-val near-duplicate leakage pairs; 0 test split provided |
| 7. Duplicate Risk | **HIGH** | 100% duplicate images between modal and amodal packages |
| 8. Cross-Dataset Leakage Risk | **LOW** | 0 overlap with existing canonical dataset |
| 9. License Clarity | **LOW** | `NEEDS_VERIFICATION`; no license file packaged in source archives |
| 10. YOLO Compatibility | **REQUIRES_CONVERSION** | Native YOLO segmentation; converting to bbox produces generic extinguisher boxes |
| 11. Suitability for FireSphere Overall | **MEDIUM** | Valuable auxiliary benchmark for occlusion reasoning |
| 12. Suitability specifically for BLOCKED | **LOW** | Unusable as direct training ground truth for Target Class 1 |

---

## 11. Provenance & License

* **Dataset Name:** FireSafetyNet: An Image-Based Dataset with Pretrained Weights for Machine Learning-Driven Fire Safety Inspection
* **Authors:** Angelina Aziz, Jan Heinbach, Lukas Trost, Niklas Gard, Markus König
* **Institution:** Ruhr University Bochum (Chair of Computing in Engineering)
* **Publication:** Journal of Computing in Civil Engineering, ASCE (June 2025)
* **Official URL:** `https://ascelibrary.org/doi/10.1061/JCCEE5.CPENG-6264`
* **License Status:** `NEEDS_VERIFICATION`

---

## 12. Canonical Dataset Protection Audit

* Pre-Audit Canonical Images: `6,471`
* Post-Audit Canonical Images: `6,471`
* Pre-Audit Canonical Labels: `6,471`
* Post-Audit Canonical Labels: `6,471`
* Pre-Audit Manifest Checksums: 100% Identical
* Post-Audit Manifest Checksums: 100% Identical
* **CANONICAL DATASET MODIFIED: NO**

---

## 13. Final Decision & Phase 9 Gate

### Final Decision:
**`DATASET_8_PARTIALLY_USABLE`**

* **Eligible for Canonical Merge of `FIRE_EXTINGUISHER_BLOCKED`:** **0 images / 0 annotations** (0% eligible).
* **Eligible as Supporting Computer Vision Research Benchmark:** 1,562 occluded images (auxiliary occlusion reasoning) and 797 unobstructed images (negative non-violation baseline).
* **Canonical Merge Action:** **DO NOT MERGE.**

### Phase 9 Gate Status:
**`PHASE_9_STATUS = BLOCKED`**

Phase 9 training remains blocked until verified real-world violation data for `FIRE_EXTINGUISHER_BLOCKED` and other unrepresented classes is obtained.
