# FireSphere.ai — Stage 8: Unified CV–Qwen Finding Contract, Reconciliation, and VLM Provider Identity Cleanup

**Date:** 2026-10-13  
**Status:** COMPLETE  
**Stage:** Stage 8  
**Final Verdict:** `PASS_WITH_LIMITATIONS`  

---

## 1. Executive Summary

Stage 8 establishes a rigorous, unified, source-aware finding contract for FireSphere.ai, integrating observations from the local Computer Vision baseline (`firesphere-perception-baseline-v0.8.0`) and the local Visual Language Model (`qwen2.5vl:3b` hosted via local Ollama). It resolves historical attribution confusion by auditing the entire workspace for inappropriate Google Gemini references, replacing implementation-facing misattributions with honest local Ollama/Qwen identifiers while preserving shared canonical schemas and backward-compatible aliases.

Crucially, Stage 8 enforces deterministic reconciliation across 12 distinct multi-provider operational states (Cases A through L), guaranteeing that provider failures, unapproved approval-gate blocks, and empty observations are never conflated, that spatial and semantic disagreements are preserved for authorized fire safety officer adjudication, and that automated AI suggestions can never issue autonomous Fire NOC compliance decisions.

---

## 2. Existing Architecture and Inspected Files

Prior to modifications, a comprehensive inspection of active contracts and services was conducted:

| Component / Subsystem | Primary File Path | Role in Architecture |
| :--- | :--- | :--- |
| **Observation & Finding Schemas** | `ai-service/app/schemas/finding.py` | Canonical domain models for bounding boxes, spatial validation, and evidence findings |
| **Reconciliation Engine** | `ai-service/app/intelligence/reconciliation.py` | Cross-model IoU matching, conflict detection, provenance aggregation, and synthesis |
| **Reconciliation Data Models** | `ai-service/app/schemas/reconciliation.py` | Status enums, conflict detail schemas, provenance records, and component state mapping |
| **Inspection Orchestration** | `ai-service/app/services/inspection_orchestrator.py` | Coordinates CV and VLM pipelines, merges evidence status, feeds reconciliation |
| **VLM Provider Factory** | `ai-service/app/models/vlm_provider.py` | Selects active VLM backend based on configuration (`ollama`, `openai`, `gemini`) |
| **Local Ollama Provider** | `ai-service/app/models/ollama_provider.py` | Implements prompt formatting, raw streaming/HTTP calls to local Qwen 2.5-VL 3B |
| **Canonical VLM DTOs** | `ai-service/app/models/gemini_schemas.py` | Provider-agnostic DTO envelopes (`GeminiObservation`, `GeminiAnalysisResult`) shared across backends |
| **Configuration Settings** | `ai-service/app/core/config.py` | Service environment variables, provider defaults, and timeout/retry thresholds |
| **Approval Gate** | `ai-service/app/models/model_approval_gate.py` | Enforces governance barrier on unapproved CV models |
| **Verification Guidance** | `ai-service/app/intelligence/verification.py` | Generates advisory on-site check guidance for human inspecting officers |

---

## 3. Workspace-Wide VLM Identity Audit Inventory

A workspace-wide audit was conducted across `ai-service` and related integration documentation. Occurrences were categorized according to the Stage 8 taxonomy:

- **Category A:** Incorrect active-provider attribution (must be corrected)
- **Category B:** Gemini-specific legacy provider implementation (preserved as inactive provider)
- **Category C:** Shared or canonical schema retained for compatibility (preserved with clear documentation)
- **Category D:** Historical evaluation report (preserved as immutable history)
- **Category E:** Documentation that incorrectly describes the active provider (corrected in Stage 8 report)
- **Category F:** Test or fixture requiring correction (corrected to reflect local Ollama/Qwen)
- **Category G:** Unrelated reference that must remain unchanged (preserved)

### Audit Inventory Matrix

