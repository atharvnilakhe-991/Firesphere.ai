# FireSphere.ai — Stage 7: VLM Fire-Safety Evaluation Report

**Document Identifier:** `DOC-INTEG-VLM-EVAL-STAGE7-v1.0`  
**Evaluation Date:** October 13, 2026  
**Workspace Root:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**AI Subsystem:** Vision-Language Model Provider (`ai-service`)  
**Evaluator Implementation:** `app/models/ollama_provider.py` (`OllamaQwenProvider`)  
**Local VLM Model:** `qwen2.5vl:3b` (via local Ollama daemon v0.40.3 at `http://127.0.0.1:11434`)  
**Evaluation Benchmark:** 14-Sample Stage 7 Controlled Exploratory Benchmark  
**Machine-Readable Summary:** `ai-service/artifacts/stage7_vlm_evaluation/metrics/vlm_evaluation_summary.json`  
**Raw Predictions Log:** `ai-service/artifacts/stage7_vlm_evaluation/metrics/vlm_predictions_raw.json`  

---

## Safety Invariants & Governance Summary

| Governance Dimension | Required Invariant | Observed System State | Compliance |
| :--- | :--- | :--- | :---: |
| **CV Production Gate** | `NOT_APPROVED` | `NOT_APPROVED` (Fail-Closed) | **PASS** |
| **CV Calibration State** | `NOT_CALIBRATED` | `NOT_CALIBRATED` (Unchanged) | **PASS** |
| **Production Authorization** | `false` | `false` (Strictly Disabled) | **PASS** |
| **Local Model Requirement** | `qwen2.5vl:3b` | `qwen2.5vl:3b` (Digest: `fb90415cde1e...`) | **PASS** |
| **Hosted AI Service Calls** | Zero calls | Zero calls (100% air-gapped local execution) | **PASS** |
| **Credentials & Secrets** | Untouched | Zero `.env` edits, zero token exposures | **PASS** |
| **CV Checkpoint Alteration** | Untouched | `model_a_baseline.pt` bit-for-bit unchanged | **PASS** |
| **Database & Storage Writes** | Zero writes | Zero writes to PostgreSQL or MinIO | **PASS** |
| **Sealed CV Holdout Split** | Untouched | `stage3_candidate/yolo/images/test/` preserved | **PASS** |

---

## 1. Executive Summary

Stage 7 evaluated the local Vision-Language Model (**Qwen2.5-VL 3B**, executed via local Ollama) on its empirical ability to recognize fire-safety equipment and detect physical egress hazards in building inspection imagery.

Stage 6 established the baseline performance of the Model A Computer Vision (CV) detector. While Model A achieved acceptable precision on fire extinguishers ($64.4\%$), it exhibited catastrophic blindness on critical egress infrastructure: **$0.0\%$ recall on exit doors (0/9 detected)**, **$0.0\%$ recall on obstacles (0/6 detected)**, and **$0.46\%$ recall on exit signs (1/218 detected)**, resulting in an overall $74.3\%$ hazard omission rate.

FireSphere.ai architectures CV and VLM as **complementary evidence providers**. Their outputs are not final verdicts; rather, they feed into a deterministic **Finding Reconciliation Engine** that maps observations into an **AI-Assisted Checklist**. An authorized human Fire Safety Officer must inspect and verify every item before rendering a Fire NOC (No Objection Certificate) decision.

### Key Empirical Findings:

1. **VLM Solves CV Blindness on Exit Doors and Exit Signs:**
   - **`EXIT_DOOR`:** Detected in **4 out of 4** ground-truth instances (**$100.0\%$ recall**, Precision: $66.7\%$, F1: $0.8000$). The VLM successfully identifies closed doorways, panic-hardware portals, and door frames that the CV detector completely missed.
   - **`EXIT_SIGN`:** Detected in **4 out of 4** ground-truth instances (**$100.0\%$ recall**, Precision: $50.0\%$, F1: $0.6667$), including low-lux illuminated stairwell conditions.
   - **`FIRE_EXTINGUISHER`:** High recognition (**$75.0\%$ recall**, Precision: $100.0\%$, F1: $0.8571$).

