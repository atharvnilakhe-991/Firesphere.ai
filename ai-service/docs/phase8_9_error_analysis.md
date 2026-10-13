# FireSphere.ai — Phase 8.9 Error Analysis & Failure Mode Report

**Document Version:** `2.0.0-phase8.9`  
**Governing Phase:** Phase 8.9 Pretrained Transfer-Learning Experiment & Controlled Evaluation  
**Author:** AI/ML Engineering Agent  
**Artifact Directory:** [`ai-service/artifacts/phase8_9/visualizations/`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/phase8_9/visualizations)  
**Date:** 2026-10-09  

---

## 1. Executive Summary & Diagnostic Sample Overview

Visual error analysis was conducted on 150 test images with automated ground-truth bounding box matching (IoU $\ge 0.50$). Annotated diagnostic images were generated and serialized under three artifact directories:
- **True Positives (TP):** [`visualizations/tp/`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/phase8_9/visualizations/tp) (10 exemplar samples)
- **False Positives (FP):** [`visualizations/fp/`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/phase8_9/visualizations/fp) (10 exemplar samples)
- **False Negatives (FN):** [`visualizations/fn/`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/phase8_9/visualizations/fn) (10 exemplar samples)

---

## 2. Failure Mode Breakdown by Class

### 2.1 `FIRE_EXTINGUISHER` (Support: 452, TP: 143, FP: 198, FN: 309)
1. **False Negatives (Missed Detections, $N = 309$):**
   - **Small/Distant Extinguishers:** Extinguishers mounted far down hallways whose normalized bounding box area is $< 0.02$ fail to trigger Head P4 ($16\times 16$ grid, cell stride 16).
   - **Obscured or Recessed Extinguishers:** Partially recessed cabinet mountings where the red cylinder is occluded by reflective glass.
2. **False Positives ($N = 198$, down from 321 in baseline):**
   - **Red Fire Alarms & Hose Cabinets:** Small red rectangular panels misclassified as extinguishers with confidence $0.15 \le \tau \le 0.28$.
   - **Pretrained Improvement:** MobileNetV2 pretrained texture filters significantly reduced spurious background activations on wall textures, cutting FPs by 38.3%.

### 2.2 `EXIT_SIGN` (Support: 320, TP: 52, FP: 76, FN: 268)
1. **Breakthrough in True Positives ($N = 52$, vs 11 in baseline):**
   - The decoupled P3 head ($32\times 32$ feature map, stride 8) combined with ImageNet edge and text-like part filters enabled reliable recognition of illuminated overhead exit signs.
2. **False Negatives ($N = 268$):**
   - Very small ceiling signs ($w < 0.04$) where illumination wash removes symbol details.
   - Non-illuminated white-on-green plaques in shadowed environments.
3. **False Positives ($N = 76$):**
   - Rectangular overhead fluorescent light fixtures and HVAC grills triggering green/white contrast activations.

### 2.3 `EXIT_DOOR` (Support: 6, TP: 0, FP: 0, FN: 6)
1. **Complete False Negative Rate (100% missed):**
   - Test ground truth contains only 6 doors.
   - Aspect ratio $[0.20, 0.75]$ in Head P4 requires high IoU overlap across tall vertical structures. When doors are photographed at oblique perspective angles, bounding box IoU drops below 0.50.
   - Model outputs for doors had confidence scores below the 0.15 operating threshold.

### 2.4 `OBSTACLE` (Support: 18, TP: 0, FP: 0, FN: 18)
1. **Complete False Negative Rate (100% missed):**
   - Ground truth support in training data is only 57 instances across 3,427 images.
   - Obstacles encompass high semantic variance (boxes, trash bins, pallets, ladders, construction debris). Pretrained features alone cannot establish a coherent visual centroid for such heterogeneous semantics without substantial dedicated annotations.

---

## 3. Engineering Recommendations for Next Phases

1. **Multi-Scale Anchor Refinement:** Assign small extinguishers ($< 0.04$ area) to P3 head alongside exit signs to recover small-extinguisher recall.
2. **Dedicated Obstacle Data Campaign:** Obstacle detection cannot succeed without $\ge 1,000$ diverse corridor obstruction instances.
3. **Door Perspective Augmentation:** Apply random perspective and shear transforms to simulate oblique corridor angles during training.