| File Path | Symbol / String | Category | Action | Technical Rationale |
| :--- | :--- | :--- | :--- | :--- |
| `app/models/vlm_provider.py` | `get_vlm_provider(provider_name="ollama")` | A | Updated | Default factory argument set to `"ollama"`. Invalid providers fail closed with `ValueError` rather than falling back to hosted Gemini. |
| `app/services/vlm_service.py` | `provider_name = "google-gemini"` | A | Updated | Replaced hardcoded fallback with dynamic `getattr(self.provider, "provider_name", "ollama")`. |
| `app/services/inspection_orchestrator.py` | `evidence_summary["geminiStatus"]` | C | Extended | Retained `geminiStatus` for legacy consumers while adding `vlmStatus` and `vlmProvider` reporting active Ollama provider. |
| `app/intelligence/reconciliation.py` | `"Gemini VLM"` in explanations & logs | A | Updated | Replaced with `"Ollama/Qwen VLM"` or dynamic provider label; eliminated claims of Gemini analysis for local Qwen executions. |
| `app/intelligence/reconciliation.py` | `geminiFindings`, `geminiObservation` | C | Extended | Preserved as bidirectional aliases alongside `vlmFindings` and `vlmObservation` in `ReconciliationResult` and `ConflictDetail`. |
| `app/schemas/finding.py` | `Finding.modelId` & `Finding.model` | C / A | Updated | Added bidirectional sync validator; defaults `modelId` to source model identifier (`qwen2.5vl:3b` or CV baseline). Added `isAdvisory: bool = True`. |
| `app/schemas/reconciliation.py` | `ReconciliationStatus` enums | C | Extended | Added Stage 8 semantic statuses (`SINGLE_PROVIDER_CV`, `SINGLE_PROVIDER_VLM`, `GATE_BLOCKED_CV`, `PARTIAL_FAILURE_CV`, `PARTIAL_FAILURE_VLM`, `INCONCLUSIVE`, `AMBIGUOUS_MATCH`). |
| `app/models/gemini_schemas.py` | File name & class names (`GeminiObservation`, etc.) | C | Retained | Retained for compatibility. Documented as shared canonical DTOs reused across local Qwen, hosted OpenAI, and Gemini backends. |
| `app/models/gemini_provider.py` | `GeminiProvider` class | B | Retained | Preserved as inactive hosted provider backend. Never invoked unless explicitly configured with `AI_VLM_PROVIDER=gemini` and valid API key. |
| `app/models/ollama_prompts.py` | Prompts for Qwen 2.5-VL | G | Retained | Accurately tailored to local Qwen 2.5-VL vision-language reasoning. |
| `app/core/config.py` | `vlm_provider = "ollama"` | A | Preserved | Verified default is `"ollama"`. `gemini_enabled = False` by default. |
| `tests/test_vlm_service_phase5.py` | `assert status_info["provider"] == "google-gemini"` | F | Updated | Updated assertions to accept active `"ollama"` provider and honest `"ollama-qwen2.5vl-v1"` version. |
| `docs/integration/phase10a_*.md` | Gemini pilot evaluation notes | D | Preserved | Historical Phase 10 evaluation artifacts documenting past Gemini API timeouts. Retained without modification. |
| `docs/integration/vlm_fire_safety_evaluation.md` | Stage 7 evaluation report | D | Preserved | Historical Stage 7 Qwen evaluation report. Retained without modification. |

---

## 4. Incorrect Gemini Attribution Defects Found and Corrected

1. **Reconciliation Narrative Misattribution:**
   - *Defect:* `reconciliation.py` hardcoded strings such as `"Gemini and Computer Vision model both identified..."` and `"Gemini flagged..."` even when inference was executed by local Qwen 2.5-VL.
   - *Fix:* Replaced all hardcoded mentions with dynamic provider formatting (`vlm_label = "Ollama/Qwen VLM"`), ensuring explanations, audit logs, and conflict details cite the true provider.

2. **Reconciliation Result Provider Naming:**
   - *Defect:* `ReconciliationResult` exposed findings solely via `geminiFindings`, forcing callers to interpret local Qwen outputs as Gemini data.
   - *Fix:* Added `vlmFindings` with a bidirectional validator ensuring `geminiFindings` and `vlmFindings` mirror each other identically.

3. **Orchestrator Evidence Diagnostics:**
   - *Defect:* `inspection_orchestrator.py` only populated `geminiStatus` in the evidence diagnostic dictionary.
   - *Fix:* Added `vlmStatus` and `vlmProvider` (`"ollama"`), while preserving `geminiStatus` for backward compatibility.

4. **Service Health Diagnostics:**
   - *Defect:* `VLMService.get_status()` returned hardcoded `provider: "google-gemini"` as a fallback when inspecting service metadata.
   - *Fix:* Bound provider reporting to `getattr(self.provider, "provider_name", "ollama")`.

5. **Silent Hosted Fallback Prevention:**
   - *Defect:* Risk that missing or misspelled provider configuration could trigger hosted Gemini API calls.
   - *Fix:* Hardened `get_vlm_provider()` to fail-closed (`ValueError`) on invalid provider strings. Default provider is explicitly pinned to `"ollama"`.

---

## 5. Gemini References Intentionally Retained and Why

Certain Gemini references were intentionally preserved to ensure zero downtime, prevent breaking public APIs, and maintain backward compatibility:

1. **`app/models/gemini_schemas.py` Filename & DTO Names:**
   - *Reason:* These schemas represent the canonical data contract for multimodal inspection reasoning across the entire service, imported by 14+ modules and test suites. Renaming the file would offer no architectural advantage while introducing significant refactoring and import churn.
   - *Clarification:* Header docstrings were updated to clarify that `gemini_schemas.py` serves as the shared, provider-neutral visual reasoning DTO layer.