2. **Severe VLM Blindness on Obstacles and Physical Obstructions:**
   - **`OBSTACLE`:** **0 out of 3** ground-truth instances identified (**$0.0\%$ recall**, Precision: $0.0\%$).
   - **`BLOCKED_EXIT_PATH`:** **0 out of 1** ground-truth instances identified (**$0.0\%$ recall**).
   - **`BLOCKED_CORRIDOR`:** **0 out of 1** ground-truth instances identified (**$0.0\%$ recall**).
   - **Critical Failure Mode:** The 3B model lacks physical spatial reasoning to distinguish movable debris or blocking crates from standard architectural features. In Sample 04 (storage crate blocking an exit doorway), the model detected the door but hallucinated the crate as a decorative "brown square pattern on the lower half of the door." In Sample 12 (stacked caution-striped crates blocking a corridor), the model completely missed the blockage and hallucinated an exit sign.

3. **Spatial Localization Is Coarse and Uncalibrated:**
   - **Mean IoU:** **$0.1193$** across 16 ground-truth objects.
   - **IoU $\ge 0.50$ Rate:** **$0.0\%$ (0/16)**. Bounding boxes emitted by `qwen2.5vl:3b` reflect general saliency regions, not tight object boundaries.

4. **Hallucinations on Negative Controls:**
   - When presented with an empty wall (Sample 05) or an office room with no safety equipment (Sample 09), the model hallucinated exit signs (`box_2d: [50, 50, 150, 150]`), yielding 4 false positives across the evaluation suite.

5. **Failure to Abstain on Severely Degraded Evidence:**
   - When presented with a blurred scene (Sample 07) or an indistinct faint blob (Sample 14), the model failed to trigger `isInconclusive: true` or report uncertainty, instead hallucinating positive exit sign detections.

6. **Structured Output & Latency:**
   - **Schema Conformance:** **$100.0\%$ (14/14)** conforming strictly to `GeminiRawResponse` JSON.
   - **Inference Latency:** Mean of **$2,224.7\text{ ms}$** (~2.2s per image), range $1,805.6\text{ ms} - 4,513.8\text{ ms}$.

---

## 2. Model & Privacy Restrictions Audit

The evaluation strictly complied with all project privacy and governance constraints:

- **Local Inference:** 100% of VLM inferences executed via the local Ollama daemon (`v0.40.3`) hosting `qwen2.5vl:3b` at `http://127.0.0.1:11434`.
- **Zero Hosted AI:** No outbound requests to OpenAI, Google Vertex/Gemini hosted APIs, Anthropic, or external inference providers.
- **Data Protection:** No inspection imagery, prompts, or findings were transmitted over external networks.
- **Credential Hygiene:** No environment variables or credentials in `.env` were read, modified, or exposed.
- **Safety Gate Invariants:**
  - `model_approval_gate` status: `NOT_APPROVED`
  - Calibration state: `NOT_CALIBRATED`
  - Production inference authorization: `false`
- **Zero Database / Object Storage Side Effects:** Zero writes were committed to PostgreSQL or MinIO.

---

## 3. Dataset Selection, Provenance, & Scope

### 3.1 Why the Stage 6 Sealed CV Holdout Split Was Not Used
The sealed Stage 6 candidate test split (`datasets/fire_safety/prepared/stage3_candidate/yolo/images/test/`) consists of 453 images labeled exclusively with 4 bounding-box equipment classes (`fire_extinguisher`, `fire_alarm_call_point`, `exit_sign`, `fire_door`). It lacks:
1. Obstacle, obstruction, or egress hazard ground truth.
2. Verified visual degradation, low-lux, or blur stratification.
3. Multimodal scene descriptions or negative control annotations.

Furthermore, under project governance, the Stage 6 test split is **sealed** for independent CV generalization benchmarking. Evaluating the VLM on that split would compromise separation and provide no ground truth for the 4 hazard categories required in Stage 7.

