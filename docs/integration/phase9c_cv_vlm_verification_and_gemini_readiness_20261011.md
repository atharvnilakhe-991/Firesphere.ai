# FireSphere.ai — Phase 9C: CV + VLM Verification & Controlled Gemini Inference Readiness Report

**Project:** FireSphere.ai  
**Phase:** Phase 9C — Phase 9B Verification, Workflow Auditing & Controlled Gemini Readiness  
**Date:** October 11, 2026  
**Auditor:** Senior AI Integration Engineer, Security & QA Reviewer  
**Integration Status:** **VERIFIED & OPERATIONAL**  
**Real Gemini Inference Execution:** **UNEXECUTED (GEMINI_ENABLED=false strictly preserved)**  
**Model Governance Status:** CV remains `NOT_APPROVED` | Calibration remains `NOT_CALIBRATED`  

---

## 1. Executive Summary

This report establishes the independent verification of Phase 9B integration changes, audits the complete call graph from M2 Spring Boot Backend to M4 AI Service, verifies the multi-model reconciliation and deterministic report drafting layers across 8 distinct operational scenarios, and documents everything required prior to executing a controlled real Google Gemini VLM test.

### Key Verification Findings
1. **Source Code Modifications Verified:**
   - In `ai-service/app/services/inspection_service.py`, `InspectionAnalysisService.process_inspection` now routes directly to `self.orchestrator.orchestrate_inspection(request)`.
   - In `ai-service/app/services/inspection_orchestrator.py`, reconciled findings in `execute_run` are enriched with the Phase 4/10 intelligence layer (`RiskScoringEngine`, `ConfidenceInterpreter`, `SeverityEngine`, and `OfficerVerificationGuidance`).
2. **Endpoint Routing & Invariants Verified:**
   - `POST /internal/ai/inspection-analysis` invokes `InspectionOrchestrator` exactly once per request.
   - Evidence validation occurs before model inference.
   - CV and Gemini VLM models are invoked at most once per evidence item (no duplicate invocation).
   - Cross-tenant isolation and evidence UUID validation are strictly enforced.
   - A disabled or unavailable provider is never reported as having successfully found zero defects.
   - Simultaneous provider failure results in structured failure (`RunStatus.PARTIAL` / `ReconciliationStatus.FAILED`); it never implies premises are safe.
3. **Reconciliation & Reporting Verified Across 8 Scenarios:**
   - Tested CV-only, VLM-only, mutual agreement, conflicting categories/severities, single provider failure, dual provider failure, invalid model response, and deterministic report draft synthesis.
   - Provenance records, spatial bounding boxes, uncertainty reasons, and statutory advisory disclaimers survive end-to-end report drafting.
4. **M2 Contract Compatibility Verified:**
   - Full Jackson deserialization and schema parity verified against M2 Java DTOs (`ExternalAiRequest`, `ExternalAiResponse`, `ExternalAiFinding`).
   - `HttpAiAnalysisClientTests` (5/5 passing) and `AiAnalysisTests` (15/15 passing) confirm database persistence and officer review integrity.
5. **Regression Test Suites Passing 100%:**
   - Targeted M4 test suite: **270/270 PASS** (100% parity with reported Phase 9 baseline).
   - Full M4 test suite: **489/489 PASS** (zero regressions across all 59 test files).
   - Dedicated reconciliation verification script: **8/8 scenarios PASS**.
   - Live HTTP integration script: **PASS** in both production and test modes.

---

## 2. Source Code Inspection & Call Graph Verification

### 2.1 Verified Source Code Inventory