2. **`geminiFindings` and `geminiObservation` Fields:**
   - *Reason:* Downstream clients, stored audit histories, and existing integration tests access `result.geminiFindings`. Removing these fields would break API serialization.
   - *Implementation:* Retained as synchronized bidirectional aliases of `vlmFindings` and `vlmObservation`.

3. **Coordinate Convention Label `gemini_0_1000`:**
   - *Reason:* The integer coordinate format `[ymin, xmin, ymax, xmax]` normalized over `[0, 1000]` was popularized by Google Vision APIs and adopted by Qwen2.5-VL bounding box prompts. The string `"gemini_0_1000"` is retained as a coordinate convention identifier to preserve database format compatibility, alongside `"normalized"` (`[0.0, 1.0]`).

4. **Historical Evaluation Artifacts (Phases 10a–10a7, 12, 7, 7.1):**
   - *Reason:* Compliance integrity mandates that historical reports remain immutable records of past engineering decisions and empirical evaluations.

---

## 6. Provider Identity Changes Made

The system now enforces unambiguous separation between provider identity, model identifier, and inference locality:

```
Provider:            ollama
Model Identifier:    qwen2.5vl:3b
Inference Locality:  local
Active Endpoint:     http://localhost:11434/api/generate
CV Model Identifier: firesphere-perception-baseline-v0.8.0
CV Checkpoint SHA:   380b08db911c42287950c45166f81e35dd74b97d268294a50d27ecf01fbe5b84
```

Every `Finding` and `ProviderProvenance` record now serializes:
- `sourceType`: `"CV"`, `"VLM"`, or `"ENSEMBLE"`
- `modelId`: Concrete model name (`"qwen2.5vl:3b"` or `"firesphere-perception-baseline-v0.8.0"`)
- `providerName`: Concrete runtime provider (`"ollama"` or `"firesphere-cv-baseline"`)
- `isCrossValidated`: Boolean indicating concordant agreement between CV and VLM
- `contributingProviders`: Explicit list of contributing modalities (e.g. `["CV", "VLM"]`)
- `isAdvisory`: Strictly `True` (all findings are non-binding advisory suggestions)

---

## 7. Unified Observation/Finding Contract

### Coordinate Systems & Localization Integrity
- **CV Coordinates:** Emits normalized floating-point coordinates `(xMin, yMin, xMax, yMax)` bounded in `[0.0, 1.0]`.
- **Qwen Coordinates:** Emits integer box coordinates `[ymin, xmin, ymax, xmax]` bounded in `[0, 1000]`.
- **Normalization:** In `validate_and_normalize_bbox()`, Qwen coordinates are transformed to normalized floats by dividing by 1000.0, reordering axes to `(xmin, ymin, xmax, ymax)`, and validating that `xMin < xMax`, `yMin < yMax`, and coordinates do not exceed valid bounds.
- **Safety Rule:** Invalid, inverted, or out-of-bounds coordinates are flagged with `SpatialValidationStatus.INVALID_COORDINATES` and discarded rather than arbitrarily mapped.

---

## 8. Reconciliation State Table (Cases A through L)

The deterministic reconciliation engine (`ResultReconciler`) handles 12 distinct multi-provider operational states without conflating failures, empty results, or unapproved states:

| Case | Scenario Description | Semantic Reconciliation Status | Resulting Findings | Conflict Generated? | Advisory Officer Action Required |
| :---: | :--- | :--- | :---: | :---: | :--- |
| **A** | CV and Qwen concordant (same object, matching hazard, IoU $\ge 0.20$) | `AGREEMENT` / `CONCORDANT_AGREEMENT` | 1 unified `ENSEMBLE` finding (`isCrossValidated=True`) | None | Officer verifies physical installation on site |
| **B** | Same object & location, but conflicting hazard classification (e.g. BLOCKED vs MISSING) | `CONFLICT` / `DISCORDANT_FINDING` | 2 separate findings (1 CV, 1 VLM preserved) | `HAZARD_MISMATCH` | Officer inspects physical equipment to determine true defect |
| **C** | CV detects hazard; Qwen returns no observations | `SINGLE_PROVIDER` / `SINGLE_PROVIDER_CV` | 1 CV finding (`isCrossValidated=False`) | None | Officer reviews single-model CV suggestion |
| **D** | Qwen detects hazard; CV returns no observations | `SINGLE_PROVIDER` / `SINGLE_PROVIDER_VLM` | 1 VLM finding (`isCrossValidated=False`) | None | Officer reviews single-model VLM suggestion |
| **E** | Both providers return empty observations | `NO_OBSERVATIONS` / `CONCORDANT_EMPTY` | 0 findings (`isSafeInference=False`) | None | Officer conducts mandatory manual visual sweep (no auto-compliance) |
| **F** | CV execution blocked by Model Approval Gate | `GATE_BLOCKED_CV` / `SINGLE_PROVIDER` | VLM findings preserved; 0 CV findings | `GATE_BLOCKED_CV` | Officer informed CV is unapproved; relies on VLM + manual review |
| **G** | CV execution fails at runtime (e.g. CUDA OOM) | `PARTIAL_FAILURE_CV` / `DISCREPANCY` | VLM findings preserved; 0 CV findings | `PROVIDER_FAILURE` | Officer notified of CV pipeline failure; manual review required |
| **H** | Qwen execution fails at runtime (e.g. socket timeout) | `PARTIAL_FAILURE_VLM` / `DISCREPANCY` | CV findings preserved; 0 VLM findings | `PROVIDER_FAILURE` | Officer notified of VLM provider failure; manual review required |
| **I** | Both visual providers fail | `FAILED` / `BOTH_PROVIDERS_FAILED` | 0 findings | `PROVIDER_FAILURE` | Full system alert; manual physical inspection mandatory |
| **J** | Evidence is visually unclear or inconclusive | `INCONCLUSIVE` / `PARTIAL_INCONCLUSIVE` | Inconclusive finding preserving limitations | None | Officer requests re-photographing or physical check |
| **K** | Observations cannot be safely matched (category mismatch, IoU $\ge 0.20$) | `CONFLICT` / `DISCREPANCY` | 1 or 2 distinct findings | `CATEGORY_MISMATCH` | Officer adjudicates which object category is present |
| **L** | Ambiguous match (same category, low overlap / borderline IoU $< 0.20$) | `CONFLICT` / `AMBIGUOUS_MATCH` | 2 separate distinct findings (CV & VLM preserved) | `LOCATION_MISMATCH` | Officer adjudicates coordinates; no automatic coordinate merge |

---

## 9. Bounding-Box Coordinate Conventions

| Property | Computer Vision Baseline | Local Qwen2.5-VL VLM | Reconciled Domain Finding (`BoundingBox`) |
| :--- | :--- | :--- | :--- |
| **Format** | Dictionary or 4-tuple | Array: `[ymin, xmin, ymax, xmax]` | Model: `{xMin, yMin, xMax, yMax}` |
| **Coordinate Space** | Normalized float: `[0.0, 1.0]` | Integer: `[0, 1000]` | Normalized float: `[0.0, 1.0]` |
| **Coordinate Ordering** | `(xMin, yMin, xMax, yMax)` | `[ymin, xmin, ymax, xmax]` | `xMin`, `yMin`, `xMax`, `yMax` |
| **IoU Matching Threshold** | Spatial match if $\text{IoU} \ge 0.20$ | Spatial match if $\text{IoU} \ge 0.20$ | Spatial match if $\text{IoU} \ge 0.20$ |
| **Precision Assumption** | Calibrated to detector grid | **NOT precise** (exploratory bounding boxes) | Rounded to 4 decimal places |
| **Out-of-Bounds Policy** | Clamped or rejected | Rejected with `INVALID_COORDINATES` | Rejected if area $\le 0.0$ or invalid coordinates |

---

## 10. Error and Uncertainty Handling

- **Explicit Provenance Without Fabrication:** When metadata (such as `modelVersion`, `checkpointMetadata`, or `rawConfidence`) is unavailable from a provider, it is serialized explicitly as `null` or `"UNAVAILABLE"`. Fields are never fabricated.
- **Fail-Closed Execution:** Any unhandled exception or missing dependency in either provider marks that component as `FAILED`, preserving error messages in `ProviderProvenance.error` and `ReconciliationResult.conflicts`.
- **Inconclusive Image Handling:** When an image is blurry or underexposed, Qwen returns `isInconclusive=True`. The reconciliation engine attaches documented visual limitations to `Finding.knownLimitations` and flags the result as `INCONCLUSIVE`.

---

## 11. Checklist Integration Boundary

1. **Advisory suggestions only:** AI findings provide advisory suggestions mapped to specific checklist items.
2. **Officer response preservation:** The AI pipeline can NEVER modify or overwrite an officer's recorded checklist response (`COMPLIANT`, `NON_COMPLIANT`, `NOT_APPLICABLE`).
3. **No autonomous compliance:** Neither an empty AI finding list nor model agreement can mark a checklist item `COMPLIANT`.
4. **Non-visual facts excluded:** Models cannot verify non-visual regulatory requirements (such as hydrostatic cylinder test dates or maintenance contracts) from photographs alone.
5. **No automated Fire NOC decisions:** The issuance or rejection of a Fire No Objection Certificate (NOC) remains the exclusive legal authority of an authorized human Fire Safety Officer.

---

## 12. Backward-Compatibility Considerations

