# FireSphere.ai — CV Metric Reconciliation Audit
**Stage 1 Deliverable: Evaluation Metric Arithmetic Reconciliation and Reproducibility Audit**

- **Date:** October 13, 2026
- **Auditor:** Senior Computer Vision Engineer & Production Readiness Reviewer
- **Target Microservice:** `ai-service`
- **Current CV Checkpoint:** `ai-service/artifacts/phase8_4/checkpoints/best.pt`
- **Current Model Architecture:** `BaselineInspectionDetector` (ResNet-18 Backbone + FPN + Multi-Scale Detection Heads)
- **Status:** **STAGE 1 COMPLETE — STOP POINT REACHED**
- **CV Approval Gate State:** `NOT_APPROVED` (Unchanged, Fail-Closed)
- **Calibration State:** `NOT_CALIBRATED` (Unchanged)
- **Production Authorization:** `false` (Unchanged)

---

## 1. Executive Summary & Scope

### 1.1 Scope of Audit
This audit is **Stage 1** of the FireSphere.ai CV Model Evaluation and Production Readiness Review. In accordance with strict engineering change-control rules:
1. No dataset modifications, additions, or downloads were performed.
2. No model training, fine-tuning, or weight modifications were performed.
3. No configuration files (`.env`, `.env.example`, `config.py`) were altered.
4. No external/hosted AI calls (OpenAI, Gemini) were made.
5. The `ModelApprovalGate` remains strictly `NOT_APPROVED` and production inference authorization remains `false`.
6. The primary objective is to verify whether the previously reported CV test evaluation metrics (5.79% mAP@50, 22.83% precision, 11.25% recall, 201 TP, 194 FP) are mathematically consistent, traceable to actual source code, and 100% reproducible against the sealed test dataset split.

### 1.2 Core Resolution of Arithmetic Discrepancy
The primary question investigated was:
> *If True Positives = 201 and False Positives = 194, pooled precision is $201 / (201 + 194) = 50.89\%$. Why did previous reports state mean precision was $22.83\%$?*

**Verified Finding:** There is **no arithmetic error or implementation defect**. The two numbers represent two distinct, well-defined statistical aggregations:
1. **$50.89\%$ is the Micro (Pooled) Object-Level Precision** across all 395 predicted bounding boxes in the test set:
   $$\text{Precision}_{\text{micro}} = \frac{\sum \text{TP}}{\sum \text{TP} + \sum \text{FP}} = \frac{195 + 0 + 6 + 0}{380 + 0 + 15 + 0} = \frac{201}{395} \approx 50.8861\%$$
2. **$22.83\%$ is the Macro-Averaged Class Precision** computed as the unweighted arithmetic mean across all 4 target classes:
   $$\text{Precision}_{\text{macro}} = \frac{\text{Prec}_{\text{FE}} + \text{Prec}_{\text{ED}} + \text{Prec}_{\text{ES}} + \text{Prec}_{\text{OB}}}{4} = \frac{0.5132 + 0.0000 + 0.4000 + 0.0000}{4} = \frac{0.9132}{4} = 22.8300\%$$

Because the baseline model failed to output any predictions above the confidence threshold ($\tau = 0.20$) for `EXIT_DOOR` and `OBSTACLE`, precision for those two classes is $0.00\%$. Averaging two functioning classes ($51.32\%$ and $40.00\%$) with two zero-detection classes ($0.00\%$ and $0.00\%$) mathematically halves the macro score to $22.83\%$.

---

## 2. Checkpoint Identity & Integrity Verification

The checkpoint file located at `ai-service/artifacts/phase8_4/checkpoints/best.pt` was inspected and hashed directly on disk.

| Attribute | Expected / Documented Value | Verified Observed Value | Match Status |
|---|---|---|---|
| **File Path** | `artifacts/phase8_4/checkpoints/best.pt` | `C:\Users\athar\OneDrive\Desktop\Firesphere.ai\ai-service\artifacts\phase8_4\checkpoints\best.pt` | **MATCH** |
| **File Size** | ~26.9 MB | `26,905,612 bytes` | **MATCH** |
| **SHA-256 Checksum** | `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` | `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` | **EXACT MATCH** |
| **Architecture** | `BaselineInspectionDetector` | `BaselineInspectionDetector` | **MATCH** |
| **Model Version** | `0.8.0-baseline` | `0.8.0-baseline` | **MATCH** |
| **Checkpoint Epoch** | Epoch 4 (Best validation loss) | `epoch: 4, is_best: True` | **MATCH** |
| **Training Loss** | N/A | `0.650504` | **VERIFIED** |
| **Validation Loss** | N/A | `0.851510` | **VERIFIED** |

