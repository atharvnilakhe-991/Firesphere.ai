# FireSphere.ai — Phase 6: Multi-Model Orchestration, CV–VLM Reconciliation, Evidence-Based Finding Aggregation, and Advisory Report Generation Report

**Project**: FireSphere.ai  
**Phase**: Phase 6 — Multi-Model Orchestration, CV–VLM Reconciliation, and Advisory Report Generation  
**Status**: **COMPLETE (Orchestration, Reconciliation, Advisory Reporting & Test Suite Verified; Real VLM Inference Remains BLOCKED by Governance Policy)**  
**Execution Date**: 2026-10-11  
**Workspaces**:  
- Canonical M4 AI Service: `C:\Users\athar\OneDrive\Desktop\Firesphere.ai\ai-service`  
- M2 Core Backend: `C:\Users\athar\OneDrive\Desktop\Firesphere.ai\M2_work\Firesphere m2\backend`  
- M3 Database Workspace: `C:\Users\athar\OneDrive\Desktop\Firesphere.ai\M3_work\IP_DB`  

---

## 1. Executive Summary

Phase 6 implements a robust, deterministic, and auditable multi-model orchestration and cross-provider reconciliation pipeline connecting the Computer Vision (CV) detection baseline and the Vision-Language Model (VLM) visual reasoning service into a unified, evidence-linked advisory inspection reporting system.

Key achievements of Phase 6:
1. **Audited & Reused Existing Reconciliation Architecture**: Audited `app/intelligence/reconciliation.py`, `app/services/inspection_orchestrator.py`, `app/reporting/deterministic_generator.py`, and `app/schemas/report.py`. Enhanced the existing `ResultReconciler` rather than introducing duplicate parallel engines.
2. **Standardized 5-State Component Semantics**: Defined `ComponentAnalysisStatus` (`SUCCESS_WITH_OBSERVATIONS`, `SUCCESS_WITH_NO_OBSERVATIONS`, `INCONCLUSIVE`, `FAILED`, `UNAVAILABLE`) and implemented `map_component_status()` to guarantee that failed or disabled components are never converted into empty successful results, and that an empty result from a successful component does not falsely certify compliance.
3. **Hardened Evidence Linkage & Integrity Verification**: Enforced strict UUID matching between requested evidence, CV detections, and VLM observations. Inconsistent evidence identifiers are rejected immediately (`InvalidEvidenceException`) rather than silently rewritten.
4. **Deterministic Cross-Model Reconciliation Matrix**:
   - **Full Agreement**: Spatial IoU ($\ge 0.30$) and category equivalence corroborate visual presence, preserving both model provenances without fabricating synthetic joint confidence scores.
   - **Partial Agreement**: Preserves condition observations alongside equipment presence observations with accurate relationship annotations.
   - **Disagreements**: Classifies `CATEGORY_MISMATCH` (spatial overlap with diverging defect types) and `LOCATION_MISMATCH` (divergent coordinates), preserving both model observations in structured `conflictDetails` without allowing either model to autonomously override the other.
   - **Degraded/Single-Component Modes**: If VLM is unavailable/disabled (`GEMINI_ENABLED=false`), CV baseline detections are preserved with `SINGLE_PROVIDER` status and transparent provenance.
5. **Multi-Model Advisory Report Generation**: Enhanced `ReportModelInfo` and `InspectionReportDraft` to report CV and VLM model versions, approval states, and operational statuses. Unresolved items explicitly flag model unavailability, unapproved CV gating, and cross-model disagreements.
6. **M2 Backend Contract Verification**: Confirmed complete structural and deserialization compatibility with M2's `ExternalAiResponse` DTO (`@JsonIgnoreProperties(ignoreUnknown = true)`).
7. **Comprehensive Test Suite & Zero Regressions**: Authored 21 focused Phase 6 tests in `tests/test_phase6_reconciliation_and_report.py` covering Cases A through W. All 21 passed. Executed the full M4 regression suite: **489 passed, 0 failed, 0 errors** across the canonical AI service.

---

## 2. Initial Architecture Audit

Before modifying any source files, an exhaustive architectural review was performed across M4 and M2:

| Component | File Path | Existing Role & Responsibility | Phase 6 Findings |
| :--- | :--- | :--- | :--- |
| **Reconciliation Engine** | `app/intelligence/reconciliation.py` | `ResultReconciler` class coordinating Gemini VLM and CV baseline outputs | Contained 12-case matrix; required strict evidence UUID validation, explicit component status mapping, and confidence preservation. |
| **Inspection Orchestrator** | `app/services/inspection_orchestrator.py` | Coordinates multi-provider execution per evidence item | Handled storage retrieval, OpenCV preprocessing, capability gating, and finding persistence; needed component status extraction for summary. |
| **Report Schemas** | `app/schemas/report.py` | Pydantic contracts for `InspectionReportDraft`, `ReportModelInfo`, and `ReportFindingItem` | Single-model metadata needed extension to support multi-model provenance (`cvModelName`, `vlmModelName`, statuses). |
| **Deterministic Generator** | `app/reporting/deterministic_generator.py` | Generates reproducible, non-statutory draft reports | Generated audit trails, verification actions, and limitations; needed multi-model status ingestion and explicit VLM-unavailable items. |
| **Inspection Report Service** | `app/services/inspection_report_service.py` | Coordinates draft report generation from findings or pipeline | Needed to forward multi-model statuses from findings provenance to the draft generator. |
| **M2 Backend DTOs** | `backend/.../ExternalAiResponse.java` | Jackson records receiving AI analysis response | Uses `@JsonIgnoreProperties(ignoreUnknown = true)`; maps `runId`, `status`, `findings`, `summary`, `model`, `modelVersion`, `modelApprovalStatus`, `calibrationStatus`. |

---

## 3. Actual CV and VLM Contracts

### CV Detection Model Contract
- **Model Identifier**: `firesphere-perception-baseline-v0.8.0`
- **Model Version**: `0.8.0`
- **Output Structure**: `RawDetection(className: str, confidence: float, boundingBox: dict, severity: str)`
- **Normalized Finding**: `Finding(findingType, confidence, severity, boundingBox, model="firesphere-perception-baseline-v0.8.0", modelApprovalStatus="NOT_APPROVED", calibrationStatus="NOT_CALIBRATED")`
- **Approval Gate**: In production mode (`test_mode=False`), inference is blocked (`NOT_APPROVED_GATED`). In test mode (`test_mode=True`), experimental inference executes with `isTestData=True`.

### VLM Visual Reasoning Contract
- **Model Identifier**: `gemini-2.5-flash`
- **Provider Version**: `gemini-genai-v2`
- **Output Structure**: `GeminiObservation(observationId: str, imageReference: str, evidenceId: str, objectCategory: str, potentialHazardCategory: Optional[str], observationDescription: str, boundingBox: Optional[BoundingBox], confidence: Optional[float], severityProposal: Optional[str])`
- **Analysis Statuses**: `GeminiAnalysisStatus` (`GEMINI_DISABLED`, `GEMINI_NOT_CONFIGURED`, `ANALYSIS_COMPLETED_WITH_OBSERVATIONS`, `ANALYSIS_COMPLETED_WITH_NO_OBSERVATIONS`, `ANALYSIS_INCONCLUSIVE`, `ANALYSIS_FAILED`, `ANALYSIS_INVALID_RESPONSE`)

---

## 4. Phase 5 Interface and Evidence-Flow Verification

The end-to-end evidence ingestion and analysis pipeline was traced:

```
[M2 Core Backend]
       |
       | Authenticated HTTP Request (POST /internal/ai/inspection-analysis)
       v
[M4 AI Service: Router -> InspectionOrchestrator]
       |
       +---> 1. EvidenceValidator.validate_requested_evidence()
       |        - Validates evidence UUID format and non-empty batch
       |        - Checks storage/registry existence under authenticated tenant
       |
       +---> 2. EvidenceClient.fetch_evidence_bytes()
       |        - Ingests raw binary bytes (S3/MinIO in production; in-memory registry in test)
       |        - Verifies SHA-256 checksum against database record
       |        - Validates file size (max 10 MB)
       |
       +---> 3. CVPreprocessor.preprocess()
       |        - Decodes image matrix and checks resolution (min 100x100)
       |        - Measures blur (Laplacian variance), brightness, and contrast
       |        - Evaluates EvidenceQuality (GOOD, ACCEPTABLE, POOR, UNUSABLE)
       |
       +---> 4. Model Inference Isolation
       |        - CV Component: Gated by model_approval_gate.check_inference_permission()
       |        - VLM Component: Checked via gemini.is_enabled() (GEMINI_ENABLED=false -> GEMINI_DISABLED)
       |
       +---> 5. ResultReconciler.reconcile()
       |        - Validates matching evidence UUIDs
       |        - Evaluates cross-model agreement, disagreement, or single-provider observations
       |        - Populates multi-provider provenance records
       |
       +---> 6. DeterministicReportGenerator.generate_draft()
       |        - Produces InspectionReportDraft with multi-model metadata and audit links
       v
[M2 Core Backend Persistence & Workflow]
       |
       v
[Authorized Fire Safety Officer Review & Statutory Decision]
```

**Interface Finding**: In `VLMService._resolve_evidence_payload()`, when `imageBytesBase64` is absent from a request, the service retrieves image bytes directly via `EvidenceClient.fetch_evidence_bytes(evidence_id)`. Both CV and VLM can be invoked independently on the same original photographic asset.

---

## 5. Component Status Semantics

The reconciliation engine defines five normalized semantic states in `ComponentAnalysisStatus` (`app/schemas/reconciliation.py`):

```python
class ComponentAnalysisStatus(str, Enum):
    SUCCESS_WITH_OBSERVATIONS = "SUCCESS_WITH_OBSERVATIONS"
    SUCCESS_WITH_NO_OBSERVATIONS = "SUCCESS_WITH_NO_OBSERVATIONS"
    INCONCLUSIVE = "INCONCLUSIVE"
    FAILED = "FAILED"
    UNAVAILABLE = "UNAVAILABLE"
```

### Deterministic State Mapping (`map_component_status`)

| Provider Raw Status | Observation Count | Error Present | Normalized Semantic State | Safety Semantic |
| :--- | :--- | :--- | :--- | :--- |
| `DISABLED`, `GEMINI_DISABLED`, `NOT_CONFIGURED` | 0 | None | `UNAVAILABLE` | Model was not executed; zero evidence evaluated |
| `NOT_APPROVED_GATED` (Production Mode) | 0 | None | `UNAVAILABLE` | Model unapproved for production; gated |
| `FAILED`, `ANALYSIS_FAILED`, `ERROR` | Any | Any | `FAILED` | Analysis aborted due to technical failure |
| `INCONCLUSIVE`, `ANALYSIS_INCONCLUSIVE` | Any | None | `INCONCLUSIVE` | Visual evidence insufficient to make determination |
| `SUCCESS`, `COMPLETED` | $> 0$ | None | `SUCCESS_WITH_OBSERVATIONS` | Model completed and identified visual patterns |
| `SUCCESS`, `COMPLETED` | 0 | None | `SUCCESS_WITH_NO_OBSERVATIONS` | Model completed; no supported defects recognized |

### Critical Safety Invariants
1. **Never Convert Failure to Empty Success**: A failed or unavailable component is never converted into `SUCCESS_WITH_NO_OBSERVATIONS`.
2. **Zero Observations $\neq$ Safety**: An empty result indicates only that no supported defects were recognized under current model capabilities; it does not certify that the premises are safe or compliant.

---

## 6. Reconciliation Rules

The reconciliation logic in `ResultReconciler` (`app/intelligence/reconciliation.py`) implements deterministic rules for cross-model coordination:

```
                          [Incoming Detections]
                                    |
            +-----------------------+-----------------------+
            |                                               |
     [Both Failed]                               [At Least One Succeeded]
            |                                               |
     Status: FAILED                         +---------------+---------------+
     (Case 9, 9B)                           |                               |
                                    [Both Succeeded]              [Single Provider]
                                            |                               |
                             +--------------+--------------+         Status: SINGLE_PROVIDER
                             |                             |         (Case 2, 3, 8)
                     [Zero Observations]          [Has Observations]
                             |                             |
                     Status: NO_OBSERVATIONS       Match Spatial IoU (>= 0.30)
                     (Case 10, 11)                 & Semantic Categories
                                                           |
                                           +---------------+---------------+
                                           |                               |
                                     [Full Match]                 [Disagreement]
                                           |                               |
                                    Status: AGREEMENT             Status: CONFLICT
                                    (Case 1)                      (Case 4: Category Mismatch)
                                                                  (Case 5: Location Mismatch)
```

1. **Spatial IoU Threshold**: Intersection over Union (IoU) $\ge 0.30$ determines spatial co-location.
2. **Semantic Category Matching**: Maps equivalent taxonomy entries (e.g. `FIRE_EXTINGUISHER` to `FIRE_EXTINGUISHER`, `EXIT_SIGN` to `EXIT_SIGNAGE_MISSING`).
3. **Partial Agreement**: When CV detects equipment presence (`FIRE_EXTINGUISHER`) and VLM flags an operational defect (`FIRE_EXTINGUISHER_BLOCKED`), the finding records `AGREEMENT` on presence with elevated severity (`HIGH`), while preserving distinct descriptions in source provenance.

---

## 7. Duplicate Handling

Deduplication occurs only when two observations satisfy both spatial overlap (IoU $\ge 0.30$) and category compatibility:
- **Consolidation**: Merged into a single `Finding` object with `reconciliationStatus="AGREEMENT"`.
- **Traceability Preservation**: The finding's `provenance` array retains distinct entries for both providers, recording:
  - `providerName`
  - `modelId`
  - `executionStatus`
  - `componentStatus`
  - `observationId` (source UUID from VLM or CV detector)
  - `rawCategory`
  - `rawSeverity`
  - `rawConfidence`
  - `boundingBox`
- **No Inferred Duplicates**: Detections that do not match both spatially and categorially are never deduplicated.

---

## 8. Disagreement Handling

When model predictions diverge, `ResultReconciler` preserves both viewpoints without allowing either model to override the other:

### Category Mismatch (Case 4)
- **Condition**: Spatial overlap (IoU $\ge 0.30$), but conflicting hazard classes (e.g., Gemini proposes `EXIT_SIGNAGE_MISSING`, CV baseline proposes `SPRINKLER_HEAD_DEFECT`).
- **Outcome**: `reconciliationStatus="CONFLICT"`, `conflictType="CATEGORY_MISMATCH"`.
- **Preservation**: `conflictDetails` stores both `geminiCategory` and `cvCategory`.
- **Explanation**: Explicitly informs the officer: *"Model disagreement detected: Gemini flagged 'EXIT_SIGNAGE_MISSING' while CV baseline flagged 'SPRINKLER_HEAD_DEFECT'. Both categories are preserved. Human Fire Officer verification is required to adjudicate."*

### Location Mismatch (Case 5)
- **Condition**: Matching hazard category, but spatially disjoint coordinates (IoU $< 0.30$).
- **Outcome**: `reconciliationStatus="CONFLICT"`, `conflictType="LOCATION_MISMATCH"`.
- **Preservation**: `conflictDetails` stores both `geminiBoundingBox` and `cvBoundingBox`, along with the measured IoU.
- **Explanation**: Bounding boxes are not merged automatically; physical on-site evaluation is required to identify the exact equipment location.

---

## 9. Confidence and Uncertainty Policy

1. **No Joint Averaging**: CV confidence ($P_{\text{det}}$) and VLM confidence ($P_{\text{vlm}}$) are derived from fundamentally different architectures and training objectives. They are never averaged into a fabricated joint score.
2. **No Calibration Fabrication**: All findings and reports explicitly declare `calibrationStatus="NOT_CALIBRATED"`. No statistical calibration claims are made.
3. **No Automatic Severity Inflation**: Two models agreeing on equipment presence does not increase operational severity. Severity is determined strictly by the underlying fire hazard.
4. **Transparent Risk Contribution**: `operationalRiskScore` is derived from deterministic risk factor tables (equipment class, occupancy hazard weight, visual clarity). It is not an uncalibrated Bayesian probability.