- **Dual Return Signature:** `ResultReconciler.reconcile()` returns a `ReconciliationExecutionResult` that simultaneously behaves as a 3-tuple `(findings, status, conflict)` for legacy callers and an object with `.status`, `.findings`, `.conflicts`, and `.explanation` for modern consumers.
- **Bidirectional Field Aliasing:**
  - `Finding.model` $\longleftrightarrow$ `Finding.modelId`
  - `ReconciliationResult.geminiFindings` $\longleftrightarrow$ `ReconciliationResult.vlmFindings`
  - `ConflictDetail.geminiObservation` $\longleftrightarrow$ `ConflictDetail.vlmObservation`
  - `ConflictDetail.cvFinding` $\longleftrightarrow$ `ConflictDetail.cvObservation`
  - `ConflictDetail.explanation` $\longleftrightarrow$ `ConflictDetail.description`
  - `ProviderProvenance.provider` $\longleftrightarrow$ `ProviderProvenance.providerName`
  - `ProviderProvenance.model` $\longleftrightarrow$ `ProviderProvenance.modelId`
- **Pydantic Enum Aliases:** Added `CONCORDANT_AGREEMENT`, `CONCORDANT_EMPTY`, `DISCORDANT_FINDING`, and `BOTH_PROVIDERS_FAILED` to `ReconciliationStatus`.

---

## 13. Tests Executed and Actual Results

All focused Stage 8 contract tests and existing regression suites were executed using `pytest`.

### Focused Stage 8 Test Suite (`test_stage8_unified_contract.py`)

| Test Name | Verified Behavior | Result |
| :--- | :--- | :---: |
| `test_cv_only_finding_preserves_provenance` | Case C: Single-provider CV observation preserves CV model ID and provenance | **PASSED** |
| `test_qwen_only_finding_preserves_provenance` | Case D: Single-provider Qwen observation preserves Qwen model ID and provenance | **PASSED** |
| `test_concordant_cv_qwen_agreement` | Case A: Concordant agreement yields unified ENSEMBLE finding (`isCrossValidated=True`) | **PASSED** |
| `test_conflicting_hazard_mismatch_preserves_both_viewpoints` | Case B: Disagreeing hazard classifications preserve both CV and VLM findings | **PASSED** |
| `test_spatial_ambiguity_borderline_iou_preserves_distinct_findings` | Case L: Low IoU preserves distinct findings without automatic coordinate merging | **PASSED** |
| `test_validate_and_normalize_bbox_handles_invalids` | Out-of-bounds/inverted boxes rejected with `INVALID_COORDINATES` | **PASSED** |
| `test_missing_provenance_is_explicit_not_fabricated` | Null metadata serialized explicitly without fabrication | **PASSED** |
| `test_qwen_provider_identity_correctness` | Qwen findings serialize `modelId="qwen2.5vl:3b"`, never Gemini | **PASSED** |
| `test_provider_provenance_serialization` | Provider provenance serializes full execution and uncertainty metadata | **PASSED** |
| `test_reconciliation_explanation_cites_ollama_qwen` | Narrative explanations cite Ollama/Qwen, never Gemini | **PASSED** |
| `test_successful_empty_cv_with_qwen_finding` | Case D: Successful empty CV preserves Qwen observation | **PASSED** |
| `test_successful_empty_qwen_with_cv_finding` | Case C: Successful empty Qwen preserves CV observation | **PASSED** |
| `test_both_providers_empty_never_claims_compliance` | Case E: Empty observations set `isSafeInference=False` | **PASSED** |
| `test_cv_approval_gated_status_handling` | Case F: Unapproved CV blocked cleanly; VLM findings preserved | **PASSED** |
| `test_cv_runtime_failure_handling` | Case G: CV runtime failure preserved with error explanation | **PASSED** |
| `test_qwen_runtime_failure_handling` | Case H: Qwen provider failure preserved with error explanation | **PASSED** |
| `test_both_providers_failing_preserves_error_details` | Case I: Dual provider failure produces `FAILED` status without crash | **PASSED** |
| `test_inconclusive_image_analysis_preserves_limitations` | Case J: Inconclusive image preserves visual caveats and limitations | **PASSED** |
| `test_invalid_provider_configuration_raises_error` | Provider factory fails closed (`ValueError`) on invalid provider string | **PASSED** |
| `test_default_provider_is_ollama_not_gemini` | Factory defaults to local Ollama provider, never hosted Gemini | **PASSED** |
| `test_shared_schema_compatibility_with_qwen` | `GeminiAnalysisResult` and `GeminiObservation` serialize Qwen data cleanly | **PASSED** |
| `test_bidirectional_finding_model_id_alias` | `Finding.model` and `Finding.modelId` stay synchronized bidirectionally | **PASSED** |
| `test_bidirectional_reconciliation_result_aliases` | `vlmFindings` and `geminiFindings` stay synchronized bidirectionally | **PASSED** |
| `test_findings_are_strictly_advisory_no_autonomous_compliance` | `f.isAdvisory is True`; autonomous approval/rejection forbidden | **PASSED** |