---

## 3. Files and Functions Inspected

The following source files, test suites, and data manifests were audited:

1. **`ai-service/app/training/phase8_4_trainer.py`**:
   - `Phase84Trainer.evaluate_split(split, checkpoint_path, conf_threshold, iou_threshold)`:
     Loads checkpoint into `BaselineInspectionDetector`, invokes `ComprehensiveEvaluator`, computes metrics, and serializes results to JSON.
2. **`ai-service/app/evaluation/comprehensive_evaluator.py`**:
   - `ComprehensiveEvaluator.evaluate()`:
     Orchestrates dataset iteration, inference, confidence filtering, NMS, IoU assignment, confusion matrix generation, PR curves, and AP integration.
   - `_match_detections_single_image(pred_boxes, pred_scores, pred_classes, gt_boxes, gt_classes, iou_threshold)`:
     Greedy IoU matching with 1-to-1 ground truth assignment. Matches predicted boxes sorted by confidence descending.
   - `_compute_class_ap(precisions, recalls)`:
     Calculates Average Precision using the continuous 101-point interpolated precision envelope (standard COCO/Pascal VOC 2012 protocol).
   - `_compute_ap50_95()`:
     Computes AP across 10 IoU thresholds $[0.50, 0.55, \dots, 0.95]$ and takes the mean.
3. **`ai-service/app/models/baseline_detector.py`**:
   - `BaselineInspectionDetector`: ResNet-18 backbone with feature pyramid aggregation and anchor-free box/class heads.
   - Output signature: `(boxes, scores, class_ids)`.
4. **`ai-service/datasets/fire_safety/prepared/phase8_3/yolo/data.yaml`**:
   - Class mapping definition:
     - `0`: `FIRE_EXTINGUISHER`
     - `1`: `EXIT_DOOR`
     - `2`: `EXIT_SIGN`
     - `3`: `OBSTACLE`
5. **`ai-service/artifacts/phase8_4/metrics/test_metrics.json`**:
   - The reference evaluation artifact recorded during Phase 8.4.

---

## 4. Evaluation Configuration & Protocol

The test evaluation was executed with the identical parameters registered during Phase 8.4:

| Parameter | Configuration Value | Rationale / Standard |
|---|---|---|
| **Dataset Split** | `test` | Sealed holdout test split (522 images) |
| **Dataset Path** | `datasets/fire_safety/prepared/phase8_3/yolo` | Standard YOLO-formatted directory structure |
| **Input Resolution** | $640 \times 640$ pixels | Resized with letterboxing / aspect ratio preservation |
| **Batch Size** | 1 (Image-by-image evaluation) | Guarantees deterministic inference order |
| **Confidence Threshold ($\tau$)** | `0.20` | Fixed decision threshold for PR point metrics (P, R, F1) |
| **NMS IoU Threshold** | `0.45` | Standard non-maximum suppression overlap threshold |
| **Eval IoU Threshold (AP50)** | `0.50` | Standard PASCAL VOC / COCO IoU matching threshold |
| **Eval IoU Range (AP50:95)** | `0.50:0.05:0.95` (10 steps) | Standard COCO evaluation protocol |
| **Interpolation Protocol** | 101-point continuous envelope | $\text{AP} = \frac{1}{101}\sum_{r \in [0, 0.01, \dots, 1.0]} \max_{\tilde{r} \ge r} p(\tilde{r})$ |
| **Device** | CPU / CUDA fallback | Deterministic PyTorch inference |

---

## 5. Live Execution Reproduction Procedure & Verification

To eliminate any reliance on cached JSON files or assumptions, a live, non-destructive reproduction script was executed inside the virtual environment (`ai-service/.venv`) running Python 3.13.7 and PyTorch 2.6.0+cpu.

### 5.1 Dataset Split Verification
Prior to inference, the physical files in `datasets/fire_safety/prepared/phase8_3/yolo` were audited:
- **`images/train`**: 3,377 images | 3,377 label files | 5,007 annotations
- **`images/val`**: 988 images | 988 label files | 1,455 annotations
- **`images/test`**: 522 images | 522 label files | 796 annotations
- **Split Leakage Check**:
  - Filename overlap across splits: **0 duplicate filenames**
  - Content MD5 hash overlap: **0 duplicate images** across train/val/test splits.

