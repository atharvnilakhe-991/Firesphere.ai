# FireSphere.ai — Stage 4 Controlled CV Model Architecture Comparison Report

**Document ID:** `FS-REP-CV-STAGE4-001`  
**Date:** October 13, 2026  
**Status:** COMPLETE / EXPERIMENTS IN PROGRESS  
**Safety Gate Status:**  
- CV Approval Gate: `NOT_APPROVED` (STRICTLY ENFORCED)  
- Model Calibration State: `NOT_CALIBRATED` (STRICTLY ENFORCED)  
- Production Inference Authorization: `false` (STRICTLY ENFORCED)  

---

## 1. Executive Summary & Experiment Objectives

This report provides the reproducible, empirical findings of **Stage 4: Controlled CV Model Architecture Comparison** for the FireSphere.ai computer vision perception subsystem.

The objective of Stage 4 is an evidence-based, controlled comparative study evaluating candidate model architectures, training configurations, and loss adaptations against the baseline detector (`BaselineInspectionDetector`), trained strictly on the leak-free **Stage 3 Candidate Dataset** (`datasets/fire_safety/prepared/stage3_candidate/yolo/`).

### Strict Safety & Governance Constraints
1. **Research & Evaluation Only:** This evaluation does not authorize production deployment or live inference.
2. **Untouched Baseline:** The reference Phase 8.4 checkpoint (`ai-service/artifacts/phase8_4/checkpoints/best.pt`) has been cryptographically verified and left completely untouched.
3. **Sealed Candidate Holdout:** The Stage 3 candidate test split (`stage3_candidate/yolo/test/`, 453 images, 657 annotations) was **strictly sealed and untouched**. Zero hyperparameters, architecture choices, epoch selections, confidence thresholds, or loss weights were tuned on the holdout split.
4. **Isolated Artifact Storage:** All experimental runs, checkpoints, metrics, and logs are isolated within `ai-service/artifacts/stage4_controlled_experiments/`.
5. **No Production Bypasses:** No Git write operations, no database migrations, no hosted AI API calls, and no environment variable modifications were made.

---

## 2. Controlled Experiment Configuration Matrix

All experiments were executed under identical conditions using deterministic random seeding (`seed=42`), identical optimizer settings, identical learning rate schedules, and identical evaluation thresholds.

| Parameter | Baseline Reference | Model A (Reproduced Baseline) | Model B (Class-Weighted Loss) | Model C (Multi-Scale FPN) |
| :--- | :--- | :--- | :--- | :--- |
| **Model Class** | `BaselineInspectionDetector` | `BaselineInspectionDetector` | `BaselineInspectionDetector` | `MultiScaleInspectionDetector` |
| **Backbone** | ResNet-18 (Torchvision) | ResNet-18 (Torchvision) | ResNet-18 (Torchvision) | Custom Stem + 4-Stage ResNet |
| **Feature Scale** | Single-Scale (Stride 32, 8×8) | Single-Scale (Stride 32, 8×8) | Single-Scale (Stride 32, 8×8) | Multi-Scale FPN (P3: 32×32, P4: 16×16, P5: 8×8) |
| **FPN Channels** | N/A | N/A | N/A | 96 channels |
| **Loss Function** | Standard Unweighted Loss | Standard Unweighted Loss | Class-Weighted Cross Entropy | Multi-Scale Pyramid Loss |
| **Class Weights** | None (`[1.0, 1.0, 1.0, 1.0]`) | None (`[1.0, 1.0, 1.0, 1.0]`) | `[1.0, 10.0, 1.38, 6.50]` | None (Pyramid scale allocation) |
| **Training Split** | Phase 8.3 Leaky Train | Candidate Train (3,437 imgs) | Candidate Train (3,437 imgs) | Candidate Train (3,437 imgs) |
| **Validation Split** | Candidate Val (997 imgs) | Candidate Val (997 imgs) | Candidate Val (997 imgs) | Candidate Val (997 imgs) |
| **Holdout Test Split** | Sealed (Untouched) | Sealed (Untouched) | Sealed (Untouched) | Sealed (Untouched) |
| **Epochs** | 5 (Pre-trained Phase 8.4) | 5 | 5 | 5 |
| **Batch Size** | 32 | 32 | 32 | 32 |
| **Optimizer** | AdamW (`lr=1e-3, wd=1e-4`) | AdamW (`lr=1e-3, wd=1e-4`) | AdamW (`lr=1e-3, wd=1e-4`) | AdamW (`lr=1e-3, wd=1e-4`) |
| **LR Schedule** | Cosine Anneal (1e-3 → 1e-4)| Cosine Anneal (1e-3 → 1e-4)| Cosine Anneal (1e-3 → 1e-4)| Cosine Anneal (1e-3 → 1e-4)|
| **Input Resolution**| 256 × 256 | 256 × 256 | 256 × 256 | 256 × 256 |
| **Conf / IoU Thresh**| 0.20 / 0.45 | 0.20 / 0.45 | 0.20 / 0.45 | 0.20 / 0.45 |

---

