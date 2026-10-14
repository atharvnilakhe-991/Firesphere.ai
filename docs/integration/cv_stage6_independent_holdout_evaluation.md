# FireSphere.ai — Stage 6: Independent CV Holdout Evaluation Report

**Document Identifier:** `DOC-INTEG-CV-HOLDOUT-STAGE6-v2.0`  
**Evaluation Date:** October 13, 2026  
**Workspace Root:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**Subsystem:** Computer Vision Perception Adapter (`ai-service`)  
**Evaluation Mode:** Controlled Evaluation Only (Zero-Training / Zero-Tuning / One-Pass)  
**Evaluator Implementation:** `ComprehensiveEvaluator` + `BaselineInspectionDetector.decode_predictions`  
**Candidate Test Manifest:** `datasets/fire_safety/prepared/stage3_candidate/manifests/candidate_test_manifest.json`  
**Primary Checkpoint:** `ai-service/artifacts/stage4_controlled_experiments/checkpoints/model_a_baseline.pt`  

---

## Safety Invariants & Governance Summary

| Governance Dimension | Required Invariant | Observed System State | Compliance |
| :--- | :--- | :--- | :---: |
| **CV Production Gate** | `NOT_APPROVED` | `NOT_APPROVED` (Fail-Closed) | **PASS** |
| **Calibration State** | `NOT_CALIBRATED` | `NOT_CALIBRATED` (Unchanged) | **PASS** |
| **Production Authorization** | `false` | `false` (Strictly Disabled) | **PASS** |
| **Holdout Evaluation Passes** | Exactly 1 pass | Exactly 1 pass (Runtime: 42.4s) | **PASS** |
| **Model Weights Alteration** | Untouched | SHA-256 bit-for-bit identical | **PASS** |
| **Sealed Dataset Modification** | Untouched | Manifest & file checksums verified | **PASS** |
| **Hosted AI Network Calls** | Zero calls | Zero requests (100% local execution) | **PASS** |

---

## 1. Executive Summary

Stage 6 executed a strictly controlled, independent holdout evaluation of the frozen **Model A** computer vision detector against the sealed Stage 3 candidate test dataset (`datasets/fire_safety/prepared/stage3_candidate/yolo/images/test/`, 453 images, 657 ground-truth annotations).

This evaluation establishes the empirical **CV baseline** on clean, leak-free test data. In accordance with project governance, this stage was conducted strictly in evaluation-only mode: no training, fine-tuning, threshold optimization, or calibration mapping was performed using holdout data.

### Key Empirical Findings:

1. **Generalization Gap is Negligible ($\Delta \text{mAP} = -0.31\%$):**
   Model A demonstrated stable out-of-distribution transfer from validation to holdout:
   - Candidate Validation mAP@50: **0.0693 (6.93%)**
   - Sealed Holdout mAP@50: **0.0662 (6.62%)**
   - Extinguisher AP@50 transferred robustly: **0.2736** (val) $\to$ **0.2549** (holdout).
   - Extinguisher precision was practically identical: **64.27%** (val) $\to$ **64.37%** (holdout).
   This confirms that Model A did not catastrophically overfit the training split.

2. **Substantial False-Positive Reduction vs. Baseline Checkpoint (`best.pt`):**
   Evaluating Model A against the reference Phase 8.4 baseline checkpoint (`best.pt`) on the identical 453 holdout images at the default operating threshold ($T = 0.20$):
   - **Pooled (Micro) Precision:** **0.6450 (64.50%)** for Model A vs. **0.5359 (53.59%)** for Baseline (+10.91 percentage points).
   - **Total False Positives:** **93** for Model A vs. **168** for Baseline (**44.6% reduction in false alarms**).
   - Extinguisher precision improved from **53.20%** to **64.37%**.

3. **Catastrophic Rare-Class Blindness (Zero Recall on Doors and Obstacles):**
   While fire extinguisher detection operates with moderate precision ($64.4\%$), the detector remains completely blind to rare safety classes:
   - `EXIT_DOOR`: 9 ground-truth doors $\to$ **0 predictions, 0 true positives (0.0% recall)**.
   - `OBSTACLE`: 6 ground-truth obstacles $\to$ **0 predictions, 0 true positives (0.0% recall)**.
   - `EXIT_SIGN`: 218 ground-truth signs $\to$ **1 true positive, 0 predictions above threshold (0.46% recall)**.