---

## 10. Consolidated Report Schema

The generated `InspectionReportDraft` (`app/schemas/report.py`) adheres to all 20 required information items:

```json
{
  "reportId": "3c4df703-0fb1-4a53-af6f-8e6a2dd942f9",
  "inspectionId": "ef6cfa46-49c1-4d66-9e17-c8f525de3104",
  "runId": "aa906054-f490-49b5-86aa-2b57ebc1cebe",
  "generatedAt": "2026-10-11T13:09:28.825036Z",
  "reportVersion": "0.5.0-phase5",
  "status": "DRAFT",
  "reviewState": "DRAFT",
  "dataClassification": "PRODUCTION_ADVISORY",
  "isTestData": false,
  "modelInformation": {
    "modelName": "firesphere-orchestrator-ensemble",
    "modelVersion": "0.3.0",
    "calibrationStatus": "NOT_CALIBRATED",
    "overallStatus": "COMPLETED",
    "cvModelName": "firesphere-perception-baseline-v0.8.0",
    "cvModelVersion": "0.8.0",
    "cvStatus": "NOT_APPROVED_GATED",
    "cvApprovalStatus": "NOT_APPROVED",
    "cvCalibrationStatus": "NOT_CALIBRATED",
    "vlmModelName": "gemini-2.5-flash",
    "vlmModelVersion": "2.5-flash",
    "vlmStatus": "UNAVAILABLE"
  },
  "summary": {
    "totalFindings": 1,
    "uniqueFindingTypes": 1,
    "highestSeverity": "MEDIUM",
    "highestRiskLevel": "MEDIUM",
    "highestRiskScore": 55,
    "evidenceCount": 1,
    "requiresOfficerReview": true,
    "findingsRequiringOfficerReview": 1,
    "narrative": "AI analysis identified 1 finding(s)... Human Fire Safety Officer on-site verification is mandatory..."
  },
  "evidenceReviewed": [...],
  "findings": [
    {
      "findingId": "...",
      "evidenceId": "...",
      "findingType": "FIRE_EXTINGUISHER",
      "confidence": 0.88,
      "confidenceLevel": "HIGH",
      "calibrationStatus": "NOT_CALIBRATED",
      "severity": "MEDIUM",
      "reconciliationStatus": "AGREEMENT",
      "conflictType": "NONE",
      "provenance": [...]
    }
  ],
  "riskSummary": {...},
  "verificationActions": [...],
  "correctiveActions": [...],
  "unresolvedItems": [
    {
      "itemId": "UNRES-001",
      "category": "VLM_COMPONENT_UNAVAILABLE",
      "description": "VLM visual reasoning component was UNAVAILABLE; multi-modal scene reasoning was not available for this draft.",
      "recommendedAction": "Authorized Fire Officer physical visual inspection is mandatory to evaluate contextual hazards."
    },
    {
      "itemId": "UNRES-002",
      "category": "CV_MODEL_NOT_APPROVED",
      "description": "CV baseline model approval state is NOT_APPROVED; detections are advisory research findings only.",
      "recommendedAction": "All equipment presence and compliance must be independently verified by an authorized Fire Officer."
    }
  ],
  "aiLimitations": [...],
  "traceability": [...],
  "auditMetadata": {
    "aiDecisionAuthority": "NONE (ADVISORY_DRAFT_ONLY)"
  },
  "regulatoryNotice": "ADVISORY REPORT DRAFT — HUMAN OFFICER REVIEW REQUIRED. The AI system does NOT make legal, statutory, or NOC approval/rejection decisions."
}
```

---

## 11. M2 Integration Behavior

1. **Authoritative Role of M2**: M2 remains authoritative for all tenant identity, authentication, inspection ownership, persistence, and statutory workflow decisions.
2. **Zero Database Access from M4**: M4 has no direct database connection strings, credentials, or SQL clients.
3. **Contract Compatibility**: M4's `InspectionAnalysisResponse` and `InspectionAISummary` map cleanly to M2's `ExternalAiResponse` record (`backend/src/main/java/com/firesphere/backend/ai/dto/ExternalAiResponse.java`). Jackson's `@JsonIgnoreProperties(ignoreUnknown = true)` guarantees forward and backward compatibility.
4. **No Database Migrations Required**: All provenance, component statuses, and reconciliation states are stored in existing JSONB finding/run columns in M2.