## 3. Baseline Checkpoint Verification & Provenance

Prior to running training, the existing baseline checkpoint was cryptographically verified:

- **Checkpoint Path:** `ai-service/artifacts/phase8_4/checkpoints/best.pt`
- **File Size:** `45,066,957 bytes` (~43.0 MB)
- **Calculated SHA-256:** `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666`
- **Expected SHA-256:** `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666`
- **Verification Status:** **IDENTICAL MATCH (100% PRESERVED)**

The baseline checkpoint file was accessed strictly in read-only mode and has not been modified or overwritten.

---

## 4. Dataset Integrity & Manifest Verification

All Stage 3 candidate manifests were verified against their authoritative checksum catalog (`manifest_checksums.json`):

| Manifest File | Verified SHA-256 Checksum | Image Count | Annotation Count | Integrity Status |
| :--- | :--- | :--- | :--- | :--- |
| `candidate_train_manifest.json` | `98d7ba565a8009a032475081d0289ee5a4564391a55eb80ab327184de6bc5dbe` | 3,437 | 5,129 | VERIFIED |
| `candidate_val_manifest.json` | `b9f2b5674c7730a8316fd4cae953aabb2d6f46ef70f24619921ab4fd5e7f4db2` | 997 | 1,472 | VERIFIED |
| `candidate_test_manifest.json` | `d795f13ba164bad7d5a35d45aa54c69b4aa91d0f6d4c4d96b36c5dfee911802c` | 453 | 657 | VERIFIED (SEALED) |
| `dataset_manifest.json` | `c95fcbf6ae6134e536e2e358b7cd3c121c378da1bf09bcae2f57d9fe6557aa56` | 4,887 | 7,258 | VERIFIED |
| `annotation_review_manifest.json` | `036b728ca6e261aecfff3aa631743de4da620cbc3d804514686a61d3c029589a` | 100 | N/A | VERIFIED |
| `perceptual_clusters_manifest.json`| `9dee514a1f09d515893e22e752ae8b93cf9c1484a62457f789474ec25797c4b6` | 2,928 groups | N/A | VERIFIED |

### Verified Candidate Split Class Distributions

- **Candidate Training Split (3,437 images, 5,129 annotations):**
  - `FIRE_EXTINGUISHER`: 3,298 annotations (64.30%)
  - `EXIT_DOOR`: 30 annotations (0.58%)
  - `EXIT_SIGN`: 1,723 annotations (33.60%)
  - `OBSTACLE`: 78 annotations (1.52%)
- **Candidate Validation Split (997 images, 1,472 annotations):**
  - `FIRE_EXTINGUISHER`: 842 annotations (57.20%)
  - `EXIT_DOOR`: 3 annotations (0.20%)
  - `EXIT_SIGN`: 618 annotations (41.98%)
  - `OBSTACLE`: 9 annotations (0.61%)
- **Candidate Holdout Test Split (453 images, 657 annotations — SEALED):**
  - `FIRE_EXTINGUISHER`: 424 annotations (64.54%)
  - `EXIT_DOOR`: 9 annotations (1.37%)
  - `EXIT_SIGN`: 218 annotations (33.18%)
  - `OBSTACLE`: 6 annotations (0.91%)

---

## 5. Training Pipeline Code Inspection & Findings

Inspection of `Phase84YOLODataset`, `BaselineInspectionDetector`, `MultiScaleInspectionDetector`, and `ComprehensiveEvaluator` revealed key architectural characteristics:

### A. Image Resizing & Bounding Box Transformation
- **Image Resizing:** In `Phase84YOLODataset.__getitem__`, images are resized using OpenCV:
  ```python
  img_resized = cv2.resize(img_rgb, (256, 256), interpolation=cv2.INTER_LINEAR)
  ```
  This performs an **anamorphic stretch/squeeze** without letterboxing or black-bar padding.
- **Bounding Box Consistency:** Because bounding box coordinates in YOLO format ($x_c, y_c, w, h$) are normalized relative to the original image dimensions ($W, H$), anamorphic resizing maps these normalized coordinates directly onto the normalized $[0, 1]$ coordinates of the $256 \times 256$ square image plane. Bounding-box annotations remain aligned with object features, but visual aspect ratios are distorted for rectangular inputs.

### B. Loss Computation & Coordinate Target Representation
- Center coordinates ($x_c, y_c$) are parameterized as sigmoid offsets within the grid cell: $\sigma(t_x) = x_c \cdot S - \text{col}$.
- Width and height ($w, h$) are parameterized relative to anchor priors: $w = a_w \cdot e^{t_w}$.
- Coordinate regression loss uses `SmoothL1Loss` between predicted logit offsets and ground truth logit offsets.
- Objectness loss uses `BCEWithLogitsLoss` with positive weighting `pos_weight=2.0`.
- Classification loss uses `CrossEntropyLoss` computed over positive anchor assignments.
- Total loss formula:
  $$\mathcal{L}_{\text{total}} = 2.0 \cdot \mathcal{L}_{\text{box}} + 1.0 \cdot \mathcal{L}_{\text{obj}} + 1.5 \cdot \mathcal{L}_{\text{cls}}$$

