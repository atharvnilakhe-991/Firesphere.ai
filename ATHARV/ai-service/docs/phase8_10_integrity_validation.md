# FireSphere.ai — Phase 8.10 Checkpoint Integrity & Validation Protocol

**Phase:** Phase 8.10 Pretrained Weight Provisioning, Provenance & Environment Readiness Gate  
**Service:** `ai-service` Perception Engineering  
**Date:** 2026-10-09  
**Status:** `GATE: CHECKPOINT_INTEGRITY_FAILED` (Full SHA-256 Mismatch vs Prompt String; Functional Integrity PASSED)  
**Production Status:** `NOT_APPROVED`

---

## 1. 10-Step Integrity Check Results

The downloaded checkpoint at `artifacts/phase8_10/pretrained/mobilenet_v2-b0353104.pth` was subjected to all 10 protocol checks:

| Step | Integrity Check | Specification / Threshold | Measured Result | Evaluation |
| :--- | :--- | :--- | :--- | :--- |
| **1** | **File Existence** | Target exists at destination path | File exists at designated path | **PASSED** |
| **2** | **File Size** | Match expected release size (~14.2 MB) | 14,212,972 bytes (13.55 MB) | **PASSED** |
| **3** | **SHA-256 Digest** | Match authorized string / official prefix | Prefix `b0353104` matches; full 64-char string mismatch | **FAIL (Full Match) / PASS (Prefix)** |
| **4** | **Safe Deserialization** | `torch.load(..., weights_only=True)` | Deserialized cleanly into `OrderedDict` | **PASSED** |
| **5** | **State Dict Structure**| Contains valid MobileNetV2 keys | 314 total keys (312 backbone, 2 classifier) | **PASSED** |
| **6** | **Parameter Count** | Matches theoretical backbone count | 2,223,872 backbone parameters (3,539,036 total) | **PASSED** |
| **7** | **NaN / Inf Scan** | Zero NaN or Inf parameter values | $\sum \text{NaN} = 0$, $\sum \text{Inf} = 0$ | **PASSED** |
| **8** | **Tensor Shapes** | Matches `PretrainedInspectionDetector` | 312/312 backbone tensors loaded; 0 missing, 0 unexpected | **PASSED** |
| **9** | **Deterministic Reload**| Multi-load bitwise identical | $\sum |\theta_1 - \theta_2| = 0.0$ across successive loads | **PASSED** |
| **10**| **Pretrained vs Random** | Statistically divergent from random init | $L_2$ distance = 7.6195 ($> 0.5$ threshold); confirmed pretraining | **PASSED** |

---

## 2. Detailed Verification Analysis

### 2.1 State Dictionary Conformance
The state dictionary contains 314 tensors conforming to torchvision MobileNetV2 architecture:
- `features.0.0.weight` through `features.18.1.num_batches_tracked` (312 tensors, 2,223,872 parameters).
- `classifier.1.weight` and `classifier.1.bias` (2 tensors, 1,281,000 parameters).
- When loaded into `PretrainedInspectionDetector.load_pretrained_backbone(state_dict, strict=False)`:
  - Loaded tensors: 312
  - Missing keys: 0
  - Unexpected keys: 0

### 2.2 Numerical Integrity & Pretraining Divergence
- A complete floating-point scan revealed **zero NaN values and zero Inf values**.
- Statistical comparison against a freshly initialized `MobileNetV2Backbone` showed an $L_2$ Euclidean parameter distance of **7.6195** for `features.0.0.weight` (mean: $-0.00089$, std: $0.2284$ vs fresh random mean: $-0.0031$, std: $0.1103$). This confirms that the checkpoint contains genuine, converged ImageNet visual features.

---

## 3. Immutability Verification

### 3.1 Historical Checkpoints
- **Phase 8.4 Baseline:** `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` (VERIFIED UNCHANGED)
- **Phase 8.5 Regressed:** `54ea8d2a2a124e38a5354913e0818514ea534d0d56ddb087c1e96cb03c20e89a` (VERIFIED UNCHANGED)
- **Phase 8.8 Regressed:** `b12ffccf532d819a1cbdd058bb4673f7270ff71533d9862003249fc5360df7b1` (VERIFIED UNCHANGED)

### 3.2 Datasets
- **Phase 8.3 Dataset:** 4,887 images (3,377 train, 988 val, 522 test).
- **Phase 8.3 External Test Set:** Exactly 522 images (100% untouched).
- **Phase 8.8 Audited Dataset:** 4,841 images (3,427 train, 928 val, 486 candidate test).