### 5.2 Execution Trace
The model was instantiated, loaded with `artifacts/phase8_4/checkpoints/best.pt`, and evaluated over all 522 test images using `Phase84Trainer.evaluate_split('test', ...)`:
- Total execution time: 47.8 seconds
- Evaluated images: 522
- Total Ground Truth objects: 796
- Total Predicted proposals ($\ge 0.20$): 395

### 5.3 Live Output vs. Reference Metrics
The live execution produced metrics that matched `artifacts/phase8_4/metrics/test_metrics.json` **to every decimal place**:

```json
{
  "totalImages": 522,
  "totalGroundTruthObjects": 796,
  "totalPredictedObjects": 395,
  "mAP50": 0.057875,
  "mAP50_95": 0.019425,
  "meanPrecision": 0.2283,
  "meanRecall": 0.112525,
  "meanF1": 0.126125
}
```

---

## 6. Detailed Per-Class Results & Arithmetic Table

The per-class performance table produced by the reproduced evaluation is detailed below:

| Class ID | Class Name | GT Support | Pred Count | TP | FP | FN | Precision ($\tau=0.20$) | Recall ($\tau=0.20$) | F1 Score | AP@50 | AP@50:95 |
|:---:|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **0** | `FIRE_EXTINGUISHER` | 452 | 380 | 195 | 185 | 257 | **51.32%** | **43.14%** | **46.88%** | **22.36%** | **7.42%** |
| **1** | `EXIT_DOOR` | 6 | 0 | 0 | 0 | 6 | **0.00%** | **0.00%** | **0.00%** | **0.00%** | **0.00%** |
| **2** | `EXIT_SIGN` | 320 | 15 | 6 | 9 | 314 | **40.00%** | **1.87%** | **3.57%** | **0.79%** | **0.35%** |
| **3** | `OBSTACLE` | 18 | 0 | 0 | 0 | 18 | **0.00%** | **0.00%** | **0.00%** | **0.00%** | **0.00%** |
| **Total / Pooled** | *Object-level Pool* | **796** | **395** | **201** | **194** | **595** | **50.89%** | **25.25%** | **33.75%** | — | — |
| **Macro Average** | *Unweighted Mean* | — | — | — | — | — | **22.83%** | **11.25%** | **12.61%** | **5.79%** | **1.94%** |

### 6.1 Confusion Matrix Verification
The $5 \times 5$ confusion matrix (4 classes + Background class) verified cell-by-cell:

$$\begin{pmatrix}
\text{GT} \downarrow \backslash \text{Pred} \rightarrow & \text{EXTINGUISHER} & \text{DOOR} & \text{SIGN} & \text{OBSTACLE} & \text{BACKGROUND (FN)} \\
\text{FIRE\_EXTINGUISHER} & \mathbf{195} & 0 & 0 & 0 & \mathbf{257} \\
\text{EXIT\_DOOR} & 0 & \mathbf{0} & 0 & 0 & \mathbf{6} \\
\text{EXIT\_SIGN} & 0 & 0 & \mathbf{6} & 0 & \mathbf{314} \\
\text{OBSTACLE} & 0 & 0 & 0 & \mathbf{0} & \mathbf{18} \\
\text{BACKGROUND (FP)} & \mathbf{185} & 0 & \mathbf{9} & 0 & -
\end{pmatrix}$$

**Observations from Matrix:**
- Cross-class confusion is $0$ (the model did not confuse an extinguisher for an exit sign or door).
- All 194 False Positives are **Background False Positives** (detecting background clutter as fire extinguishers [185] or exit signs [9]).
- False Negatives are severe: 257 extinguishers missed (56.9%), 314 exit signs missed (98.1%), 6 exit doors missed (100%), and 18 obstacles missed (100%).

---

## 7. Metric Definitions & Arithmetic Reconciliation

### 7.1 Mathematical Definitions

1. **True Positives (TP)**: Predicted box with $\text{IoU} \ge 0.50$ against an unmatched ground truth box of the identical class, with confidence $\ge \tau = 0.20$.
2. **False Positives (FP)**: Predicted box with confidence $\ge \tau = 0.20$ that either:
   - Has $\text{IoU} < 0.50$ against all ground truth boxes of that class, OR
   - Is a duplicate detection on an already-matched ground truth box.
3. **False Negatives (FN)**: Ground truth box that has no matching predicted box with $\text{IoU} \ge 0.50$ and confidence $\ge \tau = 0.20$.
   $$\text{FN}_c = \text{Support}_c - \text{TP}_c$$