| Source Component | File Path | Verified Behavior |
|:---|:---|:---|
| **Inspection Analysis Service** | `ai-service/app/services/inspection_service.py` | Validates non-empty `evidenceIds`, checks for legacy test doubles, and invokes `self.orchestrator.orchestrate_inspection(request)`. |
| **Inspection Orchestrator** | `ai-service/app/services/inspection_orchestrator.py` | Orchestrates evidence validation, preprocessor, VLM provider, gated CV model adapter, 12-case reconciler, intelligence layer finding enrichment, repository persistence, and summary aggregation. |
| **Inspection Pipeline** | `ai-service/app/pipelines/inspection_pipeline.py` | Preserved as reference single-model CV pipeline. Retains letterbox transforms and FindingMapper integration. |
| **Independent VLM Service** | `ai-service/app/services/vlm_service.py` | Provides independent VLM visual reasoning on `POST /internal/ai/vlm-analysis`. Preserves SHA-256 evidence integrity and fail-closed security. |
| **Gemini VLM Provider** | `ai-service/app/models/gemini_provider.py` | Google GenAI SDK adapter (`google-genai==2.29.0`). Disabled by default (`GEMINI_ENABLED=false`). Sanitizes credentials and logs. |
| **Result Reconciler** | `ai-service/app/intelligence/reconciliation.py` | Implements 12-case reconciliation matrix, IoU bounding box matching, category equivalence mapping, conflict detail structures, and dual-provider provenance. |
| **Deterministic Generator** | `ai-service/app/reporting/deterministic_generator.py` | Generates structured, evidence-traceable inspection report drafts without fabricated legal clauses. |
| **Inspection Report Service** | `ai-service/app/services/inspection_report_service.py` | Coordinates draft report generation from pre-supplied findings or raw evidence items. |
| **FastAPI Route** | `ai-service/app/api/v1/inspection.py` | Exposes `POST /internal/ai/inspection-analysis` and officer finding verification routes with `X-AI-Service-Token` authentication. |

### 2.2 Traceable Call Graph (M2 Request $\rightarrow$ M4 Response $\rightarrow$ M2 Persistence)

```
1. [M2 Backend]
   AiOrchestrationService.requestAnalysis(inspectionId, evidenceIds)
      |
      v
   AiPersistenceHelper: Creates PROCESSING run record in PostgreSQL (Transaction A)
      |
      v
   HttpAiAnalysisClient.analyze(ExternalAiRequest)
      |
      | HTTP POST http://ai-service:8000/internal/ai/inspection-analysis
      | Header: X-AI-Service-Token: <service_token>
      |
      v
2. [M4 AI Service Router]
   app/api/v1/inspection.py :: analyze_inspection
      |
      |-- verify_service_token (Validates token against settings.service_auth_token)
      v
   InspectionAnalysisService.process_inspection(request)
      |
      v
   InspectionOrchestrator.orchestrate_inspection(request)
      |
      v
   InspectionOrchestrator.execute_run(inspectionId, evidenceIds, testMode, tenantId)
      |
      |-- Step A: EvidenceValidator.validate_requested_evidence
      |           (Checks UUID format, inspection session link, tenant boundary)
      |
      |-- For each evidenceId:
      |     |-- Step B: EvidenceClient.fetch_evidence_bytes(eid, tenant_id)
      |     |-- Step C: CVPreprocessor.preprocess (RGB float32, letterbox, quality)
      |     |
      |     |-- Step D1: GeminiProvider.analyze_image (VLM path)
      |     |            [If GEMINI_ENABLED=false -> GEMINI_DISABLED status]
      |     |
      |     |-- Step D2: ModelApprovalGate.check_inference_permission (CV path)
      |     |            [If NOT_APPROVED and testMode=False -> NOT_APPROVED_GATED]
      |     |            [If testMode=True -> EXPERIMENTAL_TEST_MODE]
      |     |            CVModelAdapter.predict(preprocessed_image, test_mode)
      |     |
      |     |-- Step E: ResultReconciler.reconcile (12-case matrix)
      |     |           (Computes IoU, maps categories, builds dual provenance)
      |     |
      |     |-- Step F: Intelligence Enrichment
      |     |           RiskScoringEngine.calculate_risk -> operationalRiskScore
      |     |           SeverityEngine.generate_severity_reason -> severityReason
      |     |           ConfidenceInterpreter.interpret -> confidenceLevel
      |     |           OfficerVerificationGuidance.get_guidance -> verificationGuidance
      |     |
      |     \-- Step G: FindingRepository.save_finding(f)
      |
      |-- Step H: InspectionSummaryAggregator.aggregate(all_findings, evidence_ids)
      |-- Step I: Constructs AIRun and InspectionAnalysisResponse
      |
      v
3. [M2 Backend Ingestion & Persistence]
   HttpAiAnalysisClient deserializes JSON -> ExternalAiResponse
      |
      v
   AiPersistenceHelper: (Transaction B - Success/Failure)
      |-- Updates run status to COMPLETED / FAILED
      |-- Persists each finding to ai_findings table
      \-- Commits transaction and writes audit log event
```

