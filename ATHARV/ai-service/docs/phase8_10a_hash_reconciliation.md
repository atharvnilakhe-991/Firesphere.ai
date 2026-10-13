# FireSphere.ai — Phase 8.10A: Official MobileNetV2 Checkpoint Hash Reconciliation

**Phase:** Phase 8.10A (Official MobileNetV2 Checkpoint Hash Reconciliation)  
**Role:** Senior ML Infrastructure & Model-Provenance Auditor  
**Date:** 2026-10-09  
**Reconciliation Timestamp:** `2026-10-09T17:35:00+05:30`  
**Final Gate Decision:** `CHECKPOINT_HASH_REQUIRES_MANUAL_VERIFICATION`  
**Production Status:** `NOT_APPROVED`  
**Historical Phase 8.10 Gate Preserved:** `CHECKPOINT_INTEGRITY_FAILED`

---

## 1. Executive Summary & Objective

During Phase 8.10, the official PyTorch MobileNetV2 ImageNet-1K pretrained weights file (`mobilenet_v2-b0353104.pth`) was downloaded following explicit user authorization. A cryptographic discrepancy was discovered between the locally calculated SHA-256 (`b03531047ffacf1e2488318dcd2aba1126cde36e3bfe1aa5cb07700aeeee9889`) and the previously recorded expected SHA-256 string (`b0353104d49a37c02b37ba4ef5dd3f3d1b827756f7b9492e73703eb402773cbb`). Consequently, Phase 8.10 ended with the strict security gate `CHECKPOINT_INTEGRITY_FAILED`.

Phase 8.10A was initiated with a narrow, rigorous audit objective:
> **Objective:** Independently verify official PyTorch/Torchvision metadata, reconcile the hash values, re-evaluate local checkpoint integrity and structural compatibility, verify historical baseline immutability, and enact the authoritative governance gate under strict Truth-in-AI principles.

---

## 2. Independent Verification of Official Checkpoint Metadata

An independent investigation into authoritative PyTorch/Torchvision public metadata was conducted:

1. **Official Checkpoint Filename:** `mobilenet_v2-b0353104.pth`
2. **Official Source URL:** `https://download.pytorch.org/models/mobilenet_v2-b0353104.pth`
3. **Official ImageNet-1K Provenance:** In PyTorch Torchvision (`torchvision/models/mobilenetv2.py`), the model entrypoint `MobileNet_V2_Weights.IMAGENET1K_V1` directly points to this URL (`transforms=ImageClassification(crop_size=224)`, `acc@1=71.878%`, `acc@5=90.286%`).
4. **Official Hash Architecture in PyTorch:**
   - In PyTorch Hub (`torch/hub.py`), the URL parsing and validation mechanism is defined as:
     ```python
     HASH_REGEX = re.compile(r"-([a-f0-9]*)\.")
     ```
   - In `torch.hub.download_url_to_file`, verification is performed by matching the first $N$ characters of the computed digest against the filename prefix:
     ```python
     if digest[: len(hash_prefix)] != hash_prefix:
         raise RuntimeError(f'invalid hash value (expected "{hash_prefix}", got "{digest}")')
     ```
5. **Officially Published Hash:** PyTorch officially publishes the **8-character prefix `b0353104`** embedded in the filename.
6. **Full 64-character SHA-256 Availability:**
   - **Official PyTorch sources do NOT publish a full 64-character SHA-256 digest.**
   - Third-party distributions (such as ImageAI, SourceForge model indexes, and Kaggle package manifests) that archive this exact file document the full SHA-256 as `b03531047ffacf1e2488318dcd2aba1126cde36e3bfe1aa5cb07700aeeee9889`.
   - However, under Truth-in-AI governance rules, third-party mirrors cannot be substituted as authoritative official PyTorch release metadata.

---

## 3. Local File Integrity Recomputation

Direct computation on the existing local file `ai-service/artifacts/phase8_10/pretrained/mobilenet_v2-b0353104.pth` yielded:
- **File Existence:** `True`
- **File Size:** `14,212,972` bytes (13.55 MB)
- **File Modification Timestamp:** `2026-10-09T00:39:56.221433` (identical to initial download)
- **Computed SHA-256:** `b03531047ffacf1e2488318dcd2aba1126cde36e3bfe1aa5cb07700aeeee9889`
- **Integrity Result:** The local file is completely unaltered and matches the previously reported local digest bit-for-bit.

---

## 4. Reconciliation Table of Hash Values

