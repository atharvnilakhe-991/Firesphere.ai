# Phase 11C.1: OpenAI VLM Live-Test Readiness Audit Report

**Date:** 2026-10-12  
**Target AI Service:** `FireSphere.ai / ai-service`  
**Provider Under Audit:** OpenAI Vision-Language Model (`OpenAIProvider`)  
**Candidate Model:** `gpt-4o-mini` (Snapshot: `gpt-4o-mini-2024-07-18`)  
**Audit Scope:** Read-only inspection, architecture verification, official API documentation alignment, offline regression status, and live-test readiness evaluation.  
**Execution Constraints:** Strictly offline; zero live external API calls; zero database/MinIO writes; zero source-code modifications; zero credential exposure.

---

## 1. Executive Summary & Verdict

### Final Readiness Verdict
```
================================================================================
VERDICT: READY_FOR_CONTROLLED_LIVE_TEST
================================================================================
```

### High-Level Summary
FireSphere.ai's multi-provider vision-language model (VLM) architecture has been audited for live-test readiness following the completion of Phase 11B (Provider Abstraction & Offline OpenAI Adapter). 

The underlying implementation in `app/models/openai_provider.py`, `app/models/vlm_provider.py`, `app/core/config.py`, and `app/intelligence/reconciliation.py` strictly adheres to all safety, security, and schema contracts established for FireSphere.ai. The candidate model, **OpenAI `gpt-4o-mini`**, is verified against official documentation as fully supporting multimodal vision inputs, strict Structured Outputs (`json_schema`), and fast, low-cost inference ($0.150 / 1M input, $0.600 / 1M output).

The local environment is currently safely configured in fail-closed mode: `OPENAI_ENABLED` defaults to `False` and `OPENAI_API_KEY` is not present, preventing accidental or unauthorized live network egress. All 203 regression tests across provider adapters, VLM services, reconciliation engines, and core governance contracts passed with 100% success in offline mode.

---

## 2. Implementation & Source Inspection

A line-by-line inspection of the live source tree was performed without relying solely on prior documentation.

### 2.1 File Inspection Matrix

| Component File | Role / Subsystem | Source Verification Finding | Status |
| :--- | :--- | :--- | :--- |
| `app/models/vlm_provider.py` | Abstract Base Class & Factory | Defines `BaseVLMProvider` interface (`analyze_image`, `is_configured`, `get_provider_info`). Factory `get_vlm_provider()` defaults to `"gemini"`, supports `"openai"` via singleton/instantiation pattern. Enforces decoupled contract. | **VERIFIED** |
| `app/models/openai_provider.py` | OpenAI Adapter Implementation | Implements `OpenAIProvider`. Contains `OPENAI_STRUCTURED_RESPONSE_SCHEMA` adhering to strict JSON Schema requirements. Supports dual client execution (`openai` SDK if installed, or fallback to native `httpx.Client`). Fully handles Base64 formatting, image pre-validation, bounded exponential retries, and markdown fence sanitization. | **VERIFIED** |
| `app/models/openai_prompts.py` | Domain Prompts & Schema Dict | Contains `OPENAI_VLM_SYSTEM_PROMPT` tailored for NFPA/OSHA compliance inspection, `OPENAI_VLM_USER_PROMPT_TEMPLATE`, and schema references. Prompts explicitly prohibit hallucination and enforce safety bounding. | **VERIFIED** |
| `app/models/gemini_provider.py` | Google Gemini Adapter | Untouched; preserved as primary/baseline VLM provider. Retains full compatibility with `gemini-3.8-flash`. | **VERIFIED** |
| `app/models/gemini_schemas.py` | Canonical Response Schemas | Defines `GeminiAnalysisResponse`, `GeminiObservation`, `GeminiAnalysisStatus`, and domain enums. Shared canonical schema used across both Gemini and OpenAI providers. | **VERIFIED** |
| `app/services/vlm_service.py` | High-Level VLM Orchestration | Instantiates `get_vlm_provider()` dynamically using `settings.AI_VLM_PROVIDER`. Seamlessly executes `analyze_image` regardless of backend provider. | **VERIFIED** |
| `app/services/inspection_orchestrator.py` | Multi-Modal Pipeline | Coordinates CV inference, CV approval gating, and VLM advisory analysis. Passes `vlm_provider` through configuration or explicit injection. | **VERIFIED** |
| `app/intelligence/reconciliation.py` | CV/VLM Reconciliation Engine | Multi-provider aware: dynamically parses `vlm_response.model` to assign `"providerName": "openai"` or `"google-gemini"` in forensic provenance metadata. Preserves officer verification requirements unconditionally. | **VERIFIED** |
| `app/core/config.py` | Application Settings & Validation | Declares `AI_VLM_PROVIDER`, `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_TIMEOUT_SECONDS`, and `OPENAI_MAX_RETRIES`. Implements Pydantic validator `validate_openai_credentials` to auto-disable provider if API key is missing. | **VERIFIED** |
| `tests/test_openai_provider_phase11b.py` | Comprehensive Offline Test Suite | 20 unit and integration tests covering mock schemas, empty findings, inconclusive responses, unconfigured states, corrupted images, sub-resolution images, malformed JSON, schema violations, connection timeouts, HTTP 429 rate limits, HTTP 401 aborts, and CV gate preservation. | **VERIFIED** |