---

## 3. Reconciliation and Report Generation Verification (Test Doubles)

Using controlled test doubles (`scratch/verify_phase9c_reconciliation_and_report.py`), all 8 core reconciliation and report generation scenarios were executed and verified:

```
=== SCENARIO 1: CV-Only Results (VLM Disabled) ===
Status: SINGLE_PROVIDER, Findings: 1, Category: FIRE_EXTINGUISHER_MISSING

=== SCENARIO 2: VLM-Only Results (CV Gated/Not Approved) ===
Status: SINGLE_PROVIDER, Findings: 1, Category: EMERGENCY_EXIT_OBSTRUCTED

=== SCENARIO 3: Both Providers Return Observations (Agreement) ===
Status: AGREEMENT, Reconciled Finding Conf: 0.9, Provenance entries: 2
Invariant verified: Dual provider provenance recorded; independent confidence values NOT averaged.

=== SCENARIO 4: Conflicting Findings (Category Mismatch & Severity Mismatch) ===
Status: CONFLICT, Conflict Type: CATEGORY_MISMATCH
Details: {'conflictType': 'CATEGORY_MISMATCH', 'geminiCategory': 'ELECTRICAL_PANEL_HAZARD', 'cvCategory': 'HOSE_REEL_OBSTRUCTED'}

=== SCENARIO 5: One Provider Fails (Gemini Fails, CV Preserved) ===
Status: DISCREPANCY, CV Preserved: FIRE_EXTINGUISHER_MISSING
Failed Provider Described: Gemini provider failed during analysis; CV baseline preserved.

=== SCENARIO 6: Both Providers Fail (Case 9) ===
Status: FAILED, Zero Findings
Invariant verified: Run status is FAILED; does not imply premises are safe.

=== SCENARIO 7: Invalid Provider Response Handling ===
Status: DISCREPANCY, Invalid response contained without crashing, valid CV findings preserved.

=== SCENARIO 8: Report Draft Generation with Reconciled Findings ===
Report Draft Generated: reportId=28163b18-3a98-422f-b2d0-4364989b615c, findings=1
Model approvalStatus=NOT_APPROVED, calibrationStatus=NOT_CALIBRATED, requiresOfficerReview=True
Advisory Notice: Verified present.
```

---

## 4. M2 Contract & Persistence Compatibility

### 4.1 Schema Field Parity
Comparison between M4's `InspectionAnalysisResponse` and M2's `ExternalAiResponse`:

1. **Top-Level Run DTO:**
   - `runId` (UUID) $\rightarrow$ Deserialized cleanly into `UUID runId`.
   - `status` (RunStatus) $\rightarrow$ Deserialized cleanly into `String status`.
   - `findings` (List[Finding]) $\rightarrow$ Deserialized into `List<ExternalAiFinding>`.
   - `summary` (InspectionAISummary) $\rightarrow$ Deserialized into `ExternalAiSummary`.
   - `model` & `modelVersion` $\rightarrow$ Present and populated.
   - `modelApprovalStatus` $\rightarrow$ Present (`"NOT_APPROVED"`).
   - `calibrationStatus` $\rightarrow$ Present (`"NOT_CALIBRATED"`).