| Key | Identifier | Value | Source | Authoritative? | Matches Local? | Conclusion |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **A** | **Current Local SHA-256** | `b03531047ffacf1e2488318dcd2aba1126cde36e3bfe1aa5cb07700aeeee9889` | Computed from local file bytes via `hashlib.sha256` | Local Ground Truth | **YES (Identity)** | File is physically present, intact, and uncorrupted on disk. |
| **B** | **Previously Reported Local SHA-256** | `b03531047ffacf1e2488318dcd2aba1126cde36e3bfe1aa5cb07700aeeee9889` | Phase 8.10 execution output & manifest records | Internal Audit Record | **YES (Exact Match)** | File has not experienced modification or corruption since Phase 8.10. |
| **C** | **Previously Recorded Expected SHA-256** | `b0353104d49a37c02b37ba4ef5dd3f3d1b827756f7b9492e73703eb402773cbb` | Phase 8.10 initial manifest template | **NO** (Unverified template placeholder) | **NO (Mismatch)** | Generated as a placeholder before download authorization; factually incorrect and superseded. |
| **D** | **Officially Published SHA-256** | Prefix `b0353104` published; **Full 64-char SHA-256 NOT published** | PyTorch / Torchvision official repository (`torchvision/models/mobilenetv2.py`) | **YES** (Authoritative) | **PREFIX MATCH ONLY** | PyTorch authenticates models using 8-char prefixes; full 64-character digest is not published by PyTorch. |

---

## 5. Checkpoint Structural & Numerical Validation

The checkpoint was deserialized safely using `torch.load(..., map_location="cpu", weights_only=True)`:
- **Container Format:** `collections.OrderedDict`
- **Total Keys:** 314
- **Backbone Feature Keys:** 312 (`features.0.0.weight` through `features.18.1.num_batches_tracked`)
- **Classifier Keys:** 2 (`classifier.1.weight`, `classifier.1.bias`)
- **NaN / Inf Scan:** 0 NaNs, 0 Infs across all floating-point parameters
- **Loading into `PretrainedInspectionDetector`:**
  - Loaded tensors: **312 / 312**
  - Missing keys: **0**
  - Unexpected keys: **0**
- **Structural Status:** **PASS**

---

## 6. Historical Checkpoint & Dataset Immutability

| Artifact Entity | Path / Location | Expected Value | Measured Value | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Phase 8.4 Baseline Checkpoint** | `artifacts/phase8_4/checkpoints/best.pt` | `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` | `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` | **VERIFIED UNCHANGED** |
| **Phase 8.5 Regressed Checkpoint** | `artifacts/phase8_5/checkpoints/best.pt` | `54ea8d2a2a124e38a5354913e0818514ea534d0d56ddb087c1e96cb03c20e89a` | `54ea8d2a2a124e38a5354913e0818514ea534d0d56ddb087c1e96cb03c20e89a` | **VERIFIED UNCHANGED** |
| **Phase 8.8 Regressed Checkpoint** | `artifacts/phase8_8/checkpoints/best.pt` | `b12ffccf532d819a1cbdd058bb4673f7270ff71533d9862003249fc5360df7b1` | `b12ffccf532d819a1cbdd058bb4673f7270ff71533d9862003249fc5360df7b1` | **VERIFIED UNCHANGED** |
| **Phase 8.3 Prepared Dataset** | `datasets/fire_safety/prepared/phase8_3/` | 4,887 images (522 test) | 4,887 images (522 test) | **VERIFIED UNCHANGED** |
| **Phase 8.8 Prepared Dataset** | `datasets/fire_safety/prepared/phase8_8/` | 4,841 images (486 test) | 4,841 images (486 test) | **VERIFIED UNCHANGED** |

Zero historical checkpoints or datasets were modified or regenerated.

---

## 7. Decision Gate & Governance Ruling

Under the strict decision rules defined in Phase 8.10A:
- **Rule A (`PRETRAINED_WEIGHT_READY`):** Requires an exact match against an independently verified authoritative official full SHA-256. Because official PyTorch only publishes an 8-character prefix and does not publish a full 64-character SHA-256 digest, Rule A cannot be triggered without fabricating metadata.
- **Rule B (`CHECKPOINT_HASH_REQUIRES_MANUAL_VERIFICATION`):** Triggered when an authoritative full official hash cannot be established from the primary publisher.
- **Rule C (`CHECKPOINT_INTEGRITY_FAILED`):** Preserved for Phase 8.10 historical records.

```
================================================================================
PHASE 8.10A GATE DECISION: CHECKPOINT_HASH_REQUIRES_MANUAL_VERIFICATION
PRODUCTION STATUS: NOT_APPROVED
================================================================================
```

### Truth-in-AI Governance Declaration:
1. No model training or fine-tuning occurred.
2. Phase 8.9 transfer-learning training has NOT started.
3. Phase 9 has NOT started.
4. No model is registered as production. The Phase 8.4 baseline control remains authoritative.
5. All AI findings remain advisory (`REQUIRES_OFFICER_VERIFICATION`).