---

## 3. Selected Model Verification (`gpt-4o-mini`)

The intended model for the initial controlled live test is **OpenAI GPT-4o mini**.

### 3.1 Model Identity & Documentation Findings

All capabilities were cross-checked against official OpenAI developer documentation:

* **Configured Model Identifier:** `gpt-4o-mini` (Aliased to the latest snapshot, currently `gpt-4o-mini-2024-07-18`).
* **Availability:** Generally Available globally in the OpenAI Chat Completions API (`https://api.openai.com/v1/chat/completions`).
* **Multimodal Image Input:** Native vision support. Accepts images as Base64 data URIs (`data:image/jpeg;base64,...` and `data:image/png;base64,...`) within standard content arrays. Detail level is configurable (`"auto"`, `"low"`, `"high"`). High-detail images are processed in 512x512 tiles (85 base tokens + 170 tokens/tile).
* **Structured Outputs (`json_schema`):** Fully supported with strict enforcement (`"strict": true`). GPT-4o mini scores 100% on OpenAI's Structured Outputs adherence benchmarks.
* **Request-Format Compatibility:** 100% compatible. The payload generated by `OpenAIProvider._build_payload` matches OpenAI's exact schema:
  ```json
  {
    "model": "gpt-4o-mini",
    "messages": [
      {"role": "system", "content": "..."},
      {
        "role": "user",
        "content": [
          {"type": "text", "text": "..."},
          {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,...", "detail": "high"}}
        ]
      }
    ],
    "response_format": {
      "type": "json_schema",
      "json_schema": {
        "name": "fire_safety_inspection_response",
        "strict": true,
        "schema": { ... }
      }
    },
    "max_tokens": 4096,
    "temperature": 0.1
  }
  ```
* **Official Pricing (as documented):**
  * **Input Tokens:** $0.150 per 1,000,000 tokens ($0.075 / 1M cached prompt tokens).
  * **Output Tokens:** $0.600 per 1,000,000 tokens.
  * **Structured Outputs Surcharge:** $0.00 (No premium for strict schema enforcement).

---

## 4. Local Configuration Audit (Safe / Zero Secrets)

Configuration settings in `ai-service` were audited strictly without exposing any secrets, tokens, or environment dumps.

### 4.1 Configuration State

| Parameter | Current Local State | Safe Evaluation / Compliance Note |
| :--- | :--- | :--- |
| **OpenAI API Key Configured** | `NO` (`None` / Not present) | Safe. No risk of accidental live requests. |
| **OpenAI Provider Enabled** | `NO` (`OPENAI_ENABLED=False`) | Safe. Defaults to fail-closed state. |
| **Configured Model Identifier** | `gpt-4o-mini` | Matches verified candidate model. |
| **Configured Timeout** | `30.0` seconds | Explicitly bounded; prevents hangs on slow responses. |
| **Max Retries / Attempt Behavior** | `3` attempts total | Bounded exponential backoff; halts immediately on HTTP 401. |
| **Existing Gemini Config Preserved** | `YES` (`GEMINI_ENABLED=true`) | Gemini baseline configuration intact; unaffected by Phase 11. |
| **Default Active VLM Provider** | `gemini` (`AI_VLM_PROVIDER=gemini`) | Gemini remains the primary production default. |

---

## 5. Provider Integration & Safety Governance Audit

