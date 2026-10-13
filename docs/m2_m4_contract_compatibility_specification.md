# FireSphere.ai — Member 2 ↔ Member 4 API Contract Compatibility Specification

**Document ID:** `FS-SPEC-M2-M4-CONTRACT-20261010`  
**Authors:** Member 4 (AI/ML & Contract Engineering)  
**Target Subsystems:**
- Member 2: Spring Boot 3.2.3 Backend Client (`HttpAiAnalysisClient.java`)
- Member 4: FastAPI AI Service (`ai-service`)  
**Status:** **PROPOSED HARMONIZATION SPECIFICATION (AWAITING MEMBER 2 CONFIRMATION)**  

---

## 1. Executive Summary

This specification provides the authoritative side-by-side analysis of the integration boundary between the **Member 2 Spring Boot Backend** and the **Member 4 FastAPI AI Microservice**. 

Automated contract tests executed in Phase 4 ([`tests/test_phase4_evaluation.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/tests/test_phase4_evaluation.py) and [`tests/test_m2_m4_contract_specification.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/tests/test_m2_m4_contract_specification.py)) proved that Member 2's current client implementation cannot communicate with Member 4's service, resulting in immediate **404 Not Found**, **401 Unauthorized**, and **422 Unprocessable Entity** failures.

This document outlines the exact field-by-field differences, identifies the safety reasons why Member 4 requires rich payloads, and provides the minimal non-breaking remediation plan.

---

## 2. Actual Implemented Contracts

### A. Member 2 Backend Client Contract
*Source Code:* [`M2_work/Firesphere m2/Aaditya Morde/backend/src/main/java/com/firesphere/backend/ai/client/HttpAiAnalysisClient.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/Aaditya%20Morde/backend/src/main/java/com/firesphere/backend/ai/client/HttpAiAnalysisClient.java)  
*DTOs:* [`ExternalAiRequest.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/Aaditya%20Morde/backend/src/main/java/com/firesphere/backend/ai/dto/ExternalAiRequest.java), [`ExternalAiResponse.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/Aaditya%20Morde/backend/src/main/java/com/firesphere/backend/ai/dto/ExternalAiResponse.java)

- **HTTP Method & Target:** `POST ${baseUrl}/api/v1/analyze`
- **Request Headers:**
  - `Content-Type: application/json`
  - `X-API-KEY: ${apiKey}`
- **Request DTO (`ExternalAiRequest`):**
  ```json
  {
    "runId": "UUID",
    "applicationId": "UUID",
    "documentReference": "String (path/URL)",
    "analysisType": "String"
  }
  ```
- **Response DTO Expected (`ExternalAiResponse`):**
  ```json
  {
    "status": "String (e.g. SUCCESS, FAILED)",
    "modelName": "String",
    "modelVersion": "String",
    "timestamp": "OffsetDateTime",
    "findings": [
      {
        "label": "String",
        "confidence": 0.85
      }
    ],
    "errorDetail": "String"
  }
  ```

---

### B. Member 4 AI Service Authoritative Contract
*Source Code:* [`ai-service/app/api/v1/inspection.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/api/v1/inspection.py), [`ai-service/app/api/v1/jobs.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/api/v1/jobs.py)  
*Schemas:* [`app/schemas/inspection.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/schemas/inspection.py), [`app/schemas/job.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/schemas/job.py), [`app/schemas/finding.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/schemas/finding.py)

- **Synchronous Endpoint:** `POST /internal/ai/inspection-analysis`
- **Asynchronous Job Endpoint:** `POST /internal/ai/jobs`
- **Request Headers:**
  - `Content-Type: application/json`
  - `X-Service-Token: ${token}` or `Authorization: Bearer ${token}`
  - `X-Tenant-ID: ${tenantId}` (optional)
  - `X-Correlation-ID: ${correlationId}` (optional)
- **Synchronous Request Schema (`InspectionAnalysisRequest`):**
  ```json
  {
    "inspectionId": "UUID (mandatory)",
    "evidenceIds": ["UUID (mandatory, non-empty array)"],
    "testMode": false,
    "occupancyContext": "String (optional, default: UNKNOWN)",
    "historicalContext": "String (optional, default: UNAVAILABLE)",
    "tenantId": "String (optional, default: default)"
  }
  ```
- **Synchronous Response Schema (`InspectionAnalysisResponse`):**
  ```json
  {
    "runId": "UUID",
    "status": "COMPLETED | FAILED | PARTIAL_FAILURE",
    "findings": [
      {
        "findingId": "UUID",
        "evidenceId": "UUID",
        "findingType": "FIRE_EXTINGUISHER_MISSING",
        "confidence": 0.85,
        "confidenceLevel": "HIGH",
        "calibrationStatus": "NOT_CALIBRATED",
        "severity": "HIGH",
        "severityReason": "Safety perimeter requirement",
        "operationalRiskScore": 75,
        "explanation": "Extinguisher absent from bracket",
        "verificationGuidance": ["Physically inspect bracket"],
        "evidenceQuality": "GOOD",
        "complianceStatus": "REQUIRES_OFFICER_VERIFICATION",
        "boundingBox": {"xMin": 0.1, "yMin": 0.2, "xMax": 0.4, "yMax": 0.6},
        "model": "firesphere-inspection-cv",
        "modelVersion": "0.3.0-phase3",
        "isTestData": false,
        "version": 1
      }
    ],
    "summary": {
      "totalFindings": 1,
      "highestSeverity": "HIGH",
      "requiresOfficerReview": true,
      "complianceDecisionNote": "All AI findings are advisory visual observations..."
    },
    "model": "firesphere-inspection-cv",
    "modelVersion": "0.3.0-phase3",
    "modelApprovalStatus": "NOT_APPROVED",
    "calibrationStatus": "NOT_CALIBRATED"
  }
  ```