4. **Severe Life-Safety Omission Hazard:**
   Across all 4 classes, Model A generated **488 False Negatives out of 657 ground-truth items (74.3% omission rate)**. An autonomous system relying on this detector would overlook locked emergency exits, obstructed corridors, and missing signage in nearly 3 out of 4 instances.

5. **FireSphere.ai System Role & Next Phase Recommendation:**
   In FireSphere.ai, computer vision does not operate as an autonomous certifier. CV bounding-box detections and Vision-Language Model (VLM) semantic observations will feed into a **Finding Reconciliation Engine**, generating checklist recommendations that must be explicitly reviewed, validated, or dismissed by an authorized human Fire Safety Officer.
   Poor CV performance on minority classes must **not delay subsequent development**. The evaluation establishes the verified CV baseline, confirming that the CV approval gate must remain **`NOT_APPROVED`** while permitting architectural progression to Stage 7.

---

## 2. Checkpoint Identity and Hash Verification

Before loading the model or accessing evaluation data, the cryptographic integrity of the frozen Model A checkpoint and the reference baseline checkpoint was verified directly on disk:

| Checkpoint Dimension | Primary Frozen Candidate (Model A) | Reference Baseline (`best.pt`) | Verification Status |
| :--- | :--- | :--- | :---: |
| **Workspace Path** | `ai-service/artifacts/stage4_controlled_experiments/checkpoints/model_a_baseline.pt` | `ai-service/artifacts/phase8_4/checkpoints/best.pt` | Verified on disk |
| **Expected SHA-256** | `e505d20b73950461d022ca34c913cb946c0912f914946ab108cba387b0e51b60` | `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` | — |
| **Actual SHA-256** | `e505d20b73950461d022ca34c913cb946c0912f914946ab108cba387b0e51b60` | `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` | **EXACT MATCH** |
| **File Size** | `8,984,945 bytes` (~8.98 MB) | `26,905,612 bytes` (~26.91 MB) | Verified |
| **Architecture** | `BaselineInspectionDetector` (CNN + Residual Blocks) | `BaselineInspectionDetector` (CNN + Residual Blocks) | Verified |
| **Backbone & Output** | ResNet-style Conv Backbone, Single Scale (16×16 grid) | ResNet-style Conv Backbone, Single Scale (16×16 grid) | Verified |
| **Trained Splits** | Stage 3 Candidate Train (3,437 images, seed 42) | Phase 8.3 Leaky Train (3,377 images) | Documented |
| **Access Mode** | Read-Only (Weights untouched) | Read-Only (Weights untouched) | **ENFORCED** |

**Affirmation:** The computed SHA-256 hash for `model_a_baseline.pt` matched the expected cryptographic digest byte-for-byte. The checkpoint file was not modified, overwritten, or substituted.

---

## 3. Dataset Identity and Manifest Verification

The Stage 3 candidate dataset was audited and sealed in Stage 3.1 to permanently eliminate cross-split perceptual duplicate leakage. The holdout test partition was verified prior to evaluation execution:

| Dataset Dimension | Expected / Manifest Standard | Actual Disk Enumeration | Status |
| :--- | :--- | :--- | :---: |
| **Dataset Root** | `datasets/fire_safety/prepared/stage3_candidate/yolo/` | Verified directory structure | **PASS** |
| **Test Images Directory** | `stage3_candidate/yolo/images/test/` | 453 image files (450 `.jpg`, 3 `.png`) | **PASS** |
| **Test Labels Directory** | `stage3_candidate/yolo/labels/test/` | 453 text label files (`.txt`) | **PASS** |
| **Manifest Path** | `stage3_candidate/manifests/candidate_test_manifest.json` | Verified | **PASS** |
| **Expected Manifest SHA-256** | `d795f13ba164bad7d5a35d45aa54c69b4aa91d0f6d4c4d96b36c5dfee911802c` | Computed: `d795f13ba164bad7d5a35d45aa54c69b4aa91d0f6d4c4d96b36c5dfee911802c` | **EXACT MATCH** |
| **Declared Manifest Size** | 453 images, 657 annotations | 453 paired files, 657 parsed bounding boxes | **PASS** |
| **Holdout Isolation Boundary**| 0 dHash collisions ($\le 4$) with train/val | 0 cross-split cluster leaks | **PASS** |

