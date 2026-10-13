# FireSphere.ai — M1 Frontend and M5 GIS Integration Assessment

**Document Identifier:** `FS-DOC-PHASE1-M1-M5-INTEGRATION-20261010`  
**Phase:** 1 — Workspace Audit and Integration Design  
**Local Workspace Root:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**Date:** 2026-10-10  
**Classification:** Client UI and Spatial Infrastructure Integration Audit  

---

## 1. Executive Summary

This document audits the client-side user interfaces developed by **Member 1 (Admin Web Console & Mobile App)** and the geospatial/operational capabilities developed by **Member 5 (GIS, Hydrants, Hazard Zones, Dispatch, Fleet & Inventory)**.

Both members have produced mature, feature-rich implementations within their isolated boundaries. However, neither subsystem is currently connected to the central backend (Member 2) or the central database (Member 3). 
- **Member 1** operates entirely on local mock services, fixtures, and local SQLite/SQLCipher storage, failing closed when backend contracts are missing.
- **Member 5** is an independent, standalone Spring Boot 3.3.4 microservice (`ai.firesphere.firesphere-operations`) with its own Flyway migrations (V1–V6) and mock navigation providers.

---

## 2. Member 1 Frontend Integration Audit (Task C)

### 2.1 Workspace Structure & Technologies
Member 1 is located in [`M1_work/`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work):
- **Admin Web Console (`admin-console/`):** Built with React 18, TypeScript, and Vite. Implements multi-tenant administrative dashboards, organization management, audit logs, and inspection reviews.
- **Mobile Application (`mobile/`):** Built with Expo React Native (SDK 57) and TypeScript. Implements Citizen NOC submission workflows and Officer on-site inspection workflows.
- **Shared API Client (`packages/api-client/`):** Implements Axios-based HTTP client with bounded retry policies and fail-closed contract enforcement.

### 2.2 Client Workflow Reality Check
1. **Citizen Application Submission:** Implemented in [`CitizenNocFlow.tsx`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/mobile/src/features/citizen/CitizenNocFlow.tsx). Form captures applicant details, building attributes, occupancy category, and document attachments. Saves drafts locally in encrypted SQLite/SQLCipher. It does not transmit payloads to M2.
2. **Document Upload:** Documents are selected via Expo ImagePicker/DocumentPicker. Upload progress and validation states are simulated locally in [`mockServices.ts`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/mobile/src/dev/mockServices.ts). No remote multipart upload is executed.
3. **Officer Inspection Flow:** Implemented in [`OfficerInspectionFlow.tsx`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/mobile/src/features/officer/OfficerInspectionFlow.tsx). Displays assigned inspections, GPS capture with accuracy level indicators, checklist verification items, local evidence photo capture, and advisory AI findings.
4. **AI Findings & Advisory Report Display:** Rendered using mock finding objects with static badges (**AI ADVISORY ONLY**). Displays bounding box overlays and explanation dimensions (WHAT, WHERE, WHY, VERIFY).
5. **Officer Verification & Override:** Allows the officer to tap "Confirm Observation", "Dismiss Observation", or "Add Remarks". Updates local sync queue in [`syncQueue.ts`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/mobile/src/storage/syncQueue.ts); does not contact M2 backend.
6. **Final Decision Workflow:** Renders an approval/rejection preview with clear statutory disclaimers. In demo preview mode, clicking "Approve" transitions local mock state to `APPROVED` and generates a watermarked preview certificate.

### 2.3 M1 ↔ M2 Frontend Integration Gap Table

