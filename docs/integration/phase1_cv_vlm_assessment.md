# FireSphere.ai — CV and VLM Model & Orchestration Assessment

**Document Identifier:** `FS-DOC-PHASE1-CV-VLM-ASSESSMENT-20261010`  
**Phase:** 1 — Workspace Audit and Integration Design  
**Local Workspace Root:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**Date:** 2026-10-10  
**Classification:** AI/ML Quality, VLM Readiness, and Safety Governance Audit  

---

## 1. Executive Summary

This assessment audits the Computer Vision (CV), Vision-Language Model (VLM), and orchestration capabilities in [`ai-service/`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service).

### Key Assessment Findings:
1. **Model Governance Gate:** The Computer Vision gate remains strictly **`NOT_APPROVED`** and statistical calibration remains **`NOT_CALIBRATED`**, enforced in [`model_approval_gate.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/model_approval_gate.py).
2. **Weak Empirical Model Quality:** On the untouched 522-image held-out test split, the reference baseline detector achieves an overall **mAP@50 = 0.0579** (Extinguisher AP@50 = 0.2236, Exit Sign AP@50 = 0.0079, Exit Door AP@50 = 0.0000, Obstacle AP@50 = 0.0000). The model is incapable of reliably identifying violations on its own and produces zero true positives on doors and obstacles.
3. **VLM Integration Status:** The Gemini VLM provider ([`gemini_provider.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py)) is **fully implemented in code and offline-test verified**, but remains **disabled by default (`GEMINI_ENABLED=false`)**. In accordance with Phase 1 safety rules, no live external API calls were initiated.
4. **Strict Human Governance Boundary:** The AI subsystem cannot approve or reject a Fire NOC under any circumstance. All findings are strictly advisory visual notes requiring physical verification by an authorized Fire Safety Officer.

---

## 2. Computer Vision Model Audit (Task F)

### 2.1 Checkpoint Inventory & Provenance Verification
A comprehensive SHA-256 cryptographic audit was conducted across all model weights artifacts in [`ai-service/artifacts/`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts):

| Phase / Checkpoint Identifier | Relative File Path | File Size | SHA-256 Cryptographic Hash | Architecture & Parameters | Training Dataset Split |
| :--- | :--- | :---: | :---: | :--- | :--- |
| **Phase 8.4 Reference Baseline** | `artifacts/phase8_4/checkpoints/best.pt` | 26,905,612 B | `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` | PyTorch YOLO Adapter ($S=16$ grid, 2.23M params, single-scale head, 3 anchors) | Phase 8.3 Train (3,377 imgs) |
| **Phase 8.4 Last Epoch** | `artifacts/phase8_4/checkpoints/last.pt` | 26,905,612 B | `c2a628280c99c58c56222725276f97308772f06d93c731472fa32145c1e938ca` | PyTorch YOLO Adapter (Epoch 10) | Phase 8.3 Train (3,377 imgs) |
| **Phase 8.5 Model Improvement** | `artifacts/phase8_5/checkpoints/best.pt` | 28,290,957 B | `54ea8d2a2a124e38a5354913e0818514ea534d0d56ddb087c1e96cb03c20e89a` | PyTorch Custom Backbone with FPN | Phase 8.3 Train |
| **Phase 8.8 Controlled Training** | `artifacts/phase8_8/checkpoints/best.pt` | 8,980,601 B | `b12ffccf532d819a1cbdd058bb4673f7270ff71533d9862003249fc5360df7b1` | Lightweight YOLO Detection Head | Expanded Door Dataset |
| **Phase 8.9 Transfer Learning** | `artifacts/phase8_9/checkpoints/best.pt` | 14,100,328 B | `abb5bab4a4eb3de7d2275c5a946238804f3f453cc1419a3906b888fe52d88d36` | MobileNetV3-Small + FPN/PANet feature pyramid | Multi-scale Transfer Split |
| **Phase 8.10 ImageNet Pretrained**| `artifacts/phase8_10/pretrained/mobilenet_v2-b0353104.pth` | 14,212,972 B | `b03531047ffacf1e2488318dcd2aba1126cde36e3bfe1aa5cb07700aeeee9889` | MobileNetV2 torchvision reference weights | ImageNet-1K reference |