### 5.1 Provider Selection & Fallback Behavior
* **Default Routing:** `Settings.AI_VLM_PROVIDER` defaults to `"gemini"`. System routes to Gemini unless explicitly overridden via environment variable (`AI_VLM_PROVIDER=openai`) or programmatic injection.
* **Silent Fallback Prohibited:** In `VLMService` and `InspectionOrchestrator`, if the active provider fails, **no silent fallback** occurs between OpenAI and Gemini. An OpenAI failure yields `GeminiAnalysisStatus.ANALYSIS_FAILED` or `SERVICE_UNAVAILABLE` directly. This maintains deterministic audit trails and prevents cross-provider attribution contamination.

### 5.2 Fail-Closed Operational States
* **Disabled Provider:** If `OPENAI_ENABLED=False`, calls to `analyze_image` immediately return `PROVIDER_NOT_CONFIGURED` with zero network transmission.
* **Missing Key:** If `OPENAI_API_KEY` is null/empty, `app/core/config.py` validator marks `OPENAI_ENABLED=False`. If directly called, `OpenAIProvider` returns `PROVIDER_NOT_CONFIGURED`.
* **Corrupted / Sub-Resolution Images:** `OpenAIProvider._validate_and_encode_image` decodes via PIL and verifies minimum resolution (300x300 px) and size limits (15 MB max). Corrupted or sub-resolution inputs fail closed with `HTTP_422_UNPROCESSABLE_ENTITY` before reaching any external API.
* **JSON Schema Violations:** Raw completions are parsed with `GeminiAnalysisResponse.model_validate()`. Any schema error triggers an immediate fail-closed `INVALID_RESPONSE` with `overallConfidence=0.0`.

### 5.3 Provenance & Forensic Tracking
* Reconciled findings in `app/intelligence/reconciliation.py` inspect the model name and assign:
  * `"providerName": "openai"` when model matches OpenAI tokens.
  * `"providerName": "google-gemini"` when model matches Gemini.
* Evidence identifiers (`evidenceId`) passed to `analyze_image` are strictly preserved on the top-level response and attached to all individual findings.

### 5.4 Safety Gates & Human-in-the-Loop Governance
* **CV Approval Gate (`validate_cv_approval_gate`):** Remains strictly enforced in `InspectionOrchestrator`. Unapproved CV detections cannot enter downstream reconciliation regardless of VLM output.
* **Calibration Thresholds:** Confidence calibration and score weighting remain isolated in the CV pipeline.
* **Advisory Status:** All VLM outputs are tagged `isSafeInference=False`, `requiresOfficerVerification=True`, and `advisoryStatus="ADVISORY_ONLY"`. VLM cannot authorize compliance or sign off on fire safety clearances.

### 5.5 Secrets & Error Log Sanitization
* `OpenAIProvider.get_provider_info()` outputs boolean `"has_api_key": bool(self.api_key)` rather than the key itself.
* Exception handlers log HTTP status codes, error classifications, and response bodies, never headers containing `Bearer` credentials.

---

## 6. Offline Regression Status

All relevant offline test suites were executed within `ai-service` using the Python 3.13.7 virtual environment with external network requests disabled.

### 6.1 Test Execution Commands & Results

#### Suite 1: Phase 11B Focused OpenAI Provider Suite
```powershell
$env:GEMINI_ENABLED="false"; .\.venv\Scripts\python.exe -m pytest tests/test_openai_provider_phase11b.py -v
```
* **Collected:** 20 items
* **Passed:** 20
* **Failed:** 0
* **Skipped:** 0
* **Duration:** 0.31 seconds

#### Suite 2: VLM Service, Gemini Foundation, Vision & Orchestration Suite
```powershell
$env:GEMINI_ENABLED="false"; .\.venv\Scripts\python.exe -m pytest tests/test_openai_provider_phase11b.py tests/test_vlm_service_phase5.py tests/test_gemini_foundation_phase1.py tests/test_gemini_vision_phase2.py tests/test_gemini_phase3_orchestration.py -q
```
* **Collected:** 125 items
* **Passed:** 125
* **Failed:** 0
* **Skipped:** 0
* **Duration:** 4.48 seconds