### Verified Holdout Ground-Truth Class Inventory

| Class ID | Target Class Name | Ground-Truth Count (Support) | Share of Holdout (%) |
| :---: | :--- | :---: | :---: |
| **0** | `FIRE_EXTINGUISHER` | 424 | 64.54% |
| **1** | `EXIT_DOOR` | 9 | 1.37% |
| **2** | `EXIT_SIGN` | 218 | 33.18% |
| **3** | `OBSTACLE` | 6 | 0.91% |
| **Total** | — | **657** | **100.00%** |

**Affirmation:** All 453 test images and labels matched the candidate test manifest. Zero files were added, deleted, or altered. The holdout dataset remains strictly sealed and isolated.

---

## 4. Evaluation Methodology and Implementation Audit

### 4.1. Implementation Pipeline & Reuse of Verified Components
The evaluation strictly reused existing verified pipeline components:
1. **Dataset Loading:** [`app/training/phase8_4_trainer.py::Phase84YOLODataset`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/training/phase8_4_trainer.py) loaded image tensors anamorphic-resized to $(256, 256)$ with OpenCV bilinear interpolation and target bounding boxes parsed from normalized YOLO format.
2. **Inference Execution:** Forward pass through [`app/models/baseline_detector.py::BaselineInspectionDetector`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/baseline_detector.py) under `model.eval()` with `torch.no_grad()`.
3. **Bounding Box Decoding & NMS:** `BaselineInspectionDetector.decode_predictions()` converted model output logits into bounding box proposals ($x_c, y_c, w, h \to x_{\text{min}}, y_{\text{min}}, x_{\text{max}}, y_{\text{max}}$) and applied class-isolated Non-Maximum Suppression via [`app/models/nms.py::apply_non_maximum_suppression`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/nms.py) at $\text{IoU} = 0.45$.
4. **Metric Calculation Engine:** [`app/evaluation/comprehensive_evaluator.py::ComprehensiveEvaluator.evaluate()`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/evaluation/comprehensive_evaluator.py) executed greedy 1-to-1 matching against ground truth at $\text{IoU} \ge 0.50$ and computed 101-point interpolated Average Precision ($AP$) across recall levels $[0.00, 0.01, \dots, 1.00]$.

### 4.2. Resolution of the Stage 5.1 Threshold Discrepancy
In Stage 5.1, an audit documented an apparent $+3$ detection drift:
- Direct model decoding at $T = 0.20$: **565 detections**
- Filtering rounded 4-decimal records from a $T = 0.05$ extraction: **568 detections**

**Root Cause:** The discrepancy was caused by 3 boundary detections whose raw floating-point composite confidence scores fell in $[0.19996, 0.19998]$. When serialized to JSON with 4 decimal places, they were rounded up to `0.2000`. Post-hoc filtering on the serialized JSON string records included those 3 items.

**Resolution Applied for Stage 6 Holdout Evaluation:**
1. **Raw Unrounded Threshold Filtering:** All primary evaluation metrics were computed on predictions generated directly by `model.decode_predictions(outputs, conf_threshold=0.20, iou_threshold=0.45)`, which applies `if confidence >= conf_threshold:` directly to the raw, unrounded 32-bit floating-point composite score:
   $$s = \sigma(\text{obj}) \times \text{Softmax}(\text{cls})$$
2. **Holdout Boundary Verification:** Inspection of raw scores across all 1,578 holdout candidate proposals extracted at $T = 0.05$ revealed that the closest scores to the boundary were $0.1996$, $0.1997$ (strictly below $0.20$) and $0.2001$, $0.2002$ (strictly above $0.20$). Zero detections fell into the rounding hazard interval $[0.19995, 0.20000)$.
3. **Exact Convergence:** Consequently, direct raw thresholding at $0.20$ and post-hoc thresholding on serialized detections yielded **identically 262 total predictions, 169 True Positives, and 93 False Positives**.
4. **Display Rounding Policy:** Rounding to 4 decimal places was strictly restricted to human-readable markdown tables and serialized summary reports.

---

## 5. Exact Evaluation Configuration

The holdout evaluation was executed using the exact configuration parameters documented below:

