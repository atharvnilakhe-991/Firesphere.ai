# FireSphere.ai — Stage 8.2: Reconciliation Contract and Regression Resolution Report

**Document ID:** `FS-DOC-STAGE8-2-REGRESSION-RESOLUTION`  
**Date:** October 13, 2026  
**Auditor:** Independent Safety & Verification Engine (Antigravity AI)  
**Target Workspace:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**AI Service Subsystem:** `ai-service/`  
**Reference Reports:**
- `docs/integration/cv_qwen_unified_finding_contract.md`
- `docs/integration/cv_qwen_stage8_1_independent_verification.md`  
**Evaluation Mode:** Controlled Unit & Integration Verification  
**Final Stage Verdict:** `PASS_VERIFIED`

---

## 1. Executive Summary

In Stage 8.1, independent verification identified three regression failures in the Phase 6 test suite:
1. `test_case_l_inspection_uuid_empty_rejected`: Expected `InvalidEvidenceException` on null inspection ID, but reconciliation silently generated a random UUID via `uuid.uuid4()`.
2. `test_case_c2_location_mismatch_disagreement`: Failed with `assert len(findings) == 1` when the reconciler produced 2 findings.
3. `test_case_h_component_failure_fault_isolation`: Failed with `assert "Gemini provider failed" in conflict.description` when the description was `"Gemini VLM provider failed during analysis; CV baseline preserved."`.

Stage 8.2 investigated all three failures down to source code, domain architecture, caller contracts, and safety invariants. All three issues have been resolved:
- **Failure 1 (Inspection ID):** Identified as an **implementation defect** in `app/intelligence/reconciliation.py`. Silent UUID fabrication has been eliminated. Reconciliation strictly enforces existing inspection identity and raises `InvalidEvidenceException` for `None`, empty strings, whitespace, and malformed UUIDs.
- **Failure 2 (Location Mismatch):** Identified as a **stale test expectation** in `tests/test_phase6_reconciliation_and_report.py`. Stage 8 intentionally preserves both source observations (`len(findings) == 2`) under `ReconciliationStatus.CONFLICT` and `ConflictType.LOCATION_MISMATCH` so that human fire officers can adjudicate spatial disagreements without data loss. The test was updated to verify this safety contract.
- **Failure 3 (Provider Failure Reporting):** Identified as a **stale brittle string assertion** in `tests/test_phase6_reconciliation_and_report.py`. Dynamic provider attribution correctly identifies the provider (`"Gemini VLM"` or `"Ollama/Qwen VLM"`). The test assertion was updated to verify structured provider fault isolation without brittle literal string matching.

Following these resolutions, all 33 legacy Phase 6/Phase 4 tests and all 132 Stage 8/regression tests passed (165 of 165 total, 100% pass rate, exit code 0).

---

## 2. Root Cause Analysis & Resolution

### Task 1: Inspection ID Contract (`test_case_l_inspection_uuid_empty_rejected`)

#### Root Cause
In `app/intelligence/reconciliation.py` (lines 385–391), defensive code introduced during Stage 8 contained:
```python
if not inspection_id:
    inspection_id = kwargs.get("inspectionId") or uuid.uuid4()
```
When a caller explicitly passed `inspection_id=None` or omitted it, this fallback silently synthesized a new random `uuid.uuid4()`. Consequently, downstream validation (`if not inspection_id: raise InvalidEvidenceException(...)`) was unreachable dead code.

#### Caller & Domain Trace
1. **Domain Responsibilities:** The domain model binds all findings to an inspection session via `Finding.inspectionId`. Physical inspections are created and authenticated within the inspection management service / API layer. The AI reconciliation engine has zero authority to create new inspection sessions.
2. **Finding Linkage Risks:** Fabricating a random UUID detaches generated findings from the actual physical inspection in PostgreSQL. An inspecting fire officer viewing the inspection record would never see these orphaned findings.
3. **Production Callers:** `app/services/inspection_orchestrator.py` (`execute_run`) strictly receives and supplies `inspection_id: uuid.UUID` from the caller. No production caller depends on automatic UUID generation.
4. **Test Callers:** Only `tests/test_stage8_unified_contract.py` had omitted `inspection_id` when invoking `reconciler.reconcile`.

#### Code Changes Applied
- **`app/intelligence/reconciliation.py`**:
  - Removed silent `uuid.uuid4()` fabrication for `inspection_id` and `evidence_id`.
  - Implemented strict validation:
    ```python
    raw_iid = inspection_id if inspection_id is not None else kwargs.get("inspectionId")
    if raw_iid is None or (isinstance(raw_iid, str) and not raw_iid.strip()):
        raise InvalidEvidenceException("Inspection identifier cannot be empty or null.")

    if isinstance(raw_iid, str):
        try:
            inspection_id = uuid.UUID(raw_iid.strip())
        except Exception:
            raise InvalidEvidenceException(f"Invalid inspection UUID: '{raw_iid}'")
    elif isinstance(raw_iid, uuid.UUID):
        inspection_id = raw_iid
    else:
        raise InvalidEvidenceException(f"Invalid inspection identifier type: {type(raw_iid).__name__}")
    ```
