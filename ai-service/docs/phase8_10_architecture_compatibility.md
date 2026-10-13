# FireSphere.ai — Phase 8.10 Architecture Compatibility & Freeze-Plan Analysis

**Phase:** Phase 8.10 Pretrained Weight Provisioning, Provenance & Environment Readiness Gate  
**Service:** `ai-service` Perception Engineering  
**Date:** 2026-10-09  
**Status:** `GATE: EXTERNAL_WEIGHT_DOWNLOAD_REQUIRED`  
**Production Status:** `NOT_APPROVED`

---

## 1. Architectural Alignment Overview

In Phase 8.10, the `PretrainedInspectionDetector` architecture designed in Phase 8.9 was formally verified for mathematical, structural, and interface compatibility in [pretrained_detector.py](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/pretrained_detector.py).

The architecture specifically targets the diagnosed failure modes of previous phases:
1. **Targeting Phase 8.8 Representation Dilution:** Incorporates a fixed pre-trained visual backbone (MobileNetV2, 2.22M parameters) to provide generic low-level edge and texture filters, preventing filter overwriting when minority-class doors are added.
2. **Targeting Phase 8.5 Anchor Dilution:** Decouples the detection neck into two dedicated scales (P3 at stride 8 for small objects, P4 at stride 16 for medium/tall objects), restricting total anchors to 2,560 (compared to Phase 8.5's 4,032 anchors).

---

## 2. Frozen Perception Vocabulary Verification

The model is strictly restricted to the 4 canonical FireSphere perception classes established in Phase 8.1:
```
0: FIRE_EXTINGUISHER
1: EXIT_DOOR
2: EXIT_SIGN
3: OBSTACLE
```

Under Phase 8.1 scope freeze rules, downstream compliance concepts (`blocked_extinguisher`, `missing_extinguisher`, `expired_extinguisher`, `emergency_exit_obstruction`) are **not** detector classes and remain spatial rule reasoning concepts.

---

## 3. Structural Specification & Forward-Pass Verification

```
Input: (B, 3, 256, 256)
  │
  ▼
MobileNetV2 Backbone (features[0..18])
  ├── C3 Tap (features[6]):  (B, 32, 32, 32)   [Stride 8]
  └── C4 Tap (features[13]): (B, 96, 16, 16)   [Stride 16]
  │
  ▼
Decoupled Bi-Scale Neck
  ├── Lateral C4 -> P4: (B, 64, 16, 16)
  └── Top-Down Upsample (P4) + Lateral C3 -> P3: (B, 64, 32, 32)
  │
  ▼
Bi-Scale Detection Heads
  ├── Head P3: 2 anchors ([0.04, 0.04], [0.08, 0.08]) -> (B, 2, 9, 32, 32) [2,048 anchors]
  └── Head P4: 2 anchors ([0.22, 0.35], [0.20, 0.75]) -> (B, 2, 9, 16, 16) [512 anchors]
```

### Parameter Breakdown:
- **Backbone (`MobileNetV2Backbone`):** 2,223,872 parameters (312 tensors)
- **Neck (`DecoupledBiScaleNeck`):** 82,432 parameters (12 tensors)
- **Heads (`head_p3` + `head_p4`):** 2,340 parameters (4 tensors)
- **Total Model Parameters:** 2,308,644 parameters

---

## 4. Transfer-Learning Freeze Plan Validation

The model includes a native parameter-freezing and parameter-group API:

### Stage A: Warmup / Frozen Backbone
- **Configuration:** `detector.set_freeze_stage("stage_a")`
- **Backbone:** 100% frozen (`param.requires_grad = False` across all 312 tensors).
- **Neck & Heads:** Fully trainable (`requires_grad = True`).
- **Trainable Parameters:** 84,772 (3.67%).
- **Frozen Parameters:** 2,223,872 (96.33%).
- **Optimizer Group:** Single group for neck/heads at learning rate $1\times 10^{-3}$.
- **Objective:** Warm up randomly initialized neck and head layers without gradient flow corrupting pre-trained backbone representations.

### Stage B: Partial Fine-Tuning
- **Configuration:** `detector.set_freeze_stage("stage_b")`
- **Stem & Early Features (`features[0..6]`):** Frozen (`requires_grad = False`, 55,488 parameters). Preserves fundamental Gabor-like edge and texture filters.
- **Deep Features (`features[7..18]`):** Unfrozen (`requires_grad = True`, 2,168,384 parameters).
- **Neck & Heads:** Fully trainable (`requires_grad = True`, 84,772 parameters).
- **Trainable Parameters:** 2,253,156 (97.60%).
- **Frozen Parameters:** 55,488 (2.40%).
- **Optimizer Groups:**
  - Group 1: Deep backbone features ($lr = 1\times 10^{-4}$, 10x lower learning rate).
  - Group 2: Neck and detection heads ($lr = 1\times 10^{-3}$).

Gradient isolation and parameter group separation have been mathematically verified in test code.
