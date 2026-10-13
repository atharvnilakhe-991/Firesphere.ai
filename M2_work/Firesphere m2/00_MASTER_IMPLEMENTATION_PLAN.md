# FireSphere.ai — Final Five-Member Production Implementation Plan

**Status:** Team implementation baseline  
**Product:** FireSphere.ai — AI-Powered Smart Fire Administration Application  
**Delivery:** Production-oriented, mobile-first, Android/Google Play Store ready  
**Clients:** Citizen Mobile App, Fire Officer Mobile App, Administrative Web Console  
**Backend:** Java 21 + Spring Boot 3.x | **AI:** Python + FastAPI | **DB:** PostgreSQL + PostGIS | **Cache:** Redis | **Storage:** S3/MinIO | **API:** `/api/v1`

## 1. Non-Negotiable Direction
FireSphere is a real production-oriented application, not a prototype/disposable MVP. Build secure authentication, RBAC, tenant isolation, real persistence, versioned APIs, offline officer workflows, secure media, AI assistance, notifications, GIS, auditability, monitoring, testing and Play Store readiness from the beginning.

## 2. Five-Member Ownership

| Member | Primary ownership | Secondary responsibility |
|---|---|---|
| **M1** | Frontend & Mobile | UI/UX, API consumption, offline UX |
| **M2** | Backend Core & Fire NOC | Auth, RBAC, tenant security, workflows |
| **M3** | Database, Infrastructure & DevOps | PostgreSQL/PostGIS, migrations, Redis, storage, CI/CD |
| **M4** | AI/ML & AI Productivity | Computer vision, AI APIs, report drafting, evaluation |
| **M5** | Operations, GIS, Integrations & QA | Incidents, fleet, inventory, GIS, notifications, integration testing |

Ownership does not permit silent modification of another member's module.

## 3. Source-of-Truth Hierarchy
1. Company requirements
2. Final integrated technical specification
3. Approved Database MD for physical schema
4. Approved Backend MD for APIs/business rules
5. Approved Frontend/Mobile MD for client behavior
6. Member MDs
7. Existing demo/reference code

## 4. Implementation Dependency

```text
Company Requirements + Existing Repository
                 ↓
      Database/API Contract Review
                 ↓
      M3 Database/Infra + M2 Backend
                 ↓
             M1 Client
                 ↓
       M4 AI + M5 Operations
                 ↓
       Cross-Module Integration
                 ↓
          QA / Security / Staging
                 ↓
       Android Release / Production
```

Members work in parallel where contracts already exist, but no member invents conflicting contracts.

## 5. Phase 0 — Alignment
All members read the company document, Data Dictionary, current Frontend/Backend documents and their own MD. Audit existing code first. Record conflicts rather than silently resolving them.

## 6. Phase 1 — Foundation
**M3:** PostgreSQL/PostGIS, Flyway, Redis, MinIO, environments, Docker, CI foundation, backups.  
**M2:** Spring Boot, `/api/v1`, security foundation, tenant context, RBAC, errors, validation, audit foundation.  
**M1:** React Native/TypeScript, Expo/EAS, navigation, secure storage, API client, app config/versioning, auth UI.  
**M4:** FastAPI AI service, job contract, model-serving structure, evidence contract.  
**M5:** integration-test framework, operational module skeleton, GIS/notification contracts.

## 7. Phase 2 — Core NOC
```text
Citizen → NOC Application → Documents/Payment → Verification
→ Checklist → Officer Assignment → Inspection → AI Assistance
→ Officer Review → Approval/Correction → Certificate → Renewal
```
M1 owns client flow; M2 owns business workflow/API; M3 owns schema; M4 owns AI; M5 owns notifications/integration tests.

## 8. Phase 3 — Officer Productivity
Today's Work → Prioritized Assignments → Application Summary → Building History → Navigation → Offline Checklist → Evidence → AI Findings → Draft Report → Officer Review → Submit.

Goal: reduce repetitive administrative work, not merely digitize it.

## 9. Phase 4 — Operations
Progressively implement:
1. Incident Management
2. Emergency dispatch foundations
3. Fleet/vehicles
4. Fire Store/inventory
5. Training/mock drills
6. GIS operational views
7. Notifications
8. Reports/analytics
9. Historical digitization foundations

## 10. Phase 5 — AI Expansion
Initial: inspection CV, evidence-linked findings, confidence/severity, report drafting.  
Extended: policy/SOP search, source-cited assistant, document extraction/OCR, incident summaries, approved multilingual assistance.

AI never silently approves/rejects a Fire NOC or replaces authorized regulatory decisions.

## 11. Phase 6 — Integration Gate
Must verify:
- DB schema ↔ backend entities
- backend API ↔ frontend wrappers
- authentication/RBAC
- tenant isolation
- offline sync
- file upload/storage
- AI jobs
- GIS
- notifications
- audit
- consistent errors
- no hard-coded production data
- no secrets

## 12. Phase 7 — QA & Release
Test unit, integration, API contracts, mobile critical flows, offline sync, permissions, tenant isolation, file security, payment verification/idempotency, AI failure, notifications, migrations/recovery, critical performance and Android release build.

## 13. Integration Rules
- **M3 owns schema/migrations.** Others submit schema requests.
- **M2 owns core API/auth/NOC contracts.** M5 owns operational APIs.
- **M1 owns client implementation.**
- **M4 owns AI service/model contracts.**
- Shared changes require affected-owner review.
- Never silently rename endpoints, fields, tables, statuses or permissions.

## 14. Git
Recommended branches:
```text
main
├── member-1/frontend-mobile
├── member-2/backend-core
├── member-3/database-infra
├── member-4/ai
└── member-5/operations-integration
```
Use focused commits and reviewed PRs. Never commit secrets or unrelated changes.

## 15. Definition of Done
Schema verified; migration reviewed; API documented; auth/RBAC/tenant isolation verified; validation/errors implemented; audit considered; frontend integrated; offline behavior considered; tests added; OpenAPI updated; observability considered; no secrets; no regression.

## 16. Final Architecture
```text
Citizen/Officer Mobile Apps + Admin Web
                 ↓
            HTTPS / REST
                 ↓
        Spring Boot Backend
          /       |        PostgreSQL/   Redis    Object Storage
  PostGIS         \       /
                   FastAPI AI
                      ↓
             External Integrations
```

**The final product is one integrated application, not five independent projects.**
