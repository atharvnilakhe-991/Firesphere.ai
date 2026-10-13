# FireSphere.ai — Phase 8.8 Controlled Model Comparison Report

**Document Version:** `1.0.0-phase8.8`  
**Governing Phase:** Phase 8.8 Controlled Model Training & Comparative Evaluation  
**Author:** AI/ML Engineering Agent  
**Baseline Model Control:** `firesphere-perception-baseline-v0.8.0`  
**Candidate Model:** `firesphere-perception-candidate-v0.8.8`  
**Evaluation Benchmark:** Untouched Phase 8.3 Test Split (522 images, 796 ground-truth objects)  
**Date:** 2026-10-09  

---

## 1. Overview of the Controlled Comparison

Phase 8.8 was designed as an unconfounded, controlled experiment:
- **Identical Architecture:** Both models utilize `BaselineInspectionDetector` (1 scale, stride 16, 768 anchors/image, 2.24M parameters).
- **Identical Training Schedule:** Both models trained for 5 epochs with AdamW ($lr=0.001$, weight decay = $1\times 10^{-4}$, seed = 42).
- **Variable Under Test:** Training dataset.
  - Phase 8.4 Baseline trained on Phase 8.3 prepared data (3,377 train images; 14 physical door scenes, 30 obstacle scenes).
  - Phase 8.8 Candidate trained on Phase 8.8 audited data (3,427 train images; 529 physical door scenes, 30 obstacle scenes).
- **Evaluation Rule:** Evaluated strictly once on the same untouched 522-image Phase 8.3 test set at identical operating thresholds ($\tau = 0.15, \text{IoU} = 0.50$).

---

## 2. Direct Apples-to-Apples Evaluation Metrics

| Metric | Phase 8.4 Baseline | Phase 8.8 Candidate | Delta ($\Delta$) | Empirical Determination |
| :--- | :---: | :---: | :---: | :---: |
| **Mean Precision** | 0.1545 | 0.1277 | -0.0268 | Lower |
| **Mean Recall** | 0.1303 | 0.0882 | -0.0421 | Lower |
| **Mean F1 Score** | 0.1256 | 0.0949 | -0.0307 | Lower |
| **mAP@50** | **0.0514** | **0.0276** | **-0.0238** | **REGRESSED (-46.3%)** |
| **mAP@50:95** | **0.0172** | **0.0097** | **-0.0075** | **REGRESSED (-43.6%)** |

---

## 3. Class-by-Class Comparative Analysis

### 3.1 FIRE_EXTINGUISHER (Control Class)
- **Phase 8.4 Baseline:** AP@50 = 0.1973 | Precision = 0.4067 | Recall = 0.4867 (TP: 220, FP: 321, FN: 232)
- **Phase 8.8 Candidate:** AP@50 = 0.1028 | Precision = 0.3243 | Recall = 0.3186 (TP: 144, FP: 300, FN: 308)
- **Delta:** $\Delta \text{AP@50} = -0.0945$ (**-47.9% Relative Drop**)
- **Empirical Verdict:** **REGRESSED**
- **Analysis:** Introducing 524 training doors created representation competition in the single-scale shared convolutional backbone. Without multi-scale heads or frozen pre-trained feature representations, the shared CNN redirected capacity to door features, resulting in reduced extinguisher sensitivity. Under Phase 8.8 governance rule 12.A, minority-class expansion is unacceptable if it causes substantial degradation to this control class.

### 3.2 EXIT_DOOR (Target Expansion Class)
- **Phase 8.4 Baseline:** AP@50 = 0.0000 | Precision = 0.0000 | Recall = 0.0000 (TP: 0, FP: 0, FN: 6)
- **Phase 8.8 Candidate:** AP@50 = 0.0000 | Precision = 0.0000 | Recall = 0.0000 (TP: 0, FP: 0, FN: 6)
- **Delta:** $\Delta \text{AP@50} = +0.0000$
- **Empirical Verdict:** **UNCHANGED ON HELD-OUT TEST DISTRIBUTION**
- **Analysis:** While physical scene diversity in the training corpus increased from 14 to 529 scenes (+37.8x expansion), the held-out test split contains only 6 door objects, all originating from the specific visual distribution of Dataset 6 (`roboflow-blocked-emergency-exit-v1`). The newly added doors from `fire_safety_multi` did not yield zero-shot domain transfer to those specific 6 test doors at IoU 0.50 in a lightweight model trained from scratch.

### 3.3 EXIT_SIGN (Small-Object Class)
- **Phase 8.4 Baseline:** AP@50 = 0.0084 | Precision = 0.2115 | Recall = 0.0344 (TP: 11, FP: 41, FN: 309)
- **Phase 8.8 Candidate:** AP@50 = 0.0074 | Precision = 0.1864 | Recall = 0.0344 (TP: 11, FP: 48, FN: 309)
- **Delta:** $\Delta \text{AP@50} = -0.0010$
- **Empirical Verdict:** **PRESERVED**
- **Analysis:** Candidate model preserved exact true positive count (11 detections) and recall (3.44%), demonstrating that sign detection stability was unaffected by door expansion.

### 3.4 OBSTACLE (Data-Constrained Class)
- **Phase 8.4 Baseline:** AP@50 = 0.0000 | Precision = 0.0000 | Recall = 0.0000 (TP: 0, FP: 0, FN: 18)
- **Phase 8.8 Candidate:** AP@50 = 0.0000 | Precision = 0.0000 | Recall = 0.0000 (TP: 0, FP: 0, FN: 18)
- **Delta:** $\Delta \text{AP@50} = +0.0000$
- **Empirical Verdict:** **UNCHANGED**
- **Analysis:** As verified in Phase 8.7, physical clutter data remains scarce (57 training boxes across 30 scenes). The model cannot generalize open-vocabulary clutter geometry from 30 physical scenes.

---

## 4. Multi-Dimensional Evaluation Grid

| Evaluation Dimension | Baseline (Phase 8.4) | Candidate (Phase 8.8) | Comparative Assessment |
| :--- | :---: | :---: | :--- |
| **1. Overall Accuracy (mAP@50)** | **0.0514** | 0.0276 | **Baseline is superior** |
| **2. EXIT_DOOR Generalization** | 0.0000 | 0.0000 | Tied (Unresolved at IoU 0.50) |
| **3. OBSTACLE Performance** | 0.0000 | 0.0000 | Tied (Data limited) |
| **4. EXIT_SIGN Preservation** | 0.0084 | 0.0074 | Preserved |
| **5. FIRE_EXTINGUISHER Preservation** | **0.1973** | 0.1028 | **Baseline is superior (+91.9% higher)** |
| **6. Total Latency (p50)** | 39.74 ms | 40.17 ms | Tied (<0.5 ms difference) |
| **7. Checkpoint Size** | 25.66 MB | **8.56 MB** | Candidate is more compact |
| **8. Training Stability** | Stable | Stable | Both converged reliably |
| **9. Dataset Diversity** | 14 door scenes | **529 door scenes** | Candidate has +37.8x training diversity |
| **10. Operational Suitability** | Not Approved | Not Approved | Neither is approved for production |

---

## 5. Comparative Conclusion

The controlled experiment proved definitively that:
1. **Adding large vertical minority-class objects into a single-scale scratch-trained detector induces representation dilution on established classes.**
2. **Phase 8.4 remains the authoritative reference baseline for FireSphere.ai.**
3. **Phase 8.8 candidate is recorded honestly as `CANDIDATE_REGRESSED` and is barred from production.**
