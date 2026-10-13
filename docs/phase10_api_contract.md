# FireSphere.ai — Phase 10: Spring Boot & Client API Contract

**Document Version:** `2.0.0-phase10`  
**Target Consumers:**  
- Member 1 & Member 2: Spring Boot Backend & Data Engineers  
- Member 3: Frontend & Mobile Application Developers  
**Service Version:** `0.10.0-phase10`  
**Base Path:** `/internal/ai`  

---

## 1. Authentication & Security Headers

All requests from the Spring Boot backend or authorized clients to the AI service must supply valid service authentication headers. Constant-time digest comparison (`hmac.compare_digest`) prevents timing attacks.

### Supported Headers:
* `X-AI-Service-Token: <AI_SERVICE_AUTH_TOKEN>`
* `Authorization: Bearer <AI_SERVICE_AUTH_TOKEN>`
* `X-Correlation-ID: <UUID>` (Optional, propagated across all logs and job records)

---

## 2. API Endpoint Specification

### 2.1 Enqueue Asynchronous AI Job

* **Method:** `POST`
* **Path:** `/internal/ai/jobs`
* **Status:** `202 Accepted`
* **Description:** Enqueues an inspection for asynchronous CV detection and report drafting. Supports client-supplied `idempotencyKey` to prevent duplicate processing.

#### Request Body Schema:
```json
{
  "inspectionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "evidenceIds": [
    "c8a14b30-e67d-411a-8e2b-10f8a846c921",
    "9d5e3f12-b13c-4d56-8a90-332145e6f7a8"
  ],
  "operation": "INSPECTION_ANALYSIS_AND_REPORT",
  "tenantId": "mumbai-fire-zone-04",
  "occupancyContext": "COMMERCIAL",
  "historicalContext": "PAST_VIOLATIONS",
  "officerRemarks": "Inspecting basement fire pumps and emergency escape corridor.",
  "checklistData": {
    "electrical_panel_covered": true,
    "clearance_maintained": false
  },
  "idempotencyKey": "insp-3fa85f64-ev-v1-analysis",
  "testMode": false
}
```

#### Response Body (`202 Accepted`):
```json
{
  "jobId": "b1a23c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "inspectionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "tenantId": "mumbai-fire-zone-04",
  "status": "QUEUED",
  "operation": "INSPECTION_ANALYSIS_AND_REPORT",
  "attempt": 1,
  "maxAttempts": 3,
  "idempotencyKey": "insp-3fa85f64-ev-v1-analysis",
  "correlationId": "7a8c3d52-11ee-48bb-b892-9382103fca91",
  "modelName": "firesphere-perception-baseline-v0.8.0",
  "modelVersion": "0.8.0",
  "serviceVersion": "0.10.0-phase10",
  "createdAt": "2026-10-09T18:15:30.123456Z",
  "startedAt": null,
  "completedAt": null,
  "analysisRunId": null,
  "reportId": null,
  "errorCode": null,
  "errorMessage": null
}
```

---

### 2.2 Poll AI Job Status

* **Method:** `GET`
* **Path:** `/internal/ai/jobs/{job_id}`
* **Status:** `200 OK` (or `404 Not Found`)
* **Description:** Retrieves the execution state, attempt count, and linked IDs.

#### Response Body (`200 OK`):
```json
{
  "jobId": "b1a23c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "inspectionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "tenantId": "mumbai-fire-zone-04",
  "status": "COMPLETED",
  "operation": "INSPECTION_ANALYSIS_AND_REPORT",
  "attempt": 1,
  "maxAttempts": 3,
  "idempotencyKey": "insp-3fa85f64-ev-v1-analysis",
  "correlationId": "7a8c3d52-11ee-48bb-b892-9382103fca91",
  "modelName": "firesphere-perception-baseline-v0.8.0",
  "modelVersion": "0.8.0",
  "serviceVersion": "0.10.0-phase10",
  "createdAt": "2026-10-09T18:15:30.123456Z",
  "startedAt": "2026-10-09T18:15:30.201456Z",
  "completedAt": "2026-10-09T18:15:30.345678Z",
  "analysisRunId": "9b8a7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
  "reportId": "4f5e6d7c-8b9a-0e1f-2a3b-4c5d6e7f8a9b",
  "errorCode": null,
  "errorMessage": null
}
```

---

### 2.3 Retrieve Complete AI Job Result

* **Method:** `GET`
* **Path:** `/internal/ai/jobs/{job_id}/result`
* **Status:** `200 OK`
* **Description:** Retrieves the full structured findings and deterministic report draft.