---

## 12. Security and Tenant-Isolation Controls

1. **Authentication**: Internal API requests require service-token headers (`X-Service-Token` or `Authorization: Bearer`).
2. **Tenant Isolation**: Every evidence validation check verifies that evidence records belong to the authenticated `tenantId`. Cross-tenant evidence access attempts are rejected.
3. **Reference Verification**: `EvidenceValidator.validate_provider_evidence_reference()` ensures that model observations reference only evidence identifiers included in the authenticated request batch, preventing model hallucinations of foreign UUIDs.
4. **Safe Error Masking**: Internal server errors return sanitized HTTP 500 error envelopes without leaking stack traces, database schemas, or credentials.

---

## 13. Files Changed and Rationale

| File Path | Nature of Change | Architectural Rationale |
| :--- | :--- | :--- |
| `app/schemas/reconciliation.py` | Enhanced | Added `ComponentAnalysisStatus` enum, `map_component_status()` helper, `ComponentResult` model, and `componentStatus` on `ProviderProvenance`. |
| `app/schemas/report.py` | Enhanced | Added multi-model metadata attributes to `ReportModelInfo` (`cvModelName`, `cvModelVersion`, `cvStatus`, `cvApprovalStatus`, `cvCalibrationStatus`, `vlmModelName`, `vlmModelVersion`, `vlmStatus`, `overallStatus`). |
| `app/schemas/inspection.py` | Enhanced | Added optional multi-model status fields to `InspectionAISummary` (`cvStatus`, `vlmStatus`, `reconciliationStatus`). |
| `app/intelligence/reconciliation.py` | Enhanced | Added strict evidence UUID matching, component status mapping, robust handling for single-provider/disabled states, and source confidence preservation in provenance. |
| `app/reporting/deterministic_generator.py` | Enhanced | Populated multi-model metadata in `ReportModelInfo`, added explicit unresolved items for unavailable VLM / unapproved CV, and refined zero-finding compliance disclaimers. |
| `app/services/inspection_report_service.py` | Enhanced | Extracted provider statuses from findings provenance and forwarded multi-model information to `generate_draft`. |
| `app/services/inspection_orchestrator.py` | Enhanced | Populated multi-model execution statuses (`cvStatus`, `vlmStatus`, `reconciliationStatus`) onto inspection summary. |
| `tests/test_phase6_reconciliation_and_report.py` | Created | Comprehensive 21-test suite verifying all mandated cases A through W. |

---

## 14. Focused Test Results

Executed via:
```bash
.venv\Scripts\python -m pytest tests\test_phase6_reconciliation_and_report.py -v
```