### Comprehensive Regression Summary

```
============================= test session starts =============================
platform win32 -- Python 3.13.7, pytest-9.1.1, pluggy-1.6.0
collected 170 items

tests/test_stage8_unified_contract.py ........................           [ 14%]
tests/test_cv_vlm_integration_corrections.py .............               [ 21%]
tests/test_gemini_phase3_orchestration.py .............................. [ 39%]
................                                                         [ 48%]
tests/test_ollama_provider_phase13.py ...................                [ 60%]
tests/test_vlm_service_phase5.py .................                       [ 70%]
tests/test_gemini_vision_phase2.py ..........................            [ 85%]
tests/test_gemini_foundation_phase1.py ................                  [ 94%]
tests/test_schemas.py .....                                              [ 97%]
tests/test_compliance_boundary.py ....                                   [100%]

======================= 170 passed, 5 warnings in 6.30s =======================
```

---

## 14. Files Changed

1. `ai-service/app/schemas/finding.py`
   - Added `SpatialValidationStatus` enum.
   - Added `isCrossValidated: bool = False`, `contributingProviders: List[str]`, and `isAdvisory: bool = True` fields to `Finding`.
   - Added `modelId` bidirectional synchronization validator.
   - Enhanced `BoundingBox` to support coordinate aliases (`xmin` vs `xMin`) and 4-tuple equality.

2. `ai-service/app/schemas/reconciliation.py`
   - Added Stage 8 status enums to `ReconciliationStatus` and conflict types to `ConflictType`.
   - Added semantic status aliases (`CONCORDANT_AGREEMENT`, `CONCORDANT_EMPTY`, `DISCORDANT_FINDING`, `BOTH_PROVIDERS_FAILED`).
   - Extended `ProviderProvenance` with Stage 8 provenance fields, default `providerName="ollama"`, `modelId="qwen2.5vl:3b"`, and bidirectional aliases.
   - Extended `ConflictDetail` with `evidenceId`, `explanation`, and `cvFinding` aliases, accepting both models and dictionaries.
   - Added `ReconciliationResult` with synchronized `vlmFindings` and `geminiFindings`.

3. `ai-service/app/models/gemini_schemas.py`
   - Documented canonical role as shared, provider-neutral visual reasoning DTO layer.
   - Added `provider`, `metadata`, `totalObservations`, `inferenceTimeMs`, and `errorMessage` to `GeminiAnalysisResult`.
   - Added `hazardCategory` bidirectional alias to `GeminiObservation`.
   - Added `box_2d` normalization and validation helpers.

4. `ai-service/app/intelligence/reconciliation.py`
   - Implemented `ReconciliationExecutionResult` tuple subclass supporting both 3-tuple unpacking and object attribute access.
   - Implemented Case A (concordant agreement), Case B (hazard mismatch preserving both observations), and Case 5 / Case L (spatial mismatch preserving both observations).
   - Replaced all hardcoded `"Gemini"` narrative labels with dynamic provider attribution (`"Ollama/Qwen VLM"`).
   - Exported `CVVLMReconciliationService` alias and bounding box normalization utilities.

5. `ai-service/app/models/vlm_provider.py`
   - Verified default provider is `"ollama"`.
   - Hardened `get_vlm_provider()` to reject invalid providers with `ValueError` (fail-closed).

6. `ai-service/app/services/vlm_service.py`
   - Updated provider fallback to `getattr(self.provider, "provider_name", "ollama")`.

7. `ai-service/app/services/inspection_orchestrator.py`
   - Added `vlmStatus` and `vlmProvider` alongside legacy `geminiStatus`.

8. `ai-service/tests/test_stage8_unified_contract.py`
   - Created comprehensive 24-test suite validating all Stage 8 contract requirements.

9. `ai-service/tests/test_vlm_service_phase5.py`
   - Updated 3 Phase 5 assertions to accept active `"ollama"` provider and `"ollama-qwen2.5vl-v1"` version.

---

## 15. Representative Synthetic Payloads