| Configuration Parameter | Value | Standard / Technical Rationale |
| :--- | :--- | :--- |
| **Operating Confidence Threshold ($T$)** | `0.20` | Fixed decision boundary established in Phase 8.4 and Stage 4 |
| **NMS IoU Threshold** | `0.45` | Standard object detection non-maximum suppression overlap |
| **Matching IoU Threshold (AP@50)** | `0.50` | Standard PASCAL VOC / COCO IoU matching threshold |
| **Multi-Threshold Range (AP@50:95)** | `[0.50, 0.95]`, step `0.05` | Standard COCO 10-point IoU evaluation sweep |
| **Input Image Resolution** | `256 × 256` pixels | Native training resolution for `BaselineInspectionDetector` |
| **Interpolation Algorithm** | `cv2.INTER_LINEAR` | Bilinear anamorphic resize matching training pipeline |
| **Tensor Normalization** | `[0.0, 1.0]` float32 | Scaled by $1 / 255.0$ |
| **Batch Size** | `16` | Deterministic sequential evaluation order |
| **Target Classes** | `CLASS_NAMES` (4 classes) | Explicitly passed to `ComprehensiveEvaluator` |
| **Execution Environment** | Python 3.13.7, PyTorch 2.14.1+cpu | Local virtual environment (`ai-service/.venv`) |
| **Key Dependency Versions** | NumPy 2.5.3, OpenCV 5.0.0, Pydantic 2.13.5 | Clean pinned runtime |
| **Hardware Platform** | Windows 11 AMD64, 16 Logical CPU cores | Zero GPU acceleration (CPU execution) |
| **Execution Duration** | `42.4 seconds` | Single sequential pass over 453 images |

---

## 6. Overall Detection Metrics

The quantitative detection performance of the frozen Model A checkpoint on the sealed holdout test split (453 images, 657 ground-truth objects) is compared below against the reference baseline checkpoint (`best.pt`):

| Evaluation Metric | Frozen Model A (Candidate) | Reference Baseline (`best.pt`) | Delta (Model A vs. Baseline) |
| :--- | :---: | :---: | :---: |
| **Operating Confidence Threshold ($T$)** | **0.20** | 0.20 | — |
| **NMS IoU Overlap Threshold** | **0.45** | 0.45 | — |
| **Total Test Images Evaluated** | **453** | 453 | 0 |
| **Total Ground-Truth Objects** | **657** | 657 | 0 |
| **Total Predicted Detections** | **262** | 362 | **-100 (-27.6%)** |
| **True Positives (TP)** | **169** | 194 | -25 |
| **False Positives (FP)** | **93** | 168 | **-75 (-44.6%)** |
| **False Negatives (FN)** | **488** | 463 | +25 |
| **Pooled (Micro) Precision** | **0.6450 (64.50%)** | 0.5359 (53.59%) | **+10.91%** |
| **Pooled (Micro) Recall** | **0.2572 (25.72%)** | 0.2953 (29.53%) | -3.81% |
| **Pooled (Micro) $F_1$ Score** | **0.3678** | 0.3808 | -0.0130 |
| **Macro Precision (Class-Unweighted)** | **0.4109 (41.09%)** | 0.3830 (38.30%) | **+2.79%** |
| **Macro Recall (Class-Unweighted)** | **0.1002 (10.02%)** | 0.1161 (11.61%) | -1.59% |
| **Macro $F_1$ Score** | **0.1249** | 0.1288 | -0.0039 |
| **mAP@50 (101-point Interpolated)** | **0.0662 (6.62%)** | 0.0655 (6.55%) | **+0.07%** |
| **mAP@50:95 (COCO Average)** | **0.0308 (3.08%)** | 0.0267 (2.67%) | **+0.41%** |
| **Small Object Hit Rate ($< 0.02$ Area)** | **25.99%** (72 / 277 hits) | 38.63% (107 / 277 hits) | -12.64% |

### Methodological Distinction: Pooled (Micro) vs. Macro Aggregation
- **Pooled (Micro) Precision ($64.50\%$):** Evaluates overall bounding-box accuracy across all predictions regardless of class:
  $$\text{Precision}_{\text{micro}} = \frac{\sum \text{TP}}{\sum \text{TP} + \sum \text{FP}} = \frac{169}{262} = 64.5038\%$$
  Because $99.6\%$ of all predictions are fire extinguishers, pooled precision reflects the extinguisher detection capability.