---

## 3. Side-by-Side Field Differences & Incompatibilities

| Contract Dimension | Member 2 Client | Member 4 Service | Incompatibility Reason | Severity |
| :--- | :--- | :--- | :--- | :---: |
| **Endpoint Path** | `POST /api/v1/analyze` | `POST /internal/ai/inspection-analysis` | 404 Not Found on M4 service. M4 isolates internal endpoints under `/internal/ai`. | **P0** |
| **Auth Header** | `X-API-KEY` | `X-Service-Token` (or `Bearer`) | 401 Unauthorized on M4 when `AI_AUTH_ENABLED=true`. | **P0** |
| **Inspection Identifier** | `applicationId` (UUID) | `inspectionId` (UUID) | M2 lacks `inspectionId` in DTO; M4 schema strictly requires `inspectionId`. | **P0** |
| **Evidence Identifier** | `documentReference` (String) | `evidenceIds` (List[UUID]) | M2 sends a single path string; M4 requires a list of evidence UUIDs. | **P0** |
| **Run Identifier** | M2 generates `runId` | M4 returns newly generated `runId` | Conceptual conflict: M2 passes `runId` into request; M4 creates runId server-side. | **P1** |
| **Safety Mode Flag** | Missing (`analysisType` string) | `testMode: bool` | M4 fails closed (`NOT_APPROVED`) in prod mode unless `testMode=true` is passed during dev/test. | **P0** |
| **Finding Schema** | Flat `{label, confidence}` | Rich structured finding schema | M2 discards bounding boxes, severity, compliance status, and officer review requirements. | **P0** |
| **Error Format** | Generic string `errorDetail` | RFC 7807 structured JSON (`status`, `errorCode`, `validationErrors`) | M2 cannot parse structured error details from M4. | **P1** |

---

## 4. Technical Rationale for Member 4's Rich Contract

Member 4 cannot simply downgrade its contract to Member 2's flat `{label, confidence}` schema due to statutory and technical requirements:
1. **Fire Officer Explainability & Verification:** Indian Fire Service standards require officers to verify *which* evidence photograph and *which coordinates* on the wall exhibited the non-compliance. Flat labels without `boundingBox` and `evidenceId` cannot be displayed or verified in Member 1's UI.
2. **AI Safety & Statutory Governance:** AI cannot autonomously certify compliance. Findings must carry `complianceStatus: REQUIRES_OFFICER_VERIFICATION` and `severity`.
3. **Multi-Evidence Inspections:** Commercial premises involve 10 to 50 photos per inspection. A batch `evidenceIds: List[UUID]` is necessary to support atomic inspection analysis without firing dozens of isolated HTTP requests.

---

## 5. Recommended Resolution (Option A vs Option B)

### Option A (Recommended): Update Member 2 Backend Client
**Rationale:** Keeps the architecture clean, preserves rich inspection metadata, and aligns with the internal service convention (`/internal/ai`).

**Member 2 Changes:**
1. Update [`HttpAiAnalysisClient.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/Aaditya%20Morde/backend/src/main/java/com/firesphere/backend/ai/client/HttpAiAnalysisClient.java):
   - Target `baseUrl + "/internal/ai/inspection-analysis"` (or `/internal/ai/jobs` for async).
   - Set header `headers.set("X-Service-Token", apiKey)`.
2. Update [`ExternalAiRequest.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/Aaditya%20Morde/backend/src/main/java/com/firesphere/backend/ai/dto/ExternalAiRequest.java):
   ```java
   public record ExternalAiRequest(
           UUID inspectionId,
           List<UUID> evidenceIds,
           Boolean testMode,
           String occupancyContext,
           String tenantId
   ) {}
   ```
3. Update [`ExternalAiResponse.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/Aaditya%20Morde/backend/src/main/java/com/firesphere/backend/ai/dto/ExternalAiResponse.java) to ingest rich `Finding` objects (`findingId`, `findingType`, `severity`, `confidence`, `boundingBox`).

### Option B: Member 4 Implements Compatibility Adapter Route
**Rationale:** If Member 2's backend code is frozen, Member 4 can expose a dedicated adapter route `POST /api/v1/analyze` that accepts M2's DTO, maps `documentReference` to an `evidenceId`, inspects `X-API-KEY`, and downsamples findings into flat `{label, confidence}` records.
- **Drawback:** Discards spatial bounding boxes, audit trails, and officer review workflows required by Member 1's frontend.

---

## 6. Contract Verification Tests

The following contract tests are checked in and passing in the AI service test suite:
- [`tests/test_phase4_evaluation.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/tests/test_phase4_evaluation.py):
  - `test_m2_backend_endpoint_path_mismatch`: Asserts M2's path returns 404 on current M4 service.
  - `test_m2_backend_dto_payload_mismatch`: Asserts M2's DTO fails validation with 422.
  - `test_m2_backend_auth_header_mismatch`: Asserts M2's `X-API-KEY` is rejected with 401 when auth is enabled.
- [`tests/test_m2_m4_contract_specification.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/tests/test_m2_m4_contract_specification.py):
  - `test_m4_authoritative_request_schema`: Asserts mandatory `inspectionId` and `evidenceIds`.
  - `test_m4_authoritative_response_schema_completeness`: Asserts `runId`, `findings`, and `summary`.
  - `test_m4_async_job_contract_lifecycle`: Asserts 202 job submission and status polling.
  - `test_m2_finding_dto_lacks_safety_and_spatial_fields`: Asserts all 16 safety fields required by M4.
