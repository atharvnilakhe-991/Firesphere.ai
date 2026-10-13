# FireSphere.ai — Member 2: Backend Core & Fire NOC

## Mission
Build the authoritative Spring Boot business layer, security foundation and complete Fire NOC workflow.

## Owns
- Java 21 + Spring Boot 3.x foundation
- `/api/v1`
- authentication and RBAC
- tenant context/isolation
- users/organizations access
- validation/error handling
- audit integration
- Fire NOC
- core workflow/state machine
- checklist orchestration
- NOC-to-inspection contract
- certificate workflow contract
- OpenAPI

## Architecture
```text
Controller → Service → Repository → Database
```
Use DTOs at API boundaries. Never expose JPA entities.

## Security
Every protected request:
```text
Authentication → Tenant → Role → Permission → Resource Access
```
Never trust `organizationId` from the client.

## NOC APIs
Typical contract:
```text
POST /api/v1/noc/applications
GET  /api/v1/noc/applications
GET  /api/v1/noc/applications/{id}
PUT  /api/v1/noc/applications/{id}
POST /api/v1/noc/applications/{id}/submit
POST /api/v1/noc/applications/{id}/verify-documents
POST /api/v1/noc/applications/{id}/approve
POST /api/v1/noc/applications/{id}/reject
POST /api/v1/noc/applications/{id}/request-correction
POST /api/v1/noc/applications/{id}/resubmit
```
Exact contracts are finalized with M1/M3.

## State Machine
```text
DRAFT → SUBMITTED → PAYMENT_PENDING → PAYMENT_COMPLETED
→ DOCUMENT_VERIFICATION → DOCUMENTS_VERIFIED
→ CHECKLIST_GENERATED → INSPECTION_SCHEDULED
→ INSPECTION_IN_PROGRESS → INSPECTION_COMPLETED
→ AI_VALIDATION → PENDING_APPROVAL → APPROVED
→ CERTIFICATE_GENERATED → ACTIVE → RENEWAL_DUE
```
Correction/reinspection paths must be explicit and approved.

## Checklist
Generate from configured NOC category, building type, occupancy, height, risk and applicable rules. Do not invent regulatory requirements.

## Documents/Payments
Backend owns verification and payment verification. Never trust mobile-only payment success. Use idempotency for important operations.

## AI
Create asynchronous AI jobs; persist evidence-linked results; wait for officer review. AI cannot approve/reject NOCs.

## Certificate
Approved → certificate number → PDF/signature where required → storage → verification record → notification.

## Tests
Auth, RBAC, tenant isolation, NOC transitions, validation, controllers, repositories where useful, audit and API contract tests.

## Definition of Done
API documented; DTOs; validation; authorization; tenant isolation; DB mapping approved by M3; audit; tests; OpenAPI; consistent errors.

## Antigravity Rule
Read current backend, Database MD and migrations first. Do not invent tables/fields. If schema conflicts, stop and report. Do not modify frontend/AI/unrelated operational modules.