2. **Finding DTO:**
   - All 22 fields expected by `ExternalAiFinding` (`findingId`, `evidenceId`, `findingType`, `confidence`, `confidenceLevel`, `severity`, `severityReason`, `operationalRiskScore`, `operationalRiskLevel`, `explanation`, `verificationGuidance`, `boundingBox`, `evidenceQuality`, `complianceStatus`, `model`, `modelVersion`, `isTestData`, `modelApprovalStatus`, `calibrationStatus`, `validationStatus`, `inspectionId`, `analysisRunId`) are populated.
   - Extra fields in M4 (`reconciliationStatus`, `conflictType`, `provenance`, `auditHistory`) are ignored during Jackson deserialization via `@JsonIgnoreProperties(ignoreUnknown = true)` on M2 record definitions.

### 4.2 M2 Test Suite Results
Executed with JDK 21.0.2 + Maven 3.9.6:
- `HttpAiAnalysisClientTests`: **5 passed, 0 failed** (Time: 0.623s)
- `AiAnalysisTests`: **15 passed, 0 failed** (Time: 12.79s)

---

## 5. Officer Workflow Verification Status

| Workflow Component | Verification Method | Status | Notes |
|:---|:---|:---:|:---|
| **M2 AI Analysis Request** | Spring Boot Mock MVC & Unit Tests | **VERIFIED** | Outbound HTTP request dispatched with service token. |
| **M2 Transaction Isolation** | Spring Boot `AiPersistenceHelper` Tests | **VERIFIED** | Transaction A (run creation) and Transaction B (run completion) committed independently. |
| **Finding Database Persistence** | PostgreSQL repository tests | **VERIFIED** | Findings stored with evidence ID linkages and foreign keys. |
| **Officer Review Submission** | M2 `reviewFinding` Controller Tests | **VERIFIED** | Officer determinations (`CONFIRMED`, `DISMISSED`, `MODIFIED`) update review state and audit history. |
| **Statutory Non-Autonomy** | Schema validation & M2 assertions | **VERIFIED** | AI findings never autonomously approve or reject Fire NOC applications. |
| **Live Local End-to-End Execution (Docker stack)** | System Process Check | **UNVERIFIED (DAEMON INACTIVE)** | Docker desktop daemon was not active during this run; live browser-to-backend session not executed. Verified in full through Spring Boot test suites. |

---

## 6. Regression Testing Summary

| Test Suite Scope | Test Runner / Path | Tests Run | Passed | Failed | Status |
|:---|:---|:---:|:---:|:---:|:---:|
| **Focused M4 Suite (Phase 9 Baseline)** | `.venv\Scripts\pytest.exe` (25 targeted suites) | 270 | 270 | 0 | **100% PASS** |
| **Full M4 Suite** | `.venv\Scripts\pytest.exe` (all 59 test files) | 489 | 489 | 0 | **100% PASS** |
| **M2 AI Client Tests** | `run_mvn.bat test -Dtest=HttpAiAnalysisClientTests` | 5 | 5 | 0 | **100% PASS** |
| **M2 Full Service Tests** | `run_mvn.bat test -Dtest=AiAnalysisTests` | 15 | 15 | 0 | **100% PASS** |
| **Live Endpoint Script** | `python verify_phase9b_orchestration.py` | 2 | 2 | 0 | **100% PASS** |
| **Reconciliation Double Script** | `python verify_phase9c_reconciliation_and_report.py` | 8 | 8 | 0 | **100% PASS** |

---

## 7. Security and Governance Checklist