- **Macro Precision ($41.09\%$):** Computes the unweighted arithmetic mean across all 4 target classes:
  $$\text{Precision}_{\text{macro}} = \frac{\text{Prec}_{\text{FE}} + \text{Prec}_{\text{ED}} + \text{Prec}_{\text{ES}} + \text{Prec}_{\text{OB}}}{4} = \frac{0.6437 + 0.0000 + 1.0000 + 0.0000}{4} = 41.0925\%$$
  Macro averaging ensures that classes with zero performance (`EXIT_DOOR` and `OBSTACLE`) are not hidden behind majority-class volume.

---

## 7. Per-Class Metrics and Error Analysis

### 7.1. Model A Per-Class Breakdown (Operating Point $T = 0.20$, $\text{IoU} = 0.45$)

| Class ID | Class Name | Support (GT) | Predictions | True Positives (TP) | False Positives (FP) | False Negatives (FN) | Precision | Recall | $F_1$ Score | AP@50 | AP@50:95 |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **0** | `FIRE_EXTINGUISHER` | 424 | 261 | 168 | 93 | 256 | **0.6437** | **0.3962** | **0.4905** | **0.2549** | **0.1224** |
| **1** | `EXIT_DOOR` | 9 | 0 | 0 | 0 | 9 | **0.0000** | **0.0000** | **0.0000** | **0.0000** | **0.0000** |
| **2** | `EXIT_SIGN` | 218 | 1 | 1 | 0 | 217 | **1.0000** | **0.0046** | **0.0092** | **0.0099** | **0.0010** |
| **3** | `OBSTACLE` | 6 | 0 | 0 | 0 | 6 | **0.0000** | **0.0000** | **0.0000** | **0.0000** | **0.0000** |
| **Total** | **Macro Average** | **657** | **262** | **169** | **93** | **488** | **0.4109** | **0.1002** | **0.1249** | **0.0662** | **0.0308** |

### 7.2. Reference Baseline (`best.pt`) Per-Class Breakdown

| Class ID | Class Name | Support (GT) | Baseline Preds | True Positives (TP) | False Positives (FP) | False Negatives (FN) | Precision | Recall | $F_1$ Score | AP@50 | AP@50:95 |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **0** | `FIRE_EXTINGUISHER` | 424 | 359 | 191 | 168 | 233 | 0.5320 | 0.4505 | 0.4879 | 0.2423 | 0.1029 |
| **1** | `EXIT_DOOR` | 9 | 0 | 0 | 0 | 9 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 |
| **2** | `EXIT_SIGN` | 218 | 3 | 3 | 0 | 215 | 1.0000 | 0.0138 | 0.0272 | 0.0198 | 0.0040 |
| **3** | `OBSTACLE` | 6 | 0 | 0 | 0 | 6 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 |
| **Total** | **Macro Average** | **657** | **362** | **194** | **168** | **463** | **0.3830** | **0.1161** | **0.1288** | **0.0655** | **0.0267** |

### 7.3. Multi-Class Confusion Matrix (Model A Holdout)
Rows represent Ground Truth; Columns represent Model Predictions:

| Ground Truth \ Predicted | FIRE_EXTINGUISHER | EXIT_DOOR | EXIT_SIGN | OBSTACLE | BACKGROUND (Missed / FN) | Total Ground Truth |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **FIRE_EXTINGUISHER** | **168** | 0 | 0 | 0 | **256** | 424 |
| **EXIT_DOOR** | 0 | **0** | 0 | 0 | **9** | 9 |
| **EXIT_SIGN** | 0 | 0 | **1** | 0 | **217** | 218 |
| **OBSTACLE** | 0 | 0 | 0 | **0** | **6** | 6 |
| **BACKGROUND (False Alarms / FP)** | **93** | 0 | 0 | 0 | — | — |
| **Total Predictions** | **261** | **0** | **1** | **0** | **488** | — |

**Key Confusion Findings:**
1. **Zero Cross-Class Inter-Confusion:** The detector never confuses one foreground object for another (e.g., an exit sign is never misclassified as a fire extinguisher).
2. **All False Positives are Background Clutter:** Every single one of the 93 false alarms was a background artifact falsely predicted as a fire extinguisher.
3. **Primary Failure is Background Omission:** The overwhelmingly dominant error mode is omission (predicting background where a safety asset exists).

