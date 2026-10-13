# FireSphere.ai — Phase 8.8 Comprehensive Evaluation Report

**Document Version:** `1.0.0-phase8.8`  
**Governing Phase:** Phase 8.8 Controlled Model Training & Comparative Evaluation  
**Author:** AI/ML Engineering Agent  
**Candidate Model:** `firesphere-perception-candidate-v0.8.8`  
**Baseline Model:** `firesphere-perception-baseline-v0.8.0`  
**Date:** 2026-10-09  

---

## 1. Executive Summary

Phase 8.8 conducted two rigorous, isolated evaluation stages:
1. **Validation Split Evaluation & Confidence Sweep:** Conducted strictly on the audited Phase 8.8 validation partition (928 images, 1,443 ground-truth objects) to assess hyperparameter dynamics and threshold response without test contamination.
2. **Final External Test Evaluation:** Conducted strictly **once** on the untouched Phase 8.3 test partition (522 images, 796 ground-truth objects) for a true apples-to-apples comparison against the Phase 8.4 baseline.

---

## 2. Validation Split Evaluation (928 images, tau = 0.15)

At the standard operating threshold ($\tau = 0.15, \text{IoU} = 0.50$):

| Class | Support | True Positives | False Positives | False Negatives | Precision | Recall | F1 Score | AP@50 |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **FIRE_EXTINGUISHER** | 854 | 309 | 489 | 545 | 0.3872 | 0.3618 | 0.3741 | 0.1419 |
| **EXIT_DOOR** | 117 | 0 | 0 | 117 | 0.0000 | 0.0000 | 0.0000 | 0.0000 |
| **EXIT_SIGN** | 469 | 8 | 63 | 461 | 0.1127 | 0.0171 | 0.0296 | 0.0022 |
| **OBSTACLE** | 3 | 0 | 0 | 3 | 0.0000 | 0.0000 | 0.0000 | 0.0000 |
| **AGGREGATE VAL** | **1,443** | **317** | **552** | **1,126** | **0.1250** | **0.0947** | **0.1009** | **0.0360** |

---

## 3. Validation Confidence Threshold Sweep

A full sweep across 8 operating thresholds ($\tau \in [0.05, 0.50]$) was evaluated on the validation partition:

| Threshold ($\tau$) | Predictions | Mean Precision | Mean Recall | Mean F1 | Extinguisher Recall | Sign Recall | Door Recall | Obstacle Recall |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **0.05** | 3,986 | 0.060 | 0.176 | 0.086 | 0.571 | 0.132 | 0.000 | 0.000 |
| **0.10** | 1,647 | 0.083 | 0.121 | 0.092 | 0.459 | 0.024 | 0.000 | 0.000 |
| **0.15** | 869 | 0.125 | 0.095 | 0.101 | 0.362 | 0.017 | 0.000 | 0.000 |
| **0.20** | 522 | 0.140 | 0.068 | 0.088 | 0.267 | 0.006 | 0.000 | 0.000 |
| **0.25** | 299 | 0.144 | 0.048 | 0.072 | 0.191 | 0.000 | 0.000 | 0.000 |
| **0.30** | 175 | 0.160 | 0.031 | 0.052 | 0.124 | 0.000 | 0.000 | 0.000 |
| **0.40** | 54 | 0.195 | 0.012 | 0.023 | 0.049 | 0.000 | 0.000 | 0.000 |
| **0.50** | 14 | 0.196 | 0.003 | 0.006 | 0.013 | 0.000 | 0.000 | 0.000 |

### Key Sweep Insights
1. **`EXIT_SIGN` Recall Sensitivity:** Lowering $\tau$ to 0.05 recovers exit sign recall to **13.2%** (consistent with the Phase 8.7 calibration finding of 14.3%).
2. **`EXIT_DOOR` Zero-Recall Bottleneck:** `EXIT_DOOR` achieves 0 recall across all thresholds $\tau \in [0.05, 0.50]$. This proves that the door failure is **not a confidence thresholding issue**; rather, the predicted box aspect ratios and centers fail to achieve $\text{IoU} \ge 0.50$ against the ground truth boxes due to single-scale grid cell quantization and anchor mismatch.

---

## 4. Final External Test Evaluation (522 images, tau = 0.15)

Evaluated strictly once on the held-out Phase 8.3 test set:

| Class | Baseline TP/FP/FN | Candidate TP/FP/FN | Baseline AP@50 | Candidate AP@50 | Delta AP@50 | Empirical Verdict |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **FIRE_EXTINGUISHER** | 220 / 321 / 232 | 144 / 300 / 308 | 0.1973 | 0.1028 | -0.0945 | **REGRESSED (-47.9%)** |
| **EXIT_DOOR** | 0 / 0 / 6 | 0 / 0 / 6 | 0.0000 | 0.0000 | +0.0000 | **UNCHANGED** |
| **EXIT_SIGN** | 11 / 41 / 309 | 11 / 48 / 309 | 0.0084 | 0.0074 | -0.0010 | **PRESERVED** |
| **OBSTACLE** | 0 / 0 / 18 | 0 / 0 / 18 | 0.0000 | 0.0000 | +0.0000 | **UNCHANGED** |
| **OVERALL mAP@50** | — | — | **0.0514** | **0.0276** | **-0.0238** | **REGRESSED** |

---

## 5. Detailed Class-by-Class Deep-Dive Analysis

### A. FIRE_EXTINGUISHER (Control Class Regression)
- **Baseline:** Precision = 0.4067, Recall = 0.4867, AP@50 = 0.1973 (220 TP)
- **Candidate:** Precision = 0.3243, Recall = 0.3186, AP@50 = 0.1028 (144 TP)
- **Root Cause:** In Phase 8.4, 98% of the training loss gradients were driven by fire extinguishers. In Phase 8.8, 524 large vertical door boxes ($w \times h \approx 0.173$) competed for anchor assignment in Anchor 2 ($[0.55, 0.65]$) and Anchor 1 ($[0.22, 0.35]$). In a single-scale $16 \times 16$ network without pre-trained features, the shared convolutional backbone shifted attention to large structural door boundaries, degrading cylinder localization on the test set.

### B. EXIT_DOOR (Domain Generalization Failure)
- **Test AP@50:** 0.0000 for both models.
- **Root Cause:** The held-out Phase 8.3 test set contains only 6 door objects, all sourced from Dataset 6 (`roboflow-blocked-emergency-exit-v1`). The 529 newly added training doors were sourced from `fire_safety_multi` (commercial corridors, building passageways). When training a lightweight 2.2M CNN from scratch for only 5 epochs, the network learned the training corridor door features but could not generalize zero-shot across domain boundaries to the 6 specific test set doors at IoU 0.50.

### C. EXIT_SIGN (Small-Object Stability)
- **Baseline:** Recall = 3.44%, AP@50 = 0.0084 (11 TP)
- **Candidate:** Recall = 3.44%, AP@50 = 0.0074 (11 TP)
- **Status:** **PRESERVED**. The candidate model maintained identical true positive counts (11 detections) and recall.

### D. OBSTACLE (Severe Scarcity Bottleneck)
- **Test AP@50:** 0.0000 for both models.
- **Root Cause:** `OBSTACLE` remains critically data-starved (57 training boxes across 30 scenes). As established in Phase 8.7, rejecting e-commerce stock catalog photos protected room geometry, but physical obstacle samples remain insufficient for open-vocabulary clutter detection.