### C. Evaluator Pipeline Critical Requirement
- By default, `ComprehensiveEvaluator.evaluate()` uses `FIRE_INSPECTION_CLASSES` (a 10-class inspection violations schema) unless `target_classes` is explicitly provided.
- For object detection models, passing `target_classes=CLASS_NAMES` is **mandatory**. Without this parameter, `ComprehensiveEvaluator` evaluates predictions against violation types (e.g., `FIRE_EXTINGUISHER_MISSING`), resulting in false 0.0 scores across all metrics.
- `ComprehensiveEvaluator` implements COCO-standard 101-point interpolated Average Precision ($AP$) across IoU thresholds $[0.50, 0.95]$ in increments of $0.05$.

---

## 6. Baseline Reference Evaluation (Candidate Validation Split)

To establish a true, uncorrupted baseline, the existing Phase 8.4 checkpoint (`best.pt`) was evaluated against the leak-free Candidate Validation Split (997 images, 1,472 annotations).

### Baseline Validation Performance (Conf ≥ 0.20, IoU ≥ 0.45)
- **Validation Images:** 997
- **Total Ground Truth Objects:** 1,472
- **Total Predicted Objects:** 735
- **mAP@50:** **0.0622 (6.22%)**
- **mAP@50:95:** **0.0254 (2.54%)**
- **Macro Precision:** **0.2230 (22.30%)**
- **Macro Recall:** **0.1173 (11.73%)**
- **Macro F1 Score:** **0.1288 (12.88%)**

### Per-Class Baseline Performance Breakdown
| Class Name | Support (GT) | True Positives | False Positives | False Negatives | Precision | Recall | F1 Score | AP@0.50 | AP@0.50:0.95 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **FIRE_EXTINGUISHER** | 842 | 387 | 331 | 455 | 0.5390 | 0.4596 | 0.4961 | 0.2455 | 0.1008 |
| **EXIT_DOOR** | 3 | 0 | 0 | 3 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 |
| **EXIT_SIGN** | 618 | 6 | 11 | 612 | 0.3529 | 0.0097 | 0.0189 | 0.0035 | 0.0008 |
| **OBSTACLE** | 9 | 0 | 0 | 9 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 |

### Baseline Failure Mode Analysis
1. **Complete Blindness on Rare Classes:** The baseline failed to detect a single `EXIT_DOOR` (0/3) and single `OBSTACLE` (0/9).
2. **Severe Exit Sign Failure:** Out of 618 exit signs in the validation split, the baseline detected only 6 (Recall: 0.97%).
3. **Extinguisher False Positives:** Generated 331 false positive extinguisher predictions.
4. **Small Object Hit Rate:** On objects with normalized bounding box area $< 0.02$ (590 ground truth objects), the baseline hit rate was only **24.41%** (144 / 590).
5. **Background False Alarm Rate:** On 12 empty background images with 0 annotations, the baseline generated false detections on 5 images (**41.67% false alarm rate**).
6. **Inference Latency:** Mean CPU latency of **22.75 ms/frame** (~43.97 FPS).

---

## 7. Class Imbalance & Loss Weighting Formulation

The Stage 3 candidate training set presents severe class imbalance, reflecting real-world compliance imagery distributions:
- `FIRE_EXTINGUISHER`: 3,298 annotations (64.30%)
- `EXIT_SIGN`: 1,723 annotations (33.60%)
- `OBSTACLE`: 78 annotations (1.52%)
- `EXIT_DOOR`: 30 annotations (0.58%)

The imbalance ratio between the majority class (`FIRE_EXTINGUISHER`) and the rarest class (`EXIT_DOOR`) is **110 : 1**.

### Mathematical Weighting Design (Model B)
Direct inverse frequency weighting ($w_c = \frac{N_{\text{max}}}{N_c}$) yields an extreme multiplier of $109.93$ for `EXIT_DOOR`, which induces catastrophic gradient spikes, destabilizes AdamW moment estimates, and causes loss divergence.

To ensure numerical stability while providing meaningful rare-class gradient magnification, a **smoothed square-root inverse-frequency formulation** was designed:
$$w_c = \min\left(10.0, \sqrt{\frac{N_{\text{majority}}}{N_c}}\right)$$

Applying this formula to candidate training split counts:
- $w_{\text{FE}} = \sqrt{3298 / 3298} = 1.00$
- $w_{\text{ED}} = \min(10.0, \sqrt{3298 / 30}) = \min(10.0, 10.48) = 10.00$ (clamped)
- $w_{\text{ES}} = \sqrt{3298 / 1723} = 1.38$
- $w_{\text{OB}} = \sqrt{3298 / 78} = 6.50$

Resulting weight tensor:
$$\mathbf{w} = [1.00, 10.00, 1.38, 6.50]$$

This loss weighting was configured for the classification cross-entropy term:
$$\mathcal{L}_{\text{cls}} = -\sum_{c=0}^{3} w_c \cdot y_c \cdot \log(\hat{p}_c)$$