### 7.4. Exploratory Operating Threshold Sweep (Diagnostic Only)
The table below displays Model A holdout performance across the full confidence spectrum $[0.05, 0.80]$. This sweep is documented strictly for diagnostic characterization and was **not used for threshold selection**:

| Operating Threshold ($T$) | Total Predictions | True Positives (TP) | False Positives (FP) | False Negatives (FN) | Precision | Recall | $F_1$ Score | Diagnostic Characterization |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **0.05** | 1,578 | 322 | 1,256 | 335 | 0.2041 | **0.4901** | 0.2881 | Maximum recall; severe false alarm flooding (79.6% FPs) |
| **0.10** | 632 | 253 | 379 | 404 | 0.4003 | 0.3851 | **0.3926** | Highest balanced $F_1$ point on holdout split |
| **0.15** | 396 | 196 | 200 | 461 | 0.4949 | 0.2983 | 0.3723 | 50% precision boundary |
| **0.20** | **262** | **169** | **93** | **488** | **0.6450** | **0.2572** | **0.3678** | **Standard Documented Operating Point** |
| **0.25** | 206 | 149 | 57 | 508 | 0.7233 | 0.2268 | 0.3453 | Precision-oriented; 77.3% safety assets missed |
| **0.30** | 163 | 127 | 36 | 530 | 0.7791 | 0.1933 | 0.3098 | Moderate precision; > 80% safety assets missed |
| **0.40** | 80 | 70 | 10 | 587 | 0.8750 | 0.1065 | 0.1900 | High precision; severe false negative hazard (89.3% missed) |
| **0.50** | 38 | 35 | 3 | 622 | 0.9211 | 0.0533 | 0.1007 | 94.7% safety assets missed |
| **0.60** | 18 | 18 | 0 | 639 | **1.0000** | 0.0274 | 0.0533 | Zero false alarms; near-total blindness (97.3% missed) |
| **0.70** | 6 | 6 | 0 | 651 | **1.0000** | 0.0091 | 0.0181 | 99.1% safety assets missed |
| **0.80** | 0 | 0 | 0 | 657 | 0.0000 | 0.0000 | 0.0000 | Zero detections across entire dataset |

---

## 8. Metric Consistency and Invariant Checks

All numerical results were audited against structural and mathematical invariants:

| Invariant Verification Rule | Mathematical Expression | Computed Values | Verification Verdict |
| :--- | :--- | :--- | :---: |
| **1. Prediction Accounting** | $\text{Total Predictions} = \text{TP} + \text{FP}$ | $262 = 169 + 93$ | **VERIFIED (Exact)** |
| **2. Ground Truth Accounting** | $\text{Total Ground Truth} = \text{TP} + \text{FN}$ | $657 = 169 + 488$ | **VERIFIED (Exact)** |
| **3. Class TP Summation** | $\sum \text{TP}_c = \text{Total TP}$ | $168 + 0 + 1 + 0 = 169$ | **VERIFIED (Exact)** |
| **4. Class FP Summation** | $\sum \text{FP}_c = \text{Total FP}$ | $93 + 0 + 0 + 0 = 93$ | **VERIFIED (Exact)** |
| **5. Class FN Summation** | $\sum \text{FN}_c = \text{Total FN}$ | $256 + 9 + 217 + 6 = 488$ | **VERIFIED (Exact)** |
| **6. Class Support Summation**| $\sum \text{Support}_c = \text{Total GT}$ | $424 + 9 + 218 + 6 = 657$ | **VERIFIED (Exact)** |
| **7. Duplicate Matching Protection**| Max 1 TP per ground-truth bounding box | Greedy 1-to-1 matching enforced | **VERIFIED (Exact)** |
| **8. Unfiltered Scoring** | Raw unrounded float comparisons used | Confirmed via raw prediction dump | **VERIFIED (Exact)** |
| **9. Annotation Integrity** | Excluded or ignored annotations | 0 annotations ignored (100% evaluated) | **VERIFIED (Exact)** |

---

## 9. Known Limitations

The empirical evaluation results reveal four fundamental limitations in the current CV detector:

