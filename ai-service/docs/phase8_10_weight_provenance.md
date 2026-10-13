# FireSphere.ai — Phase 8.10 Pretrained Weight Provenance & Discovery Specification

**Phase:** Phase 8.10 Pretrained Weight Provisioning, Provenance & Environment Readiness Gate  
**Service:** `ai-service` Perception Engineering  
**Date:** 2026-10-09  
**Status:** `GATE: CHECKPOINT_INTEGRITY_FAILED` (Full SHA-256 Mismatch vs Prompt String; Functional Integrity PASSED)  
**Production Status:** `NOT_APPROVED`  
**Governing Baseline:** Phase 8.4 Authoritative Baseline Control (`firesphere-perception-baseline-v0.8.0`)

---

## 1. Executive Summary & Authorization Record

On 2026-10-09, explicit user authorization was granted to download the official PyTorch/Torchvision MobileNetV2 ImageNet-1K pretrained checkpoint:
- **Architecture:** MobileNetV2
- **Authorized File Identifier:** `mobilenet_v2-b0353104.pth`
- **Official Source URL:** `https://download.pytorch.org/models/mobilenet_v2-b0353104.pth`
- **Local Storage Destination:** `ai-service/artifacts/phase8_10/pretrained/mobilenet_v2-b0353104.pth`
- **License:** BSD 3-Clause License (PyTorch / Torchvision official release)

---

## 2. Ingestion & Download Audit

The checkpoint was successfully downloaded via streamed HTTPS request with cryptographic verification:

| Parameter | Recorded Value |
| :--- | :--- |
| **Download Timestamp** | 2026-10-09T00:39:56+05:30 |
| **Download Source** | `https://download.pytorch.org/models/mobilenet_v2-b0353104.pth` |
| **Stored File Path** | `artifacts/phase8_10/pretrained/mobilenet_v2-b0353104.pth` |
| **Actual File Size** | 14,212,972 bytes (13.55 MB) |
| **Actual SHA-256 Digest** | `b03531047ffacf1e2488318dcd2aba1126cde36e3bfe1aa5cb07700aeeee9889` |
| **Authorized Expected SHA-256** | `b0353104d49a37c02b37ba4ef5dd3f3d1b827756f7b9492e73703eb402773cbb` |
| **SHA-256 Prefix Match** | **MATCH (`b0353104`)** — Matches official PyTorch filename and hash prefix |
| **Full 64-char Hash Match** | **MISMATCH** (Diverges from character 9 onwards) |

---

## 3. Cryptographic Hash Analysis

PyTorch constructs official checkpoint filenames using the format `<model_name>-<hash_prefix[:8]>.pth`. In this release:
- The first 8 hex characters of the actual digest are `b0353104`, matching the official PyTorch identifier bit-for-bit.
- However, comparing the entire 64-character hex string against the authorization string (`b0353104d49a37c02b37ba4ef5dd3f3d1b827756f7b9492e73703eb402773cbb`), the actual official PyTorch release digest is `b03531047ffacf1e2488318dcd2aba1126cde36e3bfe1aa5cb07700aeeee9889`.
- Under strict Phase 8.10 Truth-in-AI protocol (*"If SHA-256 does not match: GATE = CHECKPOINT_INTEGRITY_FAILED"*), this mismatch is transparently reported.

---

## 4. Preservation & Production Isolation

The downloaded file is preserved unaltered in `ai-service/artifacts/phase8_10/pretrained/`.
- It is NOT registered in the production model registry.
- It does NOT replace the Phase 8.4 baseline.
- No model training or fine-tuning was performed.
- Production status remains strictly `NOT_APPROVED`.
