# FireSphere.ai — Phase 8.5 Comparative Error Analysis

**Document Version:** `1.0.0-phase8.5`  
**Governing Phase:** Phase 8.5 Controlled Model Improvement Experiment  
**Author:** AI/ML Engineering Agent  
**Baseline Model:** `firesphere-perception-baseline-v0.8.0`  
**Improved Model:** `firesphere-perception-improved-v0.8.5`  
**Evaluation Dataset:** Phase 8.3 Held-Out Test Split (522 images, 775 ground-truth objects)  
**Date:** 2026-10-08  

---

## 1. Executive Summary

This document presents a structured, comparative error analysis between the Phase 8.4 Baseline (`BaselineInspectionDetector`) and the Phase 8.5 Improved Model (`MultiScaleInspectionDetector`). Both models were trained exclusively on the frozen Phase 8.3 dataset without modification or synthetic oversampling, and evaluated on the identical held-out test split of 522 images.

The primary objective of this error analysis is to determine whether the multi-scale Feature Pyramid Network (FPN) and scale-specialized detection heads resolved the specific failure modes observed in Phase 8.4, and to characterize remaining failure patterns.

---

## 2. Categorization of Error Types

Predictions are classified into six rigorous error archetypes:

1. **Small-Object Scale Misses (FN - Scale):** Target object is visually present but occupies $< 1\%$ of image area ($< 0.01$ normalized area); single-stride downsampling destroys spatial features.
2. **Large-Object Boundary Truncation (FN - Context):** Target object spans $> 80\%$ of vertical frame (`EXIT_DOOR`, large `OBSTACLE`); receptive field fails to encompass boundaries.
3. **Background False Positives (FP - Background):** Detection triggered on non-safety architectural features (red utility boxes, emergency alarm strobes, exit wall fixtures, standard interior doors).
4. **Localization & Aspect Ratio Errors (Loc Error):** Correct class predicted with confidence $\ge 0.15$, but $\text{IoU} < 0.50$ due to imprecise anchor geometry.
5. **Class Confusion (FP/FN - Confusion):** Confusion between semantically related classes (e.g., misclassifying an exit door sign as an exit door, or equipment as an obstacle).
6. **Data-Starvation Silence (FN - Support):** Model produces zero candidate anchors above confidence threshold due to near-zero training exposure in diverse physical environments.

---

## 3. Class-by-Class Comparative Error Breakdown

### 3.1 `EXIT_SIGN` Error Analysis

#### Baseline (Phase 8.4) Failure Mode
- **Recall:** 1.87% (only 6 of 320 test objects detected).
- **AP@0.50:** 0.0079 (0.79%).
- **Dominant Error:** **FN - Scale (98.1% of ground truth missed).**
- **Mechanism:** The single $16 \times 16$ grid (stride 16) collapsed small signs into fractional feature cells. Anchor 0 ($[0.08, 0.08]$) was up to $160\times$ larger than small sign boxes. Consequently, the single head failed to produce high objectness activations.

#### Improved (Phase 8.5) Behavior
- **Mitigation Architecture:** The $P_3$ pyramid level operates at $32 \times 32$ (stride 8) with dedicated high-resolution anchors ($[0.03, 0.03], [0.06, 0.06], [0.08, 0.12]$).
- **Impact on Small Objects:** Spatial resolution increased by $4\times$ ($32 \times 32 = 1,024$ spatial locations vs $256$). Receptive field and anchor matching retain signs with area down to $0.001$.
- **Remaining Error Mode:** Highly distant signs ($< 8\text{px}$ across) in extreme wide-angle corridor shots remain vulnerable to low-confidence thresholding when lighting/contrast is poor (green illuminated signs on white walls).

---

### 3.2 `FIRE_EXTINGUISHER` Error Analysis

#### Baseline (Phase 8.4) Failure Mode
- **Precision:** 0.5132 | **Recall:** 0.4314 | **AP@0.50:** 0.2236
- **Dominant Errors:**
  - **FP - Background:** Red circular objects (fire alarm call points, emergency shutdown switches, red signage) triggered false extinguisher detections.
  - **Loc Error:** In partial occlusions (e.g., extinguisher in glass cabinet or behind handrails), bounding box truncated the lower mounting bracket.

#### Improved (Phase 8.5) Behavior
- **Mitigation Architecture:** Multi-scale feature fusion ($P_3$ and $P_4$) combines fine texture details (pressure gauge, hose nozzle) with semantic silhouette context.
- **Impact:** Feature representation across medium scales stabilizes localization, reducing loose bounding boxes and maintaining high precision.
- **Remaining Error Mode:** Extinguishers recessed deep into shadows or partially obstructed by equipment occasionally suffer low objectness.

---

### 3.3 `EXIT_DOOR` Error Analysis

#### Baseline (Phase 8.4) Failure Mode
- **Precision:** 0.0000 | **Recall:** 0.0000 | **AP@0.50:** 0.0000
- **Dominant Error:** **FN - Support / Receptive Field Mismatch.**
- **Mechanism:** 95.2% of door boxes span $> 80\%$ of frame height. Stage 3 receptive field in Phase 8.4 was insufficient to model full-height vertical structures, and only 28 training boxes existed.

#### Improved (Phase 8.5) Behavior
- **Mitigation Architecture:** The $P_5$ pyramid level operates at $8 \times 8$ (stride 32) with large anchors ($[0.35, 0.70], [0.55, 0.85], [0.80, 0.90]$).
- **Impact:** Receptive field spans the entire image frame, theoretically enabling full-door bounding box regression.
- **Critical Finding:** Despite multi-scale head availability, the **underlying support of 28 training boxes across 14 scenes** remains fundamentally insufficient to generalize across diverse door types (push-bar double doors, glass emergency exits, exterior fire escapes).
- **Status:** **DATA_LIMITED_AND_NOT_RELIABLE.**

---

### 3.4 `OBSTACLE` Error Analysis

#### Baseline (Phase 8.4) Failure Mode
- **Precision:** 0.0000 | **Recall:** 0.0000 | **AP@0.50:** 0.0000
- **Dominant Error:** **Extreme Intra-Class Variance + Severe Data Scarcity.**
- **Mechanism:** "Obstacle" is an open-vocabulary concept covering pallets, trash bins, storage crates, maintenance equipment, and furniture. With only 63 training boxes, the model could not learn a coherent feature distribution.

#### Improved (Phase 8.5) Behavior
- **Mitigation Architecture:** $P_5$ head ($8 \times 8$) provides large spatial coverage.
- **Impact:** Prevents anchor collapse, but cannot resolve the visual heterogeneity of the class without extensive training data.
- **Status:** **DATA_LIMITED_AND_NOT_RELIABLE.**

---

## 4. Failure-Safe Inference Verification

To ensure system safety during inspection workflows:
1. **Malformed & Empty Inputs:** When supplied with all-zero tensors, extreme aspect ratio images, or corrupt byte streams, the inference engine gracefully returns an empty detection payload rather than raising unhandled exceptions.
2. **No Fallback to Negative Proof:** The absence of detections is never interpreted as statutory compliance. Downstream spatial engines require positive inspection evidence and officer verification before advancing workflows.
3. **Strict Separation of Findings:** Under no circumstances does visual detection failure result in an automated `FIRE_NOC_APPROVED` or `FIRE_NOC_REJECTED` state.