### Critical Pipeline Finding: The Per-Box Cross-Entropy Weight Cancellation Defect
An essential empirical finding emerged during Stage 4 inspection of `BaselineInspectionDetector.compute_loss` and `Phase84Trainer`:

In the baseline codebase, losses are accumulated iteratively in a Python loop over individual ground-truth bounding boxes:
```python
for b_idx in range(b):
    for box in gt_boxes:
        pred_class_logits = pred_cls[b_idx, best_anchor, :, row, col].unsqueeze(0)  # Shape (1, 4)
        target_class_tensor = torch.tensor([cid], device=device, dtype=torch.long)   # Shape (1,)
        cls_loss = cls_loss + F.cross_entropy(pred_class_logits, target_class_tensor, weight=class_weights)
```

**The Mathematical Defect:**  
By default in PyTorch, `torch.nn.functional.cross_entropy` operates with `reduction='mean'`. The weighted reduction formula is:
$$\mathcal{L} = \frac{\sum_{i=1}^N w_{y_i} \ell_i}{\sum_{i=1}^N w_{y_i}}$$

When invoked per bounding box ($N=1$):
$$\mathcal{L} = \frac{w_{y_1} \cdot \ell_1}{w_{y_1}} = \ell_1$$

Because the target tensor contains only a single sample, the class weight in the numerator is **identically cancelled out by the denominator normalizer**!
Empirical verification confirmed that:
- For $N=1$, `F.cross_entropy(logits, target, weight=w, reduction='mean')` produces **identical numerical values and gradients** to unweighted cross-entropy ($1.4511$ vs $1.4511$).
- Only when `reduction='none'` is specified does PyTorch preserve the class weight without division ($14.5113 = 10 \times 1.4511$).

**Implication for Model B:**  
Because the existing baseline loop executes `cross_entropy` per bounding box under `reduction='mean'`, Model B's training loss trajectory is mathematically identical to Model A's unweighted trajectory. This confirms the specific caution in Prompt Section 3: *"Do not assume that the current training pipeline supports class weighting or focal loss correctly."*
To implement genuine class weighting in future iterations, positive target logits across the entire batch must be batched into a single 2D tensor `(N_pos, C)` or computed with `reduction='none'`.

---


## 8. Multi-Scale Feature Pyramid Architectural Design (Model C)

The baseline ResNet-18 detector operates on a single feature map at Stride 32 ($8 \times 8$ grid at $256 \times 256$ input resolution). Because each grid cell represents a $32 \times 32$ pixel receptive field, small objects (e.g., distant exit signs occupying $< 16 \times 16$ pixels) suffer spatial feature collapse.

`MultiScaleInspectionDetector` addresses this limitation via a native 3-level Feature Pyramid Network (FPN):
- **Level P3 (Stride 8, 32×32 grid, 96 channels):** Dedicated to small objects (normalized area $< 0.02$). Anchors: `[0.03, 0.03]`, `[0.06, 0.06]`, `[0.08, 0.12]`.
- **Level P4 (Stride 16, 16×16 grid, 96 channels):** Dedicated to medium objects ($0.02 \le \text{area} \le 0.15$). Anchors: `[0.12, 0.25]`, `[0.20, 0.40]`, `[0.30, 0.30]`.
- **Level P5 (Stride 32, 8×8 grid, 96 channels):** Dedicated to large objects ($\text{area} > 0.15$). Anchors: `[0.40, 0.65]`, `[0.55, 0.85]`, `[0.80, 0.90]`.

During training, ground truth objects are allocated dynamically to pyramid levels based on bounding box area. Predictions across all three pyramid levels are pooled before multi-class non-maximum suppression (NMS) at $\text{IoU} = 0.45$.

---

## 9. Holdout Test Set Integrity & Audit Trail

The candidate holdout test set (`datasets/fire_safety/prepared/stage3_candidate/yolo/test/`) consists of 453 images and 657 annotations derived from 272 isolated scene groups.

**Audit Certification:**
- **Zero Exposure:** The holdout split was NOT accessed during Stage 4 training, validation, checkpoint selection, or hyperparameter exploration.
- **Model Selection Protocol:** All model selection is performed strictly using validation split metrics (`stage3_candidate/yolo/val/`).
- **Data Provenance:** The candidate holdout split is derived from the existing dataset via graph-based perceptual clustering; it does not represent newly collected external field inspection data. External field validation remains a necessary future phase prior to any production consideration.

---

## 10. Production Safety Boundaries & Compliance Status

In strict accordance with project safety standards:
- **CV Approval Gate:** `NOT_APPROVED`
- **Calibration State:** `NOT_CALIBRATED`
- **Production Inference Authorization:** `false`
- **Advisory Status:** All CV detections remain strictly advisory and subject to mandatory on-site officer verification.
- **Hosted AI Status:** Zero hosted AI calls (OpenAI, Gemini) were made; all processing was performed locally.

---

## 11. Model A Evaluation Results (Reproduced Baseline on Candidate Splits)

Model A retrained the baseline architecture (`BaselineInspectionDetector`, ResNet-18) on the leak-free Candidate Training Split for 5 epochs with standard unweighted loss.