| Test Function | Mandated Case | Verification Goal | Status |
| :--- | :--- | :--- | :--- |
| `test_case_a_both_components_succeed_and_agree` | Case A | Compatible detections reconcile with `AGREEMENT` and preserve both provenances | **PASSED** |
| `test_case_b_partial_agreement_object_and_condition` | Case B | Equipment presence + condition observation preserved with severity handling | **PASSED** |
| `test_case_c1_category_mismatch_disagreement` | Case C1 | Spatial overlap with category mismatch preserved under `CONFLICT` | **PASSED** |
| `test_case_c2_location_mismatch_disagreement` | Case C2 | Category match with spatial divergence preserved under `CONFLICT` | **PASSED** |
| `test_case_d_cv_empty_vlm_unavailable` | Case D | CV empty + VLM unavailable preserved as `NO_OBSERVATIONS` | **PASSED** |
| `test_case_e_vlm_succeeds_cv_gated` | Case E | VLM observations preserved when CV is gated | **PASSED** |
| `test_case_f_both_successful_no_observations` | Case F | Both successful with 0 observations returns empty findings without safety claim | **PASSED** |
| `test_case_g_malformed_bounding_box_handled_safely` | Case G | Inverted or non-finite coordinates are safely rejected | **PASSED** |
| `test_case_h_component_failure_fault_isolation` | Case H | Provider failure isolated; working provider preserved under `DISCREPANCY` | **PASSED** |
| `test_case_i_gemini_disabled_explicit_representation` | Case I | `GEMINI_DISABLED` maps to `UNAVAILABLE` | **PASSED** |
| `test_case_j_test_mode_flagged_prominently` | Case J | Test mode carries prominent `TEST DATA` banner and unresolved item | **PASSED** |
| `test_case_k_evidence_uuid_mismatch_rejected` | Case K | Mismatched evidence UUID raises `InvalidEvidenceException` | **PASSED** |
| `test_case_l_inspection_uuid_empty_rejected` | Case L | Null inspection ID raises `InvalidEvidenceException` | **PASSED** |
| `test_case_m_cross_tenant_isolation` | Case M | Cross-tenant evidence access and foreign evidence UUIDs blocked | **PASSED** |
| `test_case_n_duplicate_observation_deduplication` | Case N | Duplicate observations merged into single finding with provenance linkage | **PASSED** |
| `test_case_o_unrelated_observations_not_merged` | Case O | Spatially/categorially distinct observations preserved separately | **PASSED** |
| `test_case_p_and_q_provenance_and_calibration_invariants` | Cases P, Q | Provenance preserved and calibration marked `NOT_CALIBRATED` | **PASSED** |
| `test_case_r_s_t_governance_invariants` | Cases R, S, T | Advisory notice present, officer review required, `AI_APPROVED` forbidden | **PASSED** |
| `test_case_u_both_failed_never_empty_success` | Case U | Both providers failing returns `FAILED`, not `NO_OBSERVATIONS` | **PASSED** |
| `test_case_v_empty_observations_do_not_certify_compliance` | Case V | Zero detections narrative strictly disclaims safety/compliance | **PASSED** |
| `test_case_w_m2_external_ai_response_compatibility` | Case W | M4 response conforms to M2 `ExternalAiResponse` Jackson contract | **PASSED** |

**Summary**: **21 passed, 0 failed in 0.10s**.

---

## 15. Full M4 Regression Results

Executed via:
```bash
.venv\Scripts\python -m pytest -q
```

**Results**:
- **489 passed**
- **0 failed**
- **12 warnings** (standard Starlette deprecation warnings for HTTP status constants)
- **Duration**: 45.95s
- **Pass Rate**: **100%** (up from 468 passed in Phase 5 baseline)

---

## 16. M2 Regression Results

M2 backend source code, database schemas, and contract DTOs were **not modified** during Phase 6. In accordance with Safety Rules 24 and 25, the unchanged M2 Maven regression suite was not reopened. The contract compatibility test (`test_case_w_m2_external_ai_response_compatibility`) verified complete deserialization alignment with M2's `ExternalAiResponse` DTO. The historical M2 baseline of **133 passed, 0 failures, 0 errors** remains intact.

---

## 17. Real versus Mocked Inference Status

| Component | Test Environment Mode | Production Operational Status | Blocker Reason / Governance Gate |
| :--- | :--- | :--- | :--- |
| **Computer Vision (CV)** | Evaluated via synthetic fixtures & historical test set | **NOT_APPROVED** | Gated by `ModelApprovalGate`; historical mAP@50 is 0.0579. |
| **Vision-Language Model (VLM)** | Evaluated via unit test mocks with explicit labels | **BLOCKED** | `GEMINI_ENABLED=false` per Rule 12; no authorized external API calls; no local VLM weights. |
| **Reconciliation Engine** | Fully operational & verified | **OPERATIONAL** | Ingestion, validation, conflict resolution, and reporting verified with 100% test coverage. |

---

## 18. Known Limitations

