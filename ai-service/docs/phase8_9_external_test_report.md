# FireSphere.ai — Phase 8.9 External Test Evaluation Report

**Document Version:** `2.0.0-phase8.9`  
**Governing Phase:** Phase 8.9 Pretrained Transfer-Learning Experiment & Controlled Evaluation  
**Author:** AI/ML Engineering Agent  
**Dataset:** Untouched Phase 8.3 External Test Split (522 images, 796 ground truth objects)  
**Evaluation Protocol:** Evaluated strictly ONCE after model selection  
**Date:** 2026-10-09  

---

## 1. Executive Summary

The candidate model `firesphere-perception-pretrained-v0.8.9` was evaluated strictly once against the untouched Phase 8.3 external test set (522 images, 796 ground truth annotations) using the validation-selected operational threshold $\tau = 0.15$ and IoU threshold 0.45.

### Key Empirical Findings:
1. **Dramatic Exit Sign Breakthrough:** `EXIT_SIGN` AP@50 increased by **+0.0600** (from 0.0084 in baseline to **0.0684** — an 8.1x increase). True Positives jumped from 11 to 52, while Precision nearly doubled from 0.2115 to 0.4062.
2. **Partial Extinguisher Representation Recovery:** Compared to Phase 8.8 (AP@50 = 0.1028), the pretrained backbone recovered `FIRE_EXTINGUISHER` AP@50 to **0.1329** (+0.0301) and drastically suppressed false positives from 321 to 198. However, compared to the authoritative Phase 8.4 baseline (AP@50 = 0.1973), extinguisher performance remains lower by **-0.0644**.
3. **Severe Support Scarcity for Door and Obstacle:** Ground truth support on the untouched test split contains only **6 exit doors** and **18 obstacles**. The model achieved zero true positives on both.

---

## 2. External Test Performance Metrics

| Metric | Phase 8.4 Baseline | Phase 8.8 Candidate | Phase 8.9 Candidate | Delta vs Baseline |
| :--- | :---: | :---: | :---: | :---: |
| **Evaluated Images** | 522 | 522 | 522 | 0 |
| **Ground Truth Objects** | 796 | 796 | 796 | 0 |
| **Predicted Detections** | 593 | 503 | 469 | -124 |
| **Mean Precision** | 0.1545 | 0.1277 | **0.2064** | **+0.0519** |
| **Mean Recall** | 0.1303 | 0.0882 | 0.1197 | -0.0106 |
| **Mean F1-Score** | 0.1256 | 0.0949 | **0.1482** | **+0.0226** |
| **mAP@50** | **0.0514** | 0.0276 | 0.0503 | -0.0011 |
| **mAP@50:95** | 0.0172 | 0.0097 | **0.0195** | **+0.0023** |

---

## 3. Per-Class Analysis on Held-Out Test Data

| Class | Support | TP | FP | FN | Precision | Recall | F1 | AP@50 | Delta AP50 vs 8.4 |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **FIRE_EXTINGUISHER** | 452 | 143 | 198 | 309 | 0.4194 | 0.3164 | 0.3607 | 0.1329 | -0.0644 |
| **EXIT_DOOR** | 6 | 0 | 0 | 6 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | +0.0000 |
| **EXIT_SIGN** | 320 | 52 | 76 | 268 | 0.4062 | 0.1625 | 0.2321 | **0.0684** | **+0.0600** |
| **OBSTACLE** | 18 | 0 | 0 | 18 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | +0.0000 |

### Detailed Class Insights
- **`FIRE_EXTINGUISHER`:** Precision improved from 0.4067 to 0.4194, and False Positives dropped by 38.3% (from 321 to 198). However, recall dropped from 0.4867 to 0.3164 as the model adopted more conservative confidence scores when balancing multiple classes.
- **`EXIT_SIGN`:** Pretrained convolutional features at stride 8 (P3 head) resolved the primary limitation identified in Phase 8.4–8.8. Sign AP@50 surged from 0.0084 to 0.0684.
- **`EXIT_DOOR`:** With only 6 ground-truth doors in the test set, generalization cannot be validated externally. The detector generated 0 predictions for door on the test set, reflecting extreme conservatism under anchor aspect ratio $[0.20, 0.75]$.
- **`OBSTACLE`:** With only 18 ground-truth obstacles in the test set and only 57 training instances across the entire corpus, the model failed to learn viable visual representations.
