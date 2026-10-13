# FireSphere.ai — Phase 8.4 Model Evaluation Report

**Document Version:** `1.0.0-phase8.4`  
**Model Identifier:** `firesphere-perception-baseline-v0.8.0`  
**Evaluated Checkpoint:** `ai-service/artifacts/phase8_4/checkpoints/best.pt` (Epoch 4)  
**Evaluation Protocols:** Standard COCO/YOLO Precision, Recall, mAP@50, mAP@50:95 (101-point interpolated AP)  
**Execution Timestamp:** `2026-10-08T13:05:11Z`  
**Evaluation Status:** **COMPLETED**

---

## 1. Executive Summary

This document provides the objective evaluation of the trained Phase 8.4 baseline perception model across both the validation set (988 images) and the held-out test set (522 images).

### Critical Finding
In strict compliance with Phase 8.4 guidelines:
- **No Aggregate Masking:** Weak, data-limited classes are **not hidden** behind aggregate metrics.
- **Empirical Reality:** The baseline model demonstrates reasonable localization on abundant classes (`FIRE_EXTINGUISHER` AP@50 = 22.4%), but completely fails on data-starved classes (`EXIT_DOOR` AP@50 = 0.0%, `OBSTACLE` AP@50 = 0.0%).
- **Governance Gate Result:** Because `EXIT_DOOR` (6 test boxes) and `OBSTACLE` (18 test boxes) are data-limited and unlearned by the baseline, the model is **NOT APPROVED FOR PRODUCTION** and requires **TARGETED DATASET EXPANSION**.

---

## 2. Validation Split Evaluation (988 Images)

The validation split was used exclusively for model checkpoint selection (Epoch 4 selected):

| Metric | Overall Value |
| :--- | :--- |
| **Total Validation Images** | 988 |
| **Ground Truth Objects** | 1,455 |
| **Total Predicted Detections** | 808 |
| **Mean Precision (mPrec)** | 0.1668 (16.68%) |
| **Mean Recall (mRec)** | 0.1113 (11.13%) |
| **Mean F1 Score** | 0.1210 |
| **mAP@0.50** | **0.0588 (5.88%)** |
| **mAP@0.50:0.95** | **0.0227 (2.27%)** |

### Validation Per-Class Detailed Breakdown

| Class Name | Support (GT) | TP | FP | FN | Precision | Recall | F1 Score | AP@0.50 | AP@0.50:0.95 | Data Limitation Status |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| `FIRE_EXTINGUISHER` | 948 | 420 | 381 | 528 | 0.5243 | 0.4430 | 0.4802 | **0.2336** | **0.0909** | NONE (READY_FOR_STAGING) |
| `EXIT_DOOR` | 3 | 0 | 0 | 3 | 0.0000 | 0.0000 | 0.0000 | **0.0000** | **0.0000** | **DATA_LIMITED (Severe Undersampling)** |
| `EXIT_SIGN` | 498 | 1 | 6 | 497 | 0.1429 | 0.0020 | 0.0039 | **0.0014** | **0.0001** | NONE (Low Baseline Convergence) |
| `OBSTACLE` | 6 | 0 | 0 | 6 | 0.0000 | 0.0000 | 0.0000 | **0.0000** | **0.0000** | **DATA_LIMITED (Severe Undersampling)** |

---

## 3. Held-Out Test Split Evaluation (522 Images)

> [!IMPORTANT]
> The test set remained completely untouched throughout training and model selection. It was evaluated exactly once after all parameters and checkpoints were frozen.  
> `TEST_SET_USED_FOR_FINAL_EVALUATION = TRUE`

| Metric | Overall Value |
| :--- | :--- |
| **Total Test Images** | 522 |
| **Ground Truth Objects** | 796 |
| **Total Predicted Detections** | 395 |
| **Mean Precision (mPrec)** | 0.2283 (22.83%) |
| **Mean Recall (mRec)** | 0.1125 (11.25%) |
| **Mean F1 Score** | 0.1261 |
| **mAP@0.50** | **0.0579 (5.79%)** |
| **mAP@0.50:0.95** | **0.0194 (1.94%)** |

### Test Per-Class Detailed Breakdown

| Class Name | Support (GT) | TP | FP | FN | Precision | Recall | F1 Score | AP@0.50 | AP@0.50:0.95 | Data Limitation Status |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| `FIRE_EXTINGUISHER` | 452 | 195 | 185 | 257 | 0.5132 | 0.4314 | 0.4688 | **0.2236** | **0.0764** | NONE (CANDIDATE_FOR_STAGING) |
| `EXIT_DOOR` | 6 | 0 | 0 | 6 | 0.0000 | 0.0000 | 0.0000 | **0.0000** | **0.0000** | **DATA_LIMITED (0 / 6 Detected)** |
| `EXIT_SIGN` | 320 | 6 | 9 | 314 | 0.4000 | 0.0187 | 0.0357 | **0.0079** | **0.0014** | NONE (Requires Anchor Tuning) |
| `OBSTACLE` | 18 | 0 | 0 | 18 | 0.0000 | 0.0000 | 0.0000 | **0.0000** | **0.0000** | **DATA_LIMITED (0 / 18 Detected)** |

---

## 4. Multi-Class Confusion Matrix (Test Set, IoU = 0.50)

The confusion matrix includes the 4 perception classes and explicit Background (false alarms and misses):

```
Confusion Matrix (Test Set, IoU >= 0.50):
---------------------------------------------------------------------------------------------------------
Predicted Class \ GT     FIRE_EXTINGUISHER   EXIT_DOOR   EXIT_SIGN   OBSTACLE   BACKGROUND (FP)
---------------------------------------------------------------------------------------------------------
FIRE_EXTINGUISHER              195               0           0          0             185
EXIT_DOOR                        0               0           0          0               0
EXIT_SIGN                        0               0           6          0               9
OBSTACLE                         0               0           0          0               0
BACKGROUND (FN / Misses)       257               6         314         18               -
---------------------------------------------------------------------------------------------------------
```

### Analysis of the Confusion Matrix
1. **Low Cross-Class Confusion:** When the model fires, it does not confuse extinguishers with exit signs or doors (zero off-diagonal cross-class confusion).
2. **Dominant Miss Mode (False Negatives):** The primary failure mode across all classes is miss-detection against background (FN), particularly on small objects (`EXIT_SIGN` has 314 misses) and underrepresented objects (`EXIT_DOOR` and `OBSTACLE` 100% missed).
3. **Extinguisher False Positives:** 185 false positives occur mostly on wall-mounted red fixtures, fire alarm panels, and equipment brackets in cluttered scenes.

---

## 5. Data-Limited Class Empirical Reality

1. **`EXIT_DOOR` (6 Test Positives, 14 Unique Groups in Entire Dataset):**
   - The class is severely data-starved. With only 33 training samples in a 3,377 image corpus (0.9%), unweighted baseline training cannot learn the door boundary representations.
   - **Conclusion:** Cannot be deployed for exit door localization in production without targeted scene expansion.
2. **`OBSTACLE` (18 Test Positives, 30 Unique Groups in Entire Dataset):**
   - Obstacles in the test set (corridor clutter, pallets, boxes) were completely undetected by the baseline detector.
   - **Conclusion:** Standalone object detection is insufficient for obstacle perception without spatial context and dataset enrichment.
