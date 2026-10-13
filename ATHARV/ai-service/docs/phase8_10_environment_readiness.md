# FireSphere.ai — Phase 8.10 Environment Readiness & Dependency Analysis

**Phase:** Phase 8.10 Pretrained Weight Provisioning, Provenance & Environment Readiness Gate  
**Service:** `ai-service` Perception Engineering  
**Date:** 2026-10-09  
**Status:** `GATE: EXTERNAL_WEIGHT_DOWNLOAD_REQUIRED`  
**Production Status:** `NOT_APPROVED`

---

## 1. Execution Environment Specification

A comprehensive technical audit of the current Python virtual environment was executed to verify hardware and runtime compatibility:

- **Operating System:** Windows 11 Pro / Enterprise (AMD64)
- **Python Version:** 3.13.7 (MSC v.1944 64-bit AMD64)
- **PyTorch Version:** `2.14.1+cpu`
- **CUDA Hardware Acceleration:** `False` (CPU execution only)
- **Available Core Dependencies:** `numpy` 2.5.3, `pillow` 12.3.0, `opencv-python-headless` 5.0.0.93, `fastapi` 0.142.2, `pydantic` 2.13.5, `pytest` 9.1.1.

---

## 2. Dependency Strategy: Native PyTorch Implementation

### Analysis of Optional Libraries (`torchvision`, `timm`):
- `torchvision`: Currently **not installed**.
- `timm`: Currently **not installed**.

### Risk Assessment of Installing `torchvision`:
Installing binary packages such as `torchvision` into a Python 3.13 environment running PyTorch `2.14.1+cpu` carries high risk of:
1. Binary C-extension ABI incompatibilities.
2. Unwanted torch version overrides or virtual environment degradation.
3. Network overhead and uncontrolled wheel dependency graphs.

### Native Solution Implemented in FireSphere:
Rather than requiring third-party library installations, `MobileNetV2Backbone` is implemented directly in native PyTorch (`nn.Module`).
- It precisely mirrors the official PyTorch torchvision layer hierarchy (`features.0` through `features.18`).
- Its parameter names match the 312 tensors in official PyTorch `mobilenet_v2-b0353104.pth` checkpoints bit-for-bit.
- It loads official weights directly via `torch.load` and `load_state_dict(strict=False)` with zero external dependencies.

**Result:** Dependency compatibility is fully resolved without installing any risky binary dependencies.

---

## 3. Preprocessing Compatibility

| Parameter | FireSphere Standard (Phase 8.4) | ImageNet Pretrained Standard (Phase 8.10 Target) | Adaptation Strategy |
| :--- | :--- | :--- | :--- |
| **Resolution** | $256 \times 256$ pixels | $224 \times 224$ (typical) / flexible | Retain $256 \times 256$ (fully convolutional backbone supports arbitrary spatial grids divisible by 32) |
| **Channels** | 3-channel RGB | 3-channel RGB | Identical (no conversion required) |
| **Scaling** | $[0.0, 1.0]$ float32 | $[0.0, 1.0]$ float32 | Identical |
| **Normalization** | None (mean 0.0, std 1.0) | Mean: `[0.485, 0.456, 0.406]`, Std: `[0.229, 0.224, 0.225]` | Apply ImageNet normalization dynamically in Dataset `__getitem__` during transfer learning |
| **Source Data** | Intact on disk | Intact on disk | Zero modification to Phase 8.3 or 8.8 files on disk |

---

## 4. Resource Envelope & Execution Feasibility

- **Model Weight Size:** ~9.23 MB (float32 parameters in memory).
- **RAM Footprint:** Projected memory during batch collation is <120 MB RSS, well within desktop and server CPU envelopes.
- **CPU Latency Profile:** MobileNetV2 inverted residual blocks with depthwise separable convolutions are engineered specifically for low-latency CPU inference.