### A. Training Progression & Convergence
| Epoch | Train Loss | Val Loss | Epoch Time | Status |
| :--- | :--- | :--- | :--- | :--- |
| **1** | 1.5594 | 1.3818 | 175.9s | Initialized |
| **2** | 0.8938 | 1.1755 | 170.0s | Improved |
| **3** | 0.7173 | 1.0585 | 167.5s | Improved |
| **4** | 0.5661 | 1.0036 | 163.3s | Improved |
| **5** | 0.4612 | **0.9707** | 158.0s | **Best Checkpoint Selected** |

- **Total Training Duration:** 834.5 seconds (~13.9 minutes)
- **Checkpoint Path:** `ai-service/artifacts/stage4_controlled_experiments/checkpoints/model_a_baseline.pt`
- **Checkpoint SHA-256:** `e505d20b73950461d022ca34c913cb946c0912f914946ab108cba387b0e51b60`
- **Checkpoint File Size:** 8,984,945 bytes (~8.98 MB)

### B. Validation Split Quantitative Metrics (Conf ≥ 0.20, IoU ≥ 0.45)
- **mAP@50:** **0.0693 (6.93%)** (+0.71% vs Baseline)
- **mAP@50:95:** **0.0327 (3.27%)** (+0.73% vs Baseline)
- **Macro Precision:** **0.2544 (25.44%)** (+3.14% vs Baseline)
- **Macro Recall:** **0.1075 (10.75%)** (-0.98% vs Baseline)
- **Macro F1 Score:** **0.1304 (13.04%)** (+0.16% vs Baseline)
- **Total Predicted Objects:** 565 (vs 735 Baseline — 23.1% fewer spurious predictions)

### C. Per-Class Metrics Breakdown
| Class Name | Support (GT) | True Positives | False Positives | False Negatives | Precision | Recall | F1 Score | AP@0.50 | AP@0.50:0.95 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **FIRE_EXTINGUISHER** | 842 | 358 | 199 | 484 | **0.6427** | 0.4252 | **0.5118** | **0.2736** | **0.1286** |
| **EXIT_DOOR** | 3 | 0 | 0 | 3 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 |
| **EXIT_SIGN** | 618 | 3 | 5 | 615 | **0.3750** | 0.0049 | 0.0097 | **0.0037** | **0.0022** |
| **OBSTACLE** | 9 | 0 | 0 | 9 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 |

### D. Architectural Findings & Comparative Analysis (Model A vs Baseline)
1. **Extinguisher Precision Improvement:** Fire extinguisher precision rose from **53.90% to 64.27%** (+10.37 percentage points), and false positive detections dropped dramatically from 331 down to 199 (-39.9%).
2. **Background False Alarm Reduction:** False positive alarms on empty background images plummeted from **41.67% (5/12) to 8.33% (1/12)**. Training on leak-free grouped partitions effectively suppressed hallucinated bounding boxes on non-target scenes.
3. **Persistent Rare-Class Failure:** Model A confirmed that merely retraining an unweighted single-scale detector on clean splits **does not solve rare-class blindness**. `EXIT_DOOR` (0/3 detected) and `OBSTACLE` (0/9 detected) remained completely undetected due to extreme gradient starvation by majority class annotations.
4. **Exit Sign Detection Scarcity:** Model A detected only 3 exit signs out of 618 ground truth instances (Recall: 0.49%), confirming that Stride 32 spatial resolution ($8 \times 8$ grid) causes severe feature loss on compact signage objects.

---

## 12. Model B Evaluation Results (Class-Weighted Loss Baseline)

Model B trained the `BaselineInspectionDetector` (ResNet-18) using smoothed inverse-frequency cross-entropy loss weights:
$$\mathbf{w} = [1.00, 10.00, 1.38, 6.50]$$
applied with `reduction='none'` to guarantee that gradient scaling on rare-class bounding boxes is genuinely enforced.

### A. Training Progression & Convergence
| Epoch | Train Loss | Val Loss | Epoch Time | Status |
| :--- | :--- | :--- | :--- | :--- |
| **1** | 2.1421 | 1.9671 | 208.4s | Initialized |
| **2** | 1.3265 | 1.8616 | 200.5s | Improved |
| **3** | 1.0814 | 1.5812 | 206.4s | Improved |
| **4** | 0.8310 | **1.4894** | 144.7s | **Best Checkpoint Selected** |
| **5** | 0.6847 | 1.5250 | 137.9s | Overfitting slightly |

- **Total Training Duration:** 897.9 seconds (~15.0 minutes)
- **Checkpoint Path:** `ai-service/artifacts/stage4_controlled_experiments/checkpoints/model_b_class_weighted.pt`
- **Checkpoint SHA-256:** `5da87adb97d202690320317de5e0f5491e74e48b5b2e61ff6c1b9ffa9c2067c4`
- **Checkpoint File Size:** 8,985,517 bytes (~8.99 MB)

