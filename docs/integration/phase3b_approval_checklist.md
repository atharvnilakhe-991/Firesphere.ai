# FireSphere.ai — Phase 3B: Pre-Implementation Approval Checklist & Gate

**Author**: Senior Database Architect, Spring Boot Persistence Engineer, PostgreSQL/PostGIS Engineer, AI Service Integration Engineer  
**Status**: PRE-IMPLEMENTATION DESIGN / APPROVAL GATE  
**Date**: October 2026  
**Phase**: Phase 3B (Controlled Database & Evidence Integration)  

---

## 1. Executive Summary

This checklist establishes the formal **Pre-Implementation Approval Gate** for Phase 3B. Implementation changes (code edits, migration authoring, dependency additions, database execution) MUST NOT begin until every item in this checklist is formally signed off by the responsible team leads.

---

## 2. Technical Facts Verified Directly from Source Code

These items have been verified by direct static analysis of repository files:

- [x] **FACT 1 (M3 Schema Scope)**: Migrations `V1.0.0` through `V1.0.6` define 44 tables strictly in `firesphere.` schema with `id BIGSERIAL PRIMARY KEY` and `uuid UUID UNIQUE` on core tables. Verified in [`M3_work/IP_DB/database/migrations/`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/database/migrations).
- [x] **FACT 2 (M3 Missing Tables)**: Neither AI tables (`ai_analysis_runs`, `ai_findings`, `ai_officer_reviews`) nor inspection photo tables (`inspection_evidence`) exist in M3. Verified in [`M3_work/IP_DB/DATABASE_AI_SCHEMA_BLOCKER.md`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M3_work/IP_DB/DATABASE_AI_SCHEMA_BLOCKER.md).
- [x] **FACT 3 (M2 Primary Key Type)**: All 13 JPA entities in M2 define `@Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id`. Confirmed mismatch with M3's `BIGINT` primary keys. Verified in [`M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend).
- [x] **FACT 4 (M2 Missing Schema Namespace)**: M2 entities lack `schema = "firesphere"` and `application.properties` lacks `currentSchema=firesphere`, defaulting Hibernate to `public`.
- [x] **FACT 5 (M2 Dummy Storage)**: M2 uses [`DummyDocumentStorageService.java`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend/document/service/DummyDocumentStorageService.java) returning in-memory mock strings. Zero S3/MinIO SDK is configured in `pom.xml`.
- [x] **FACT 6 (M4 Storage Implementation)**: M4 has a functional [`EvidenceClient`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/storage/evidence_client.py) and [`S3StorageProvider`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/storage/s3_storage.py) expecting objects at `tenants/{tenantId}/evidence/{evidenceId}`.
- [x] **FACT 7 (M4 Ephemeral Repositories)**: M4's `InMemoryJobRepository` and `InMemoryFindingRepository` are in-memory Python dictionaries. M4 has zero direct PostgreSQL credentials.
- [x] **FACT 8 (M2 Open Transaction Anti-Pattern)**: [`AiOrchestrationService.requestAnalysis()`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/M2_work/Firesphere%20m2/backend/src/main/java/com/firesphere/backend/ai/service/AiOrchestrationService.java#L57) executes blocking HTTP call `aiClient.analyze()` inside Spring `@Transactional`.
- [x] **FACT 9 (PostgreSQL RLS Runtime Reality)**: `V1.0.5` enables RLS requiring session variable `app.current_org_id`. M2 does not set this session variable and connects as superuser `postgres` (bypassing RLS). Runtime isolation currently relies exclusively on application-level filtering.
- [x] **FACT 10 (Local PostgreSQL Status)**: Local PostgreSQL port 5432 is currently closed/stopped. Audit was executed strictly offline.

---

## 3. Decisions Requiring M2 Approval (Spring Boot Backend Lead)

The Member 2 Lead must review and approve:

- [ ] **M2-DEC-1 (Dual-Key Entity Refactoring)**: Approve refactoring all 13 JPA entities to map `@Id Long id` (internal PK) and `@Column(name="uuid") UUID uuid` (public business key).
- [ ] **M2-DEC-2 (Schema Namespacing)**: Approve setting `spring.jpa.properties.hibernate.default_schema=firesphere` and `currentSchema=firesphere` in `application.properties` with `ddl-auto=validate`.
- [ ] **M2-DEC-3 (Two-Phase Transaction Redesign)**: Approve removing `@Transactional` from the outer `requestAnalysis()` method, decoupling HTTP calls from HikariCP database connection holding.
- [ ] **M2-DEC-4 (Evidence Upload Controller)**: Approve introduction of `InspectionEvidenceController` (`POST /api/v1/inspections/{id}/evidence`) with binary magic byte validation and SHA-256 computation.
- [ ] **M2-DEC-5 (MinIO Object Storage Client)**: Approve adding AWS S3 / MinIO Java SDK dependency to `pom.xml` and replacing `DummyDocumentStorageService` with `S3StorageService`.
- [ ] **M2-DEC-6 (AI Entities & Repositories)**: Approve introduction of `NocInspectionEvidence`, `AiAnalysisRun`, `AiFinding`, `AiOfficerReview`, and `AiFindingAudit` JPA entities in M2.

---

## 4. Decisions Requiring M3 Approval (Database & DevOps Lead)

The Member 3 Lead must review and approve:

- [ ] **M3-DEC-1 (Flyway V1.0.7 Script Approval)**: Approve the formal DDL for `V1.0.7__create_inspection_evidence_and_ai_tables.sql`.
- [ ] **M3-DEC-2 (Dual-Key Retention)**: Approve retaining `id BIGSERIAL PRIMARY KEY` internally in M3 and confirming that M3's existing `uuid UUID UNIQUE` columns serve as public business keys.
- [ ] **M3-DEC-3 (Inspection Evidence Table Introduction)**: Approve adding `firesphere.inspection_evidence` linked to `inspections(id)` and `organizations(id)` rather than overloading `building_documents`.
- [ ] **M3-DEC-4 (Deletion Cascade Rules)**: Approve `ON DELETE CASCADE` for runs -> findings, and `ON DELETE RESTRICT` for organizations, inspections, and evidence references.
- [ ] **M3-DEC-5 (RLS Session Context)**: Approve whether M2 should set session variable `app.current_org_id` on JDBC checkout, or if application-level filtering in Spring Data JPA is accepted for Phase 3B.

---

## 5. Decisions Requiring M4 Approval (AI/ML Lead)

The Member 4 Lead must review and approve:

- [ ] **M4-DEC-1 (AI Finding Schema Alignment)**: Confirm that `ai_findings` columns (`finding_type`, `severity`, `confidence`, `bounding_box JSONB`, `verification_guidance`) accurately and losslessly capture all outputs from M4's CV and VLM inference models.
- [ ] **M4-DEC-2 (Evidence Storage Key Convention)**: Confirm standardization on MinIO key format: `tenants/{tenantId}/evidence/{evidenceId}.jpg` across both M2 upload and M4 download.
- [ ] **M4-DEC-3 (Stateless Inference Role)**: Confirm that M4 will NOT attempt direct database writes, remaining a stateless inference worker with in-memory caching while M2 acts as the sole authoritative writer.

---

## 6. Decisions Requiring Joint Approval (M2 + M3 + M4 Leads)

All three leads must jointly sign off on:

- [ ] **JOINT-DEC-1 (Standardization on Strategy C: Dual-Key Architecture)**: Formal agreement that internal primary keys are 64-bit `BIGINT` and external API keys are 128-bit `UUID`.
- [ ] **JOINT-DEC-2 (Statutory Advisory Lock)**: Formal agreement that database check constraint `chk_finding_statutory_advisory (is_advisory = TRUE)` will be enforced on all AI findings to guarantee statutory compliance.
- [ ] **JOINT-DEC-3 (Evidence Identity Ownership)**: Formal agreement that M2 assigns and owns persistent evidence UUIDs, transmitting them to M4 for inference and linking them to persisted findings.

---

## 7. Blockers Preventing Phase 3B Implementation

The following technical blockers must be resolved before executing implementation tasks:

1. **BLOCKER 1: PostgreSQL Instance Offline**: Local PostgreSQL on port 5432 is currently stopped. Implementation tasks requiring migration execution or integration test validation cannot run until the database container is started.
2. **BLOCKER 2: MinIO S3 Instance Offline**: MinIO object storage is required for testing genuine evidence photo upload and download.
3. **BLOCKER 3: Team Lead Sign-Off**: The approval decisions in Sections 3, 4, 5, and 6 must be formally granted by the respective team leads.

---

## 8. Exact Criteria Satisfied to Open Phase 3B Implementation

Phase 3B implementation may proceed **ONLY WHEN**:

1. [x] Phase 3A Database Schema Audit is complete and documented.
2. [x] Phase 3A Identifier Strategy is formally recommended with evidence.
3. [x] Phase 3A Evidence Resolution and Storage Audit is complete.
4. [x] Phase 3B Architecture Dependency Map is documented.
5. [x] Phase 3B Database Schema Review is documented.
6. [x] Phase 3B Identifier Mapping Review is documented.
7. [x] Phase 3B Evidence Workflow Design is documented.
8. [x] Phase 3B Transaction & Security Design is documented.
9. [ ] Formal approval is recorded for M2-DEC-1 through M2-DEC-6.
10. [ ] Formal approval is recorded for M3-DEC-1 through M3-DEC-5.
11. [ ] Formal approval is recorded for M4-DEC-1 through M4-DEC-3.
12. [ ] Formal approval is recorded for JOINT-DEC-1 through JOINT-DEC-3.
13. [ ] Local PostgreSQL container is started on port 5432.
14. [ ] Local MinIO container is started on port 9000.