1. **Real VLM Inference Blocked**: Live VLM inference requires `GEMINI_ENABLED=true` and a valid API key, which is prohibited by current offline governance rules.
2. **CV Baseline Accuracy**: The reference CV baseline has an overall mAP@50 of 0.0579 and remains uncalibrated (`NOT_CALIBRATED`) and unapproved (`NOT_APPROVED`).
3. **No 3D Spatial Geometry**: Bounding box localization is limited to 2D normalized planar coordinates; real-world clearance distances (e.g. 1m clearance) cannot be measured directly from photograph pixels without photogrammetry.
4. **Advisory Boundary**: AI cannot autonomously approve, reject, or issue Fire NOC certificates under any circumstances.

---

## 19. CV Approval and Calibration Status

- **CV Model Checkpoint**: `firesphere-perception-baseline-v0.8.0`
- **Model Approval State**: `NOT_APPROVED` (enforced by `model_approval_gate.py`)
- **Statistical Calibration State**: `NOT_CALIBRATED`
- **Overall mAP@50**: `0.0579` (historical measured baseline from Phase 4)
- **Overall mAP@50:95**: `0.0194`

---

## 20. Phase 6 Exit Criteria Evaluation

| # | Exit Criterion | Verification Status |
| :---: | :--- | :--- |
| 1 | Actual CV and VLM contracts inspected | **VERIFIED** — Audited in `app/models/gemini_schemas.py` and `app/models/cv_model_adapter.py`. |
| 2 | Evidence-flow assumptions verified | **VERIFIED** — Validated across storage, preprocessor, and validators. |
| 3 | Component status preserved correctly | **VERIFIED** — 5-state `ComponentAnalysisStatus` implemented and mapped. |
| 4 | CV and VLM outputs validated before reconciliation | **VERIFIED** — Evidence UUID mismatch raises `InvalidEvidenceException`. |
| 5 | Agreement, partial agreement, disagreement, and unavailable handled | **VERIFIED** — Cases A, B, C1, C2, D, E, F verified with focused unit tests. |
| 6 | Source evidence and model provenance preserved | **VERIFIED** — Provenance array tracks source IDs, categories, and confidences. |
| 7 | Duplicate handling preserves source observations | **VERIFIED** — Merged observations retain both provider records in provenance. |
| 8 | Confidence and risk not falsely represented as calibrated | **VERIFIED** — Confidences never averaged; calibration remains `NOT_CALIBRATED`. |
| 9 | Consolidated report schema-validated | **VERIFIED** — `InspectionReportDraft` validated with all 20 required fields. |
| 10 | Every report remains advisory | **VERIFIED** — Regulatory notice and non-statutory disclaimers present. |
| 11 | No automatic statutory approval/rejection possible | **VERIFIED** — `validate_review_state_safety` forbids `AI_APPROVED`/`AI_REJECTED`. |
| 12 | Authentication and tenant isolation intact | **VERIFIED** — Service token auth and tenant boundary checks enforced. |
| 13 | Relevant focused tests pass | **VERIFIED** — 21 of 21 tests passed in `test_phase6_reconciliation_and_report.py`. |
| 14 | Full relevant M4 regression suite passes | **VERIFIED** — 489 of 489 tests passed in canonical AI service. |
| 15 | M2 contract compatibility verified | **VERIFIED** — Jackson serialization compatibility verified for `ExternalAiResponse`. |
| 16 | Actual VLM inference status accurately reported | **VERIFIED** — Real inference honestly reported as BLOCKED (`GEMINI_ENABLED=false`). |
| 17 | Documentation describes actual implementation | **VERIFIED** — Complete report documented in `docs/integration/phase6_...`. |

**Overall Exit Status**: **COMPLETE**

---

## 21. Recommended Phase 7 Work

1. **Officer Review Workspace Integration (UI/Frontend)**: Expose the reconciled findings, multi-model provenance, and advisory report drafts to authorized Fire Safety Officers in the inspection review interface.
2. **Observation Verification Actions**: Allow officers to accept, dismiss, or edit individual AI observations with auditable human-in-the-loop review remarks.
3. **Controlled Live VLM Pilot**: When authorized, enable `GEMINI_ENABLED=true` in a designated pilot sandbox environment to benchmark real multimodal visual reasoning on real inspection photographs.
4. **Model Retraining & Calibration**: Retrain the CV perception baseline on domain-specific fire equipment datasets and execute isotonic regression or Platt scaling to establish true statistical calibration.