| UI Feature / Screen | M1 Client Implementation | M2 Backend Target API | Contract Status | Integration Gap & Required Alignment |
| :--- | :--- | :--- | :---: | :--- |
| **Authentication & Session** | [`authAdapter.ts`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/mobile/src/auth/authAdapter.ts) (Local mock tokens in SecureStore) | `POST /api/v1/auth/login`<br>`POST /api/v1/auth/register` | `DISCONNECTED` | M1 needs to replace `mockServices.login` with actual call to M2 `/api/v1/auth/login` returning JWT bearer token. |
| **Organization Tenant Scope** | Development preview selector in [`App.tsx`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/admin-console/src/App.tsx) | `GET /api/v1/organizations`<br>`TenantContext` | `DISCONNECTED` | M1 admin console must fetch real organization list and attach `organizationId` in JWT / tenant header. |
| **NOC Application Submission** | Form in [`CitizenNocFlow.tsx`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/mobile/src/features/citizen/CitizenNocFlow.tsx) | `POST /api/v1/noc/applications`<br>Payload: `CreateNocRequest` | `DISCONNECTED` | Field mismatch: M1 uses client camelCase fields (`buildingName`, `totalAreaSqFt`); M2 expects `CreateNocRequest` DTO structure. |
| **Document Upload** | Simulated in [`mockServices.ts`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/mobile/src/dev/mockServices.ts) | `POST /api/v1/documents/upload`<br>Multipart form data | `DISCONNECTED` | M1 must implement genuine multipart `FormData` upload to M2 document controller. |
| **Dynamic Checklist** | Static items (`CHK-001`, `CHK-002`) | `POST /api/v1/noc/applications/{id}/checklists`<br>`GET .../checklists` | `DISCONNECTED` | M1 must fetch dynamically generated checklist rules from M2 rather than using hardcoded items. |
| **Officer Assignment List** | Fixture assignments in [`mockServices.ts`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/admin-console/src/dev/mockServices.ts) | `GET /api/v1/inspections/assigned`<br>Pageable response | `DISCONNECTED` | M1 officer mobile app must consume M2 paginated `InspectionResponse` list. |
| **AI Findings Display** | Mock findings in [`OfficerInspectionFlow.tsx`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/mobile/src/features/officer/OfficerInspectionFlow.tsx) | Endpoint currently missing on M2 (M2 persists to `AiAnalysisRun`, but lacks finding query endpoint) | `BLOCKED` | M2 must provide `GET /api/v1/inspections/{id}/ai-findings` returning harmonized findings to M1. |
| **Officer Finding Verification** | Local state in [`syncQueue.ts`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M1_work/mobile/src/storage/syncQueue.ts) | Endpoint currently missing on M2 (M4 has `/verify-finding`, but M2 has no bridge) | `BLOCKED` | M2 must expose `POST /api/v1/inspections/{id}/findings/{findingId}/verify` and forward to M4 / PostgreSQL. |
| **Statutory Approval Decision** | Simulated watermarked preview | `POST /api/v1/noc/applications/{id}/approve`<br>Guarded by RBAC | `DISCONNECTED` | M1 must wire "Approve NOC" button to M2 workflow transition endpoint, requiring authorized officer JWT. |

---

## 3. Member 5 GIS & Operations Integration Audit (Task I)

### 3.1 Workspace Structure & Implemented Modules
Member 5 is located in [`M5_work/Industry M5`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M5_work/Industry%20M5):
- **Base Framework:** Spring Boot 3.3.4, Java 21, Hibernate Spatial, JTS Topology Suite 1.19.0, Flyway.
- **Implemented Subsystems:**
  1. **GIS Foundation (`ai.firesphere.gis.foundation`):** Strict GPS coordinate validation ($[-90, +90]$, $[-180, +180]$), telemetry accuracy categorization (HIGH $\le 15$m, MODERATE $15$–$50$m, POOR $> 50$m), Haversine proximity calculations, and provider-neutral navigation handoff ([`gis-foundation.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M5_work/Industry%20M5/docs/member5/gis-foundation.md)).
  2. **PostGIS Operational Mapping (`ai.firesphere.gis`):** PostGIS GiST spatial indexing, spatial entities (`Hydrant`, `HazardZone`), SRID 4326 JTS geometry serialization, dynamic 32-point hazard zone polygon generation ([`gis-postgis.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M5_work/Industry%20M5/docs/member5/gis-postgis.md)).
  3. **Emergency Dispatch & Incident Lifecycle (`ai.firesphere.dispatch`, `ai.firesphere.incident`):** Real-time incident tracking, dispatch recommendations, unit status timeline.
  4. **Fleet & Inventory (`ai.firesphere.fleet`, `ai.firesphere.inventory`):** Fire engine tracking, equipment readiness, inventory stock movements.
  5. **Notification Orchestration (`ai.firesphere.notification`):** Multi-channel notifications (SMS, WhatsApp, Push, Email) with template rendering and retry executors.
  6. **External Integration Provider Framework (`ai.firesphere.integration`):** Decoupled provider contracts with safe deterministic mocks (`MockMapsProvider`, `MockPaymentProvider`, `MockStorageProvider`).

### 3.2 M5 Integration Reality Check
- **No Shared Spring Context:** M5 defines its own Spring Boot application context (`ai.firesphere.FireSphereApplication`). It does not run inside M2's context (`com.firesphere.backend.FiresphereBackendApplication`).
- **Database Partitioning:** M5 maintains its own Flyway migrations `V1` through `V6__gis_postgis_operational_mapping.sql` in its resources directory, distinct from M3's central Flyway scripts in `M3_work/IP_DB`.
- **Navigation Handoff:** Fully operational in deterministic mock mode. Produces RFC-5870 standard `geo:lat,lng` URIs as well as Google Maps, Apple Maps, and Waze URLs for mobile officer handoff without contacting third-party tile APIs.

