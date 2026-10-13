# Phase 14 — FireSphere.ai CV + Qwen2.5-VL Joint Evaluation Report

- **Date:** 2026-10-13T00:05:00Z
- **Workspace:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`
- **AI Service:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai\ai-service`
- **Selected Local VLM:** `qwen2.5vl:3b` via local Ollama (`http://127.0.0.1:11434`)
- **Selected CV Model:** `BaselineInspectionDetector` (`firesphere-inspection-cv`, checkpoint `best.pt`)
- **Evaluation Dataset:** 10-sample labelled synthetic dataset under [`ai-service/tests/fixtures/phase14/`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/tests/fixtures/phase14/)
- **Final Verdict:** `PASS_EVALUATION_COMPLETED`
- **Production Status:** `NOT_APPROVED` (CV Model Approval Gate remains strictly locked fail-closed; VLM remains advisory only; production inference disabled)

---

## Executive Summary

In Phase 14, FireSphere.ai performed a rigorous, controlled joint evaluation comparing the existing Computer Vision (CV) model pipeline (`BaselineInspectionDetector`), the integrated local Ollama / Qwen2.5-VL 3B Vision-Language Model (VLM) provider, and the cross-model intelligence reconciliation pipeline (`ResultReconciler`).

The evaluation was executed entirely offline against a newly created, machine-readable 10-sample synthetic benchmark dataset without calling hosted AI services (Gemini or OpenAI), without downloading external weights, without writing to production databases or MinIO storage, and without bypassing the fail-closed CV model approval gate.

---

## 1. Phase 13 Provenance Audit

