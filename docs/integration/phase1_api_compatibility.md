# FireSphere.ai — M2 ↔ M4 API Contract Compatibility Assessment

**Document Identifier:** `FS-DOC-PHASE1-API-COMPATIBILITY-20261010`  
**Phase:** 1 — Workspace Audit and Integration Design  
**Local Workspace Root:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**Date:** 2026-10-10  
**Classification:** Inter-Service Interface Audit & Harmonization Specification  

---

## 1. Executive Summary

This document performs an exhaustive, field-by-field audit between the **Member 2 Spring Boot Backend AI Client** and the **Member 4 FastAPI AI Microservice**. 

The investigation confirms that the previously suspected incompatibility is **100% genuine and actively present in the workspace source code**. As currently implemented, any HTTP request initiated by Member 2's [`HttpAiAnalysisClient.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend/ai/client/HttpAiAnalysisClient.java) will immediately fail with **HTTP 404 (Not Found)**, **HTTP 401 (Unauthorized)**, or **HTTP 422 (Unprocessable Content)** when received by Member 4's [`inspection.py`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/api/v1/inspection.py).

---

## 2. Comprehensive API Compatibility Matrix

| Contract Dimension | Member 2 Implementation (Spring Boot) | Member 4 Implementation (FastAPI) | Compatibility Status | Exact Mismatch & Technical Root Cause |
| :--- | :--- | :--- | :---: | :--- |
| **HTTP Method** | `POST` | `POST` | `COMPATIBLE` | Both use HTTP POST. |
| **Endpoint Path** | `${baseUrl} + "/api/v1/analyze"` | `/internal/ai/inspection-analysis` (Sync)<br>`/internal/ai/jobs` (Async) | `INCOMPATIBLE` | **Path Mismatch:** M2 calls `/api/v1/analyze` which is not registered in FastAPI router (`api_router.py` mounts `/internal/ai`). Results in **HTTP 404 Not Found**. |
| **Authentication Header** | `headers.set("X-API-KEY", apiKey)` | `verify_service_token` checks:<br>1. `X-AI-Service-Token: <token>`<br>2. `Authorization: Bearer <token>` | `INCOMPATIBLE` | **Header Name Mismatch:** M2 sends `X-API-KEY`. M4 ignores this header and returns **HTTP 401 Unauthorized (`UNAUTHORIZED_SERVICE`)**. |
| **Identifier Semantics** | Sends `applicationId` and `runId` | Requires `inspectionId` and `evidenceIds` | `INCOMPATIBLE` | **Semantic Inversion:** M2 binds analysis to a high-level Fire NOC application, whereas M4 inspects visual evidence tied to an on-site inspection session. |
| **Request Payload: Document Reference** | `documentReference: String` (Single document URL/path) | `evidenceIds: List[UUID]` (Non-empty array of evidence file UUIDs) | `INCOMPATIBLE` | **Cardinality & Type Mismatch:** M2 sends a single string path; M4 strictly requires a list of RFC 4122 UUIDs (`min_length=1`). FastAPI validation fails with **HTTP 422**. |
| **Request Payload: Context Fields** | None (`runId`, `applicationId`, `documentReference`, `analysisType`) | `occupancyContext: str`<br>`historicalContext: str`<br>`testMode: bool`<br>`tenantId: str` | `PARTIAL_MISMATCH` | M4 provides defaults (`UNKNOWN`, `UNAVAILABLE`, `False`, `default`), so missing fields do not fail schema validation, but M2 provides no tenant/occupancy metadata. |
| **Response Payload: Structure** | `ExternalAiResponse`: Simple flat list of `{ label, confidence }` | `InspectionAnalysisResponse`: Hierarchical report containing `runId`, `status`, `findings`, `summary`, `model`, `modelVersion` | `INCOMPATIBLE` | **Deserialization Failure:** M2's Jackson object mapper expects top-level fields `modelName`, `timestamp`, `errorDetail`. M4 returns `findings` with rich explanation objects. |
| **Response Finding Fields** | `Finding(String label, Double confidence)` | `Finding`: `findingId`, `evidenceId`, `findingType`, `confidence`, `confidenceLevel`, `severity`, `severityReason`, `operationalRiskScore`, `explanation`, `verificationGuidance`, `boundingBox`, `calibrationStatus`, `reviewStatus` | `INCOMPATIBLE` | M2 cannot parse coordinates, severities, or verification checklists. M4 does not return a `label` field (it returns enum `findingType`). |
| **Sync vs Async Behavior** | Synchronous blocking exchange (`RestTemplate.exchange`) with 30s timeout | Supports both synchronous (`/inspection-analysis`) and asynchronous job lifecycle (`/jobs`) | `PARTIALLY_COMPATIBLE` | Large batch inspections (10+ high-res photos) will breach M2's 30s socket read timeout during synchronous execution. |
| **Error Format** | Checks `response.getStatusCode().is2xxSuccessful()`, else returns fallback | Structured JSON: `{ status: "FAILED", errorCode: "...", message: "..." }` | `PARTIALLY_COMPATIBLE` | M2 logs raw response string on error; does not parse M4 error codes. |
| **Idempotency** | M2 generates client-side `runId` | M4 supports optional `idempotencyKey` in `/jobs` endpoint | `PARTIALLY_COMPATIBLE` | M2 does not supply an `Idempotency-Key` HTTP header. |
| **Multi-Tenancy** | Handled via `TenantContext` on M2; not forwarded in AI request | M4 supports `X-Tenant-ID` header and payload `tenantId` | `DISCONNECTED` | M4 defaults to tenant `"default"`; tenant isolation is not propagated. |

