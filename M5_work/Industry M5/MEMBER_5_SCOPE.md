# Member 5 Operational Architecture & Scope Specification
**Project:** FireSphere.ai — AI-Powered Smart Fire Administration Application  
**Role:** Member 5 — Operations, Incidents, Emergency Dispatch, Fleet, Inventory, GIS, Notifications, Integrations, Testing & QA  
**Date:** October 2026  
**Status:** COMPLETE & ACCEPTED (Production Candidate 1.0.0 — All non-GIS requirements, Object Storage, Payment Webhook/Reconciliation, AI Job Workflow, Multi-Channel Templates, Officer Checklists, and E2E Scenarios A-E fully verified with 465 passing tests; GIS verification remains safely paused)  

---

## 1. Responsibilities

Member 5 owns the core operational heartbeat of the FireSphere.ai platform. Member 5 is responsible for:

1. **Incident Management (`incident`)**:
   - Intake, verification, severity triage, lifecycle state progression (`REPORTED` $\rightarrow$ `CONTAINED` $\rightarrow$ `CLOSED`).
   - Incident timeline recording, SitRep reporting, and mobile offline synchronization contracts.
2. **Emergency Dispatch (`dispatch`)**:
   - Turnout recommendation calculations (apparatus type and crew sizing based on hazard profile).
   - Dispatch order issuance, acknowledgment receipts, and unit status transitions.
3. **Fleet / Vehicle Management (`fleet`)**:
   - Apparatus inventory cataloging (pumpers, aerial ladders, water tankers, rescue tenders).
   - Readiness states (`AVAILABLE`, `DISPATCHED`, `MAINTENANCE`, `OUT_OF_SERVICE`).
   - IoT telematics ingestion (GPS coordinates, speed, heading, fuel, water/foam tank levels).
4. **Fire Store / Inventory Management (`inventory`)**:
   - Equipment and consumable tracking (PPE, foam concentrates, breathing apparatus, hoses, nozzles).
   - Station inventory pools, batch tracking, dispatch issues (`/issue`), returns (`/return`), and low-stock reserve alerts.
5. **GIS Operational Services (`gis`)**:
   - PostGIS-ready spatial querying for operable hydrants, hazardous material zones, and evacuation perimeters.
   - Emergency route waypoint calculation and distance matrices with emergency vehicle preemption (Runtime PostGIS paused).
6. **Notification Orchestration (`notification`)**:
   - Priority-based turnout broadcasts and multi-channel alerting (FCM push, SMS, Email, WhatsApp).
   - Complete 14-event lifecycle matrix with 56 pre-registered multi-channel templates and dynamic placeholder interpolation.
   - Officer delivery receipts and mobile turnout acknowledgment.
7. **External Integration Framework (`integration`)**:
   - Object Storage SPI and provider (`StorageIntegration`, `MockStorageProvider`, `StorageIntegrationAdapter`, 10MB limits, SHA-256 hashes, MIME checking).
   - Payment Webhook & Reconciliation (`PaymentIntegration`, `MockPaymentProvider`, HMAC-SHA256 signature verification, idempotency deduplication, ledger reconciliation).
   - AI Inspection Job Workflow (`AIJobState`, `MockAIInspectionResultProvider`, async job submission, hazard finding ingestion, HITL review actions).
   - Municipal 112/911 CAD integration adapters and SCADA hydrant feeds.