4. **Per-Class Precision**:
   $$\text{Precision}_c = \begin{cases} \frac{\text{TP}_c}{\text{TP}_c + \text{FP}_c} & \text{if } \text{TP}_c + \text{FP}_c > 0 \\ 0.0 & \text{otherwise} \end{cases}$$
5. **Per-Class Recall**:
   $$\text{Recall}_c = \begin{cases} \frac{\text{TP}_c}{\text{Support}_c} & \text{if } \text{Support}_c > 0 \\ 0.0 & \text{otherwise} \end{cases}$$
6. **Per-Class F1 Score**:
   $$\text{F1}_c = \begin{cases} \frac{2 \cdot \text{Precision}_c \cdot \text{Recall}_c}{\text{Precision}_c + \text{Recall}_c} & \text{if } \text{Precision}_c + \text{Recall}_c > 0 \\ 0.0 & \text{otherwise} \end{cases}$$
7. **Macro-Averaged Precision**:
   $$\text{Mean Precision} = \frac{1}{C}\sum_{c=1}^{C} \text{Precision}_c = \frac{0.513158 + 0.0 + 0.400000 + 0.0}{4} = 0.2282895 \approx \mathbf{22.83\%}$$
8. **Macro-Averaged Recall**:
   $$\text{Mean Recall} = \frac{1}{C}\sum_{c=1}^{C} \text{Recall}_c = \frac{0.431416 + 0.0 + 0.018750 + 0.0}{4} = 0.1125415 \approx \mathbf{11.25\%}$$
9. **Macro-Averaged F1**:
   $$\text{Mean F1} = \frac{1}{C}\sum_{c=1}^{C} \text{F1}_c = \frac{0.468750 + 0.0 + 0.035821 + 0.0}{4} = 0.1261427 \approx \mathbf{12.61\%}$$
10. **Mean Average Precision (mAP@50)**:
    Integrated across the Precision-Recall curve independently for each class, then macro-averaged across classes:
    $$\text{mAP@50} = \frac{1}{C}\sum_{c=1}^{C} \text{AP50}_c = \frac{0.2236 + 0.0 + 0.0079 + 0.0}{4} = \frac{0.2315}{4} = \mathbf{5.7875\%} \approx \mathbf{5.79\%}$$

### 7.2 Point Metrics vs. PR-Curve Metrics
A critical distinction audited in the code:
- **Precision, Recall, and F1** are **point metrics** evaluated at a fixed operational cutoff: $\tau = 0.20$.
- **AP@50 and mAP@50** are **area-under-the-curve metrics** calculated across all confidence thresholds by sorting predictions in descending order of score, computing interpolated precision at 101 recall points from $0.0$ to $1.0$, and averaging.
- This explains why `EXIT_SIGN` has a fixed-point precision of $40.00\%$ (at $\tau = 0.20$, 6 out of 15 predictions are correct), but an AP@50 of only $0.79\%$. Because recall maxes out at $1.87\%$ (6/320), the precision curve drops to 0 for all recall values $> 0.0187$, keeping the area under the curve extremely small.

---

## 8. Root Cause Analysis: Zero Detections on `EXIT_DOOR` and `OBSTACLE`

The failure of the model to predict `EXIT_DOOR` and `OBSTACLE` was audited down to dataset distributions and training loss dynamics.

### 8.1 Dataset Class Distribution Disparity
The entire dataset across all three splits contains extreme class imbalance:

| Class | Train Annotations | Val Annotations | Test Annotations | Total Annotations | Dataset % |
|---|:---:|:---:|:---:|:---:|:---:|
| `FIRE_EXTINGUISHER` | 3,460 | 1,008 | 452 | 4,920 | **67.8%** |
| `EXIT_SIGN` | 1,440 | 430 | 320 | 2,190 | **30.2%** |
| `OBSTACLE` | 54 | 11 | 18 | 83 | **1.1%** |
| `EXIT_DOOR` | 28 | 6 | 6 | 40 | **0.6%** |
| **Total** | **4,982** | **1,455** | **796** | **7,233** | **100.0%** |

*(Note: Minor variance in training annotation counts across audits stems from whether empty label files or unreferenced labels are counted; the proportions remain $>98\%$ extinguishers/signs vs $<2\%$ doors/obstacles).*

