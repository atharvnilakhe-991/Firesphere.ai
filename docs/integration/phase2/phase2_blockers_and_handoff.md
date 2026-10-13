# FireSphere.ai — Phase 2: Blockers, Architectural Decisions & Handoff Report
**Document Reference:** `docs/integration/phase2/phase2_blockers_and_handoff.md`  
**Status:** HANDOFF READY  
**Authors:** Senior Backend Integration Engineer, Python FastAPI Engineer, Java Spring Boot Engineer, QA Engineer  
**Date:** October 2026  

---

## 1. Executive Summary

Phase 2 successfully resolved all direct interface and contract incompatibilities between Member 2 (Spring Boot Backend) and Member 4 (FastAPI AI Service). The communication contract is now locked, test-backed, and aligned across endpoints, authentication, request schemas, response schemas, and error boundaries.

However, full end-to-end runtime integration across the entire five-member system requires addressing cross-cutting architectural prerequisites in Member 2 (persistence/evidence mapping), Member 3 (database schema and S3 bucket provisioning), and Member 4 (CV model calibration).

---

## 2. Unresolved Architectural Decisions & Cross-Member Prerequisites

### 2.1 Member 2 (Backend) Prerequisites
1. **Document-to-Evidence Resolution Service:**
   - **Current State:** M2 currently persists `noc_documents` with columns `(id, applicationId, storageReference, documentType)`. When an inspection is conducted, M2 does not have an internal service or database table mapping an uploaded plan/document into persistent inspection `evidenceIds: List[UUID]`.
   - **Prerequisite:** M2 must implement an `EvidenceResolutionService` or expose inspection photo capture endpoints that persist physical inspection photos with their own distinct UUIDs before invoking M4.
   - **Anti-Fabrication Decision:** In Phase 2, M2 was programmed to reject requests missing `evidenceIds` with `HTTP 400 Bad Request` rather than fabricating synthetic identifiers or substituting `documentId` for `evidenceId`. This ensures data integrity.
2. **Inspection Linkage at Submission:**
   - When AI analysis is requested at the desk-review stage (prior to scheduling physical site inspections), M2 needs a formal policy on whether an automated pre-inspection record is created, or whether a separate desk-review analysis endpoint should be designated.

### 2.2 Member 3 (Database & Storage) Prerequisites
1. **Inspection Evidence Persistence Schema:**
   - **Current State:** M3's database migrations (V1.0.1 through V1.0.5) include `incident_evidence` (dedicated to emergency response dispatches) and an unindexed `photo_evidence_s3_key` column in `inspection_findings`. There is no dedicated `inspection_evidence` table linking multiple photos to an inspection session.
   - **Prerequisite:** In Phase 3, an approved database migration must introduce either:
     - Option A: A dedicated `inspection_evidence` table `(id UUID, inspection_id UUID, s3_key TEXT, sha256 TEXT, mime_type TEXT, uploaded_at TIMESTAMPTZ)`.
     - Option B: A polymorphic `evidence_media` table shared between incidents and inspections.
2. **MinIO S3 Bucket Provisioning:**
   - M4 expects evidence files in `firesphere-evidence` or `firesphere-photos`. M3's MinIO container must be running and pre-seeded with bucket access policies accessible by both M2 and M4.

### 2.3 Member 4 (AI / Computer Vision) Prerequisites
1. **Model Calibration Gate (`CALIBRATION_STATUS`):**
   - **Current State:** M4 models return `calibrationStatus = "NOT_CALIBRATED"`.
   - **Prerequisite:** Temperature scaling and empirical validation on local fire safety datasets (NFPA / NBC India standards) must be conducted before model confidence can be calibrated.
2. **Production Deployment Approval Gate (`MODEL_APPROVAL_STATUS`):**
   - **Current State:** M4 models return `modelApprovalStatus = "NOT_APPROVED"`.
   - **Prerequisite:** Formal sign-off by a certified Fire Safety Authority is required before the model gate can be transitioned to `APPROVED`.
3. **Gemini VLM Integration (Phase 4):**
   - Gemini VLM multi-modal orchestration remains strictly disabled (`GEMINI_ENABLED=false`). In Phase 4, an external API key and cloud proxy must be configured if multi-modal visual QA is desired.

---

## 3. Operational Invariants & Boundary Confirmation

The following safety and operational invariants were maintained without compromise throughout Phase 2:

| Invariant | Status | Verification Evidence |
| :--- | :--- | :--- |
| **Advisory-Only Safety Boundary** | **ENFORCED** | Responses explicitly include `complianceDecisionNote` and disclaim autonomous approval. Pydantic validation rejects `AI_APPROVED` / `AI_REJECTED`. |
| **CV Gate State** | **NOT_APPROVED** | All tests assert `modelApprovalStatus == "NOT_APPROVED"` and `calibrationStatus == "NOT_CALIBRATED"`. |
| **Gemini Integration** | **DISABLED** | Verified `settings.gemini_enabled == False`. Zero external requests made. |
| **Database Migrations** | **UNTOUCHED** | Zero Flyway or SQL migration files modified. Schema unchanged. |
| **Member 1 (Frontend)** | **UNTOUCHED** | Zero files modified in M1 citizen/officer applications. |
| **Member 5 (GIS Service)** | **UNTOUCHED** | Zero files modified in M5 GIS service. |
| **Local Git History** | **CLEAN** | Zero git write operations performed (`git reset`, `git commit`, `git push`, etc. were never run). |

---

## 4. Phase 3 Integration Roadmap & Handoff Next Steps

1. **Phase 3 Focus:** M2–M3 Database & Object Storage Integration.
   - Establish live local Docker Compose environment running PostgreSQL 16 and MinIO.
   - Formalize evidence upload workflow: Citizen/Officer uploads photo to MinIO via M2 -> M2 records `evidenceId` in PostgreSQL -> M2 triggers AI analysis with real `evidenceIds`.
2. **Phase 4 Focus:** M4 Model Calibration & Verification Gate.
   - Implement temperature scaling calibration routines.
   - Wire authorized Officer Verification workflow (`/internal/ai/inspection-analysis/{inspection_id}/verify-finding`).
3. **Phase 5 Focus:** M1 Frontend AI Inspection Result Presentation.
   - Render bounding boxes, risk levels, and advisory notices in the officer inspection dashboard.