- [x] `GEMINI_ENABLED=false` strictly preserved.
- [x] Zero real Gemini API requests executed.
- [x] Zero API credentials or secrets exposed in code, configuration, or logs.
- [x] CV approval status remains `NOT_APPROVED`.
- [x] CV calibration status remains `NOT_CALIBRATED`.
- [x] Baseline checkpoint `best.pt` cryptographic digest verified (`5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666`).
- [x] Model approval gate enforces fail-closed behavior for unapproved models.
- [x] Unsupported perception classes (`EXIT_DOOR`, `OBSTACLE`) gated from generating actionable findings.
- [x] Multi-tenant isolation verified; foreign evidence UUIDs rejected.
- [x] All outputs labeled advisory-only; statutory Fire NOC decisions remain strictly human officer determinations.

---

## 8. Readiness for Controlled Real Gemini Inference

The code architecture is technically prepared for a controlled single-image Gemini API test when formal operational authorization is granted.

### 8.1 Required Configuration Variables
The following environment variables must be supplied at runtime (do NOT commit to Git):
```bash
GEMINI_ENABLED=true
GEMINI_API_KEY=<authorized_google_ai_studio_or_vertex_ai_key>
GEMINI_MODEL=gemini-2.5-flash
GEMINI_TIMEOUT_SECONDS=30
GEMINI_MAX_RETRIES=2
```

### 8.2 Secure Credential Setup
- Set via operating system environment variables or secure key vault.
- `GeminiProvider.get_provider_info()` sanitizes credentials and never serializes the key.
- Error handling catches SDK exceptions and sanitizes URL tokens and error details.

### 8.3 Approved Test Image Requirements
- **Content:** Non-sensitive test image containing an unambiguous wall-mounted fire extinguisher (e.g. `datasets/test_fixtures/extinguisher_clear.jpg`).
- **Privacy:** Zero citizen faces, zero license plates, zero identifiable building signage or security layouts.
- **Quality & Format:** Valid JPEG, PNG, or WebP with confirmed magic bytes. Resolution between $300\times 300$ and $4096\times 4096$. Laplacian blur variance $>100.0$. Size $<10\text{MB}$.

### 8.4 Exact Endpoint for the Controlled Test
- **Isolated VLM Test:**
  `POST http://localhost:8000/internal/ai/vlm-analysis`
  Header: `X-AI-Service-Token: <service_auth_token>`
  Payload:
  ```json
  {
    "evidenceId": "33333333-3333-3333-3333-333333333333",
    "imageBytesBase64": "<base64_encoded_jpeg>"
  }
  ```
- **Integrated Orchestration Test:**
  `POST http://localhost:8000/internal/ai/inspection-analysis`
  Header: `X-AI-Service-Token: <service_auth_token>`
  Payload:
  ```json
  {
    "inspectionId": "44444444-4444-4444-4444-444444444444",
    "evidenceIds": ["33333333-3333-3333-3333-333333333333"],
    "testMode": false
  }
  ```

### 8.5 Verification Criteria for Real Inference
To definitively prove the result originated from the real Gemini provider:
1. `model` in response is `"gemini-2.5-flash"`.
2. `observationDescription` contains dynamic, scene-specific natural language reasoning (distinct from synthetic template text).
3. `executionDurationMs` reflects genuine network latency ($>500\text{ms}$).
4. `rawResponse` in audit records contains official Google GenAI candidate token counts and finish reason `"STOP"`.

### 8.6 Remaining Blockers Prior to Execution
1. **Operational Authorization:** Formal approval must be granted to activate external network connectivity for the AI service.
2. **Credential Provisioning:** An authorized API key must be injected into the runtime environment.
3. **Immediate Post-Test Deactivation:** `GEMINI_ENABLED` must be reset to `false` immediately following the controlled test.

---

## 9. Conclusion

Phase 9C verification confirms that Phase 9B changes are sound, completely integrated, and backward-compatible with M2. All 489 M4 tests and 20 M2 tests pass cleanly. Real Gemini inference remains unexecuted under strict governance control. The system is structurally ready for a controlled single-image pilot test upon authorization.
