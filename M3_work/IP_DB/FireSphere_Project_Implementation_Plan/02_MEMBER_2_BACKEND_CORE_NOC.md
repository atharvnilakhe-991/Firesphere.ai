# Member 2 — Backend Core + Fire NOC Implementation Plan

## Ownership
Java 21/Spring Boot 3.x REST APIs, authentication/authorization integration, Fire NOC business workflow, documents, payments, inspections, reports and certificates.

## Phase 1 — Backend foundation
- [ ] Spring Boot structure
- [ ] profiles/configuration
- [ ] validation
- [ ] exception handling
- [ ] response/error conventions
- [ ] correlation IDs/logging
- [ ] OpenAPI

## Phase 2 — Security
- [ ] OAuth2/OIDC/JWT integration
- [ ] citizen/officer/admin roles
- [ ] authorization rules
- [ ] tenant/organization context
- [ ] protected endpoints
- [ ] security tests

## Phase 3 — Fire NOC workflow
- [ ] create/save/submit application
- [ ] application validation
- [ ] status transitions
- [ ] application history
- [ ] citizen/officer/admin access rules

Suggested lifecycle:
DRAFT → SUBMITTED → DOCUMENT_VERIFICATION → PAYMENT_PENDING/PAID → INSPECTION_SCHEDULED → INSPECTION_IN_PROGRESS → INSPECTION_SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED → CERTIFICATE_ISSUED → RENEWAL_DUE.

## Phase 4 — Documents
- [ ] metadata API
- [ ] secure upload authorization
- [ ] verification status
- [ ] missing/rejected document handling
- [ ] history
- [ ] object-storage integration

## Phase 5 — Payments
- [ ] fee interface
- [ ] payment initiation
- [ ] provider abstraction
- [ ] webhook verification
- [ ] payment status
- [ ] duplicate protection
- [ ] receipt
- [ ] failure handling
- [ ] audit events

## Phase 6 — Inspection
- [ ] create inspection
- [ ] officer assignment
- [ ] schedule/reschedule
- [ ] checklist retrieval
- [ ] evidence metadata
- [ ] officer submission
- [ ] history
- [ ] duplicate-submission protection

## Phase 7 — AI contract
- [ ] POST AI job
- [ ] inspectionId/jobId/analysisRunId linkage
- [ ] evidence references
- [ ] idempotency
- [ ] status/result retrieval
- [ ] finding persistence contract
- [ ] officer verification persistence
- [ ] timeout/failure handling

AI remains advisory and cannot autonomously issue legal approval/rejection.

## Phase 8 — Reports/certificates
- [ ] report workflow
- [ ] officer review/finalization
- [ ] certificate workflow
- [ ] certificate metadata/access
- [ ] renewal linkage
- [ ] audit trail

## Phase 9 — Tests/handoff
- [ ] unit tests
- [ ] controller/service tests
- [ ] authorization tests
- [ ] workflow transition tests
- [ ] payment security tests
- [ ] AI job/idempotency tests
- [ ] API documentation
- [ ] environment documentation
- [ ] completion report