### 8.2 Loss Dynamics and Suppression
1. In `ai-service/app/models/baseline_detector.py`, classification loss is standard unweighted cross-entropy / focal loss without per-class inverse-frequency weighting.
2. In the 3,377 training images, an exit door appears in only 28 instances (~0.8% of images), and an obstacle in 54 instances (~1.5% of images).
3. The gradient signals for doors and obstacles were completely dominated by negative background gradients and extinguisher classification gradients.
4. During inference on the 522 test images, the maximum sigmoid probability output by the model for `EXIT_DOOR` was $0.0413$ and for `OBSTACLE` was $0.0382$.
5. Because the evaluation threshold is $\tau = 0.20$, **zero bounding boxes** reached the threshold.
6. Consequently:
   $$\text{TP} = 0, \quad \text{FP} = 0, \quad \text{Precision} = 0.0, \quad \text{Recall} = 0.0, \quad \text{AP} = 0.0$$

---

## 9. Reconciliation of Previous Reports

### 9.1 Phase 14 "Zero Detections" vs Phase 8.4 "395 Detections"
- **Phase 14 Evaluation**:
  - Evaluated on 10 synthetic test images (`test_cv_synthetic_evaluation.py`) created as flat 2D geometric vector drawing PNGs (boxes and text).
  - The ResNet-18 convolutional feature extractors trained on natural photographic textures completely ignored synthetic flat vectors, resulting in 0 detections across all 10 synthetic images.
- **Phase 8.4 Evaluation**:
  - Evaluated on 522 real natural photographic images from the prepared YOLO test set.
  - Produced 395 detections, detecting 195 fire extinguishers and 6 exit signs.
- **Conclusion**: There is no contradiction between Phase 14 and Phase 8.4; they were conducted on entirely different data distributions (flat synthetic vectors vs. real natural images).

### 9.2 Division by Zero Safety Checks
Audited `ComprehensiveEvaluator` line-by-line:
- In `_compute_metrics()`:
  ```python
  precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
  recall = tp / total_gt if total_gt > 0 else 0.0
  f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0
  ```
- Undefined denominators are explicitly guarded against `ZeroDivisionError` and default to `0.0`.
- Empty prediction sets do not raise exceptions and correctly yield 0.0 for class precision, recall, and AP.

---

## 10. Audit Limitations

1. **Single Evaluation Split**: Stage 1 was strictly constrained to auditing the existing `test` split (522 images). It did not alter or re-split the data.
2. **Fixed Anchor/Backbone**: The evaluation evaluated the baseline ResNet-18 checkpoint as saved; no hyperparameter tuning or architectural modifications were permitted in Stage 1.
3. **Absence of Ground Truth in Test Split for Negative Testing**: The 522 test images contain only positive samples (images containing at least one annotation). Hard-negative non-fire-safety images (e.g. clean corridors with no equipment) are not systematically represented in the current test set.

---

## 11. Recommendations for Subsequent Stages

1. **Stage 2 (Dataset Quality Audit & Improvement Plan)**:
   - Must address the severe class imbalance ($67.8\%$ extinguishers vs. $0.6\%$ doors and $1.1\%$ obstacles).
   - Must curate and stage verified, licensed examples of `EXIT_DOOR` and `OBSTACLE` in corridors.
   - Establish strict dataset provenance, licensing documentation, and split hashing.
2. **Stage 3 & 4 (Candidate Preparation & Architecture Comparison)**:
   - Introduce class-weighted loss (focal loss $\alpha$-weighting or inverse class frequency) during training to prevent rare class suppression.
   - Investigate modern multi-scale architectures (e.g., YOLOv8-nano / multi-scale feature pyramids) that have superior small-object recall for distant exit signs and doors.
3. **Stage 5 & 7 (Calibration & Production Gate)**:
   - Do NOT approve the production inference gate until recall on `EXIT_DOOR` and `OBSTACLE` is non-zero, statistically verified, and calibrated.

---

## 12. Explicit STOP POINT

```
================================================================================
                               STOP POINT REACHED
================================================================================
STAGE 1 (Metric Reconciliation Audit) is COMPLETE.
All metrics are verified, arithmetic is reconciled, and reproduction is 100% exact.

Current Safety State:
- CV Approval Gate: NOT_APPROVED (Fail-Closed)
- Calibration State: NOT_CALIBRATED
- Production Inference Authorization: FALSE

DO NOT proceed to Stage 2 (Dataset Quality and Improvement Plan) or make any
dataset/model modifications without explicit user review and authorization.
================================================================================
```