### 3.3 M5 Integration Gap Table

| M5 Capability / Module | Current M5 State | FireSphere Target Integration Path | Integration Status | Required Actions & Architectural Decisions |
| :--- | :--- | :--- | :---: | :--- |
| **GIS Foundation & Validation** | Standalone REST API `/api/v1/gis/foundation` | Embed validation and navigation handoff into M1 Mobile App & M2 Inspection Service | `DISCONNECTED` | Expose M5 GIS endpoints through M2 backend gateway or import `ai.firesphere.gis.foundation` as a shared Maven library. |
| **Hydrant & Infrastructure Registry** | Spring Data JPA on `gis_hydrants` table | Connect to M3 central database `firesphere.hydrants` table | `BLOCKED` | Reconcile M5 `gis_hydrants` entity with M3's `firesphere.hydrants` table in migration `V1.0.2`. |
| **Hazard Zones** | Spring Data JPA on `gis_hazard_zones` table | Link to premise safety profiles and inspection risk assessment | `DISCONNECTED` | Expose hazard zone perimeter queries to M2 inspection scheduling and M4 risk scoring. |
| **Officer Navigation Handoff** | Returns RFC-5870 `geo:` URIs and map URLs | Integrate into M1 Officer Mobile App assignment screen | `MOCK_ONLY` | Wire M1 mobile app "Navigate to Site" button to invoke M5's `NavigationHandoffService`. |
| **Emergency Incident & Dispatch** | Standalone controllers `/api/v1/incidents`, `/api/v1/dispatch` | Secondary operational module alongside Fire NOC | `DEFERRED` | Defer active incident dispatch to Phase 7/9; focus initial integration on core Fire NOC inspections. |
| **Fleet & Inventory** | Standalone controllers `/api/v1/fleet`, `/api/v1/inventory` | Standalone operational modules | `DEFERRED` | Keep operational modules encapsulated; integrate through common database schema. |
| **Notification Orchestration** | Provider abstraction with mock channels | Shared notification service for NOC status updates | `PARTIALLY_COMPATIBLE` | Can be invoked by M2 `WorkflowService` to dispatch SMS/Email notifications on NOC approval/rejection. |

---

## 4. Harmonized Integration Architecture

```mermaid
graph TD
    subgraph M1_MOBILE["M1 — Officer Mobile Client"]
        AssignmentView["Inspection Assignment View"]
        NavButton["'Navigate to Site' Button"]
        ChecklistView["Dynamic Checklist View"]
        PhotoEvidence["Evidence Camera Capture"]
        VerificationView["Advisory Finding Review"]
    end

    subgraph M2_CORE["M2 — Core Backend Gateway (:8080)"]
        NOCController["NOC & Inspection Controller"]
        GISGateway["GIS Proxy / Integration Controller"]
        AIGateway["AI Finding Proxy Controller"]
    end

    subgraph M5_ENGINE["M5 — GIS & Operations Engine"]
        GisNav["NavigationHandoffService (geo: URI / Maps URL)"]
        HydrantService["NearbyInfrastructureService (Hydrants)"]
        HazardService["HazardZoneService (PostGIS Buffers)"]
    end

    subgraph M4_AI["M4 — AI Service (:8000)"]
        Orchestrator["Inspection Analysis Orchestrator"]
        FindingRepo["Finding Repository"]
    end

    %% M1 to M2
    NavButton -->|GET /api/v1/gis/navigation/handoff| GISGateway
    ChecklistView -->|GET /api/v1/noc/applications/{id}/checklists| NOCController
    PhotoEvidence -->|POST /api/v1/documents/upload| NOCController
    VerificationView -->|POST /api/v1/inspections/{id}/findings/{fid}/verify| AIGateway

    %% M2 to M5
    GISGateway -->|Call Provider| GisNav
    GISGateway -->|Query Proximity| HydrantService
    GISGateway -->|Query Hazards| HazardService

    %% M2 to M4
    NOCController -->|POST /internal/ai/inspection-analysis| Orchestrator
    AIGateway -->|POST /internal/ai/findings/{fid}/verify| FindingRepo

    %% Navigation intent returned to mobile OS
    GisNav -.->|Return geo:lat,lng| NavButton
```
