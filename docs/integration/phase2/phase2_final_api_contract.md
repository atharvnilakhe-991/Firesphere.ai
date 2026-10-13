# FireSphere.ai — Phase 2: Final API Contract Specification
**Document Reference:** `docs/integration/phase2/phase2_final_api_contract.md`  
**Status:** IMPLEMENTED & APPROVED (Local Contract Baseline)  
**Authors:** Senior Backend Integration Engineer, Python FastAPI Engineer, Java Spring Boot Engineer, QA Engineer  
**Date:** October 2026  

---

## 1. Executive Summary

This specification establishes the authoritative, production-grade service-to-service communication contract between Member 2's Spring Boot Backend and Member 4's FastAPI AI Service. Based on the Phase 1 integration audit, all previously identified architectural mismatches—including endpoint path divergence, conflicting authentication headers, identifier semantics, and finding serialization structures—have been formally reconciled and aligned.

The contract enforces strict regulatory safety: **all AI outputs are strictly advisory observations and do not autonomously issue or deny Fire NOC certificates.**

---

## 2. Service Endpoint & Transport Protocol

| Parameter | Specification |
| :--- | :--- |
| **HTTP Method** | `POST` |
| **Path** | `/internal/ai/inspection-analysis` |
| **Protocol** | HTTP/1.1 (TLS termination at ingress in production) |
| **Content-Type** | `application/json; charset=utf-8` |
| **Accept** | `application/json` |
| **Connection Timeout** | 5,000 ms (5.0 s) |
| **Read Timeout** | 30,000 ms (30.0 s) |
| **Retry Policy** | No automatic retries for HTTP 4xx (client validation or authentication failures). Single retry permitted for idempotent read operations or network dropouts prior to request receipt. |

---

## 3. Service Authentication & Security Contract

### 3.1 Mutual Service Credential
Communication between the Spring Boot backend (M2) and the AI service (M4) is authenticated via a shared internal service secret.

- **Primary Header:** `X-AI-Service-Token: <AI_SERVICE_AUTH_TOKEN>`
- **Fallback / Standard Header:** `Authorization: Bearer <AI_SERVICE_AUTH_TOKEN>`
- **Validation Mechanism:** Constant-time comparison using `hmac.compare_digest` to prevent timing attacks.
- **Credential Masking:** Authentication tokens are never logged, echoed in error payloads, or stored in plaintext repositories.
- **Rejection Behavior:** Requests with missing or invalid tokens receive `HTTP 401 Unauthorized` with a standardized structured error body.

### 3.2 Tenant Isolation
Multi-tenant isolation is enforced via the `tenantId` field within the JSON request payload, supplemented by the optional HTTP header `X-Tenant-ID`. The AI service isolates all in-memory registries, caching, and audit logging by tenant.

---

## 4. Contract Mapping & Harmonization Table

| Contract Field | M2 Source (`ExternalAiRequest`) | M4 Expected (`InspectionAnalysisRequest`) | Type (Java / Python) | Required / Optional | Compatibility Decision & Validation Rule |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `inspectionId` | `UUID inspectionId` | `inspectionId` | `java.util.UUID` / `uuid.UUID` | **Required** | Decoupled from `applicationId`. Must refer to an existing scheduled/active inspection. UUID format strictly validated. |
| `evidenceIds` | `List<UUID> evidenceIds` | `evidenceIds` | `List<UUID>` / `List[uuid.UUID]` | **Required** | Replaced single `documentReference: String`. Enforces `min_length >= 1`. Empty list rejected with HTTP 422. |
| `testMode` | `Boolean testMode` | `testMode` | `Boolean` / `Optional[bool]` | Optional (default: `false`) | Enables synthetic finding generation in local test environments. Cannot bypass production safety boundaries. |
| `occupancyContext` | `String occupancyContext` | `occupancyContext` | `String` / `Optional[str]` | Optional (default: `"UNKNOWN"`) | Premise hazard category (e.g., `COMMERCIAL_MERCANTILE`, `RESIDENTIAL`, `ASSEMBLY`, `HAZARDOUS`). |
| `historicalContext` | `String historicalContext` | `historicalContext` | `String` / `Optional[str]` | Optional (default: `"UNAVAILABLE"`) | Contextual prior compliance history. |
| `tenantId` | `String tenantId` | `tenantId` | `String` / `Optional[str]` | Optional (default: `"default"`) | Multi-tenant organization UUID or slug string. |

---

## 5. Request Contract Specification