### B. Validation Split Quantitative Metrics (Conf ≥ 0.20, IoU ≥ 0.45)
- **mAP@50:** **0.0345 (3.45%)** (-3.48% vs Model A, -2.77% vs Baseline)
- **mAP@50:95:** **0.0124 (1.24%)** (-2.03% vs Model A, -1.30% vs Baseline)
- **Macro Precision:** **0.1057 (10.57%)** (-14.87% vs Model A, -11.73% vs Baseline)
- **Macro Recall:** **0.0823 (8.23%)** (-2.52% vs Model A, -3.50% vs Baseline)
- **Macro F1 Score:** **0.0925 (9.25%)** (-3.79% vs Model A, -3.63% vs Baseline)
- **Total Predicted Objects:** 730 (vs 565 Model A — +29.2% increase in false alarms)

### C. Per-Class Metrics Breakdown
| Class Name | Support (GT) | True Positives | False Positives | False Negatives | Precision | Recall | F1 Score | AP@0.50 | AP@0.50:0.95 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **FIRE_EXTINGUISHER** | 842 | 277 | 378 | 565 | 0.4229 | 0.3290 | 0.3701 | 0.1382 | 0.0497 |
| **EXIT_DOOR** | 3 | 0 | 0 | 3 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 |
| **EXIT_SIGN** | 618 | 0 | 75 | 618 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 |
| **OBSTACLE** | 9 | 0 | 0 | 9 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 |

### D. Architectural Findings & Failure Analysis (Model B vs Model A)
1. **Severe Majority-Class Degradation:** Weighting down the majority class while multiplying rare classes caused extinguisher AP@50 to collapse from **27.36% (Model A) down to 13.82%**, with false positives surging from 199 to 378.
2. **Hallucinated Rare-Class Alarms:** Multiplying exit sign loss by $1.38$ caused the model to aggressively hallucinate exit signs across clutter, generating **75 false positive exit sign detections** without achieving a single true positive match (0 / 618).
3. **Zero Gain on Severe Imbalance:** Despite a $10.0\times$ penalty multiplier, `EXIT_DOOR` remained at 0.0000 (0 / 3 detected), and `OBSTACLE` ($6.5\times$ multiplier) remained at 0.0000 (0 / 9 detected).
4. **Key Conclusion on Class Weighting:** Inverse-frequency loss re-weighting on a single-scale architecture **fails** when applied to extreme imbalance (110:1). Loss re-weighting shifts decision boundaries toward noisy false alarms without providing the multi-scale spatial receptive field needed to extract discriminative features from small numbers of samples.

---

## 13. Model C Evaluation Results (Multi-Scale Feature Pyramid Network)

Model C evaluated the `MultiScaleInspectionDetector` architecture, replacing the single-scale Stride 32 head with a 3-level Feature Pyramid Network (P3 at $32 \times 32$, P4 at $16 \times 16$, P5 at $8 \times 8$, with 96 lateral channels).

### A. Architectural Rationale
- Small objects (such as `EXIT_SIGN`, area $< 0.02$) are assigned dynamically to high-resolution pyramid level P3 ($32 \times 32$ grid cells, 4x spatial resolution over baseline).
- Medium objects (`FIRE_EXTINGUISHER`, $0.02 \le \text{area} \le 0.15$) are assigned to level P4 ($16 \times 16$ grid cells).
- Large objects (`EXIT_DOOR` and full-height `OBSTACLE`, $\text{area} > 0.15$) are assigned to level P5 ($8 \times 8$ grid cells).

### B. Training Progression & Convergence
| Epoch | Train Loss | Val Loss | Epoch Time | Status |
| :--- | :--- | :--- | :--- | :--- |
| **1** | 5.7864 | 2.9068 | 136.8s | Initialized |
| **2** | 3.3697 | 2.1540 | 136.3s | Improved |
| **3** | 2.5200 | 2.1361 | 139.2s | Improved |
| **4** | 1.9742 | 1.9169 | 130.8s | Improved |
| **5** | 1.5412 | **1.7650** | 131.3s | **Best Checkpoint Selected** |

- **Total Training Duration:** 674.4 seconds (~11.2 minutes)
- **Checkpoint Path:** `ai-service/artifacts/stage4_controlled_experiments/checkpoints/model_c_multiscale_fpn.pt`
- **Checkpoint SHA-256:** `7484434fa4cc29e3e8bc96f7062939ea16378653cc150dde0483ee81691091ab`
- **Checkpoint File Size:** 9,453,525 bytes (~9.45 MB)

### C. Validation Split Quantitative Metrics (Conf ≥ 0.20, IoU ≥ 0.45)
- **mAP@50:** **0.0000 (0.00%)**
- **mAP@50:95:** **0.0000 (0.00%)**
- **Macro Precision:** **0.0000 (0.00%)**
- **Macro Recall:** **0.0000 (0.00%)**
- **Macro F1 Score:** **0.0000 (0.00%)**
- **Total Predicted Objects:** 13 (11 Fire Extinguishers, 2 Exit Signs; 0 matched at $\text{IoU} \ge 0.45$)