### 3.2 Evaluation Benchmark Provenance (Exploratory Controlled Suite)
Because no large-scale, expert-annotated photographic fire-safety hazard dataset exists in the workspace, Stage 7 utilized a dedicated **14-sample controlled benchmark**:
- **Directory:** [`ai-service/artifacts/stage7_vlm_evaluation/images/`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage7_vlm_evaluation/images/)
- **Manifest:** [`ai-service/artifacts/stage7_vlm_evaluation/stage7_ground_truth_manifest.json`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage7_vlm_evaluation/stage7_ground_truth_manifest.json)
- **Provenance & Composition:**
  - **Samples 01–10:** Reused the verified geometric test cases established during Phase 14 (`sample_01` to `sample_10`).
  - **Sample 11:** `sample_11_near_exit_unblocked.png` — Clear exit door with a lateral waste bin $>1.5\text{m}$ away (testing non-blocking obstacle discernment).
  - **Sample 12:** `sample_12_blocked_corridor.png` — Egress corridor perspective completely blocked by stacked cargo crates.
  - **Sample 13:** `sample_13_poor_lighting_exit.png` — Low-lux darkened stairwell with illuminated emergency exit sign.
  - **Sample 14:** `sample_14_unidentifiable_shape.png` — Faint, blurry unidentifiable shape on wall (testing model abstention).
- **Dataset Limitation:** This benchmark is **exploratory and synthetic/geometric**. It measures baseline visual-reasoning capabilities under controlled parameters. It is **not** a replacement for a field-collected photographic dataset. Reliable conclusions regarding production field deployment cannot be drawn from this exploratory sample alone.

### 3.3 Proposal for a Separate Real-World Photographic Evaluation Dataset
To certify VLM and CV models for municipal inspection pilots, a separate field-collected dataset must be compiled:
1. **Target Volume:** 500–1,000 high-resolution photographic inspection images sourced from real commercial, industrial, educational, and residential buildings.
2. **Annotation Protocol:** Dual-expert consensus annotations by certified Fire Safety Officers, recording:
   - Equipment classes (`FIRE_EXTINGUISHER`, `EXIT_SIGN`, `EXIT_DOOR`, `HOSE_REEL`, `MANUAL_CALL_POINT`).
   - Obstacle semantics (`OBSTACLE`, `BLOCKED_EXIT_PATH`, `BLOCKED_CORRIDOR`, `STORAGE_WITHIN_CLEARANCE_ZONE`).
   - Ambiguity & image quality ratings (`GOOD`, `ACCEPTABLE`, `POOR`, `UNUSABLE`).
3. **Environmental Stratification:** Daylight, emergency illumination, low-lux battery backup, lens distortion, varied viewing angles ($0^\circ-60^\circ$), and genuine transient clutter.

---

## 4. Quantitative Evaluation Across Required Categories

The model was evaluated against ground truth across all 8 required categories:

| Category | Ground Truth Support | True Positives (TP) | False Positives (FP) | False Negatives (FN) | Precision | Recall | F1-Score |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`FIRE_EXTINGUISHER`** | 4 | 3 | 0 | 1 | **1.0000** | **0.7500** | **0.8571** |
| **`EXIT_SIGN`** | 4 | 4 | 4 | 0 | **0.5000** | **1.0000** | **0.6667** |
| **`EXIT_DOOR`** | 4 | 4 | 2 | 0 | **0.6667** | **1.0000** | **0.8000** |
| **`OBSTACLE`** | 3 | 0 | 0 | 3 | **0.0000** | **0.0000** | **0.0000** |
| **`BLOCKED_EXIT_PATH`** | 1 | 0 | 0 | 1 | **0.0000** | **0.0000** | **0.0000** |
| **`BLOCKED_CORRIDOR`** | 1 | 0 | 0 | 1 | **0.0000** | **0.0000** | **0.0000** |
| **`OTHER_VISIBLE_FIRE_SAFETY_HAZARD`** | 2 | 0 | 0 | 2 | **0.0000** | **0.0000** | **0.0000** |
| **`UNCERTAIN_OR_UNIDENTIFIABLE`** | 2 | 0 | 0 | 2 | **0.0000** | **0.0000** | **0.0000** |
| **Macro Average** | **21** | **11** | **6** | **10** | **0.2708** | **0.3438** | **0.2905** |

### Category Breakdown & Analysis:

1. **`FIRE_EXTINGUISHER` (F1: 0.8571):**
   - Correctly identified in clean (Sample 01), partially obscured (Sample 06), and multi-object (Sample 08) scenes with zero false alarms.
   - Missed only in Sample 07 due to heavy Gaussian blur.