#### Response Body Structure:
```json
{
  "jobId": "b1a23c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "inspectionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "tenantId": "mumbai-fire-zone-04",
  "status": "COMPLETED",
  "analysisRunId": "9b8a7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
  "reportId": "4f5e6d7c-8b9a-0e1f-2a3b-4c5d6e7f8a9b",
  "createdAt": "2026-10-09T18:15:30.123456Z",
  "completedAt": "2026-10-09T18:15:30.345678Z",
  "analysis": {
    "runId": "9b8a7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
    "status": "COMPLETED",
    "findings": [
      {
        "findingId": "e1f2a3b4-5c6d-7e8f-9a0b-1c2d3e4f5a6b",
        "evidenceId": "c8a14b30-e67d-411a-8e2b-10f8a846c921",
        "tenantId": "mumbai-fire-zone-04",
        "inspectionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "analysisRunId": "9b8a7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
        "perceptionClass": "FIRE_EXTINGUISHER",
        "findingType": "FIRE_EXTINGUISHER_MISSING",
        "confidence": 0.88,
        "confidenceLevel": "HIGH",
        "calibrationStatus": "NOT_CALIBRATED",
        "severity": "HIGH",
        "operationalRiskScore": 58,
        "operationalRiskLevel": "MODERATE_RISK",
        "explanation": "AI analysis identified a possible vacant or absent fire extinguisher at a designated station.",
        "verificationGuidance": [
            "Confirm visual presence of extinguisher cylinder",
            "Verify inspection tag maintenance date",
            "Check pressure gauge indicator"
        ],
        "knownLimitations": [
            "Advisory visual detection only; does not evaluate hydrostatic test date stamp",
            "Subject to false positives on red cylinders; requires on-site officer verification"
        ],
        "complianceStatus": "REQUIRES_OFFICER_VERIFICATION",
        "officerVerificationStatus": "UNREVIEWED",
        "modelApprovalStatus": "NOT_APPROVED",
        "boundingBox": {
            "xMin": 0.12,
            "yMin": 0.35,
            "xMax": 0.52,
            "yMax": 0.90
        },
        "isTestData": false,
        "createdAt": "2026-10-09T18:15:30.250123Z"
      }
    ],
    "model": "firesphere-perception-baseline-v0.8.0",
    "modelVersion": "0.8.0",
    "executionDurationMs": 144.2
  },
  "report": {
    "reportId": "4f5e6d7c-8b9a-0e1f-2a3b-4c5d6e7f8a9b",
    "inspectionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "analysisRunId": "9b8a7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
    "status": "DRAFT",
    "reviewState": "DRAFT",
    "executiveSummary": {
      "totalFindings": 1,
      "requiresOfficerReview": true,
      "findingsRequiringOfficerReview": 1,
      "narrative": "AI analysis generated an advisory inspection draft. All findings require physical on-site human Fire Safety Officer verification."
    }
  },
  "errorCode": null,
  "errorMessage": null
}
```

---

### 2.4 Synchronous Inspection Analysis

* **Method:** `POST`
* **Path:** `/internal/ai/inspection-analysis`
* **Status:** `200 OK`
* **Description:** Directly executes the computer vision pipeline on evidence items.

#### Request Body Schema:
```json
{
  "inspectionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "evidenceIds": ["c8a14b30-e67d-411a-8e2b-10f8a846c921"],
  "testMode": false,
  "tenantId": "mumbai-fire-zone-04",
  "occupancyContext": "COMMERCIAL",
  "historicalContext": "PAST_VIOLATIONS"
}
```

#### Response Body Schema:
```json
{
  "runId": "4fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "COMPLETED",
  "findings": [],
  "summary": {
    "totalFindings": 0,
    "uniqueFindingTypes": 0,
    "evidenceCount": 1,
    "requiresOfficerReview": false,
    "calibrationStatus": "NOT_CALIBRATED",
    "complianceDecisionNote": "All AI findings are advisory visual observations. The AI system does NOT approve or reject Fire NOC applications. Authorized Fire Officer on-site verification is required."
  },
  "model": "firesphere-perception-baseline-v0.8.0",
  "modelVersion": "0.8.0",
  "executionDurationMs": 42.1,
  "tenantId": "mumbai-fire-zone-04",
  "modelApprovalStatus": "NOT_APPROVED",
  "calibrationStatus": "NOT_CALIBRATED"
}
```

---

### 2.5 Health and Readiness Checks

* `GET /health` (`200 OK`):
  ```json
  {
    "status": "HEALTHY",
    "service": "firesphere-ai-service",
    "version": "0.8.0",
    "environment": "development",
    "timestamp": "2026-10-09T18:15:30.123456Z"
  }
  ```
* `GET /ready` (`200 OK`):
  ```json
  {
    "status": "READY",
    "service": "firesphere-ai-service",
    "version": "0.8.0",
    "components": {
      "modelRunner": "READY",
      "evidenceStorage": "READY",
      "preprocessor": "READY"
    },
    "timestamp": "2026-10-09T18:15:30.123456Z"
  }
  ```
* `GET /metrics`: Prometheus text-format scrape endpoint.