### 5.1 JSON Request Schema
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "InspectionAnalysisRequest",
  "type": "object",
  "required": ["inspectionId", "evidenceIds"],
  "properties": {
    "inspectionId": {
      "type": "string",
      "format": "uuid",
      "description": "Unique identifier of the physical inspection session."
    },
    "evidenceIds": {
      "type": "array",
      "items": { "type": "string", "format": "uuid" },
      "minItems": 1,
      "description": "Non-empty list of persistent inspection evidence photo identifiers."
    },
    "testMode": {
      "type": "boolean",
      "default": false,
      "description": "Synthetic testing flag."
    },
    "occupancyContext": {
      "type": "string",
      "default": "UNKNOWN",
      "description": "Premises classification."
    },
    "historicalContext": {
      "type": "string",
      "default": "UNAVAILABLE",
      "description": "Prior violation or compliance track record."
    },
    "tenantId": {
      "type": "string",
      "default": "default",
      "description": "Tenant organization identifier."
    }
  }
}
```

### 5.2 Example Request Payload
```json
{
  "inspectionId": "4a71d880-92f5-4e78-b11c-197e4dc68bb2",
  "evidenceIds": [
    "c83d6e52-1982-4e0e-92fb-728b49911e30",
    "f29e1d88-b21a-4c28-9da3-0219c2e0b571"
  ],
  "testMode": false,
  "occupancyContext": "COMMERCIAL_MERCANTILE",
  "historicalContext": "NO_PRIOR_VIOLATIONS",
  "tenantId": "e1f2a3b4-5c6d-7e8f-9a0b-1c2d3e4f5a6b"
}
```

---

## 6. Response Contract Specification

### 6.1 Finding Data Structure & Rich AI Fields
The response schema preserves all analytical dimensions generated by Member 4's Computer Vision and Orchestration pipelines. Findings are **not** reduced to simplified label-confidence tuples.

Every finding item contains:
1. `findingId` (`UUID`): Unique observation record identifier.
2. `evidenceId` (`UUID`): Direct foreign link to source photograph.
3. `findingType` (`String`): Categorized hazard (e.g., `FIRE_EXTINGUISHER_MISSING`, `EMERGENCY_EXIT_OBSTRUCTED`, `SPRINKLER_HEAD_DEFECT`).
4. `confidence` (`Double`): Model prediction confidence between `0.0` and `1.0`.
5. `confidenceLevel` (`String`): Categorical confidence rating (`VERY_LOW`, `LOW`, `MEDIUM`, `HIGH`, `VERY_HIGH`).
6. `severity` (`String`): Defect operational severity (`INFORMATIONAL`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`).
7. `severityReason` (`String`): Justification for assigned severity.
8. `operationalRiskScore` (`Integer`): Quantitative hazard risk score (`0` to `100`).
9. `operationalRiskLevel` (`String`): Assessed risk bracket (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`).
10. `explanation` (`String`): Human-readable explanatory narrative for the reviewing officer.
11. `verificationGuidance` (`List<String>`): Specific on-site check instructions for the Fire Safety Officer.
12. `boundingBox` (`Object`): Normalized coordinates `{xMin, yMin, xMax, yMax}` with values bounded `[0.0, 1.0]`.
13. `evidenceQuality` (`String`): Visual rating (`GOOD`, `ACCEPTABLE`, `POOR`, `UNUSABLE`).
14. `complianceStatus` (`String`): Strictly set to `REQUIRES_OFFICER_VERIFICATION`. Values such as `AI_APPROVED` or `AI_REJECTED` are forbidden by Pydantic validators.
15. `model` & `modelVersion` (`String`): Originating model provenance (e.g., `firesphere-inspection-cv`, `0.3.0-phase3`).
16. `modelApprovalStatus` (`String`): Current governance gate state: `NOT_APPROVED`.
17. `calibrationStatus` (`String`): Current calibration state: `NOT_CALIBRATED`.
18. `validationStatus` (`String`): Verification status: `VALID`.

### 6.2 Inspection-Level Summary
The `summary` object provides aggregated metrics for the human officer review queue:
- `totalFindings`: Count of observations.
- `requiresOfficerReview`: Flag set to `true` whenever findings exist or quality issues require human attention.
- `calibrationStatus`: Always `NOT_CALIBRATED`.
- `complianceDecisionNote`: Explicit legal governance notice:
  > *"All AI findings are advisory visual observations. The AI system does NOT approve or reject Fire NOC applications. Authorized Fire Officer on-site verification is required."*

### 6.3 Example Response Payload
```json
{
  "runId": "7d9b231c-5290-41ab-8ce1-19ab8400f912",
  "status": "COMPLETED",
  "findings": [
    {
      "findingId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "evidenceId": "c83d6e52-1982-4e0e-92fb-728b49911e30",
      "findingType": "FIRE_EXTINGUISHER_MISSING",
      "confidence": 0.94,
      "confidenceLevel": "VERY_HIGH",
      "severity": "HIGH",
      "severityReason": "Mandatory first-line fire suppression equipment absent from designated wall bracket",
      "operationalRiskScore": 85,
      "operationalRiskLevel": "HIGH",
      "explanation": "Wall bracket identified without mounted fire extinguisher unit. Direct line of sight obstruction absent.",
      "verificationGuidance": [
        "Verify physical presence and pressure gauge status of extinguisher on-site",
        "Inspect maintenance log tag for recent servicing"
      ],
      "boundingBox": {
        "xMin": 0.15,
        "yMin": 0.25,
        "xMax": 0.45,
        "yMax": 0.65
      },
      "evidenceQuality": "GOOD",
      "complianceStatus": "REQUIRES_OFFICER_VERIFICATION",
      "model": "firesphere-inspection-cv",
      "modelVersion": "0.3.0-phase3",
      "isTestData": false,
      "modelApprovalStatus": "NOT_APPROVED",
      "calibrationStatus": "NOT_CALIBRATED",
      "validationStatus": "VALID",
      "inspectionId": "4a71d880-92f5-4e78-b11c-197e4dc68bb2",
      "analysisRunId": "7d9b231c-5290-41ab-8ce1-19ab8400f912"
    }
  ],
  "summary": {
    "totalFindings": 1,
    "uniqueFindingTypes": 1,
    "highestSeverity": "HIGH",
    "highestRiskLevel": "HIGH",
    "highestRiskScore": 85,
    "evidenceCount": 2,
    "requiresOfficerReview": true,
    "findingsRequiringOfficerReview": 1,
    "confidenceSummary": {
      "averageConfidence": 0.94
    },
    "evidenceQualitySummary": {
      "GOOD": 2
    },
    "calibrationStatus": "NOT_CALIBRATED",
    "complianceDecisionNote": "All AI findings are advisory visual observations. The AI system does NOT approve or reject Fire NOC applications. Authorized Fire Officer on-site verification is required."
  },
  "model": "firesphere-inspection-cv",
  "modelVersion": "0.3.0-phase3",
  "executionDurationMs": 142.8,
  "tenantId": "e1f2a3b4-5c6d-7e8f-9a0b-1c2d3e4f5a6b",
  "modelApprovalStatus": "NOT_APPROVED",
  "calibrationStatus": "NOT_CALIBRATED"
}
```

---

## 7. Error Contract Specification

The service guarantees uniform error payload structures across all failure modes:

```json
{
  "status": "FAILED",
  "errorCode": "STRING_ERROR_CODE",
  "message": "Human-readable description without internal stack traces or secrets.",
  "details": {},
  "validationErrors": []
}
```

| HTTP Status | Error Code | Trigger Condition | Retry Strategy |
| :--- | :--- | :--- | :--- |
| `400 Bad Request` | `INVALID_INSPECTION_ID`, `INVALID_EVIDENCE_ID` | Business validation violation or malformed data logic. | Do **not** retry. Correct client input. |
| `401 Unauthorized` | `UNAUTHORIZED_SERVICE` | Missing or invalid `X-AI-Service-Token`. | Do **not** retry. Review configured credentials. |
| `404 Not Found` | `EVIDENCE_NOT_FOUND` | Specified evidence ID does not exist in object storage. | Do **not** retry until evidence is uploaded. |
| `422 Unprocessable Content` | `VALIDATION_ERROR` | Schema validation error (e.g., malformed UUID, empty `evidenceIds` list). | Do **not** retry without correcting JSON payload. |
| `500 Internal Error` | `INTERNAL_SERVER_ERROR` | Unhandled server exception during image processing. | Safe to retry once with exponential backoff if transient. |
| `502 Bad Gateway` | `GEMINI_PROVIDER_ERROR` | External inference failure (when Gemini VLM is enabled in future phases). | Safe to retry up to 2 times with jitter. |

---

## 8. Correlation & Traceability

Traceability across microservice boundaries is maintained via:
1. **Inspection Traceability:** `inspectionId` is persisted and indexed in both Spring Boot and AI Service storage.
2. **Run Traceability:** `runId` generated by M4 is returned in the response and stored in M2's `ai_analysis_runs` table (`run.setId()`).
3. **Evidence Linkage:** Every individual `Finding` maintains an explicit `evidenceId` and `inspectionId` reference.
4. **Audit Logging:** Both M2 (`AuditLogger`) and M4 (`logger`) record every request and completion event with associated `runId` and `documentId`/`inspectionId`.