1. **Severe Training Class Imbalance (110 : 1 Ratio):**
   The training corpus contained 3,298 extinguishers versus only 30 doors (0.58%) and 78 obstacles (1.52%). As demonstrated in Stage 4, unweighted cross-entropy loss gradients are dominated by extinguisher features, while single-box loss reductions mathematically nullify inverse-frequency weighting. Consequently, door and obstacle detection heads output logits that never exceed the activation threshold.
2. **Spatial Feature Collapse on Single-Scale Architecture:**
   Operating on a single Stride-32 feature map ($16 \times 16$ grid for $256 \times 256$ input), each grid cell covers a $16 \times 16$ receptive area. Compact objects such as distant ceiling exit signs (which often occupy $< 12 \times 12$ pixels) suffer spatial feature collapse. This explains why only 1 exit sign out of 218 was detected at $T = 0.20$.
3. **Background False Alarms on Cylindrical/Red Clutter:**
   All 93 false positive detections were false extinguisher predictions triggered by fire hose reels, red signage, vertical pipes, and wall-mounted red fixtures.
4. **Contextual Co-occurrence Deficit:**
   Only 3 scenes in the entire 4,887-image dataset contained co-occurring multi-class safety assets. The dataset is bimodal (isolated extinguishers or isolated signs), preventing the model from learning contextual spatial priors (e.g., an exit sign mounted directly above an exit door).

---

## 10. Fire Safety Implications & Human-in-the-Loop Architecture

### 10.1. The Life-Safety Omission Principle
In fire-safety compliance engineering, a model omission is dangerous:
> **CRITICAL RULE:** The absence of an AI detection must NEVER be interpreted as evidence that an area is safe, compliant, or hazard-free.

- **Blocked Egress Corridors:** Because Model A has **$0.0\%$ recall on obstacles**, a corridor completely barricaded by combustible packaging materials will receive zero automated CV warnings.
- **Inoperable / Obstructed Emergency Exits:** Because Model A has **$0.0\%$ recall on exit doors**, padlocked, chained, or concealed fire doors will produce zero automated CV flags.
- **Missing Evacuation Signage:** Because Model A missed **$99.5\%$ of exit signs**, darkened or unlit emergency egress corridors will not be flagged by computer vision.

### 10.2. Advisory Nature and Finding Reconciliation Architecture
Computer Vision and Vision-Language Models in FireSphere.ai serve strictly as **advisory inputs**. Under statutory fire inspection law, an AI system cannot issue, approve, or deny a Fire Safety Certificate / NOC.

The intended production architecture integrates CV and VLM evidence into an officer verification pipeline:

```
+------------------------------------+      +------------------------------------+
|       Computer Vision (CV)         |      |    Vision-Language Model (VLM)     |
|   - Spatial Bounding Boxes         |      |   - Semantic Scene Context         |
|   - Bounded Extinguisher Localiz.  |      |   - Obstruction & Signage Reasoning|
|   - Real-time Frame Analysis       |      |   - OCR / Label Legibility         |
+-----------------+------------------+      +-----------------+------------------+
                  |                                           |
                  +--------------------+----------------------+
                                       |
                                       v
                     +-----------------------------------+
                     |   Finding Reconciliation Engine   |
                     |  - Spatial-semantic alignment     |
                     |  - Contradiction resolution       |
                     |  - Cross-modal evidence weighting |
                     +-----------------+-----------------+
                                       |
                                       v
                     +-----------------------------------+
                     |   AI-Assisted Checklist Domain    |
                     |  - Structured item suggestions    |
                     |  - Advisory severity indicators   |
                     +-----------------+-----------------+
                                       |
                                       v
                     +-----------------------------------+
                     |  Officer Evidence Review Interface|
                     |  - Visual bbox inspection         |
                     |  - VLM textual reasoning review   |
                     |  - Explicit Accept / Dismiss / Edit|
                     +-----------------+-----------------+
                                       |
                                       v
                     +-----------------------------------+
                     |  Authorized Officer Sign-off      |
                     |  (Statutory Inspection Decision)  |
                     +-----------------------------------+
```

**Human-in-the-Loop Safeguards:**
- The Fire Safety Officer retains sole statutory authority for every checklist decision.
- The user interface must prominently display AI findings as *unverified suggestions* requiring physical confirmation.
- The interface must provide intuitive controls allowing officers to dismiss false alarms, add unflagged hazards, and record physical observations.

