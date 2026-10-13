# FireSphere.ai — Phase 8.9 Validation & Confidence Threshold Sweep Report

**Document Version:** `2.0.0-phase8.9`  
**Governing Phase:** Phase 8.9 Pretrained Transfer-Learning Experiment & Controlled Evaluation  
**Author:** AI/ML Engineering Agent  
**Dataset:** Phase 8.8 Validation Split (928 images, 1,443 ground truth objects)  
**Model Under Test:** `firesphere-perception-pretrained-v0.8.9` (`best.pt`, Epoch 5)  
**Date:** 2026-10-09  

---

## 1. Executive Summary

Evaluation was conducted across the 928 validation images of the audited Phase 8.8 split. The candidate model demonstrated significant improvements on compact object detection (`EXIT_SIGN`) compared to scratch baselines, but confirmed the persistence of extreme aspect ratio challenges for `EXIT_DOOR` and severe data scarcity for `OBSTACLE`.

---

## 2. Validation Metrics at Baseline Operational Threshold ($\tau = 0.15$)

| Metric | Overall Value |
| :--- | :---: |
| **Evaluated Images** | 928 |
| **Ground Truth Objects** | 1,443 |
| **Predicted Detections** | 897 |
| **Mean Precision** | **0.1967** |
| **Mean Recall** | **0.1248** |
| **Mean F1-Score** | **0.1495** |
| **mAP@50** | **0.0506** |
| **mAP@50:95** | **0.0204** |

### Per-Class Validation Breakdown

| Class | Support | TP | FP | FN | Precision | Recall | F1 | AP@50 | AP@50:95 |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **FIRE_EXTINGUISHER** | 854 | 288 | 404 | 566 | 0.4162 | 0.3372 | 0.3726 | 0.1401 | 0.0515 |
| **EXIT_DOOR** | 117 | 0 | 0 | 117 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 |
| **EXIT_SIGN** | 469 | 76 | 129 | 393 | 0.3707 | 0.1620 | 0.2255 | 0.0624 | 0.0303 |
| **OBSTACLE** | 3 | 0 | 0 | 3 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | 0.0000 |

---

## 3. Validation Confidence Threshold Sweep ($\tau \in [0.05, 0.50]$)

A systematic confidence threshold sweep was performed exclusively on validation data to calibrate the operating threshold without biasing against the held-out test split:

| $\tau$ Threshold | Precision | Recall | F1-Score | mAP@50 | Extinguisher AP50 | Exit Door AP50 | Exit Sign AP50 | Obstacle AP50 |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **0.05** | 0.0682 | 0.1988 | 0.1004 | 0.0267 | 0.0781 | 0.0003 | 0.0282 | 0.0000 |
| **0.10** | 0.1299 | 0.1547 | 0.1396 | 0.0418 | 0.1168 | 0.0000 | 0.0505 | 0.0000 |
| **0.15 (Nominal)** | **0.1967** | **0.1248** | **0.1495** | **0.0506** | 0.1401 | 0.0000 | **0.0624** | 0.0000 |
| **0.20** | 0.2343 | 0.0980 | 0.1354 | 0.0479 | **0.1404** | 0.0000 | 0.0512 | 0.0000 |
| **0.25** | 0.2685 | 0.0796 | 0.1211 | 0.0461 | 0.1379 | 0.0000 | 0.0463 | 0.0000 |
| **0.30** | 0.3219 | 0.0678 | 0.1100 | 0.0462 | 0.1381 | 0.0000 | 0.0467 | 0.0000 |
| **0.40** | 0.3639 | 0.0426 | 0.0751 | 0.0343 | 0.1058 | 0.0000 | 0.0314 | 0.0000 |
| **0.50** | 0.4040 | 0.0288 | 0.0534 | 0.0275 | 0.0832 | 0.0000 | 0.0270 | 0.0000 |

### Calibration Analysis
1. **Optimal Tradeoff:** $\tau = 0.15$ achieves the highest global F1-score (0.1495) and global mAP@50 (0.0506).
2. **Noise vs. Precision:** Setting $\tau < 0.10$ floods predictions with background clutter (Precision collapses to 0.0682 at $\tau = 0.05$).
3. **Threshold Selection Integrity:** $\tau = 0.15$ was selected strictly based on validation data prior to evaluating the untouched Phase 8.3 test set.