#### Suite 3: Multi-Modal Reconciliation & Production Integration Suite
```powershell
$env:GEMINI_ENABLED="false"; .\.venv\Scripts\python.exe -m pytest tests/test_phase6_reconciliation_and_report.py tests/test_phase10_production_integration.py -q
```
* **Collected:** 34 items
* **Passed:** 34
* **Failed:** 0
* **Skipped:** 0
* **Duration:** 0.31 seconds

#### Suite 4: Core Governance, Schemas & Evidence Quality Suite
```powershell
$env:GEMINI_ENABLED="false"; .\.venv\Scripts\python.exe -m pytest tests/test_compliance_boundary.py tests/test_confidence.py tests/test_evidence_validation.py tests/test_explainability.py tests/test_finding_aggregation.py tests/test_inspection_analysis.py tests/test_risk_scoring.py tests/test_schemas.py tests/test_severity.py tests/test_verification_guidance.py -q
```
* **Collected:** 44 items
* **Passed:** 44
* **Failed:** 0
* **Skipped:** 0
* **Duration:** 0.34 seconds

### 6.2 Regression Summary Matrix
* **Total Executed Tests:** 203
* **Total Passed:** 203 (100%)
* **Total Failures:** 0
* **Total Errors:** 0
* **Total Skipped:** 0

---

## 7. Defect Register & Preflight Prerequisites

### 7.1 Unresolved Code Defects
* **Zero blocking defects identified.** Code implementation is sound, decoupled, and adheres to all contracts.

### 7.2 Non-Blocking Observations & Live-Test Prerequisites

1. **Dependency Packaging (`openai` SDK):**
   * *Observation:* The virtual environment (`ai-service\.venv`) currently contains `httpx` (0.28.1), but the `openai` Python SDK package is not installed.
   * *Impact:* Non-blocking. `OpenAIProvider` contains a built-in fallback to direct `httpx.Client` REST calls against `https://api.openai.com/v1/chat/completions`, which was verified in Phase 11B offline tests.
   * *Recommendation for Live Testing:* While `httpx` is functional, installing `openai>=1.40.0` is recommended prior to Phase 11C live testing for standard connection pooling and official SDK error parsing.

2. **Credential Provisioning:**
   * *Observation:* `OPENAI_API_KEY` is currently unset.
   * *Requirement for Phase 11C.2 (Live Execution):* The operator must generate an OpenAI API key with spending caps (e.g. $5.00 limit) and provide it in `.env` along with setting `OPENAI_ENABLED=true` and `AI_VLM_PROVIDER=openai`.

3. **Starlette Deprecation Warnings:**
   * *Observation:* Pytest output noted minor warnings regarding Starlette HTTP status constant naming (`HTTP_422_UNPROCESSABLE_ENTITY` vs `HTTP_422_UNPROCESSABLE_CONTENT`).
   * *Impact:* Cosmetic only; does not affect runtime behavior or safety.

---

## 8. Controlled Live-Test Protocol (Phase 11C.2 Preflight)

When authorized to proceed with Phase 11C.2 (Controlled Live Inference Test), the following protocol must be strictly observed:

1. **Single Image Pilot:** Exactly one curated, standardized test image from the test fixture suite (`test_evidence_001.jpg` or equivalent fire extinguisher image).
2. **Deterministic Configuration:**
   * `AI_VLM_PROVIDER=openai`
   * `OPENAI_ENABLED=true`
   * `OPENAI_MODEL=gpt-4o-mini`
   * `OPENAI_TIMEOUT_SECONDS=30.0`
3. **Execution Capture:**
   * Record exact HTTP request/response latency.
   * Verify token usage metrics (prompt tokens, completion tokens, total tokens).
   * Verify strict adherence to `GeminiAnalysisResponse` schema on real OpenAI inference.
   * Verify forensic provenance (`providerName == "openai"`, `model == "gpt-4o-mini"`).
4. **Immediate Post-Test Tear-Down:**
   * Reset `OPENAI_ENABLED=false` or remove key to maintain fail-closed security.
   * Ensure total cost of live test does not exceed $0.05.

---

## 9. Sign-Off & Recommendation

The FireSphere.ai codebase is thoroughly validated, regression-free, and architecturally primed for OpenAI VLM live testing.

* **Audit Decision:** **GO**
* **Readiness State:** **`READY_FOR_CONTROLLED_LIVE_TEST`**
* **Next Authorized Action:** Authorize Phase 11C.2 controlled single-request live pilot upon user key provisioning.