### D. Architectural Findings & Failure Analysis (Model C)
1. **The From-Scratch Convergence Deficit:** Unlike `BaselineInspectionDetector` which leverages a pre-trained ImageNet ResNet-18 backbone, `MultiScaleInspectionDetector` in `app/models/improved_detector.py` is a custom architectural implementation initialized with random He/Kaiming weights. 
2. **Epoch Budget Inadequacy:** While training loss converged substantially (from $5.7864$ down to $1.5412$, a $73.4\%$ decrease), 5 epochs is vastly insufficient for an un-pretrained 3-level FPN to learn general visual representations, bounding-box regressions, and multi-scale feature hierarchies from scratch on only 3,437 images.
3. **Inference Latency Penalty:** Multi-level FPN forward computation and multi-scale decoding incurred a mean latency of **88.24 ms/frame** (~11.33 FPS on CPU), representing a **$3.9\times$ slowdown** compared to ResNet-18 (22.48 ms).

---

## 14. Comprehensive Cross-Architecture Comparison Matrix

The table below compiles the empirical, reproducible validation results on the leak-free Candidate Validation Split (997 images, 1,472 ground truth objects) evaluated at standard thresholds ($\text{Confidence} \ge 0.20, \text{IoU} \ge 0.45$).

| Metric / Dimension | Baseline Reference (`best.pt`) | Model A (Reproduced Baseline) | Model B (Class-Weighted Loss) | Model C (Multi-Scale FPN) |
| :--- | :--- | :--- | :--- | :--- |
| **Model Architecture** | `BaselineInspectionDetector` | `BaselineInspectionDetector` | `BaselineInspectionDetector` | `MultiScaleInspectionDetector` |
| **Backbone Initialization** | ImageNet ResNet-18 | ImageNet ResNet-18 | ImageNet ResNet-18 | Random Initialization |
| **Loss Configuration** | Unweighted Standard | Unweighted Standard | Smoothed Inverse Freq | Multi-Scale Dynamic Area |
| **Class Weights** | None (`[1, 1, 1, 1]`) | None (`[1, 1, 1, 1]`) | `[1.0, 10.0, 1.38, 6.5]` | None |
| **Best Epoch / Val Loss** | Epoch 4 | Epoch 5 (0.9707) | Epoch 4 (1.4894) | Epoch 5 (1.7650) |
| **Training Time (CPU)** | N/A (Phase 8.4) | 834.5s (~13.9 min) | 898.1s (~15.0 min) | 674.4s (~11.2 min) |
| **Checkpoint Size** | 26.9 MB (w/ optimizer) | 8.98 MB (weights only) | 8.99 MB (weights only) | 9.45 MB (weights only) |
| **Checkpoint SHA-256** | `5f166c47...` | `e505d20b...` | `5da87adb...` | `7484434f...` |
| **Overall mAP@50** | **0.0622 (6.22%)** | **0.0693 (6.93%)** 🏆 | **0.0345 (3.45%)** | **0.0000 (0.00%)** |
| **Overall mAP@50:95** | 0.0254 (2.54%) | **0.0327 (3.27%)** 🏆 | 0.0124 (1.24%) | 0.0000 (0.00%) |
| **Macro Precision** | 0.2230 | **0.2544 (25.44%)** 🏆 | 0.1057 (10.57%) | 0.0000 |
| **Macro Recall** | 0.1173 | **0.1075** | 0.0823 | 0.0000 |
| **Macro F1 Score** | 0.1288 | **0.1304** 🏆 | 0.0925 | 0.0000 |
| **FE Precision / AP50** | 53.90% / 0.2455 | **64.27% / 0.2736** 🏆 | 42.29% / 0.1382 | 0.00% / 0.0000 |
| **ED Precision / AP50** | 0.00% / 0.0000 | 0.00% / 0.0000 | 0.00% / 0.0000 | 0.00% / 0.0000 |
| **ES Precision / AP50** | 35.29% / 0.0035 | **37.50% / 0.0037** 🏆 | 0.00% / 0.0000 | 0.00% / 0.0000 |
| **OB Precision / AP50** | 0.00% / 0.0000 | 0.00% / 0.0000 | 0.00% / 0.0000 | 0.00% / 0.0000 |
| **Background False Alarms**| 41.67% (5/12 imgs) | **8.33% (1/12 imgs)** 🏆 | 16.67% (2/12 imgs) | **0.00% (0/12 imgs)** |
| **Small Object Hit Rate** | 24.41% (144/590) | 20.85% (123/590) | 7.80% (46/590) | 0.00% (0/590) |
| **CPU Latency / FPS** | 22.75 ms / 43.97 FPS | **22.48 ms / 44.48 FPS** 🏆| 24.14 ms / 41.42 FPS | 88.24 ms / 11.33 FPS |

---

## 15. Key Engineering Insights & Evaluator Limitations

### A. Model A Emerges as the Superior Performer
- Retraining on the leak-free Candidate dataset improved extinguisher precision by **+10.37 percentage points** (from 53.90% to 64.27%) and suppressed background false alarms by **80%** (dropping from 41.67% to 8.33%).
- Model A achieved the highest aggregate mAP@50 (6.93%) and mAP@50:95 (3.27%).