- **`tests/test_stage8_unified_contract.py`**:
  - Updated all 16 test cases to supply an explicit `inspection_id=iid` where `iid = uuid.uuid4()`.
  - Added Section 24 containing targeted tests for:
    - `None` inspection ID rejection (`InvalidEvidenceException`).
    - Empty and whitespace string inspection ID rejection (`InvalidEvidenceException`).
    - Malformed UUID string rejection (`InvalidEvidenceException`).
    - Valid `uuid.UUID` and valid string UUID acceptance and propagation to `finding.inspectionId`.

---

### Task 2: Location-Mismatch Regression (`test_case_c2_location_mismatch_disagreement`)

#### Root Cause
In Phase 6, on spatial disagreement (IoU = 0.00), the legacy reconciler merged or discarded one model's observation, returning `len(findings) == 1`. The test asserted `assert len(findings) == 1`.

#### Intentional Stage 8 Safety Contract
Stage 8 established an explicit fire-safety rule:
- **No Silent Discarding:** When two models identify the same hazard class at different physical coordinates, neither observation may be discarded autonomously.
- **Dual Observation Preservation:** Both the CV observation (`sourceType="CV"`) and the VLM observation (`sourceType="VLM"`) are preserved in `result.findings` (`len(findings) == 2`).
- **Conflict Marking:** Both findings are assigned `reconciliationStatus = "CONFLICT"` and `conflictType = "LOCATION_MISMATCH"`.
- **Bounding Box Integrity:** Bounding boxes are retained independently (`vlmBoundingBox`/`geminiBoundingBox` and `cvBoundingBox`) in `conflictDetails`.
- **No False Cross-Validation:** Neither finding is marked `isCrossValidated=True`.
- **Officer Review Required:** Discrepancies are held for human fire officer adjudication.

#### Classification & Changes Applied
- **Classification:** Stale test expectation. Stage 8 implementation behavior is correct and non-negotiable for statutory safety.
- **`tests/test_phase6_reconciliation_and_report.py`**:
  - Updated `test_case_c2_location_mismatch_disagreement` to assert:
    ```python
    assert status == ReconciliationStatus.CONFLICT
    assert len(findings) == 2
    for f in findings:
        assert f.reconciliationStatus == ReconciliationStatus.CONFLICT.value
        assert f.conflictType == ConflictType.LOCATION_MISMATCH.value
    vlm_f = next(f for f in findings if f.sourceType == "VLM")
    assert vlm_f.conflictDetails["conflictType"] == "LOCATION_MISMATCH"
    assert "geminiBoundingBox" in vlm_f.conflictDetails or "vlmBoundingBox" in vlm_f.conflictDetails
    assert "cvBoundingBox" in vlm_f.conflictDetails
    cv_f = next(f for f in findings if f.sourceType == "CV")
    assert cv_f.boundingBox is not None
    ```

---

### Task 3: Provider Failure Reporting (`test_case_h_component_failure_fault_isolation`)

#### Root Cause
In Phase 6, the test asserted `assert "Gemini provider failed" in conflict.description`.
In Stage 8, dynamic provider identification was introduced via `_resolve_vlm_identity(vlm_result, vlm_model_info)`. When `gemini-2.5-flash` is tested, `vlm_label` resolves to `"Gemini VLM"`, producing:
`"Gemini VLM provider failed during analysis; CV baseline preserved."`
Because the word `"VLM"` intervened, the literal substring `"Gemini provider failed"` was not matched, despite fault isolation functioning as intended.

#### Provider Isolation Verification
1. **CV Baseline Preservation:** When VLM fails, CV detections are preserved (`len(findings) == 1`, `status = DISCREPANCY`).
2. **VLM Finding Preservation:** When CV fails, VLM observations are preserved (`len(findings) == 1`, `status = DISCREPANCY` / `PARTIAL_FAILURE_CV`).
3. **Correct Provider Attribution:**
   - Gemini failure: `conflict.description` cites `"Gemini VLM provider failed"`.
   - Qwen failure: `conflict.description` cites `"Ollama/Qwen VLM provider failed"`. Gemini is never cited when Qwen fails.
4. **Dual Provider Failure:** When both fail, `status = BOTH_PROVIDERS_FAILED` or `FAILED`, `len(findings) == 0`, and error details from both providers are preserved without silent fallback to hosted Gemini.
5. **No Silent Hosted Fallback:** Fallback from local Ollama to hosted Gemini or OpenAI is strictly prohibited and verified by `test_factory_selects_ollama_without_silent_fallback` and `test_default_provider_is_ollama_not_gemini`.

#### Classification & Changes Applied
- **Classification:** Stale brittle string assertion in legacy test.
- **`tests/test_phase6_reconciliation_and_report.py`**:
  - Updated `test_case_h_component_failure_fault_isolation` to assert:
    ```python
    assert status == ReconciliationStatus.DISCREPANCY
    assert len(findings) == 1
    assert conflict is not None
    assert conflict.conflictType == ConflictType.UNRESOLVED_DISAGREEMENT
    assert "provider failed" in conflict.description.lower()
    assert "gemini" in conflict.description.lower()
    ```