---

## 3. Forensic Evidence from Source Code

### Evidence 1: Member 2 Backend Client (`HttpAiAnalysisClient.java`)
```java
// Source: M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/client/HttpAiAnalysisClient.java
// Lines 41-51
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
headers.set("X-API-KEY", apiKey);

HttpEntity<ExternalAiRequest> entity = new HttpEntity<>(request, headers);

ResponseEntity<ExternalAiResponse> response = restTemplate.exchange(
        baseUrl + "/api/v1/analyze",
        HttpMethod.POST,
        entity,
        ExternalAiResponse.class
);
```

### Evidence 2: Member 2 Request DTO (`ExternalAiRequest.java`)
```java
// Source: M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/dto/ExternalAiRequest.java
public record ExternalAiRequest(
        UUID runId,
        UUID applicationId,
        String documentReference,
        String analysisType
) {}
```

### Evidence 3: Member 4 Endpoint & Authentication Dependency (`inspection.py` & `auth.py`)
```python
# Source: ai-service/app/api/v1/inspection.py (Lines 19-33)
@router.post(
    "/inspection-analysis",
    response_model=InspectionAnalysisResponse,
    status_code=status.HTTP_200_OK,
)
async def analyze_inspection(
    request: InspectionAnalysisRequest,
    _authenticated: bool = Depends(verify_service_token),
) -> InspectionAnalysisResponse:
    return inspection_service.process_inspection(request)

# Source: ai-service/app/core/auth.py (Lines 14-23)
def verify_service_token(
    x_ai_service_token: Optional[str] = Header(default=None, alias="X-AI-Service-Token"),
    bearer_auth: Optional[HTTPAuthorizationCredentials] = Security(bearer_scheme),
) -> bool:
    # Verifies either 'X-AI-Service-Token' header or 'Authorization: Bearer <token>'
```

### Evidence 4: Member 4 Request Schema (`app/schemas/inspection.py`)
```python
# Source: ai-service/app/schemas/inspection.py (Lines 33-63)
class InspectionAnalysisRequest(BaseModel):
    inspectionId: uuid.UUID = Field(...)
    evidenceIds: List[uuid.UUID] = Field(..., min_length=1)
    testMode: Optional[bool] = Field(default=False)
    occupancyContext: Optional[str] = Field(default="UNKNOWN")
    historicalContext: Optional[str] = Field(default="UNAVAILABLE")
    tenantId: Optional[str] = Field(default="default")
```

---

## 4. Incompatibility Breakdown and Resolution Options