### 1. Computer Vision (CV) Observation Payload
```json
{
  "findingId": "d14a51e2-6b94-4e28-9844-3d0d828a2b10",
  "evidenceId": "7b82f1a0-3882-4bf1-8973-ec6c35c8e234",
  "findingType": "FIRE_EXTINGUISHER_BLOCKED",
  "confidence": 0.88,
  "rawConfidence": 0.88,
  "severity": "HIGH",
  "explanation": "CV baseline detector identified equipment obstruction at designated coordinates.",
  "sourceType": "CV",
  "model": "firesphere-perception-baseline-v0.8.0",
  "modelVersion": "0.8.0",
  "modelId": "firesphere-perception-baseline-v0.8.0",
  "isCrossValidated": false,
  "contributingProviders": ["CV"],
  "isAdvisory": true,
  "coordinateConvention": "normalized",
  "boundingBox": {
    "xMin": 0.125,
    "yMin": 0.21,
    "xMax": 0.485,
    "yMax": 0.65
  },
  "provenance": [
    {
      "providerName": "firesphere-cv-baseline",
      "modelId": "firesphere-perception-baseline-v0.8.0",
      "modelVersion": "0.8.0",
      "sourceType": "CV",
      "executionStatus": "SUCCESS",
      "calibrationStatus": "NOT_CALIBRATED",
      "coordinateConvention": "normalized",
      "checkpointMetadata": {
        "checkpointSha256": "380b08db911c42287950c45166f81e35dd74b97d268294a50d27ecf01fbe5b84",
        "approvalStatus": "NOT_APPROVED"
      }
    }
  ],
  "complianceStatus": "REQUIRES_OFFICER_VERIFICATION"
}
```

### 2. Local Qwen VLM Observation Payload
```json
{
  "findingId": "9e1c2b44-f89a-4712-a1b3-4682049e211b",
  "evidenceId": "7b82f1a0-3882-4bf1-8973-ec6c35c8e234",
  "findingType": "FIRE_EXTINGUISHER_MISSING",
  "confidence": 0.85,
  "rawConfidence": 0.85,
  "severity": "HIGH",
  "explanation": "Ollama/Qwen VLM identified visual defect: Designated extinguisher bracket is empty with sign visible.",
  "sourceType": "VLM",
  "model": "qwen2.5vl:3b",
  "modelVersion": "ollama-qwen2.5vl-v1",
  "modelId": "qwen2.5vl:3b",
  "isCrossValidated": false,
  "contributingProviders": ["VLM"],
  "isAdvisory": true,
  "coordinateConvention": "gemini_0_1000",
  "boundingBox": {
    "xMin": 0.15,
    "yMin": 0.22,
    "xMax": 0.48,
    "yMax": 0.64
  },
  "provenance": [
    {
      "providerName": "ollama",
      "modelId": "qwen2.5vl:3b",
      "modelVersion": "ollama-qwen2.5vl-v1",
      "sourceType": "VLM",
      "executionStatus": "SUCCESS",
      "calibrationStatus": "NOT_CALIBRATED",
      "coordinateConvention": "gemini_0_1000",
      "uncertaintyMetadata": {
        "uncertaintyOrLimitations": ["Bounding box estimated from local 3B VLM reasoning grid"]
      }
    }
  ],
  "complianceStatus": "REQUIRES_OFFICER_VERIFICATION"
}
```

### 3. CV/Qwen Disagreement (Case B — HAZARD_MISMATCH) Payload
```json
{
  "evidenceId": "7b82f1a0-3882-4bf1-8973-ec6c35c8e234",
  "status": "CONFLICT",
  "explanation": "Cross-provider conflict detected: Computer Vision and Ollama/Qwen VLM models produced conflicting classifications. Both observations preserved for officer adjudication.",
  "findings": [
    {
      "findingId": "9e1c2b44-f89a-4712-a1b3-4682049e211b",
      "findingType": "FIRE_EXTINGUISHER_MISSING",
      "sourceType": "VLM",
      "modelId": "qwen2.5vl:3b",
      "isCrossValidated": false,
      "reconciliationStatus": "CONFLICT",
      "conflictType": "HAZARD_MISMATCH",
      "explanation": "Model hazard disagreement detected on same physical object: Ollama/Qwen VLM flagged 'FIRE_EXTINGUISHER_MISSING' while CV baseline flagged 'FIRE_EXTINGUISHER_BLOCKED'."
    },
    {
      "findingId": "d14a51e2-6b94-4e28-9844-3d0d828a2b10",
      "findingType": "FIRE_EXTINGUISHER_BLOCKED",
      "sourceType": "CV",
      "modelId": "firesphere-perception-baseline-v0.8.0",
      "isCrossValidated": false,
      "reconciliationStatus": "CONFLICT",
      "conflictType": "HAZARD_MISMATCH",
      "explanation": "Model hazard disagreement detected on same physical object: CV baseline flagged 'FIRE_EXTINGUISHER_BLOCKED' while Ollama/Qwen VLM flagged 'FIRE_EXTINGUISHER_MISSING'."
    }
  ],
  "conflicts": [
    {
      "conflictType": "HAZARD_MISMATCH",
      "description": "Hazard conflict on same object: Ollama/Qwen VLM identified 'FIRE_EXTINGUISHER_MISSING' whereas CV baseline identified 'FIRE_EXTINGUISHER_BLOCKED'.",
      "requiresOfficerAttention": true,
      "vlmObservation": {
        "objectCategory": "FIRE_EXTINGUISHER",
        "potentialHazardCategory": "FIRE_EXTINGUISHER_MISSING",
        "confidence": 0.85
      },
      "cvFinding": {
        "findingType": "FIRE_EXTINGUISHER_BLOCKED",
        "confidence": 0.88
      }
    }
  ],
  "isSafeInference": false
}
```

