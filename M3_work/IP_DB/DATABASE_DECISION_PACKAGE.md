# FireSphere.ai — Database Decision & Contract Freeze Package (Phase 1C)

**Document Status:** Formal Decision Package & Contract Freeze Baseline  
**Authority:** Architectural Audit of [FireSphere_Database_Documentation_v2.xml](file:///d:/IP%20Folder/FireSphere_Database_Documentation_v2.xml) & Legacy SQL Exports (`database_2.sql` to `database_61.sql`)  
**Date:** October 2026  
**Auditor / Preparer:** Member 3 (Database, Infrastructure & DevOps Engineer)  
**Implementation Gate:** **PHYSICAL IMPLEMENTATION STRICTLY PAUSED**

---

## A. Phase 1B Audit Conclusion & Architectural Baseline

1. **Implementation Lock Enforced:**
   Physical database implementation remains **strictly paused**. No SQL table DDL, Flyway migration scripts, Docker Compose files, Redis configurations, MinIO configurations, JPA/ORM entities, Spring/Node repositories, application code, or cloud deployment infrastructure have been created.
2. **Document Status:**
   The primary source document [FireSphere_Database_Documentation_v2.xml](file:///d:/IP%20Folder/FireSphere_Database_Documentation_v2.xml) is an authoritative **candidate architectural specification**, but it is **not yet a fully frozen physical schema contract**. It contains internal contradictions, scope discrepancies, and un-modeled entities that must be formally resolved by designated stakeholders before physical database modeling can commence.
3. **Table Scope Discrepancy:**
   The document mathematically claims **85 total tables** across its narrative sections (Sections 1, 2, 4, 5, 6, and 9) derived from **60 legacy tables** and **25 new required production tables**. However, **Appendix A catalogs 91 numbered tables**, introducing 6 extra tables marked `(NEW)`.
4. **Legacy Inventory State:**
   Exactly **60 legacy tables** exist in the supplied MariaDB export files (`database_2.sql` to `database_61.sql`). The exports are data-only and lack `CREATE TABLE` DDL. Only **12 tables are populated and data-verified**; the remaining **48 tables are completely empty (0 rows)** and their column contracts are legally classified as: *"Not verifiable from supplied SQL dump — requires schema inspection."*
5. **New Production Table Dictionaries:**
   Section 6 provides detailed column-level data dictionaries, data types, nullabilities, and default constraints for **25 tables** (Sections 6.1 through 6.25).
6. **Missing Data Dictionaries:**
   The **6 additional tables** cataloged in Appendix A (`inventory_categories`, `notification_templates`, `payments`, `purchase_order_items`, `purchase_orders`, `vendors`) have **zero column data dictionaries**, data types, or constraint definitions in Section 6.
7. **Missing Critical Entity (`incident_ai_analysis`):**
   `incident_ai_analysis`—explicitly mandated for resolution in Member 3 responsibilities ([03_MEMBER_3_DATABASE_INFRA_DEVOPS.md](file:///d:/IP%20Folder/03_MEMBER_3_DATABASE_INFRA_DEVOPS.md) Line 27)—is **completely absent** from the candidate architecture (0 occurrences across 473,748 bytes of XML).
8. **Tenant Identifier Contradiction:**
   A systemic collision exists between Section 2.2 specifying `organization_id UUID` and Section 6 DDL / Section 8.1 specifying `org_id BIGINT` as the primary relational and Row-Level Security (RLS) key.
9. **Relational & Naming Discrepancies:**
   A mutual foreign key circular dependency exists between `departments` and `users`. Additionally, naming collisions persist between legacy and candidate terms (`sso_user_identities` vs `sso_identities`, and `audit_logs` vs `cdc_audit_logs`).

---

## B. Formal Decision Register

The following register details all pending architectural decisions required to achieve a formal Database Contract Freeze. In accordance with Member 3 constraints, Member 3 does not invent architecture or make authoritative decisions on behalf of other stakeholders. All items remain in a documented, traceable, non-implemented state.

---

### Master Decision Summary Table

| Decision ID | Topic | Current Evidence | Conflicting Sources | Recommended Temporary State | Required Decision Owner | Required Approvers | Blocking Phase 2? | Decision Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **ADR-004** | 85 vs 91 Table Target Scope | Sec 1, 2, 5, 6, 9 state 85 tables (60 legacy + 25 new). App A lists 91 numbered tables. | Sec 1, 2, 9 vs Appendix A (#46, #57, #64, #66, #67, #83). | Scope frozen at 85 tables for initial V1 core; 6 extra tables quarantined. | Platform Architect | Project Lead, Member 1, Member 3 | **YES** | `PROPOSED — PENDING APPROVAL` |
| **ADR-005** | Incident AI Analysis Persistence (`incident_ai_analysis`) | XML has 0 occurrences of `incident_ai_analysis`. Generic `ai_insights` exists in Sec 4.4 / 7.9. | [03_MEMBER_3_DATABASE_INFRA_DEVOPS.md](file:///d:/IP%20Folder/03_MEMBER_3_DATABASE_INFRA_DEVOPS.md) Line 27 vs `FireSphere_Database_Documentation_v2.xml`. | Quarantine dedicated table; require AI Lead to define storage model. | Member 2 (AI Lead) | Platform Architect, Member 1, Member 3 | **YES** | `BLOCKED — UNRESOLVED` |
| **ADR-006** | Tenant Identifier Canonicalization | Sec 2.2 mandates `organization_id UUID`. Sec 6 & 8.1 implement `org_id BIGINT` FK & RLS filter. | Sec 2.2 vs Sec 6.1–6.25 DDL & Sec 8.1 RLS policy. | Dual-key pattern: `id BIGSERIAL` / `org_id BIGINT` for DB joins; `uuid UUID` for public APIs. | Platform Architect & Member 1 | Project Lead, Member 3 | **YES** | `PROPOSED — PENDING APPROVAL` |
| **ADR-007** | Department / User Circular Foreign Key | `departments.head_user_id` -> `users(id)` and `users.department_id` -> `departments(id)`. | Sec 7.2 relational topology vs standard relational insertion order. | Make both FKs nullable; declare `DEFERRABLE INITIALLY DEFERRED`. | Member 3 (Database Engineer) | Member 1 (Backend Lead), Platform Architect | **YES** | `PROPOSED — PENDING APPROVAL` |
| **ADR-008** | `incident_evidence` Scope & Semantics | Legacy table #25 (empty). XML Sec 4.25 / 5 marks `MODIFY` with SHA-256 and MinIO key. | Legacy empty dump vs App A #34 vs AI processing pipeline requirements. | S3 storage key + metadata model for raw media; no AI bounding box columns. | Member 2 (AI Lead) & Member 1 | Platform Architect, Member 3 | **YES** | `UNDER REVIEW — PENDING SIGN-OFF` |
| **ADR-009** | SSO Entity Canonical Naming | Legacy file is `database_52.sql` (`sso_user_identities`). App A #71 lists `sso_identities (REPLACED)`. | `database_52.sql` / Sec 4.51 vs Sec 5, Sec 9, App A #71. | Adopt `sso_identities` in target DDL; map legacy extraction in V1.0.5. | Member 1 (Backend / Auth Lead) | Member 3, Platform Architect | **NO** (Can resolve at DDL naming) | `PROPOSED — PENDING APPROVAL` |
| **ADR-010** | Audit Log Table Canonical Naming | Sec 4.11 & App A #11 list `audit_logs`. Sec 9 & 10.1 refer to `cdc_audit_logs`. | Sec 4.11, App A vs Sec 9 table & Sec 10.1 Flyway migration. | Adopt `firesphere.audit_logs` as canonical table; CDC trigger feeds this table. | Member 3 & Member 1 | Platform Architect, Project Lead | **NO** (Can resolve at DDL naming) | `PROPOSED — PENDING APPROVAL` |

---

## Detailed Architectural Decision Records

---

### ADR-004: 85 vs 91 Table Production Target Scope

- **Decision ID**: ADR-004
- **Topic**: Target Table Scope Definition & Missing Table Dictionaries
- **Current Evidence**:
  - The narrative in Sections 1, 2, 4, 5, 6, and 9 explicitly states a production scope of **85 tables** (60 legacy + 25 new required tables).
  - Section 9 Master Metrics table explicitly asserts: *"Total Tables in Final Production Database (Architecture v2.0): 85 Tables."*
  - Section 6 provides column data dictionaries for exactly **25 tables** (Subsections 6.1 through 6.25).
  - Appendix A catalogs **91 numbered tables** (1 to 91), labeling 31 tables as `(NEW)`.
- **Conflicting Sources**:
  - `FireSphere_Database_Documentation_v2.xml` Section 1, 2, 9 vs Appendix A (#46, #57, #64, #66, #67, #83).
- **The Six Missing Dictionaries in Question**:
  1. `inventory_categories` (Appendix A #46)
  2. `notification_templates` (Appendix A #57)
  3. `payments` (Appendix A #64)
  4. `purchase_order_items` (Appendix A #66)
  5. `purchase_orders` (Appendix A #67)
  6. `vendors` (Appendix A #83)
- **Options**:
  1. **Option A (85 Tables — Core Scope):** Freeze target production database at 85 tables. Treat the 6 extra Appendix A tables as Phase 2 extensions or unapproved draft proposals. Omit them from initial Flyway DDL.
  2. **Option B (91 Tables — Expanded Scope):** Authoritatively expand production scope to 91 tables. The Platform Architect must provide complete column-level data dictionaries, data types, nullabilities, and constraints for the 6 missing tables before Flyway DDL can be authored.
  3. **Option C (Hybrid/Phased):** Implement 85 tables in initial migration baseline (`V1.0.1`); deliver the 6 procurement/payment tables in an incremental migration (`V1.1.0`) once dictionaries are authored.
- **Recommended Temporary State**: Option A / Option C baseline. Freeze initial V1 DDL at 85 tables.
- **Required Decision Owner**: Platform Architect
- **Required Approvers**: Project Lead, Member 1 (Backend Lead), Member 3 (Database Engineer)
- **Blocking Phase 2?**: **YES**. Flyway script generation cannot begin without a definitive table count and complete schemas.
- **Decision Status**: `PROPOSED — PENDING APPROVAL`
- **Decision Date**: 2026-10-05
- **Final Decision**: *Pending stakeholder sign-off.*
- **Rationale**: Member 3 cannot guess columns, data types, nullabilities, or foreign keys for the 6 missing tables without violating core pairing rules.

---

### ADR-005: Incident AI Analysis Persistence (`incident_ai_analysis`)

- **Decision ID**: ADR-005
- **Topic**: Incident AI Analysis Storage Contract & Lifecycle
- **Current Evidence**:
  - `03_MEMBER_3_DATABASE_INFRA_DEVOPS.md` (Line 27) explicitly mandates resolving "ambiguous incident_ai_analysis".
  - An exhaustive regex search across `FireSphere_Database_Documentation_v2.xml` yielded **0 occurrences** of `incident_ai_analysis`.
  - The candidate specification defines a generic AI subsystem: `ai_models`, `ai_agents`, `ai_agent_workflows`, `ai_agent_workflow_steps`, `ai_sessions`, `ai_runs`, and `ai_insights`.
  - In Section 4.4, `ai_insights` records polymorphic predictions via columns `(target_entity_type VARCHAR(50), target_entity_id BIGINT, insight_summary TEXT, confidence_score NUMERIC(5,2))`.
- **Conflicting Sources**:
  - `03_MEMBER_3_DATABASE_INFRA_DEVOPS.md` (Line 27) vs `FireSphere_Database_Documentation_v2.xml` (entire document).
- **Options**:
  1. **Option A (Polymorphic AI Insights):** Persist incident AI analysis records as rows within `ai_insights` (`target_entity_type = 'incident'`, `target_entity_id = incident.id`), eliminating the need for a separate table.
  2. **Option B (Dedicated Relational Table):** Create a dedicated table `incident_ai_analysis` linked (N:1) to `incidents`, holding structured incident computer vision detections, fire spread telemetry, risk scores, bounding boxes, and model versions.
  3. **Option C (JSONB Extension on Incidents):** Store AI analysis outputs directly inside a `ai_analysis_json JSONB` column on the `incidents` table.
- **Recommended Temporary State**: Option A as inferred architectural baseline, but marked **BLOCKED** pending Member 2 validation.
- **Required Decision Owner**: Member 2 (AI & Computer Vision Lead)
- **Required Approvers**: Platform Architect, Member 1 (Backend Services), Member 3 (Database Engineer)
- **Blocking Phase 2?**: **YES**. Missing persistence contract directly impacts Member 2's AI inference ingestion pipelines.
- **Decision Status**: `BLOCKED — UNRESOLVED`
- **Decision Date**: 2026-10-05
- **Final Decision**: *Pending Member 2 input.*
- **Rationale**: Member 3 cannot invent table definitions or assume Member 2's pipeline can fit into generic `ai_insights`.

---

### ADR-006: Tenant Identifier Canonicalization (`organization_id UUID` vs `org_id BIGINT`)

- **Decision ID**: ADR-006
- **Topic**: Tenant Key Name & Data Type Standardization
- **Current Evidence**:
  - Section 2.2 states: *"Every tenant-aware table includes an organization_id UUID foreign key referencing organizations(id)."*
  - Section 6 defines `organizations.id` as `BIGSERIAL` (BIGINT) and `organizations.uuid` as `UUID`.
  - Section 6 child tables (e.g. 6.1 `building_contacts`, 6.6 `incident_dispatches`, 6.19 `vehicle_crew`) define tenant keys as `org_id BIGINT REFERENCES organizations(id)`.
  - Section 8.1 defines RLS policy filter as `WHERE (org_id = current_setting('app.current_org_id')::BIGINT)`.
- **Conflicting Sources**:
  - Section 2.2 narrative vs Section 6 DDL specifications & Section 8.1 RLS implementation.
- **Affected System Areas**:
  1. `organizations` primary and alternate key definitions.
  2. All tenant-aware child tables (naming and foreign key constraints).
  3. PostgreSQL Row-Level Security (RLS) policy definitions.
  4. Session context injection functions (`set_tenant_context()`).
  5. JWT token claims structure (`org_id` numeric vs `organization_id` UUID).
  6. Backend JPA/Hibernate entity mappings and Spring Data repositories.
  7. Public REST/GraphQL API contracts (client-facing resource locators).
  8. CDC audit log schemas (`audit_logs.organization_id`).
- **Options**:
  1. **Option A (Pure UUID Architecture):** Use `id UUID PRIMARY KEY DEFAULT gen_random_uuid()` on `organizations` and `organization_id UUID` on all child tables. Eliminate numeric serial IDs entirely.
  2. **Option B (Dual-Key High-Performance Architecture — Recommended):** 
     - **Internal Persistence:** `id BIGSERIAL PRIMARY KEY` and `org_id BIGINT NOT NULL REFERENCES organizations(id)` on child tables for compact B-tree indexes, minimal storage bloat, and fast joins.
     - **External Identity:** `uuid UUID DEFAULT gen_random_uuid() UNIQUE NOT NULL` on `organizations` and all entities for public API exposure and mobile sync.
     - **RLS Policy:** `(org_id = current_setting('app.current_org_id')::BIGINT)`.
  3. **Option C (Pure BIGINT Architecture):** Use `org_id BIGINT` everywhere, exposing numeric IDs via API (High security risk, exposes entity enumeration vulnerabilities).
- **Recommended Temporary State**: Option B (Dual-Key Architecture). Standardize on `org_id BIGINT` for internal relational persistence and `uuid UUID` for public endpoints.
- **Required Decision Owner**: Platform Architect & Member 1 (Backend Lead)
- **Required Approvers**: Project Lead, Member 3 (Database Engineer)
- **Blocking Phase 2?**: **YES**. Must be finalized before DDL table creation and RLS script authoring.
- **Decision Status**: `PROPOSED — PENDING APPROVAL`
- **Decision Date**: 2026-10-05
- **Final Decision**: *Pending stakeholder sign-off.*
- **Rationale**: Dual-key provides optimal PostgreSQL performance while securing APIs against enumeration attacks.

---

### ADR-007: Department / User Circular Foreign Key Deadlock

- **Decision ID**: ADR-007
- **Topic**: Mutual Relational Dependency Between `departments` and `users`
- **Current Evidence**:
  - Section 7.2 states: *"departments (head_user_id) links (N:1) to users. users (department_id) links (N:1) to departments."*
  - Populated legacy data in `database_19.sql` (`departments`) and `database_53.sql` (`users`) demonstrates both tables are active simultaneously.
- **Conflicting Sources**:
  - Section 7.2 ER relational topology vs relational DDL execution constraints.
- **Options**:
  1. **Option A (Deferred Constraint Execution — Recommended):**
     - Define `departments.head_user_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED`.
     - Define `users.department_id BIGINT NULL REFERENCES departments(id) ON DELETE SET NULL`.
     - Initial schema creates tables cleanly; transaction blocks allow mutually dependent inserts.
  2. **Option B (Decouple Head of Department to Mapping Table):**
     - Remove `head_user_id` from `departments`.
     - Create an explicit assignment table `department_leadership (department_id, user_id, start_date, is_active)`.
  3. **Option C (Break Circularity):** Make `departments.head_user_id` an unconstrained scalar integer without a foreign key constraint. (Violates relational integrity rules).
- **Recommended Temporary State**: Option A (`DEFERRABLE INITIALLY DEFERRED`).
- **Required Decision Owner**: Member 3 (Database Engineer)
- **Required Approvers**: Member 1 (Backend Lead), Platform Architect
- **Blocking Phase 2?**: **YES**. Initial Flyway migration DDL script cannot execute without resolving this circular constraint.
- **Decision Status**: `PROPOSED — PENDING APPROVAL`
- **Decision Date**: 2026-10-05
- **Final Decision**: *Pending Member 1 approval.*
- **Rationale**: Option A preserves strict foreign key validation without adding table overhead or breaking relational integrity.

---

### ADR-008: `incident_evidence` Scope & Semantics

- **Decision ID**: ADR-008
- **Topic**: Evidence Entity Scope, Media Storage, and AI Annotations
- **Current Evidence**:
  - Legacy table #25 (`database_26.sql`) contained `incident_evidence` with 0 rows and no DDL.
  - Section 4.25 defines inferred columns: `id`, `incident_id`, `evidence_type`, `file_path`, `file_hash_sha256`, `captured_at`, `uploaded_by`.
  - Section 5 marks it `MODIFY`: adds `organization_id`, SHA-256 hash, and GPS coordinates.
  - Appendix A lists it as table #34.
- **Conflicting Sources**:
  - Legacy empty dump vs Section 4.25 vs AI computer vision bounding box ingestion requirements.
- **Options**:
  1. **Option A (Raw Evidence Store Only):** `incident_evidence` strictly stores raw media references (MinIO S3 object keys, MIME types, file sizes, SHA-256 hashes, capture coordinates, and uploader IDs). AI predictions and annotations are stored in `ai_insights`.
  2. **Option B (Combined Media & Annotation Store):** Add `ai_detection_json JSONB`, `hazard_labels TEXT[]`, and `processed_status VARCHAR(50)` directly onto `incident_evidence`.
  3. **Option C (Parent-Child Evidence Hierarchy):** Retain `incident_evidence` for raw files and create `incident_evidence_annotations` for AI bounding boxes.
- **Recommended Temporary State**: Option A (Raw media storage).
- **Required Decision Owner**: Member 2 (AI Lead) & Member 1 (Backend Lead)
- **Required Approvers**: Platform Architect, Member 3 (Database Engineer)
- **Blocking Phase 2?**: **YES**. Affects column definitions in `V1.0.1__ddl_relational_schema.sql` and MinIO bucket key path contracts.
- **Decision Status**: `UNDER REVIEW — PENDING SIGN-OFF`
- **Decision Date**: 2026-10-05
- **Final Decision**: *Pending Member 1 & Member 2 sign-off.*
- **Rationale**: Separation of concerns between immutable evidence capture and versioned AI model inferences.

---

### ADR-009: SSO Entity Canonical Naming

- **Decision ID**: ADR-009
- **Topic**: SSO Identity Table Canonical Naming Standard
- **Current Evidence**:
  - Legacy dump file `database_52.sql` is named `sso_user_identities`.
  - Section 4.51 audits legacy table 51 as `sso_user_identities`.
  - Section 5 and Section 9 assert: *"REPLACE: sso_user_identities -> sso_identities"*.
  - Appendix A lists item #71 as `sso_identities (REPLACED)`.
- **Conflicting Sources**:
  - Legacy SQL filename and Section 4.51 vs Section 5, Section 9, and Appendix A (#71).
- **Options**:
  1. **Option A (Adopt `sso_identities`):** Use `sso_identities` in schema `firesphere`. In data migration `V1.0.5`, map legacy records from `sso_user_identities` to `sso_identities`.
  2. **Option B (Retain Legacy `sso_user_identities`):** Retain the legacy name `sso_user_identities` across all DDL and migrations to maintain 1:1 parity with legacy MariaDB exports.
- **Recommended Temporary State**: Option A (`sso_identities`).
- **Required Decision Owner**: Member 1 (Backend / Auth Lead)
- **Required Approvers**: Member 3 (Database Engineer), Platform Architect
- **Blocking Phase 2?**: **NO** (Does not block architectural modeling, but requires confirmation before DDL naming freeze).
- **Decision Status**: `PROPOSED — PENDING APPROVAL`
- **Decision Date**: 2026-10-05
- **Final Decision**: *Pending Member 1 approval.*
- **Rationale**: `sso_identities` is cleaner, matches modern Auth0/Keycloak identity terminology, and aligns with Section 9.

---

### ADR-010: Audit Log Canonical Naming

- **Decision ID**: ADR-010
- **Topic**: Audit Log Table Canonical Naming Standard
- **Current Evidence**:
  - Section 4.11 and Appendix A (#11) list `audit_logs`.
  - Section 8.3 describes trigger implementation targeting `audit_logs`.
  - Section 9 transformation table asserts: *"audit_logs -> cdc_audit_logs"*.
  - Section 10.1 designates Flyway migration as `V1.0.4__cdc_audit_triggers.sql`.
- **Conflicting Sources**:
  - Section 4.11 & Appendix A (#11) vs Section 9 transformation summary.
- **Options**:
  1. **Option A (Canonical `audit_logs`):** Designate `firesphere.audit_logs` as the canonical physical table. Clarify that "CDC" refers to the trigger mechanism, not the table name.
  2. **Option B (Canonical `cdc_audit_logs`):** Name the physical table `firesphere.cdc_audit_logs` to explicitly differentiate it from legacy application-level audit logs.
- **Recommended Temporary State**: Option A (`firesphere.audit_logs`).
- **Required Decision Owner**: Member 3 (Database Engineer) & Member 1 (Backend Lead)
- **Required Approvers**: Platform Architect, Project Lead
- **Blocking Phase 2?**: **NO** (Internal naming alignment).
- **Decision Status**: `PROPOSED — PENDING APPROVAL`
- **Decision Date**: 2026-10-05
- **Final Decision**: *Pending stakeholder sign-off.*
- **Rationale**: `audit_logs` is standard enterprise terminology, matches Appendix A, and avoids awkward table naming.