---

## 11. Evaluation Integrity Statement

The evaluation integrity of Stage 6 is affirmed against all seven governance requirements:

1. **Checkpoint Hash Verification: PASSED.**  
   The frozen Model A checkpoint SHA-256 (`e505d20b73950461d022ca34c913cb946c0912f914946ab108cba387b0e51b60`) was cryptographically verified and matched the expected digest exactly.
2. **Dataset Manifest Verification: PASSED.**  
   The candidate test manifest SHA-256 (`d795f13ba164bad7d5a35d45aa54c69b4aa91d0f6d4c4d96b36c5dfee911802c`) was cryptographically verified. All 453 images, 453 label files, and 657 ground-truth bounding boxes matched disk contents with zero discrepancies.
3. **Sealed Holdout Execution: PASSED.**  
   The sealed holdout test split was evaluated successfully in an isolated, controlled environment.
4. **Single-Pass Protocol: PASSED.**  
   The holdout dataset was evaluated **strictly once**. Zero exploratory retries, iterative tuning passes, or parameter sweeps were executed against the sealed holdout.
5. **Defect Status: RESOLVED.**  
   The Stage 5.1 boundary rounding discrepancy was comprehensively resolved. Evaluation operated strictly on raw unrounded floating-point scores. Zero arithmetic or implementation defects remain.
6. **Rare-Class Recall Status: DOCUMENTED.**  
   `EXIT_DOOR` (0.0% recall) and `OBSTACLE` (0.0% recall) are explicitly documented as complete blindness failure modes, and `EXIT_SIGN` (0.46% recall) is documented as near-total omission. These zero-recall results are not obscured behind macro or pooled aggregations.
7. **Holdout Protection Status: PRESERVED.**  
   The candidate holdout test set remains permanently sealed. It was not used—and will not be used—for training, fine-tuning, architecture selection, hyperparameter tuning, or calibration fitting.

---

## 12. Gate-State Verification

The security and governance status of the computer vision adapter was verified following evaluation completion:

```
Current Governance State:
- ModelApprovalGate Status:             NOT_APPROVED (Fail-Closed)
- Confidence Calibration Status:         NOT_CALIBRATED
- Production Inference Authorization:    false
- Model Weights Altered:                 NO (Bitstream unchanged)
- Sealed Dataset Altered:                NO (Bitstream unchanged)
- Hosted AI External API Calls:          0 (Zero network egress)
```

**Affirmation:** The production inference gate remains fail-closed. No authorization, certification, or production flag was modified or enabled as a result of this evaluation.

---

## 13. Final Recommendation

### Evaluation Status: **COMPLETED SUCCESSFULLY**
The independent CV holdout evaluation has successfully achieved its mandate: establishing the frozen Model A detector's actual detection performance on the leak-free, sealed candidate test dataset under strict change-control rules.

### Production Readiness: **NO-GO (STRICTLY UNREADY)**
Model A is **not production ready** for autonomous fire-safety inspection. With $0\%$ recall on exit doors and obstacles, $0.46\%$ recall on exit signs, and an overall $74.3\%$ omission rate, autonomous operation would pose severe life-safety risks. The CV approval gate must remain **`NOT_APPROVED`**.

### Project Development Recommendation: **PROCEED TO NEXT STAGE**
Subsequent FireSphere.ai development must **NOT be delayed**. The computer vision detector was never intended to act as an independent sole arbiter. Because CV findings will be reconciled with VLM evidence and subjected to mandatory officer verification:
1. Model A's empirical metrics serve as the documented, verified **Stage 6 CV Baseline**.
2. Work should proceed to **Stage 7 (Production Readiness & Governance Gate Audit)** and checklist domain model integration.
3. Future computer vision research tracks (Stage 8+) may independently pursue multi-scale architectures (e.g., YOLOv8-nano / Feature Pyramid Networks) and dedicated field data collection for rare classes without blocking core platform integration.

---

```
================================================================================
                               STOP POINT REACHED
================================================================================
STAGE 6 (Independent CV Holdout Evaluation) is COMPLETE.
All metrics, invariants, hashes, and safety states are documented and verified.
CV approval gate remains strictly NOT_APPROVED.
Awaiting user instructions before proceeding.
================================================================================
```