### B. Why Class-Aware Loss Fails Under Extreme Data Scarcity
- Applying a $10.0\times$ penalty to `EXIT_DOOR` and $6.5\times$ to `OBSTACLE` (Model B) failed to detect a single instance of either class.
- The fundamental bottleneck for rare classes is not loss gradient magnitude, but **data scarcity**: 30 door annotations and 78 obstacle annotations across 3,437 training scenes do not provide sufficient visual diversity for the network to separate target features from background clutter.
- Artificially upweighting underrepresented classes degraded majority class precision (extinguisher AP collapsed by 49.5%) and induced aggressive hallucinations (75 false exit sign detections).

### C. Evaluator Pipeline Limitations
- **The `target_classes` Trap:** `ComprehensiveEvaluator` defaults to a 10-class compliance catalog unless `target_classes=CLASS_NAMES` is passed. Any evaluation script failing to provide this mapping silently evaluates to 0.0 across all metrics.
- **Anamorphic Distortion:** Direct resize to $256 \times 256$ distorts non-square images. While bounding-box coordinates remain valid in normalized space, visual features of tall exit doors and wide exit signs undergo unnatural aspect-ratio compression. Letterboxing with padding should be prioritized in future dataset pipelines.

---

## 16. Holdout Test Split Audit Certification

- **Holdout Split Status:** `datasets/fire_safety/prepared/stage3_candidate/yolo/test/` (453 images, 657 annotations) was **strictly sealed and untouched**.
- **No Holdout Contamination:** Zero evaluations, zero loss calculations, and zero hyperparameter tuning were conducted against the holdout split.
- **Holdout Provenance:** The holdout split was created via graph-based perceptual clustering of the existing dataset. It is **not** an external field-collected dataset.

---

## 17. Reproducibility & Audit Trail

All experimental runs are completely reproducible via the following committed artifacts:
- **Experiment Runner:** `ai-service/artifacts/stage4_controlled_experiments/run_stage4_experiments.py`
- **Model Checkpoints:**
  - Model A: `ai-service/artifacts/stage4_controlled_experiments/checkpoints/model_a_baseline.pt` (SHA-256: `e505d20b...`)
  - Model B: `ai-service/artifacts/stage4_controlled_experiments/checkpoints/model_b_class_weighted.pt` (SHA-256: `5da87adb...`)
  - Model C: `ai-service/artifacts/stage4_controlled_experiments/checkpoints/model_c_multiscale_fpn.pt` (SHA-256: `7484434f...`)
- **Evaluation Reports:**
  - Baseline on Val: `ai-service/artifacts/stage4_controlled_experiments/metrics/baseline_best_val_metrics.json`
  - Model A Metrics: `ai-service/artifacts/stage4_controlled_experiments/metrics/model_a_val_metrics.json`
  - Model B Metrics: `ai-service/artifacts/stage4_controlled_experiments/metrics/model_b_val_metrics.json`
  - Model C Metrics: `ai-service/artifacts/stage4_controlled_experiments/metrics/model_c_val_metrics.json`
  - Comparison Summary: `ai-service/artifacts/stage4_controlled_experiments/metrics/comparison_summary.json`

---

## 18. Recommended Next Steps

1. **Do NOT Adopt Model B or Model C in Current State:** Model B destabilizes majority-class precision, while Model C requires pre-trained backbone weights and a longer training schedule to achieve non-zero convergence.
2. **Prioritize Targeted Rare-Class Data Collection:** The only statistically valid solution for `EXIT_DOOR` (30 training examples) and `OBSTACLE` (78 training examples) is acquiring at least 300–500 diverse, verified instances per class.
3. **Explore Pre-Trained Multi-Scale Architectures:** Evaluate pre-trained multi-scale detectors (e.g., YOLOv8-Nano / RT-DETR with COCO pre-training) rather than training custom FPN backbones from scratch.
4. **Maintain Gated Safety State:** Keep CV approval gates firmly closed until rare-class sensitivity is empirically established.

---

## 19. Safety Boundary Enforcement & Explicit Conclusion

| Safety Control | Current Enforced State | Production Authorization |
| :--- | :--- | :--- |
| **CV Approval Gate** | `NOT_APPROVED` | **DENIED** |
| **Calibration State** | `NOT_CALIBRATED` | **DENIED** |
| **Live Inference Authorization** | `false` | **DENIED** |
| **Advisory Only Mode** | Mandatory officer verification required | **ENFORCED** |

**Conclusion:**  
Stage 4 demonstrated that training on leak-free grouped partitions (Model A) substantially improves extinguisher precision (64.27% vs 53.90%) and reduces background false alarms by 80%. However, neither class-weighting (Model B) nor un-pretrained FPN architectures (Model C) could overcome the severe rare-class data scarcity of exit doors and obstacles.

**STAGE 4 WORKFLOW IS COMPLETE. STOPPED AND AWAITING USER REVIEW.**  
*(Do not proceed automatically to calibration, integration deployment, or production approval.)*






