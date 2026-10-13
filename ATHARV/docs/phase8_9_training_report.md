# FireSphere.ai — Phase 8.9 Pretrained Transfer-Learning Training Report

**Document Version:** `2.0.0-phase8.9`  
**Governing Phase:** Phase 8.9 Pretrained Transfer-Learning Experiment & Controlled Evaluation  
**Author:** AI/ML Engineering Agent  
**Environment:** `ai-service/.venv` (Python 3.13.7, PyTorch 2.14.1+cpu)  
**Date:** 2026-10-09  
**Status:** `TRAINING_COMPLETED_EXPERIMENT_ONLY`  
**Production Status:** `NOT_APPROVED`  

---

## 1. Executive Summary & Authorization Context

Following Phase 8.10 and Phase 8.10A provenance audits, the user explicitly authorized a controlled, non-production research experiment utilizing the local PyTorch/Torchvision MobileNetV2 ImageNet-1K checkpoint:
- **Authorization Scope:** Non-production research experiment only.
- **Production Status:** Strictly `NOT_APPROVED`.
- **Authoritative Baseline Control:** `firesphere-perception-baseline-v0.8.0` (Phase 8.4) remains the unchanged reference baseline.
- **Provenance Limitation Recorded:** The complete 64-character SHA-256 digest is not published by official PyTorch documentation (only the 8-character prefix `b0353104` is published). Execution proceeded under explicit user research authorization.

---

## 2. Pre-Training Checkpoint Integrity Verification

Before training execution, strict pre-training integrity verification was performed:

| Checkpoint Parameter | Expected Value | Verified Local Value | Status |
| :--- | :--- | :--- | :---: |
| **Path** | `ai-service/artifacts/phase8_10/pretrained/mobilenet_v2-b0353104.pth` | Same | Match |
| **SHA-256 Digest** | `b03531047ffacf1e2488318dcd2aba1126cde36e3bfe1aa5cb07700aeeee9889` | `b03531047ffacf1e2488318dcd2aba1126cde36e3bfe1aa5cb07700aeeee9889` | **Match** |
| **File Size** | `14,212,972` bytes | `14,212,972` bytes | **Match** |
| **Deserialization Safety** | `weights_only=True` | Passed with zero unpickling warnings | **Pass** |
| **Structural Compatibility** | 312 backbone parameter tensors | 312 loaded (0 missing, 0 unexpected) | **Pass** |
| **Numerical Integrity** | Zero NaN or Inf values | 0 NaNs, 0 Infs across all weights | **Pass** |

Historical Phase 8.10 (`CHECKPOINT_INTEGRITY_FAILED`) and Phase 8.10A (`CHECKPOINT_HASH_REQUIRES_MANUAL_VERIFICATION`) reports remain preserved and unchanged.

---

## 3. Dataset Configuration & Class Balance

Training used the audited Phase 8.8 dataset partitions with zero split leakage:
- **Training Set:** 3,427 images ([`datasets/fire_safety/prepared/phase8_8/yolo/images/train`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/phase8_8/yolo/images/train))
- **Validation Set:** 928 images ([`datasets/fire_safety/prepared/phase8_8/yolo/images/val`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/phase8_8/yolo/images/val))
- **Untouched Test Benchmark:** 522 images ([`datasets/fire_safety/prepared/phase8_3/yolo/images/test`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/datasets/fire_safety/prepared/phase8_3/yolo/images/test))

### Class Distribution in Training Data
- Class 0 (`FIRE_EXTINGUISHER`): 2,813 annotations (mean normalized area = 0.048, aspect ratio = 4.24)
- Class 1 (`EXIT_DOOR`): 524 annotations (mean normalized area = 0.183, aspect ratio = 3.38, 529 physical scenes)
- Class 2 (`EXIT_SIGN`): 1,566 annotations (mean normalized area = 0.119, aspect ratio = 1.11)
- Class 3 (`OBSTACLE`): 57 annotations (mean normalized area = 0.396, severe long-tail scarcity)

---

## 4. Two-Stage Transfer Learning Protocol

To prevent randomly initialized neck and head parameters from destroying pretrained feature representations, training was decoupled into two sequential stages:

### Stage A: Frozen Backbone Warmup (Epochs 1–2)
- **Backbone Status:** 100% frozen (`param.requires_grad = False`).
- **Trainable Parameters:** 84,772 parameters (Decoupled Bi-Scale Neck + Heads P3 and P4).
- **Frozen Parameters:** 2,223,872 parameters (MobileNetV2 features 0–18).
- **Optimizer:** AdamW ($lr = 1\times 10^{-3}$, weight decay = $1\times 10^{-4}$).

### Stage B: Partial Unfreeze & Fine-Tuning (Epochs 3–5)
- **Stem & Early Backbone:** Features 0 through 6 remained frozen (55,488 parameters).
- **Deep Backbone:** Features 7 through 18 unfrozen (2,168,384 parameters).
- **Trainable Parameters:** 2,253,156 parameters.
- **Optimizer:** AdamW with parameter groups:
  - Deep Backbone: $lr = 1\times 10^{-4}$ (0.1x head rate)
  - Neck and Heads: $lr = 1\times 10^{-3}$ (1.0x)

---

## 5. Empirical Training History & Convergence

| Epoch | Stage | Train Loss | Train Box | Train Obj | Train Cls | Val Loss | Val Box | Val Obj | Val Cls | Best? | Duration |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| 1 | Stage A | 1.6613 | 0.4650 | 0.4404 | 0.1940 | 1.2104 | 0.4384 | 0.2313 | 0.0682 | Yes | 232.07s |
| 2 | Stage A | 0.8268 | 0.3182 | 0.1400 | 0.0336 | 0.9689 | 0.4020 | 0.0822 | 0.0551 | Yes | 134.62s |
| 3 | Stage B | 0.6022 | 0.2692 | 0.0441 | 0.0132 | 0.8703 | 0.3866 | 0.0264 | 0.0472 | Yes | 181.84s |
| 4 | Stage B | 0.4388 | 0.2048 | 0.0225 | 0.0045 | 0.8477 | 0.3828 | 0.0183 | 0.0425 | Yes | 182.86s |
| 5 | Stage B | 0.3442 | 0.1617 | 0.0173 | 0.0023 | **0.8250** | 0.3730 | 0.0146 | 0.0429 | **Yes** | 209.60s |

**Total Training Duration:** 943.58 seconds (~15.7 minutes on CPU).

---

## 6. Candidate Checkpoint Artifact

- **Model ID:** `firesphere-perception-pretrained-v0.8.9`
- **Model Version:** `0.8.9-candidate`
- **Best Epoch:** Epoch 5
- **Best Validation Loss:** 0.82503
- **Checkpoint Location:** [`ai-service/artifacts/phase8_9/checkpoints/best.pt`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/phase8_9/checkpoints/best.pt)
- **Checkpoint File Size:** 14,100,328 bytes (13.45 MB)
- **Computed SHA-256:** `abb5bab4a4eb3de7d2275c5a946238804f3f453cc1419a3906b888fe52d88d36`
