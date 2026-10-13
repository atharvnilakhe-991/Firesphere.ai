# FireSphere.ai — Phase 8.9 Candidate Model Card

**Model Identifier:** `firesphere-perception-pretrained-v0.8.9`  
**Model Version:** `0.8.9-candidate`  
**Architecture:** `PretrainedInspectionDetector` (MobileNetV2 + Decoupled Bi-Scale Neck)  
**Creation Date:** 2026-10-09  
**Status:** `EXPERIMENTAL_CANDIDATE_NOT_APPROVED`  
**Authoritative Baseline Reference:** `firesphere-perception-baseline-v0.8.0` (Phase 8.4)  

---

## 1. Model Overview & Specifications

- **Task:** 4-Class Object Detection for Fire Safety Compliance Inspection
- **Classes:**
  - `0`: `FIRE_EXTINGUISHER`
  - `1`: `EXIT_DOOR`
  - `2`: `EXIT_SIGN`
  - `3`: `OBSTACLE`
- **Input Resolution:** $256 \times 256 \times 3$ RGB (normalized to $[0, 1]$)
- **Backbone:** MobileNetV2 (features 0–18, initialized with official ImageNet-1K weights)
- **Neck:** Decoupled Bi-Scale FPN (P3 stride 8, P4 stride 16; 64 neck channels)
- **Anchors:** 2,560 total anchors
  - P3 ($32\times 32$): $[0.04, 0.04]$, $[0.08, 0.08]$ (2 anchors/cell = 2,048 anchors)
  - P4 ($16\times 16$): $[0.22, 0.35]$, $[0.20, 0.75]$ (2 anchors/cell = 512 anchors)
- **Total Parameters:** 2,308,644 (Trainable: 2,253,156, Frozen: 55,488 in Stage B)
- **Model Checkpoint Size:** 13.45 MB (`artifacts/phase8_9/checkpoints/best.pt`)
- **Checkpoint SHA-256:** `abb5bab4a4eb3de7d2275c5a946238804f3f453cc1419a3906b888fe52d88d36`

---

## 2. Intended Use & Operational Guardrails

- **Intended Use:** Non-production research experiment evaluating transfer learning for minority-class expansion resilience.
- **Human-in-the-Loop Constraint:** Under FireSphere.ai core operating principles, perception models are strictly advisory. Detections must undergo human verification (`REQUIRES_OFFICER_VERIFICATION`). The model MUST NOT autonomously issue fire NOC approvals or rejections.
- **Production Status:** **`NOT_APPROVED`**. This model is NOT deployed to API endpoints or production inference pipelines.

---

## 3. Evaluation & Validation Summary

### Held-Out Test Set Performance (522 images, $\tau = 0.15$)
- **mAP@50:** 0.0503
- **mAP@50:95:** 0.0195
- **Mean Precision:** 0.2064
- **Mean Recall:** 0.1197
- **Mean F1:** 0.1482
- **FIRE_EXTINGUISHER AP@50:** 0.1329 (Precision: 0.4194, Recall: 0.3164)
- **EXIT_SIGN AP@50:** 0.0684 (Precision: 0.4062, Recall: 0.1625)
- **EXIT_DOOR AP@50:** 0.0000 (0 detections on 6 test objects)
- **OBSTACLE AP@50:** 0.0000 (0 detections on 18 test objects)

---

## 4. Known Limitations & Failure Modes

1. **Primary Compliance Regressed vs Baseline:** `FIRE_EXTINGUISHER` AP@50 regressed by -0.0644 relative to the Phase 8.4 baseline (0.1329 vs 0.1973).
2. **Door and Obstacle Inoperability:** The model cannot reliably detect exit doors or corridor obstacles in its current state.
3. **CPU Latency Profile:** Single-threaded CPU inference latency is 170.47 ms (~5.87 FPS), which is suitable for offline asynchronous batch inspection but insufficient for real-time mobile video processing.
4. **Provenance Disclosure:** Pretrained backbone initialized from `mobilenet_v2-b0353104.pth` under explicit user research authorization; full 64-character SHA-256 is not published by PyTorch.