---

## 3. Summary of Files Modified

| File | Change Type | Purpose |
| :--- | :--- | :--- |
| `ai-service/app/intelligence/reconciliation.py` | Code Correction | Removed silent `uuid.uuid4()` fabrication; implemented strict `inspection_id` and `evidence_id` validation raising `InvalidEvidenceException`. |
| `ai-service/tests/test_phase6_reconciliation_and_report.py` | Test Modernization | Updated `test_case_c2` to assert dual observation preservation (`len(findings) == 2`); updated `test_case_h` to assert dynamic provider failure attribution. |
| `ai-service/tests/test_stage8_unified_contract.py` | Test Enhancement | Supplied explicit `inspection_id=iid` across 16 reconcile calls; added Section 24 tests for `None`, empty, malformed, and valid inspection UUIDs. |

---

## 4. Test Verification Evidence

All tests were executed in the project's active virtual environment (`Python 3.13.7`, `pytest 9.1.1`, Windows OS).

### Command 1: Legacy Reconciliation & Evaluation Suites
```powershell
pytest tests/test_phase6_reconciliation_and_report.py tests/test_phase4_evaluation.py
```
- **Exit Code:** `0`
- **Collected:** `33`
- **Passed:** `33`
- **Failed:** `0`
- **Skipped:** `0`
- **Duration:** `0.13s`
- **Key Suites:**
  - `tests/test_phase6_reconciliation_and_report.py`: 21 passed (including Cases C2, H, L)
  - `tests/test_phase4_evaluation.py`: 12 passed

### Command 2: Focused Stage 8 and Regression Suites
```powershell
pytest tests/test_stage8_unified_contract.py tests/test_cv_vlm_integration_corrections.py tests/test_gemini_phase3_orchestration.py tests/test_ollama_provider_phase13.py tests/test_vlm_service_phase5.py tests/test_schemas.py tests/test_compliance_boundary.py
```
- **Exit Code:** `0`
- **Collected:** `132`
- **Passed:** `132`
- **Failed:** `0`
- **Skipped:** `0`
- **Duration:** `2.35s`
- **Key Suites:**
  - `tests/test_stage8_unified_contract.py`: 28 passed (including new Section 24 tests)
  - `tests/test_cv_vlm_integration_corrections.py`: 13 passed
  - `tests/test_gemini_phase3_orchestration.py`: 46 passed
  - `tests/test_ollama_provider_phase13.py`: 19 passed
  - `tests/test_vlm_service_phase5.py`: 17 passed
  - `tests/test_schemas.py`: 5 passed
  - `tests/test_compliance_boundary.py`: 4 passed

### Combined Grand Total
- **Total Tests Executed:** `165`
- **Total Passed:** `165` (100.0%)
- **Total Failed:** `0`
- **Total Skipped:** `0`
- **Database Note:** All evaluations utilized mock fixtures; no live PostgreSQL connection was asserted or required.

---

## 5. Governance & Safety Boundary Audit

The following safety constraints were audited and verified intact:
1. **CV Model Approval Gate:** Remains `NOT_APPROVED` (`is_production_approved() == False`).
2. **Calibration Status:** Remains `NOT_CALIBRATED` across all finding constructors.
3. **Production Inference Authorization:** Remains `false`.
4. **Model Weights & Datasets:** No `.pt`, `.onnx`, YOLO, or COCO files modified.
5. **Stage 6 Sealed Holdout Data:** Untouched and unaccessed.
6. **Hosted API Boundaries:** Zero outbound requests to OpenAI, Google Gemini, or external endpoints.
7. **Git History Integrity:** No git commits, merges, pushes, or branch switches were executed.
8. **Frontend Integrity:** No frontend source files were viewed or modified.

---

## 6. Remaining Risks & Next Steps

1. **Local Ollama Daemon Availability:** In live development mode, Ollama must be running locally (`http://127.0.0.1:11434`) with `qwen2.5vl:3b` pulled. In test mode, all responses are mocked and fail-closed isolation is verified.
2. **Officer Adjudication UI:** Spatial mismatch conflicts now reliably produce two findings with detailed bounding boxes. The frontend checklist UI (Stage 9) must render both bounding boxes clearly for the human officer.

---

## 7. Stage 8.2 Verdict

```
================================================================================
FINAL VERDICT: PASS_VERIFIED
================================================================================
All 3 reported Phase 6 regressions have been resolved with root-cause verification.
Inspection ID strict validation enforced; no silent UUID generation.
Location-mismatch dual observation preservation verified.
Dynamic provider fault isolation verified.
165 of 165 unit and integration tests passing (100%).
All safety, governance, and model approval gates preserved.
Stage 8.2 complete. STOPPED prior to Stage 9.
================================================================================
```