8. **Testing, QA & Resilience (`testing`)**:
   - Comprehensive automated test suite: **468 tests executed, 465 passed, 0 failures, 0 errors, 3 skipped (PostGIS tests paused per mandate)**.
   - Functional QA: 52 passing tests verifying cross-module operational coherence, non-fabrication GPS coordinate safety, zero-network mock provider delivery, retry exhaustion, and multi-tenant isolation.
   - Negative/Security QA: 51 dedicated security tests covering authentication and role-based authorization matrices, strict unauthenticated access rejection, cross-tenant isolation enforcement across all domain modules, input validation and malformed request handling, operational data integrity, and terminal state guardrails.
   - Mobile QA: 32 dedicated mobile-readiness tests validating field officer task workflows and offline sync delta queries, GPS accuracy classification, provider-neutral navigation handoffs, mobile turnout push notifications, and lightweight `PageResponse` envelopes. Physical device testing accurately marked as BLOCKED pending mobile client code.
   - AI QA: 27 dedicated AI safety and reliability tests validating computer vision hazard finding ingestion, Human-in-the-Loop review workflows (`APPROVED`, `DISMISSED`, `ESCALATED`), confidence score boundaries ($[0.0, 1.0]$), deterministic fallback dispatch turnout sizing, advisory read-only safety, concurrent thread-safe mock processing, and zero external network calls.
   - End-to-End Business Scenarios: 5 automated lifecycle tests covering Scenario A (Citizen Application), Scenario B (Officer Inspection), Scenario C (AI Safety Analysis), Scenario D (Officer Review & Decision), and Scenario E (Annual Renewal).
9. **Officer Operations Support (`operations.officer`)**:
   - Inspection task tracking with granular checklists and inspection notes, deterministic overdue logic with injectable Clock, workload metrics and capacity status.
   - Alert evaluation with deduplication, SLA breach auto/manual escalation to supervisory roles, terminal state immutability, and automatic event notifications.

---

## 2. Module Boundaries

To maintain clean architectural modularity and team separation of concerns, Member 5 adheres to strict boundaries:

| Module Domain | Owner | Member 5 Boundary & Integration Point |
| :--- | :--- | :--- |
| **Mobile Client UI (Officer & Citizen)** | Member 1 & 2 | Member 5 provides REST JSON APIs (`/api/v1/...`) with pagination (`PageResponse`), idempotent keys, and compact payloads. Member 5 does not write mobile UI screens. |
| **Authentication & RBAC Core** | Member 1 & 2 | Member 5 enforces security via Spring Security contracts (`@PreAuthorize`) and extracts tenant identity from the authenticated security context. Member 5 does not build OAuth2/JWT token issuance. |
| **Database Architecture Core** | Member 3 | Member 5 defines domain entities and Flyway migrations strictly scoped to `incidents`, `dispatch_orders`, `fleet_vehicles`, `inventory_items`, `gis_hydrants`, etc., following M3 database standards. |
| **AI / Computer Vision Models** | Member 4 | Member 5 does not train or run PyTorch/YOLO/OpenCV models. Member 5 integrates AI findings via a structured DTO and ensures human-in-the-loop regulatory review. |

---

## 3. Independent Development Strategy

Member 5 does **not** block or wait for other members. If a peer module is in development or unavailable, Member 5 operates under the following independence principles:

1. **Interface-First Architecture**: Every external dependency is defined as a clean Java interface within `ai.firesphere.contracts.*`.
2. **Local Mock Implementations**: Every interface has a production-ready mock implementation located in `ai.firesphere.contracts.mock.*` activated via Spring's `@ConditionalOnMissingBean`.
3. **Pluggable Architecture**: When peer implementations become available, they can be plugged in as Spring beans without altering Member 5's internal domain services or API signatures.
4. **Isolated Test Execution**: Unit and integration test suites run against in-memory H2 (configured in PostgreSQL compatibility mode), allowing full CI/CD builds without external infrastructure dependencies.

---

## 4. Mock Strategy

The following contract abstractions and mock implementations decouple Member 5 from external systems during independent development:

| Contract Interface | Mock Implementation | Role & Fallback Behavior |
| :--- | :--- | :--- |
| [`IncidentAssignmentContract`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/contracts/incident/IncidentAssignmentContract.java) | [`MockIncidentAssignmentService`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/contracts/mock/MockIncidentAssignmentService.java) | Simulates station personnel rosters and duty shifts without requiring Member 1's user service. |
| [`NotificationProviderContract`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/contracts/notification/NotificationProviderContract.java) | [`MockNotificationProvider`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/contracts/mock/MockNotificationProvider.java) | Simulates FCM push and SMS delivery receipts with zero third-party cloud credentials. |
| [`MapRoutingProviderContract`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/contracts/gis/MapRoutingProviderContract.java) | [`MockMapProvider`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/contracts/mock/MockMapProvider.java) | Employs geodesic Haversine distance calculations and simulated waypoints without external GIS map servers. |
| [`VehicleLocationProviderContract`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/contracts/fleet/VehicleLocationProviderContract.java) | [`MockVehicleLocationProvider`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/contracts/mock/MockVehicleLocationProvider.java) | Thread-safe concurrent in-memory telematics storage for apparatus tracking without physical OBD-II/GPS hardware. |
| [`AIInspectionResultProviderContract`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/contracts/integration/AIInspectionResultProviderContract.java) | [`MockAIInspectionResultProvider`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/contracts/mock/MockAIInspectionResultProvider.java) | Provides simulated computer vision findings and stores officer review actions. |
| [`ExternalCadIntegrationContract`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/contracts/integration/ExternalCadIntegrationContract.java) | [`MockExternalCadIntegrationService`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/contracts/mock/MockExternalCadIntegrationService.java) | Simulates bidirectional transmission with external 112/911 dispatch networks. |

---

## 5. Assumptions

1. **Multi-Tenancy & Identity**:
   - Authenticated JWTs contain a `tenant_id` claim representing the municipal department/tenant.
   - Client requests never dictate `tenantId` in the body or URL path for secure operations; it is resolved via [`TenantContext`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/src/main/java/ai/firesphere/common/tenant/TenantContext.java).
2. **Mobile Network Conditions**:
   - The Fire Officer mobile application communicates over variable cellular networks.
   - Operations endpoints must support pagination, compact JSON DTOs, and clear HTTP error codes.
3. **Regulatory AI Safety Guardrails**:
   - Member 4's AI detection models (e.g., blocked fire exits, expired extinguishers) are purely advisory.
   - Automated enforcement or permit revocations are prohibited; an authorized fire officer must review and confirm or dismiss all findings.
4. **Spatial Operations**:
   - Coordinates are represented in WGS84 standard decimal degrees (`latitude`, `longitude`).

---

## 6. Future Integration Points

When peer components are ready, Member 5 will connect at the following interfaces:

1. **Member 1 (Auth & Mobile App)**:
   - Replace development security filter with Member 1's JWT verification filter.
   - Expose OpenAPI specification to Member 1 for React Native TypeScript client generation.
2. **Member 2 (NOC & Officer Workflows)**:
   - Provide WebSocket / STOMP or Server-Sent Events (SSE) channels for live incident triage dispatch boards.
3. **Member 3 (Database & Infrastructure)**:
   - Connect Member 5 Flyway migrations into Member 3's centralized database pipeline.
   - Activate PostGIS native geometry types and spatial indexing.
4. **Member 4 (AI / Computer Vision)**:
   - Connect FastAPI webhook endpoints to Member 5's `/api/v1/integrations/ai/findings` ingest API.

---

## 7. Deliverables & Documentation Index

The following acceptance reports in `docs/member5/` document all implemented modules, tests, and security validations:

- [`remaining-work-checklist.md`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/docs/member5/remaining-work-checklist.md): Traceability matrix mapping every requirement to source files, line numbers, and passing test methods.
- [`implementation-report.md`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/docs/member5/implementation-report.md): Technical architecture report detailing Object Storage, Payment Webhooks/Reconciliation, AI Job Workflows, Notification Templates, and Officer Task Checklists.
- [`qa-report.md`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/docs/member5/qa-report.md): Comprehensive QA report with test inventory (468 tests, 465 passed, 0 failures, 0 errors, 3 skipped), mock breakdown, and security matrices.
- [`end-to-end-report.md`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/docs/member5/end-to-end-report.md): End-to-end walkthrough and sequence diagrams for all 5 business scenarios (A: Citizen, B: Officer, C: AI, D: Decision, E: Renewal).
- [`release-acceptance-report.md`](file:///c:/Users/ROHAN%20NAVSEKAR/OneDrive/Desktop/Industry%20M5/docs/member5/release-acceptance-report.md): Final release audit report confirming 100% non-GIS test passage, build artifacts, and production readiness.