### 4. Provider Failure (Case H — PARTIAL_FAILURE_VLM) Payload
```json
{
  "evidenceId": "7b82f1a0-3882-4bf1-8973-ec6c35c8e234",
  "status": "DISCREPANCY",
  "explanation": "Ollama/Qwen VLM provider failed during analysis; CV baseline preserved.",
  "findings": [
    {
      "findingId": "d14a51e2-6b94-4e28-9844-3d0d828a2b10",
      "findingType": "FIRE_EXTINGUISHER_BLOCKED",
      "sourceType": "CV",
      "modelId": "firesphere-perception-baseline-v0.8.0",
      "isCrossValidated": false,
      "reconciliationStatus": "DISCREPANCY",
      "conflictType": "PROVIDER_FAILURE"
    }
  ],
  "conflicts": [
    {
      "conflictType": "PROVIDER_FAILURE",
      "description": "Ollama/Qwen VLM provider failed during analysis; CV baseline preserved.",
      "requiresOfficerAttention": true
    }
  ],
  "isSafeInference": false
}
```

### 5. Inconclusive Observation (Case J — INCONCLUSIVE) Payload
```json
{
  "evidenceId": "7b82f1a0-3882-4bf1-8973-ec6c35c8e234",
  "status": "INCONCLUSIVE",
  "explanation": "VLM analysis was inconclusive due to visual limitations: Image underexposed and motion blurred. Human Fire Officer verification required.",
  "findings": [
    {
      "findingId": "a5c7d812-70b1-4199-a3b4-184719280911",
      "findingType": "GENERIC_FIRE_SAFETY_DEFECT",
      "confidence": 0.4,
      "sourceType": "VLM",
      "modelId": "qwen2.5vl:3b",
      "knownLimitations": [
        "Inconclusive Ollama/Qwen evaluation: Image underexposed and motion blurred"
      ],
      "complianceStatus": "REQUIRES_OFFICER_VERIFICATION",
      "isAdvisory": true
    }
  ],
  "isSafeInference": false
}
```

---

## 16. Known Limitations

1. **Qwen Bounding Box Imprecision:** As established in Stage 7 and Stage 7.1 audits, Qwen 2.5-VL 3B exhibits zero localized bounding boxes achieving $\text{IoU} \ge 0.50$ against human ground truth. Its coordinates must be treated as indicative region-of-interest proposals rather than precision metric bounding boxes.
2. **Confidence Calibration Pending:** Neither model has undergone post-hoc calibration (temperature scaling / isotonic regression). Raw confidence scores cannot be interpreted as posterior probabilities of hazard occurrence.
3. **No Retraining Conducted:** Model weights and checkpoints were left completely untouched in accordance with controlled evaluation guidelines.

---

## 17. Production-Readiness Limitations

1. **Model Approval Gate:** The Computer Vision detector remains strictly `NOT_APPROVED` for autonomous deployment (`ModelApprovalGate.is_production_approved() == False`).
2. **Calibration State:** Calibration remains strictly `NOT_CALIBRATED`.
3. **Production Authorization:** Production inference authorization remains `False`.
4. **Human Verification Invariant:** All findings generated by the unified CV–Qwen contract are strictly advisory (`isAdvisory = True`). They serve as decision-support suggestions for authorized human Fire Safety Officers.

---

## 18. Final Verdict

# `PASS_WITH_LIMITATIONS`

### Summary of Stage 8 Achievements:
1. **Accurate Attribution Established:** Active VLM inference is identified honestly as local Ollama running `qwen2.5vl:3b`. Gemini misattributions have been eliminated from runtime logs, explanations, and model IDs.
2. **Fail-Closed Provider Security:** Provider selection defaults to local Ollama; invalid configurations fail closed with zero possibility of silent fallback to hosted Gemini.
3. **Deterministic Multi-State Reconciliation:** Implemented full 12-case reconciliation handling concordant agreement, hazard disagreements, spatial mismatches, gate-blocked models, and provider failures while preserving complete observation provenance.
4. **Safety & Governance Invariants Preserved:** `ModelApprovalGate` remains `NOT_APPROVED`, calibration remains `NOT_CALIBRATED`, production authorization remains `False`, and AI findings remain strictly advisory suggestions.
5. **Zero Regression:** All 170 unit and integration tests pass cleanly with zero failures.