### Incompatibility 1: Endpoint Path & Prefix
- **M2 Implementation:** Requests `${baseUrl}/api/v1/analyze`.
- **M4 Implementation:** Routes mounted under `/internal/ai` prefix ([`main.py:130`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/main.py#L130)). Synchronous route is `/internal/ai/inspection-analysis`.
- **Potential Impact:** 100% failure rate (HTTP 404).
- **Available Resolution Options:**
  - *Option A:* Update M2's `HttpAiAnalysisClient.java` to invoke `/internal/ai/inspection-analysis`.
  - *Option B:* Add a compatibility redirect/proxy route `/api/v1/analyze` in M4's FastAPI router.
- **Recommended Option:** **Option A**. The `/internal/ai/*` URI scheme reflects enterprise microservice security boundaries (internal service-to-service vs public API).

### Incompatibility 2: Authentication Header
- **M2 Implementation:** Sends `X-API-KEY`.
- **M4 Implementation:** Expects `X-AI-Service-Token` or `Authorization: Bearer <token>`.
- **Potential Impact:** 100% failure rate (HTTP 401).
- **Available Resolution Options:**
  - *Option A:* Update M2's `HttpAiAnalysisClient.java` to send `X-AI-Service-Token: ${apiKey}`.
  - *Option B:* Update M4's `verify_service_token` to accept `X-API-KEY` as a third alias.
- **Recommended Option:** **Option A + B (Bilateral Alignment)**. Update M2 client to emit `X-AI-Service-Token`, while updating M4 to accept `X-API-KEY` as a permissive fallback alias.

### Incompatibility 3: Payload Semantic & Schema Mismatch
- **M2 Implementation:** Sends `applicationId` and single `documentReference: String`. Expects `{ label, confidence }`.
- **M4 Implementation:** Requires `inspectionId: UUID` and `evidenceIds: List[UUID]`. Returns rich `Finding` objects with `findingType`, `severity`, `boundingBox`, `operationalRiskScore`, etc.
- **Potential Impact:** HTTP 422 Unprocessable Content on request; Jackson JSON parsing crash on response.
- **Available Resolution Options:**
  - *Option A:* Modernize M2's client DTOs to match M4's `InspectionAnalysisRequest` and `InspectionAnalysisResponse`. Update `NocInspectionService` to pass the active `inspectionId` and associated evidence UUIDs.
  - *Option B:* Implement a legacy translation adapter in M4 that receives M2's flat request, synthesizes mock evidence UUIDs, and flattens findings to `{label, confidence}`.
- **Recommended Option:** **Option A**. Option B destroys evidence traceability, drops bounding box metadata, and violates Architectural Rules 4 and 5.

---

## 5. Proposed Harmonized Contract (Status: PROPOSED)

### 5.1 Request Contract (`POST /internal/ai/inspection-analysis`)
```http
POST /internal/ai/inspection-analysis HTTP/1.1
Host: ai-service:8000
Content-Type: application/json
X-AI-Service-Token: <SHARED_SECRET_TOKEN>
X-Tenant-ID: <TENANT_UUID>
X-Correlation-ID: <TRACE_UUID>

{
  "inspectionId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "evidenceIds": [
    "c3b88b22-8239-4d69-a1b9-87cfa90f1d3e"
  ],
  "testMode": false,
  "occupancyContext": "COMMERCIAL",
  "historicalContext": "STANDARD_COMPLIANCE",
  "tenantId": "org-pune-municipal-01"
}
```

### 5.2 Response Contract (`HTTP 200 OK`)
```json
{
  "runId": "4fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "COMPLETED",
  "model": "firesphere-perception-baseline-v0.8.0",
  "modelVersion": "0.8.0",
  "modelApprovalStatus": "NOT_APPROVED",
  "calibrationStatus": "NOT_CALIBRATED",
  "executionDurationMs": 142.5,
  "tenantId": "org-pune-municipal-01",
  "findings": [
    {
      "findingId": "d1c2b3a4-5678-90ab-cdef-1234567890ab",
      "evidenceId": "c3b88b22-8239-4d69-a1b9-87cfa90f1d3e",
      "inspectionId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "findingType": "FIRE_EXTINGUISHER_MISSING",
      "sourceModel": "CV_YOLO_BASELINE",
      "confidence": 0.82,
      "confidenceLevel": "HIGH",
      "severity": "HIGH",
      "severityReason": "Safety perimeter requires immediate placement",
      "operationalRiskScore": 75,
      "explanation": "Extinguisher absent from bracket; empty mounting plate identified",
      "verificationGuidance": [
        "Physically inspect mounting bracket on wall",
        "Verify if extinguisher was temporarily removed for servicing"
      ],
      "boundingBox": {
        "xMin": 0.15,
        "yMin": 0.20,
        "xMax": 0.45,
        "yMax": 0.60
      },
      "validationStatus": "VALIDATED",
      "conflictStatus": "NO_CONFLICT",
      "reviewStatus": "UNREVIEWED"
    }
  ],
  "summary": {
    "totalFindings": 1,
    "uniqueFindingTypes": 1,
    "highestSeverity": "HIGH",
    "highestRiskScore": 75,
    "requiresOfficerReview": true,
    "complianceDecisionNote": "All AI findings are advisory visual observations. The AI system does NOT approve or reject Fire NOC applications. Authorized Fire Officer on-site verification is required."
  }
}
```

### 5.3 Asynchronous Job Endpoint (For Large Batches)
For inspections with $> 5$ photographs, M2 will invoke `POST /internal/ai/jobs` and poll `GET /internal/ai/jobs/{jobId}` until `status == "COMPLETED"`.