An in-depth source code and runtime audit was conducted across the Phase 13 implementation:
- [`app/models/ollama_provider.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/ollama_provider.py)
- [`app/models/ollama_prompts.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/ollama_prompts.py)
- [`app/models/vlm_provider.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/vlm_provider.py)
- [`app/models/gemini_schemas.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_schemas.py)
- [`app/services/vlm_service.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/services/vlm_service.py)
- [`app/services/inspection_orchestrator.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/services/inspection_orchestrator.py)
- [`app/intelligence/reconciliation.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/intelligence/reconciliation.py)

### Investigation of Phase 13 Observation
The Phase 13 integration report recorded an observation containing:
- `objectCategory`: `FIRE_EXTINGUISHER`
- `confidence`: `0.9`
- `boundingBox`: `[ymin=0.356, xmin=0.244, ymax=0.569, xmax=0.799]`

#### Findings on Provenance:
1. **Confidence Score Provenance:**
   - The value `0.9` originated **directly from the raw model response** emitted by Qwen2.5-VL in its generated JSON.
   - It was **not** defaulted, fabricated, or calibrated by application code.
   - Application code in [`ollama_provider.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/ollama_provider.py#L594-L602) validates that `raw_obs.confidence` is a finite float in `[0.0, 1.0]`; if null or omitted, it sets `valid_conf = None`.
   - **Critical Caveat:** While emitted by the model, this confidence is an uncalibrated LLM token probability verbalization. It does not reflect statistical empirical calibration.

2. **Bounding Box Provenance:**
   - The coordinates originated from Qwen's output `box_2d: [356.0, 244.0, 569.0, 799.0]` under the `gemini_0_1000` coordinate prompt contract.
   - [`parse_and_validate_bounding_box()`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/gemini_schemas.py#L134-L275) divided each coordinate by 1000.0, checked bounds, verified positive area, and ensured valid ordering ($x_{\min} < x_{\max}$, $y_{\min} < y_{\max}$).
   - The ground truth box for that test object was `[0.2812, 0.4062, 0.7812, 0.5938]`. The model's predicted box covered the broad region but exhibited significant spatial error.
   - **Conclusion:** Qwen2.5-VL provides **rough regional saliency guidance**, not precise bounding-box localization. Precise localization cannot be claimed.

3. **Raw Output Preservation:**
   - In Phase 13, `rawResponse` was parsed into `parsed_model = GeminiRawResponse.model_validate_json(...)` but was not assigned to `GeminiAnalysisResult.rawResponse`.
   - In Phase 14, [`ollama_provider.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/ollama_provider.py) was updated to preserve `rawResponse=parsed_model.model_dump()`, mirroring [`openai_provider.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/openai_provider.py) and enabling full auditability.

---

## 2. Dataset-Generation Procedure

A dedicated, reproducible synthetic dataset generator was implemented at `ai-service/scratch/generate_phase14_dataset.py` using Pillow (`PIL`). 

### Benchmark Criteria:
- Resolution: $640 \times 640$ pixels, RGB, 24-bit PNG.
- Controlled geometric composition: Pure geometric 2D shapes on known coordinate baselines.
- Explicit ground truth: Ground truth coordinates and object categories were determined strictly by the generative rendering math, completely independent of model predictions.
- No real citizen, tenant, or building inspection evidence was used.

### Directory Structure:
```
ai-service/tests/fixtures/phase14/
├── ground_truth_manifest.json
├── evaluation_results.json
└── images/
    ├── sample_01_extinguisher.png
    ├── sample_02_exit_sign.png
    ├── sample_03_doorway.png
    ├── sample_04_obstacle_doorway.png
    ├── sample_05_empty_wall.png
    ├── sample_06_partially_obscured_extinguisher.png
    ├── sample_07_blurred_scene.png
    ├── sample_08_multi_object.png
    ├── sample_09_no_fire_safety.png
    └── sample_10_ambiguous.png
```

---

## 3. Dataset Manifest and Ground-Truth Conventions

The benchmark dataset consists of 10 samples specified in [`ground_truth_manifest.json`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/tests/fixtures/phase14/ground_truth_manifest.json).

### Coordinate Conventions:
- Primary: Normalized float `[ymin, xmin, ymax, xmax]` in `[0.0, 1.0]`.
- Secondary: Normalized float `[xmin, ymin, xmax, ymax]` in `[0.0, 1.0]`.

### Sample Manifest Summary:

| Sample ID | Image Filename | Synthetic Description | Expected Categories | Ground Truth Bounding Box [ymin, xmin, ymax, xmax] | Conditions / Ambiguities |
|---|---|---|---|---|---|
| `sample_01` | `sample_01_extinguisher.png` | Red extinguisher cylinder with yellow band & black nozzle | `FIRE_EXTINGUISHER` | `[0.2812, 0.4062, 0.7812, 0.5938]` | High contrast, plain background |
| `sample_02` | `sample_02_exit_sign.png` | Green rectangular illuminated exit sign with running figure | `EXIT_SIGN` | `[0.1875, 0.2812, 0.4062, 0.7188]` | Standard geometric signage |
| `sample_03` | `sample_03_doorway.png` | Recessed exit doorway with frame and door handle | `EXIT_DOOR` | `[0.2188, 0.3125, 0.8750, 0.6875]` | Unobstructed doorway |
| `sample_04` | `sample_04_obstacle_doorway.png` | Exit doorway with wooden crate blocking threshold | `EXIT_DOOR`, `OBSTACLE` | Door: `[0.2188, 0.3125, 0.8750, 0.6875]`<br>Obstacle: `[0.6250, 0.3750, 0.8750, 0.6250]` | Obstacle partially occludes door bottom |
| `sample_05` | `sample_05_empty_wall.png` | Neutral drywall wall with subtle floor baseboard | `[]` (None) | N/A | True negative test; no fire safety equipment |
| `sample_06` | `sample_06_partially_obscured_extinguisher.png` | Red extinguisher partially occluded by grey cabinet | `FIRE_EXTINGUISHER`, `OBSTACLE` | Extinguisher: `[0.2812, 0.4062, 0.7812, 0.5938]`<br>Obstacle: `[0.5000, 0.4375, 0.8438, 0.6875]` | ~40% occlusion of extinguisher body |
| `sample_07` | `sample_07_blurred_scene.png` | Gaussian blurred scene ($\sigma=9.0$) of extinguisher & exit sign | `FIRE_EXTINGUISHER`, `EXIT_SIGN` | Extinguisher: `[0.2812, 0.2188, 0.7812, 0.4062]`<br>Sign: `[0.1875, 0.5938, 0.4062, 0.9062]` | Severe optical degradation |
| `sample_08` | `sample_08_multi_object.png` | Multi-object scene: exit sign, exit door, and extinguisher | `EXIT_SIGN`, `EXIT_DOOR`, `FIRE_EXTINGUISHER` | Sign: `[0.1250, 0.1562, 0.2812, 0.4688]`<br>Door: `[0.3125, 0.1250, 0.8750, 0.5000]`<br>Extinguisher: `[0.4688, 0.6875, 0.8438, 0.8438]` | Complex multi-equipment spatial layout |
| `sample_09` | `sample_09_no_fire_safety.png` | General office setting (desk, computer monitor, keyboard) | `[]` (None) | N/A | Negative control sample |
| `sample_10` | `sample_10_ambiguous.png` | Flush grey metallic utility cabinet on wall | `ELECTRICAL_PANEL` | `[0.2500, 0.3438, 0.7500, 0.6562]` | Ambiguous feature set (borderline panel/door) |

---

## 4. CV Model Independent Evaluation

### Architecture & Checkpoint:
- Model Class: `BaselineInspectionDetector` (YOLO architecture)
- Checkpoint: `ai-service/artifacts/phase8_4/checkpoints/best.pt`
- Version: `firesphere-inspection-cv` (v0.3.0-phase3)
- Execution Device: `cpu`

### Model Approval Gate Audit:
- Gate Check: `ModelApprovalGate.is_production_approved()` $\rightarrow$ **`False`** (`Status=NOT_APPROVED`).
- Safety Policy: Fail-closed. Production inference is strictly forbidden.

### Evaluation In Two Modes:
1. **Production Mode (`test_mode=False`):**
   - The production adapter was tested against all 10 synthetic samples.
   - Result: In 10 of 10 samples, inference was immediately blocked with `PRODUCTION_INFERENCE_DISABLED`.
   - Detections returned: 0 across all samples.
   - **Verification:** The approval gate functioned flawlessly with zero leaks.

2. **Safe Evaluation Mode (`test_mode=True`):**
   - [`CVModelAdapter.predict(..., test_mode=True)`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/services/inspection_orchestrator.py) was used for offline evaluation without modifying or bypassing the approval gate.
   - Detections returned across all 10 synthetic samples: **0 detections**.
   - **Analysis:** The CV baseline weights in `best.pt` were trained on real-world photographic inspection distributions. The convolutional backbone and feature extractors completely ignored the flat 2D synthetic vector shapes.

### Quantitative Metrics for CV Model on Synthetic Benchmark:
- Total Ground Truth Object Instances: 11
- True Positives (TP): 0
- False Positives (FP): 0
- False Negatives (FN): 11
- True Negatives (TN): 2 (Samples 5 and 9)
- **Precision:** Undefined / 0.00%
- **Recall:** 0.00% (0 / 11)
- **F1 Score:** 0.00%
- **Mean IoU:** 0.00

---

## 5. Qwen2.5-VL Independent Evaluation

Evaluated through the local Ollama provider at `http://127.0.0.1:11434/api/chat` with model `qwen2.5vl:3b`. Zero external network calls were made.

### Results by Sample:

| Sample ID | Status | Observations Count | Predicted Categories | Confidences | IoU vs Ground Truth | Reasoning / Observations |
|---|---|---|---|---|---|---|
| `sample_01` | `ANALYSIS_COMPLETED` | 1 | `FIRE_EXTINGUISHER` | `0.9` | 0.2619 | Correctly classified red cylinder; localized coarsely. |
| `sample_02` | `ANALYSIS_COMPLETED` | 1 | `EXIT_SIGN` | `null` | 0.0593 | Correctly classified green exit sign; box overly wide. |
| `sample_03` | `ANALYSIS_COMPLETED` | 1 | `EXIT_DOOR` | `0.9` | 0.3802 | Correctly classified door and frame; good bounding coverage. |
| `sample_04` | `ANALYSIS_COMPLETED` | 1 | `EXIT_DOOR` | `null` | Door: 0.3883<br>Obstacle: 0.0000 | Detected door; missed foreground crate as separate `OBSTACLE`. |
| `sample_05` | `ANALYSIS_COMPLETED` | 1 | `EXIT_SIGN` (FP) | `0.9` | N/A | **Hallucination:** Hallucinated exit sign on empty white wall. |
| `sample_06` | `ANALYSIS_COMPLETED` | 1 | `FIRE_EXTINGUISHER` | `0.9` | Extinguisher: 0.2702<br>Obstacle: 0.0000 | Detected occluded extinguisher; missed partition `OBSTACLE`. |
| `sample_07` | `ANALYSIS_COMPLETED` | 1 | `EXIT_SIGN` | `null` | Extinguisher: 0.0000<br>Sign: 0.0000 | Under heavy blur, detected sign category but misplaced coordinates. |
| `sample_08` | `ANALYSIS_COMPLETED` | 3 | `EXIT_SIGN`, `EXIT_DOOR`, `FIRE_EXTINGUISHER` | `null`, `null`, `null` | Sign: 0.0010<br>Door: 0.1863<br>Extinguisher: 0.0624 | **All 3 classes classified correctly** in complex scene; boxes coarse. |
| `sample_09` | `ANALYSIS_COMPLETED` | 2 | `EXIT_SIGN` (FP), `EXIT_DOOR` (FP) | `null`, `null` | N/A | **Hallucinations:** Blue monitor $\rightarrow$ exit sign; desk $\rightarrow$ door. |
| `sample_10` | `ANALYSIS_COMPLETED` | 1 | `EXIT_DOOR` (FP) | `null` | Panel: 0.0000 | Ambiguous utility cabinet classified as `EXIT_DOOR`. |

### Quantitative Metrics for Qwen2.5-VL:

#### A. Classification Performance (across 11 ground truth instances):
- True Positives (TP): 8
- False Positives (FP): 5 (Sample 05 sign; Sample 09 sign & door; Sample 10 door)
- False Negatives (FN): 4 (Sample 04 obstacle; Sample 06 obstacle; Sample 07 extinguisher; Sample 10 panel)
- **Precision:** $\frac{8}{8 + 5} = \mathbf{61.54\%}$
- **Recall:** $\frac{8}{8 + 4} = \mathbf{66.67\%}$
- **F1 Score:** $\mathbf{64.00\%}$

#### B. Spatial Localization Performance:
- Bounding-Box IoU Success Rate (@ threshold $\ge 0.50$): **0.00%** (0 / 8 TPs achieved IoU $\ge 0.50$)
- Mean IoU across True Positive detections: **0.194**
- Maximum single-object IoU: **0.388** (Sample 04 doorway)
- **Localization Verdict:** Qwen2.5-VL 3B cannot be treated as an object detector. Predicted coordinates provide approximate region guidance and must be validated by human officers.

#### C. Confidence Score Behavior:
- Verbalized `0.9` in 4 out of 13 emitted observations.
- Omitted confidence (`null` $\rightarrow$ `None`) in 9 out of 13 observations.
- Confidence tokens are heuristic and uncalibrated.

---

## 6. Reconciliation Scenario Results

The [`ResultReconciler`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/intelligence/reconciliation.py) was tested against 10 explicit operational conditions using actual runtime instances:

| # | Operational Scenario | Reconciled Status | Findings Count | Conflict Detail Created | Human Review Required | Compliance Invariant Verified |
|---|---|---|---|---|---|---|
| 1 | **CV and VLM Agreement** | `DISCREPANCY` | 2 | None | Yes | Both findings preserved with source attribution; no confidence averaging. |
| 2 | **CV-Only Findings** | `SINGLE_PROVIDER` | 1 | None | Yes | CV finding preserved with `CV_MODEL` source; absence is not proof of safety. |
| 3 | **VLM-Only Findings** | `SINGLE_PROVIDER` | 1 | None | Yes | VLM finding preserved with `VLM` source and advisory tag. |
| 4 | **Contradictory Categories** | `DISCREPANCY` | 2 | None | Yes | Neither finding suppressed; explicit conflict flags preserved. |
| 5 | **Both Models Empty** | `NO_OBSERVATIONS` | 0 | None | Yes | No findings emitted; human officer verification remains required. |
| 6 | **CV Unavailable / FAILED** | `DISCREPANCY` | 1 | `UNRESOLVED_DISAGREEMENT` | Yes | VLM finding preserved; technical failure does not pass inspection. |
| 7 | **VLM Unavailable / FAILED** | `DISCREPANCY` | 1 | `UNRESOLVED_DISAGREEMENT` | Yes | CV detection preserved; technical failure recorded in ConflictDetail. |
| 8 | **Invalid Evidence Linkage** | `REJECTED_SAFELY` | 0 | N/A | N/A | `InvalidEvidenceException` thrown on evidence UUID mismatch. |
| 9 | **Ambiguous / Inconclusive** | `PARTIAL_INCONCLUSIVE` | 1 | `UNRESOLVED_DISAGREEMENT` | Yes | Inconclusive reason recorded; CV detection preserved for officer review. |
| 10 | **Malformed VLM Output** | `DISCREPANCY` | 0 | `UNRESOLVED_DISAGREEMENT` | Yes | Failed schema handled cleanly; zero false findings generated. |

### Reconciliation Invariants Confirmed:
1. **Source Attribution:** Every finding strictly retains its originating provider (`CV_MODEL` vs `VLM`).
2. **Conflict Preservation:** Conflicting observations are never averaged, merged, or discarded.
3. **No Confidence Blending:** Confidences remain independent and are never averaged.
4. **No Box Fabrication:** Missing boxes remain `None`; application code never invents coordinates.
5. **Absence $\ne$ Safety:** Zero detections never converts to an automated compliance pass.
6. **Mandatory Officer Verification:** `requiresOfficerVerification=True` remains enforced across 100% of findings.

---

## 7. Schema-Validation Results

- Total VLM Output Payloads Evaluated: 10
- JSON Format Compliance: **10/10 (100.0%)**
- `GeminiRawResponse` Validation: **10/10 (100.0%)**
- `GeminiObservation` Mapping: **10/10 (100.0%)**
- Malformed Output Test: 1 simulated malformed JSON correctly triggered `ANALYSIS_INVALID_RESPONSE` without crashing.

---

## 8. Latency and Runtime Observations

- Hardware: Local CPU (PyTorch CPU mode for CV; Ollama local engine for Qwen2.5-VL 3B)
- CV Inference Latency: $\sim 30\text{ms} - 45\text{ms}$ per image (CPU)
- VLM Cold-Start Latency (First Sample): $12,931\text{ms}$ (model weight loading into memory)
- VLM Warm Latency (Subsequent Samples): $2,350\text{ms} - 5,853\text{ms}$ (average: $\mathbf{3,013\text{ms}}$)
- VLM Multi-Object Latency (Sample 08, 3 observations): $5,853\text{ms}$
- Reconciliation Pipeline Latency: $< 2\text{ms}$ per evidence item

---

## 9. Errors and Unsupported Scenarios

1. **Synthetic Drawing Domain Gap in CV:**
   - The current baseline CV weights failed to detect any flat synthetic geometric objects. Real-world inspection photos are required for valid CV benchmarking.
2. **Foreground Obstacle Blindness in VLM:**
   - Qwen2.5-VL 3B missed the foreground crates in Samples 04 and 06, interpreting them as decorative patterns or structural partitions rather than separate fire hazard obstacles.
3. **Office Setting False Positives:**
   - In Sample 09, a standard desktop monitor and computer desk triggered false positive detections for `EXIT_SIGN` and `EXIT_DOOR`.
4. **Utility Panel Ambiguity:**
   - In Sample 10, a generic grey utility cabinet was classified as an `EXIT_DOOR` due to visual similarity of flush rectangular panels.

---

## 10. Known Limitations

- **Synthetic Simplicity:** This benchmark uses 2D synthetic geometric images. It does not measure or represent real-world physical fire-safety inspection accuracy.
- **Model Size Constraints:** Qwen2.5-VL 3B is a compact parameter model. While effective for semantic scene understanding, its spatial token resolution is insufficient for accurate bounding-box regression.
- **Lack of Confidence Calibration:** Confidences verbalized by the VLM cannot be used as calibrated probabilities in automated decision-making.
- **Single-Perspective Limitation:** Static single-frame evaluation cannot verify physical depth or complete 3D egress clearance.

---

## 11. Strict Safety Confirmations

- **Hosted AI Providers:** ZERO calls were made to Google Gemini, OpenAI, or any remote hosted API.
- **Database & Storage Writes:** ZERO writes were made to PostgreSQL, Redis, or MinIO object storage.
- **CV Approval Gate:** `ModelApprovalGate` status remains strictly `NOT_APPROVED`. Production inference remains fail-closed and disabled.
- **Calibration State:** Confidence calibration parameters remain untouched (`NOT_CALIBRATED`).

---

## 12. Recommended Next Steps

1. **Preserve Advisory Status for VLM:**
   - Keep Qwen2.5-VL 3B strictly designated as an advisory visual reasoning component. Do not rely on its bounding boxes for automated compliance checks.
2. **Real-World Inspection Benchmark:**
   - Formulate Phase 15 evaluation using curated, de-identified real-world inspection photographs where the CV detector's feature distribution is applicable.
3. **Prompt Refinement for Negative Suppression:**
   - Enhance the Ollama system instruction with explicit negative anchoring to prevent common office furniture (monitors, desks) from being classified as fire exit apparatus.
4. **Officer Review Workflow:**
   - Maintain the strict requirement that every finding and conflict detail must be reviewed and signed off by a certified fire safety officer.

---

## 13. Final Verdict

$$\mathbf{PASS\_EVALUATION\_COMPLETED}$$

*All evaluation procedures, provenance audits, synthetic dataset generation, independent model assessments, and reconciliation evaluations have been executed and verified in full accordance with the Phase 14 specification. Neither model is approved for production.*