2. **`EXIT_DOOR` (F1: 0.8000):**
   - Correctly identified in all 4 test doors (Samples 03, 04, 08, 11).
   - This provides **100% recall complementarity** where the Stage 6 CV baseline failed completely ($0.0\%$ recall).
   - Two false alarms occurred: Sample 09 (standard interior office door) and Sample 10 (gray electrical cabinet panel misclassified as a door).

3. **`EXIT_SIGN` (F1: 0.6667):**
   - 100% recall on genuine signs, including multi-object scenes (Sample 08) and pitch-black emergency stairwells (Sample 13).
   - Suffers from low precision ($50.0\%$) due to 4 hallucinations on empty walls and corridor perspectives.

4. **`OBSTACLE`, `BLOCKED_EXIT_PATH`, `BLOCKED_CORRIDOR` (Recall: 0.0%):**
   - The 3B model failed to identify obstacles in all test scenarios.
   - It treated foreground obstacles as door textures (Sample 04), missed floor obstacles entirely (Sample 06), and overlooked massive corridor blockages (Sample 12).
   - **Conclusion:** The local 3B VLM cannot be trusted to detect physical egress blockages autonomously.

5. **`UNCERTAIN_OR_UNIDENTIFIABLE` (Recall: 0.0%):**
   - The model failed to abstain in both degraded test cases (Sample 07 blur and Sample 14 indistinct blob).
   - In both cases, the model forced a positive detection instead of populating `isInconclusive: true` or requesting further inspection.

---

## 5. Spatial Localization Quality

Evaluating normalized bounding boxes across all 16 ground-truth items:

| Metric | Measured Value | Standard Required for Tight Bounding Box | Evaluation |
| :--- | :---: | :---: | :---: |
| **Mean Intersection-over-Union (IoU)** | **0.1193** | $\ge 0.50$ | **FAILED** |
| **Objects with IoU $\ge 0.50$** | **0 / 16 (0.0%)** | $\ge 80.0\%$ | **FAILED** |
| **Objects with IoU $\ge 0.25$** | **4 / 16 (25.0%)** | $\ge 90.0\%$ | **POOR** |
| **Objects with IoU $= 0.0$** | **8 / 16 (50.0%)** | $0.0\%$ | **HIGH OMISSION** |

### Root Cause of Localization Degradation:
1. **Coordinate Quantization:** `qwen2.5vl:3b` tokenizes bounding boxes as textual integers `[0 - 1000]`.
2. **Coarse Attention Field:** While the model correctly attends to the image quadrant containing the object, it fails to outline exact mechanical boundaries.
3. **System Rule:** **A VLM bounding box must only be presented as an advisory region of interest, never as a precision spatial detection.**

---

## 6. Structured Output Validity, Latency, & Runtime

- **Structured Output Validity:** **14 / 14 ($100.0\%$)**  
  All responses were successfully parsed and validated against the `GeminiRawResponse` Pydantic schema via Ollama's structured output format enforcement.
- **Runtime Latency Distribution:**
  - Mean Latency: **$2,224.68\text{ ms}$** (~2.2s)
  - Median Latency: **$1,926.84\text{ ms}$**
  - Minimum Latency: **$1,805.59\text{ ms}$** (Single object)
  - Maximum Latency: **$4,513.83\text{ ms}$** (Multi-object scene, Sample 08)
  - Total Suite Execution: **$31.6\text{ seconds}$**
- **Runtime Failures:** 0 exceptions, 0 HTTP timeouts, 0 process crashes.

---

## 7. CV–VLM Finding Reconciliation Audit

The Stage 7 evaluation tested the deterministic **`ResultReconciler`** across 6 cross-provider inspection scenarios, verifying how CV and VLM evidence are reconciled:

| Sample | CV Detection (Model A) | VLM Observation (Qwen-VL) | Reconciled Status | Findings Count | Requires Human Officer Review |
| :--- | :--- | :--- | :---: | :---: | :---: |
| **`sample_01`** (Extinguisher) | `FIRE_EXTINGUISHER` (Conf: 0.92) | `FIRE_EXTINGUISHER` (Conf: 0.90) | `DISCREPANCY` (Spatial box difference) | 2 | **True (Mandatory)** |
| **`sample_03`** (Doorway) | None (CV Missed) | `EXIT_DOOR` (Conf: 0.95) | `CONFLICT` (Category Mismatch / CV Absence) | 1 | **True (Mandatory)** |
| **`sample_04`** (Blocked Door) | None (CV Missed) | `EXIT_DOOR` (Conf: 0.75) | `CONFLICT` (Category Mismatch / CV Absence) | 1 | **True (Mandatory)** |
| **`sample_08`** (Multi-Object) | `FIRE_EXTINGUISHER` | `EXIT_SIGN`, `EXIT_DOOR`, `FIRE_EXTINGUISHER` | `DISCREPANCY` (Multi-Observation Union) | 4 | **True (Mandatory)** |
| **`sample_11`** (Near-Exit) | None (CV Missed) | `EXIT_DOOR` (Conf: 0.75) | `DISCREPANCY` (Single-Provider Proposal) | 2 | **True (Mandatory)** |
| **`sample_12`** (Blocked Corridor)| None (CV Missed) | `EXIT_SIGN` (Hallucinated) | `DISCREPANCY` (Single-Provider Proposal) | 2 | **True (Mandatory)** |

### Key Reconciliation Findings:
1. **Preservation of Discrepancies:** When CV reports no detection but VLM detects an exit door (Samples 03, 04), the engine does **not** discard the VLM finding. It preserves it as an advisory item requiring officer review.
2. **No Autonomous Overwrite:** Neither model can suppress the other's findings. Both models' provenance records (`providerName`, `modelId`, `modelVersion`, uncalibrated `rawConfidence`) are permanently preserved on each `Finding` record.
3. **Invariable Human Review Gate:** In 100% of cases, `requiresHumanReview = True` and `complianceStatus = REQUIRES_OFFICER_VERIFICATION`.

---

## 8. Human Review Requirements & Safety Governance

Under FireSphere.ai regulatory standards:
1. **Advisory Role Only:** VLM observations are advisory hypotheses. They propose checklist candidates for inspection verification.
2. **Prohibited Autonomous Actions:**
   - The VLM cannot automatically mark a checklist item compliant.
   - The VLM cannot autonomously issue, approve, or reject a Fire NOC.
   - Absence of an observation must **never** be interpreted as proof of building safety or code compliance.
   - The model must never invent inspection evidence or hallucinate compliance stamps.
3. **Officer Checklist Integration:**
   All reconciled findings map directly to the actionable on-site inspection checklist defined in [`app/intelligence/verification.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/intelligence/verification.py) (`OfficerVerificationGuidance`), directing the inspecting officer to measure clearances, verify emergency power, and inspect mechanical hardware on site.

---

## 9. Failure Modes & Recommended Integration Changes

### Identified Failure Modes:
1. **Obstacle Blindness:** Inability to classify floor storage, boxes, or barriers as hazards.
2. **Hallucination on Negative Controls:** Emitting exit signs on empty walls.
3. **Abstention Failure:** Generating positive detections on severely blurred or unidentifiable scenes instead of flagging uncertainty.
4. **Coarse Bounding Boxes:** Quantized bounding boxes with mean IoU of 0.12.

### Recommended System Improvements:
1. **Prompt Grounding Enhancements:**
   - Update `app/models/ollama_prompts.py` to incorporate negative few-shot examples ("When an empty wall has no fixtures, return zero observations").
   - Explicitly instruct the model: "If image quality is severely blurred, low-contrast, or unidentifiable, set `isInconclusive: true` and return no observations."
2. **Egress Obstruction Few-Shot Context:**
   - Provide concrete examples defining physical obstacles: "Any box, crate, pallet, cart, or object located on the floor in front of a door or within an egress hallway must be classified as an `OBSTACLE` with hazard `BLOCKED_EXIT_PATH`."
3. **Photographic Dataset Compilation:**
   - Execute the proposed 500+ image photographic inspection dataset collection before considering model fine-tuning or pilot field trials.

---

## 10. Gate Invariants Confirmation

The following safety and governance invariants remain strictly enforced and unchanged:

- **CV Model Approval Gate:** `NOT_APPROVED`
- **Confidence Calibration State:** `NOT_CALIBRATED`
- **Production Inference Authorization:** `false`
- **Stage Status:** **Stage 7 VLM Fire-Safety Evaluation is Complete.**