### 2.2 Currently Configured vs Best-Supported Checkpoint
- **Configured Checkpoint:** The runtime service configuration ([`app/core/config.py:32`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/core/config.py#L32)) sets `AI_MODEL_NAME="firesphere-inspection-cv"` and `AI_MODEL_VERSION="0.3.0-phase3"`. In the absence of an explicit path override, [`ModelLoader`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/model_loader.py#L113) defaults to the Case C baseline adapter.
- **Best-Supported Checkpoint by Documentation:** The Phase 8.4 checkpoint (`artifacts/phase8_4/checkpoints/best.pt`) is established as the **Reference Baseline** across all governance documentation and test assertions ([`model_approval_gate.py:56`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/model_approval_gate.py#L56)).
- **Evaluation Evidence Summary:**
  - `FIRE_EXTINGUISHER`: Moderate recall (69.4% on validation split at $\tau=0.05$), but suffers from high false-positive rates on red objects, fire hose reels, and structural columns.
  - `EXIT_SIGN`: Low recall (22.7% at $\tau=0.05$), mAP@50 $< 0.01$.
  - `EXIT_DOOR`: Unsupported (0 true positives across 6 test ground-truth instances).
  - `OBSTACLE`: Unsupported (0 true positives).
- **Inference Pipeline & Device Selection:**
  - Input resolution: $640 \times 640 \times 3$ RGB (letterbox resized).
  - Normalization: $[0.0, 1.0]$ float32.
  - Device: Auto-discovery (CUDA GPU if available with clean CPU fallback).
  - Post-processing: Greedy NMS at IoU threshold $0.45$.

---

## 3. Vision-Language Model (VLM) Audit (Task G)

### 3.1 Architecture and Provider Capabilities
- **Adapter Module:** [`app/models/gemini_provider.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_provider.py).
- **Target Model:** `gemini-2.5-flash` configured via `GEMINI_MODEL`.
- **System Instruction:** Defined in [`app/models/gemini_prompts.py:system_instruction`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_prompts.py). Strictly instructs the model to act as a grounded visual observer, evaluate 10 statutory hazards, output normalized coordinates $[0, 1000]$, and never issue autonomous NOC approvals.
- **Structured Schema:** Validated using Pydantic schemas in [`app/models/gemini_schemas.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_schemas.py) (`GeminiRawResponse`, `GeminiObservation`, `parse_and_validate_bounding_box`).
- **Resilience:** Bounded exponential backoff retries ($N=3$ max retries) on transient rate limits (HTTP 429) and network timeouts.
- **Safety Invariant:** Credentials and API keys are masked; never logged or serialized into metadata.
- **Offline Mocking:** Fully test-covered via unittest mock injection (`test_gemini_vision_phase2.py`).
- **Runtime Verdict:** **Fully implemented in source code and unit-tested offline, but currently disabled (`GEMINI_ENABLED=false`) and unverified with live Google cloud credentials.**

---

## 4. Multi-Model Orchestration & Finding Reconciliation (Task H)

### 4.1 Orchestration Workflow
The orchestration lifecycle is governed by [`InspectionOrchestrator.java` / `inspection_orchestrator.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/services/inspection_orchestrator.py):
1. **Evidence Acquisition:** Retrieves inspection photographs by UUID from local storage or S3/MinIO ([`evidence_client.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/storage/evidence_client.py)).
2. **Quality & Preprocessing Gate:** [`ImageQualityValidator`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/cv/quality.py) checks minimum resolution ($64 \times 64$), maximum file size (20MB), and blurriness using Laplacian variance ($\tau = 30.0$). Degraded images are flagged or rejected with HTTP 422.
3. **Dual-Model Inference:**
   - CV model executes object detection pass for bounding boxes.
   - If enabled, VLM executes semantic visual reasoning pass.
4. **Result Reconciliation:** [`ResultReconciler`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/intelligence/reconciliation.py) compares CV and VLM detections using spatial IoU and semantic class matching:
   - *Spatial Agreement ($\text{IoU} \ge 0.40$):* Promotes confidence to `HIGH`; validation status set to `RECONCILED_AGREEMENT`.
   - *Semantic Conflict (Same box, conflicting hazard category):* Flags as `CONFLICTING_OBSERVATION`; preserves both raw model hypotheses and flags for mandatory human officer review.
   - *Independent Observations:* Preserved as single-model findings with `sourceModel` explicitly recorded.
5. **Deterministic Report Generation:** [`InspectionReportService`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/services/inspection_report_service.py) compiles findings into an advisory inspection report draft with operational risk scoring [0–100] and physical verification guidance checklists.

---

## 5. Proposed Shared Finding Contract (Status: PROPOSED)

To ensure complete traceability from model output to the officer dashboard and database persistence, the internal finding model must satisfy this canonical contract:

```json
{
  "findingId": "d1c2b3a4-5678-90ab-cdef-1234567890ab",
  "inspectionId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "evidenceId": "c3b88b22-8239-4d69-a1b9-87cfa90f1d3e",
  "sourceModel": "CV_YOLO_BASELINE",
  "sourceModelVersion": "0.8.0",
  "findingType": "FIRE_EXTINGUISHER_MISSING",
  "category": "FIRE_PROTECTION_EQUIPMENT",
  "description": "Fire extinguisher is missing from designated wall bracket",
  "confidence": 0.82,
  "confidenceLevel": "HIGH",
  "calibrationStatus": "NOT_CALIBRATED",
  "severity": "HIGH",
  "severityReason": "Safety perimeter requires immediate placement",
  "operationalRiskScore": 75,
  "explanation": "Extinguisher absent from bracket; empty mounting plate identified in image frame",
  "verificationGuidance": [
    "Physically inspect mounting bracket on wall",
    "Verify if extinguisher was temporarily removed for annual maintenance"
  ],
  "boundingBox": {
    "xMin": 0.150,
    "yMin": 0.200,
    "xMax": 0.450,
    "yMax": 0.600
  },
  "validationStatus": "VALIDATED",
  "conflictStatus": "NO_CONFLICT",
  "reviewStatus": "UNREVIEWED",
  "isTestData": false,
  "createdAt": "2026-10-10T11:45:00Z"
}
```

---

## 6. Safety & Human Governance Invariants

1. **Zero Autonomous NOC Authority:** The AI system is structurally prevented from approving or rejecting a Fire NOC application. Code inspection of [`InspectionAISummary`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/schemas/inspection.py#L24-L30) confirms an immutable regulatory warning notice is hardcoded into every response payload.
2. **Unsupported Classes Fail Closed:** Exit doors and obstacles cannot generate actionable safety violations due to zero validated true positive performance.
3. **No Hallucinated Bounding Boxes:** M4 will never invent bounding boxes when model confidence is below threshold or when inference fails.
4. **Traceability to Physical Evidence:** Every finding contains a non-null `evidenceId` linking directly to an inspected photograph stored in object storage.
