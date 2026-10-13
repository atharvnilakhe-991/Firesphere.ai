# FireSphere.ai — Stakeholder Decision & Contract Freeze Approval Form (Phase 1C)

**Document Purpose:** Authoritative Human-Review Decision Form for Closing Phase 1C  
**Auditor / Custodian:** Member 3 (Database, Infrastructure & DevOps Engineer)  
**Date Issued:** October 2026  
**Implementation Gate:** **STRICTLY CLOSED / LOCKED**  
**Gate Condition:** Physical database implementation (DDL, Flyway scripts, Docker, Redis, MinIO, JPA/code) cannot begin until all decisions in this form are authoritatively signed off by designated stakeholder owners.

---

## Governance Rules & Approval Principles

> [!CRITICAL]
> **GOVERNANCE DIRECTIVE ON ARCHITECTURAL DECISIONS:**
> 1. **A proposed option is not an approved database contract.**
> 2. **A recommendation is not an architectural decision.**
> 3. **Phase 2 may begin only after the required decision owner explicitly approves the applicable contract item.**
> 4. Under no circumstances may Member 3 (Database Engineer) make assumptions, invent missing columns or business rules, or unilaterally approve decisions on behalf of other stakeholders.
> 5. Every decision in this document defaults to status **`UNRESOLVED`**. No final decisions are pre-filled.

---

## Master Stakeholder Approval Roster

| Role / Responsibility | Stakeholder Name / Title | Assigned Decision Scope | Sign-Off Status |
| :--- | :--- | :--- | :--- |
| **Project Lead** | Project Governance & Delivery Lead | ADR-004, ADR-006, ADR-010, ADR-013 | `PENDING REVIEW` |
| **Platform Architect** | Systems & Enterprise Architect | ADR-004, ADR-005, ADR-006, ADR-007, ADR-008, ADR-009, ADR-010, ADR-011, ADR-012, ADR-013 | `PENDING REVIEW` |
| **Member 1** | Backend Services & Domain Lead | ADR-004, ADR-006, ADR-007, ADR-008, ADR-009, ADR-010, ADR-011, ADR-012, ADR-013 | `PENDING REVIEW` |
| **Member 2** | AI Pipeline, Computer Vision & GIS Lead | ADR-005, ADR-008, ADR-011, ADR-012 | `PENDING REVIEW` |
| **Member 3** | Database, Infrastructure & DevOps Lead | Custodian of Register & Implementation Gatekeeper | `GATE CLOSED` |

---

# Stakeholder Decision Register

---

## ADR-004: Production Target Table Scope (85 vs 91 Tables)

- **Decision ID:** `ADR-004`
- **Topic:** Production Target Table Scope & Missing Table Dictionaries
- **Evidence:**
  - Sections 1, 2, 4, 5, 6, and 9 of [FireSphere_Database_Documentation_v2.xml](file:///d:/IP%20Folder/FireSphere_Database_Documentation_v2.xml) repeatedly state a scope of **85 tables** (60 legacy tables + 25 new required production tables).
  - Section 9 Master Inventory asserts: *"Total Tables in Final Production Database (Architecture v2.0): 85 Tables."*
  - Section 6 provides column data dictionaries for exactly **25 tables** (Subsections 6.1 through 6.25).
  - Appendix A catalogs **91 numbered tables** (1 to 91), labeling 31 tables as `(NEW)`.
- **Conflict / Question:**
  Is the production target schema:
  - Exactly **85 tables** (consisting of 60 legacy + 25 documented new tables), OR
  - **91 tables** (including the six additional Appendix A tables)?
  
  *Mandatory Rule:* If Option B (91 tables) is selected, complete column-level data dictionaries (column names, PostgreSQL data types, nullabilities, default values, and foreign keys) for the following six tables must be provided by the Platform Architect before physical schema authoring begins. Member 3 is strictly forbidden from inventing these specifications:
  1. `inventory_categories` (Appendix A #46)
  2. `notification_templates` (Appendix A #57)
  3. `payments` (Appendix A #64)
  4. `purchase_order_items` (Appendix A #66)
  5. `purchase_orders` (Appendix A #67)
  6. `vendors` (Appendix A #83)
- **Available Options:**
  - **Option A:** **85 tables** — Production target frozen at 85 tables (60 legacy + 25 documented in Section 6). The 6 extra Appendix A tables are excluded from the initial release scope.
  - **Option B:** **91 tables** — Production target expanded to 91 tables. Platform Architect must supply full column-level data dictionaries for the 6 missing tables prior to Phase 2 entry.
  - **Option C:** **Other** — Requires explicit scope definition and documented table inventory approved by Project Lead.
- **Recommended Option:** `Option A (85 Tables)` — *PROPOSED — REQUIRES APPROVAL*
- **Approval Required From:** Platform Architect & Project Lead
- **Selected Option:** `[   ]` *(Stakeholder to fill: A, B, or C)*
- **Final Decision:** `[ Pending Stakeholder Input ]`
- **Rationale:** `[ Stakeholder to provide justification ]`
- **Approved By:** `[ Name / Signature ]`
- **Approval Date:** `[ YYYY-MM-DD ]`
- **Status:** `UNRESOLVED`

---

## ADR-005: Incident AI Analysis Persistence Architecture

- **Decision ID:** `ADR-005`
- **Topic:** Representation & Persistence of Incident AI Inference Data
- **Evidence:**
  - [03_MEMBER_3_DATABASE_INFRA_DEVOPS.md](file:///d:/IP%20Folder/03_MEMBER_3_DATABASE_INFRA_DEVOPS.md) (Line 27) explicitly mandates resolution of `incident_ai_analysis`.
  - An exhaustive regex search of [FireSphere_Database_Documentation_v2.xml](file:///d:/IP%20Folder/FireSphere_Database_Documentation_v2.xml) yields **zero occurrences** of `incident_ai_analysis` (0 matches across 473,748 bytes).
  - The document defines a generic AI subsystem: `ai_models`, `ai_agents`, `ai_agent_workflows`, `ai_agent_workflow_steps`, `ai_sessions`, `ai_runs`, and `ai_insights`.
  - In Section 4.4, `ai_insights` records polymorphic predictions: `(target_entity_type VARCHAR(50), target_entity_id BIGINT, insight_summary TEXT, confidence_score NUMERIC(5,2))`.
- **Conflict / Question:**
  How should incident-specific AI analysis persistence (computer vision detections, thermal anomaly heatmaps, evacuation risk scores, apparatus recommendations) be architected and persisted?
- **Available Options:**
  - **Option A:** **Generic `ai_insights`** — Persist incident AI inference records as polymorphic rows within `ai_insights` (`target_entity_type = 'incident'`, `target_entity_id = incidents.id`). No separate table created.
  - **Option B:** **Dedicated `incident_ai_analysis`** — Author a dedicated relational table `incident_ai_analysis` linked (N:1) to `incidents` with explicit columns for model ID, bounding boxes, detection labels, confidence scores, and officer review timestamps. *(Requires full column dictionary to be authored by Member 2 / Architect).*
  - **Option C:** **JSONB Column on `incidents`** — `NEW PROPOSAL — REQUIRES ARCHITECTURAL REVIEW`: Store AI analysis results in an `ai_analysis_json JSONB` column on the `incidents` table. *(Note: This option is not defined in the source document; requires formal review).*
  - **Option D:** **Other** — Another explicitly approved architectural model specified by Member 2 (AI Lead).
- **Recommended Option:** `Option A (Generic ai_insights)` — *PROPOSED — REQUIRES APPROVAL*
- **Approval Required From:** Member 2 (AI Lead) & Platform Architect
- **Selected Option:** `[   ]` *(Stakeholder to fill: A, B, C, or D)*
- **Final Decision:** `[ Pending Stakeholder Input ]`
- **Rationale:** `[ Stakeholder to provide justification ]`
- **Approved By:** `[ Name / Signature ]`
- **Approval Date:** `[ YYYY-MM-DD ]`
- **Status:** `UNRESOLVED`

---

## ADR-006: Tenant Identifier Canonicalization & Data Type Standard

- **Decision ID:** `ADR-006`
- **Topic:** Tenant Primary Key, Foreign Key, and Isolation Data Type Standards
- **Evidence:**
  - Section 2.2 narrative states: *"Every tenant-aware table includes an organization_id UUID foreign key referencing organizations(id)."*
  - Section 6 defines `organizations.id` as `BIGSERIAL` (BIGINT) and `organizations.uuid` as `UUID`.
  - Section 6 DDL specifications define child tenant foreign keys as `org_id BIGINT REFERENCES organizations(id)` (e.g. Subsections 6.1, 6.6, 6.19).
  - Section 8.1 defines Row-Level Security policy filter as:
    `WHERE (org_id = current_setting('app.current_org_id')::BIGINT)`
- **Conflict / Question:**
  Which tenant identifier standard governs the platform database architecture, JPA entity mappings, and API contracts?
  
  *Mandatory Specification Requirement:* The approved decision must explicitly define:
  1. `organizations` Primary Key type (`BIGSERIAL` vs `UUID`)
  2. Tenant Foreign Key column name on domain tables (`org_id` vs `organization_id`)
  3. Tenant Foreign Key data type (`BIGINT` vs `UUID`)
  4. JWT tenant claim attribute name and format (e.g., `org_id: 101` vs `organization_id: "uuid"`)
  5. PostgreSQL RLS session variable name and cast (e.g., `app.current_org_id::BIGINT` vs `app.current_org_id::UUID`)
  6. Backend JPA / Spring Data entity field mapping
  7. Public REST / GraphQL API resource identifier convention
- **Available Options:**
  - **Option A:** **Pure UUID Architecture** — `organizations.id UUID PRIMARY KEY DEFAULT gen_random_uuid()`. Child tables define `organization_id UUID NOT NULL REFERENCES organizations(id)`. RLS filters on `app.current_org_id::UUID`. Numeric serial IDs eliminated for tenant keys.
  - **Option B:** **Pure BIGINT Architecture** — `organizations.id BIGSERIAL PRIMARY KEY`. Child tables define `org_id BIGINT NOT NULL REFERENCES organizations(id)`. Numeric IDs exposed in APIs.
  - **Option C:** **Dual Internal/Public Identifier Model** — 
    - Database relational layer: `organizations.id BIGSERIAL PK` and `child_tables.org_id BIGINT FK` for compact B-Tree indexing and high join performance.
    - Public / API layer: `organizations.uuid UUID UNIQUE NOT NULL` and `child_tables.uuid UUID UNIQUE NOT NULL` for public REST endpoints and mobile sync (enumeration protection).
    - RLS session filter: `WHERE (org_id = current_setting('app.current_org_id')::BIGINT)`.
  - **Option D:** **Other** — Another explicitly specified multi-tenancy identifier model.
- **Recommended Option:** `Option C (Dual Internal/Public Model)` — *PROPOSED — REQUIRES APPROVAL*
- **Approval Required From:** Platform Architect & Member 1 (Backend Lead)
- **Selected Option:** `[   ]` *(Stakeholder to fill: A, B, C, or D)*
- **Final Decision:** `[ Pending Stakeholder Input ]`
- **Explicit Specifications Defined:**
  - `organizations` PK: `[ BIGSERIAL / UUID ]`
  - Tenant FK Name: `[ org_id / organization_id ]`
  - Tenant FK Type: `[ BIGINT / UUID ]`
  - JWT Claim Key: `[ org_id / organization_id ]`
  - RLS Variable: `[ app.current_org_id::BIGINT / UUID ]`
  - JPA Mapping: `[ Long orgId / UUID organizationId ]`
  - API Representation: `[ UUID / Long ]`
- **Rationale:** `[ Stakeholder to provide justification ]`
- **Approved By:** `[ Name / Signature ]`
- **Approval Date:** `[ YYYY-MM-DD ]`
- **Status:** `UNRESOLVED`

---

## ADR-007: Department / User Circular Foreign Key Deadlock Resolution

- **Decision ID:** `ADR-007`
- **Topic:** Circular Relational Dependency Between `departments` and `users`
- **Evidence:**
  - Section 7.2 states: *"departments (head_user_id) links (N:1) to users. users (department_id) links (N:1) to departments."*
  - Populated legacy exports (`database_19.sql` for `departments` and `database_53.sql` for `users`) confirm mutual operational linkage.
- **Conflict / Question:**
  How should the chicken-and-egg relational deadlock between `departments` and `users` be resolved in PostgreSQL DDL and Flyway migrations?
- **Available Options:**
  - **Option A:** **`DEFERRABLE INITIALLY DEFERRED` Foreign Key** — Define `departments.head_user_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED`, and `users.department_id BIGINT NULL REFERENCES departments(id) ON DELETE SET NULL`. Constraints evaluated at commit time, allowing mutual inserts within a single transaction.
  - **Option B:** **Nullable Foreign Key with Application-Level Sequencing** — Both foreign keys are standard non-deferred nullable constraints. Insertion sequence: Insert Department with `head_user_id = NULL`, insert Users referencing Department, update Department setting `head_user_id`.
  - **Option C:** **Decouple Leadership via Separate Assignment Table** — Remove `head_user_id` from `departments`; create `department_leadership (department_id, user_id, start_date, is_active)` to eliminate circularity.
  - **Option D:** **Other** — Another approved relational pattern.
- **Recommended Option:** `Option A (DEFERRABLE INITIALLY DEFERRED)` — *PROPOSED — REQUIRES APPROVAL*
- **Approval Required From:** Member 1 (Backend Lead) & Platform Architect
- **Selected Option:** `[   ]` *(Stakeholder to fill: A, B, C, or D)*
- **Final Decision:** `[ Pending Stakeholder Input ]`
- **Rationale:** `[ Stakeholder to provide justification ]`
- **Approved By:** `[ Name / Signature ]`
- **Approval Date:** `[ YYYY-MM-DD ]`
- **Status:** `UNRESOLVED`

---

## ADR-008: `incident_evidence` Scope, Semantics & Media Storage Model

- **Decision ID:** `ADR-008`
- **Topic:** Evidentiary Scope, Binary Storage Linkage, and AI Annotation Relationships
- **Evidence:**
  - Legacy table #25 (`database_26.sql`) contained `incident_evidence` with 0 rows and no DDL.
  - Section 4.25 defines inferred columns: `id`, `incident_id`, `evidence_type`, `file_path`, `file_hash_sha256`, `captured_at`, `uploaded_by`.
  - Section 5 marks it `MODIFY`: adds `organization_id`, SHA-256 integrity hash, and GPS coordinates.
  - Appendix A lists it as table #34.
- **Conflict / Question:**
  Does `incident_evidence` represent strictly raw physical file metadata, or does it incorporate processed computer vision bounding boxes and AI annotations?
  
  *Mandatory Specification Requirement:* The approved decision must explicitly specify:
  1. Object storage relationship (MinIO / S3 bucket hierarchy and path schema)
  2. Relational metadata attributes stored in PostgreSQL (`file_path`, `mime_type`, `file_size_bytes`)
  3. Cryptographic integrity hash algorithm (`SHA-256`)
  4. Spatial GPS capture metadata representation (`GEOMETRY(Point, 4326)`)
  5. Relationship to AI computer vision annotations (stored in `incident_evidence` vs `ai_insights`)
  6. Evidentiary retention and legal hold expectations (if applicable; must not be invented by Member 3)
- **Available Options:**
  - **Option A:** **Raw Evidence Metadata Only** — `incident_evidence` records exclusively raw evidence file metadata (S3 key, MIME type, file size, SHA-256 hash, GPS point, capture timestamp, uploader ID). AI vision inferences and bounding boxes are strictly stored in `ai_insights`.
  - **Option B:** **Raw + Processed / Annotated Evidence** — `incident_evidence` stores both raw media metadata and AI inference outputs via additional columns (`ai_detection_json JSONB`, `hazard_tags TEXT[]`, `processed_status VARCHAR(50)`).
  - **Option C:** **Separate Raw and Processed Evidence Models** — Retain `incident_evidence` for raw capture; establish a dedicated child table `incident_evidence_annotations` for AI model predictions and human reviewer corrections.
  - **Option D:** **Other** — Another approved architecture.
- **Recommended Option:** `Option A (Raw Evidence Metadata Only)` — *PROPOSED — REQUIRES APPROVAL*
- **Approval Required From:** Member 2 (AI Lead) & Member 1 (Backend Lead)
- **Selected Option:** `[   ]` *(Stakeholder to fill: A, B, C, or D)*
- **Final Decision:** `[ Pending Stakeholder Input ]`
- **Explicit Specifications Defined:**
  - Storage Key Schema: `[ e.g., /evidence/{org_id}/{incident_id}/{uuid}.{ext} ]`
  - Relational Metadata Columns: `[ file_path, file_size, mime_type, captured_at ]`
  - Hash Verification: `[ SHA-256 ]`
  - Spatial Representation: `[ location GEOMETRY(Point, 4326) ]`
  - AI Annotation Target: `[ ai_insights / incident_evidence / separate table ]`
  - Retention / Legal Hold Policy: `[ Indefinite / Municipal Policy / Not Applicable ]`
- **Rationale:** `[ Stakeholder to provide justification ]`
- **Approved By:** `[ Name / Signature ]`
- **Approval Date:** `[ YYYY-MM-DD ]`
- **Status:** `UNRESOLVED`

---

## ADR-009: SSO Identity Entity Canonical Naming

- **Decision ID:** `ADR-009`
- **Topic:** SSO Identity Table Canonical Name Standard
- **Evidence:**
  - Legacy dump file `database_52.sql` is named `sso_user_identities`.
  - Section 4.51 audits legacy table 51 as `sso_user_identities`.
  - Section 5 and Section 9 transformation tables assert: *"REPLACE: sso_user_identities -> sso_identities"*.
  - Appendix A lists item #71 as `sso_identities (REPLACED)`.
- **Conflict / Question:**
  Which canonical name should be assigned to the target SSO authentication identity table in schema `firesphere`?
- **Available Options:**
  - **Option A:** **`sso_user_identities`** — Retain legacy name for direct 1:1 parity with legacy MariaDB dump `database_52.sql`.
  - **Option B:** **`sso_identities`** — Adopt simplified name as specified in Section 5, Section 9, and Appendix A (#71). Legacy extraction script maps `sso_user_identities` -> `sso_identities`.
  - **Option C:** **Other** — Another approved canonical name.
- **Recommended Option:** `Option B (sso_identities)` — *PROPOSED — REQUIRES APPROVAL*
- **Approval Required From:** Member 1 (Auth / Backend Lead) & Platform Architect
- **Selected Option:** `[   ]` *(Stakeholder to fill: A, B, or C)*
- **Final Decision:** `[ Pending Stakeholder Input ]`
- **Rationale:** `[ Stakeholder to provide justification ]`
- **Approved By:** `[ Name / Signature ]`
- **Approval Date:** `[ YYYY-MM-DD ]`
- **Status:** `UNRESOLVED`

---

## ADR-010: Audit Log Entity Canonical Naming

- **Decision ID:** `ADR-010`
- **Topic:** Audit Log Table Canonical Name Standard
- **Evidence:**
  - Section 4.11 and Appendix A (#11) list `audit_logs`.
  - Section 8.3 describes trigger implementation writing to `audit_logs`.
  - Section 9 transformation summary asserts: *"Original Tables Replaced With Enhanced Architectures (REPLACE): 2 (sso_user_identities -> sso_identities; audit_logs -> cdc_audit_logs)"*.
  - Section 10.1 designates Flyway migration as `V1.0.4__cdc_audit_triggers.sql`.
- **Conflict / Question:**
  What is the canonical physical table name for change data capture and security audit records in schema `firesphere`?
- **Available Options:**
  - **Option A:** **`audit_logs`** — Canonical table named `firesphere.audit_logs`. Trigger function named `firesphere_audit_trigger()`. Matches Appendix A (#11) and Section 4.11.
  - **Option B:** **`cdc_audit_logs`** — Canonical table named `firesphere.cdc_audit_logs` as asserted in Section 9.
  - **Option C:** **Separate Audit and CDC Tables** — Maintain `audit_logs` for high-level user actions and `cdc_audit_logs` for row-level BEFORE/AFTER JSONB diffs.
  - **Option D:** **Other** — Another approved naming and partition structure.
- **Recommended Option:** `Option A (firesphere.audit_logs)` — *PROPOSED — REQUIRES APPROVAL*
- **Approval Required From:** Member 3 (Database Engineer), Member 1, & Platform Architect
- **Selected Option:** `[   ]` *(Stakeholder to fill: A, B, C, or D)*
- **Final Decision:** `[ Pending Stakeholder Input ]`
- **Rationale:** `[ Stakeholder to provide justification ]`
- **Approved By:** `[ Name / Signature ]`
- **Approval Date:** `[ YYYY-MM-DD ]`
- **Status:** `UNRESOLVED`

---

## ADR-011: Public External Identifier Standard (`uuid UUID UNIQUE NOT NULL`)

- **Decision ID:** `ADR-011`
- **Topic:** API-Facing Public Identifier Architecture & Mobile Sync Contract
- **Evidence:**
  - Section 2.1 specifies `uuid-ossp` active extension.
  - Section 6 DDL specifications define `uuid UUID UNIQUE NOT NULL DEFAULT gen_random_uuid()` on newly created tables.
  - Section 8.8 (Offline-First Mobile Field Sync) requires client-generated UUIDs to prevent primary key collisions during disconnected inspection sync.
- **Conflict / Question:**
  Is the `uuid UUID UNIQUE NOT NULL` pattern formally approved as the mandatory external public identifier standard across all API-exposed domain tables?
  
  *Context to Clarify:*
  - Relationship between internal database primary keys (`id BIGSERIAL` for compact joins and indexing)
  - Public API identifiers (REST URIs: `/api/v1/buildings/{uuid}`)
  - Mobile synchronization identifiers (offline UUID creation before server sync)
  - Object/resource locators in MinIO storage
- **Available Options:**
  - **Option A:** **Mandatory Dual-Identifier Pattern** — Every domain table holds both internal `id BIGSERIAL PK` and public `uuid UUID UNIQUE NOT NULL DEFAULT gen_random_uuid()`. APIs and mobile apps interact strictly via UUIDs.
  - **Option B:** **Single Primary Key (UUID Only)** — Eliminate `BIGSERIAL` entirely; use `id UUID PRIMARY KEY DEFAULT gen_random_uuid()` everywhere.
  - **Option C:** **Single Primary Key (BIGINT Only)** — Eliminate UUIDs; expose numeric IDs directly via API (High vulnerability to enumeration attacks).
  - **Option D:** **Other** — Another approved identification convention.
- **Recommended Option:** `Option A (Mandatory Dual-Identifier Pattern)` — *PROPOSED — REQUIRES APPROVAL*
- **Approval Required From:** Platform Architect & Member 1 (Backend Lead)
- **Selected Option:** `[   ]` *(Stakeholder to fill: A, B, C, or D)*
- **Final Decision:** `[ Pending Stakeholder Input ]`
- **Rationale:** `[ Stakeholder to provide justification ]`
- **Approved By:** `[ Name / Signature ]`
- **Approval Date:** `[ YYYY-MM-DD ]`
- **Status:** `UNRESOLVED`

---

## ADR-012: PostGIS Spatial Conventions & Geometry Specifications

- **Decision ID:** `ADR-012`
- **Topic:** Spatial Reference System, Geometry Types, and Indexing Conventions
- **Evidence:**
  - Section 2.1 specifies PostgreSQL 16+ with PostGIS 3.4+.
  - Section 8.2 defines spatial computing in WGS 84 (SRID 4326), KNN nearest-neighbor queries, and GIST indexing.
  - Legacy MariaDB dumps stored flat numeric `latitude` and `longitude` columns without spatial indexes.
- **Conflict / Question:**
  Are the proposed PostGIS spatial conventions formally approved for all spatial tables?
  
  *Required Confirmations:*
  1. Geometry type per spatial entity:
     - `fire_stations`: `location Point`, `jurisdiction_geom Polygon`
     - `hydrants`: `location Point`
     - `water_sources`: `location Point`
     - `buildings`: `location Point`, `building_footprint Polygon`
     - `incidents`: `location Point`
     - `vehicle_location_history`: `location Point`
     - `zones`: `boundary_geom Polygon`
     - `beats`: `boundary_geom Polygon`
  2. Coordinate Reference System: Standardized to **SRID 4326** (WGS 84).
  3. Spatial Index Strategy: Mandatory **GiST** index on all geometry columns.
  4. Coordinate Handling Expectations: Strict **longitude-first** ordering in SQL (`ST_MakePoint(longitude, latitude)`).
- **Available Options:**
  - **Option A:** **PostGIS WGS 84 (SRID 4326) with Native Geometries & GiST Indexes** — Implement candidate spatial architecture as audited.
  - **Option B:** **Geography Type Alternative** — Use native `GEOGRAPHY` types instead of `GEOMETRY` to compute spherical distances natively without casting.
  - **Option C:** **Flat Coordinates Retained** — Retain legacy numeric `latitude`/`longitude` columns (Disables spatial KNN indexing and polygon containment).
  - **Option D:** **Other** — Another approved GIS architecture.
- **Recommended Option:** `Option A (PostGIS SRID 4326 + GiST Indexes)` — *PROPOSED — REQUIRES APPROVAL*
- **Approval Required From:** Member 2 (GIS Lead) & Member 1 (Backend Lead)
- **Selected Option:** `[   ]` *(Stakeholder to fill: A, B, C, or D)*
- **Final Decision:** `[ Pending Stakeholder Input ]`
- **Rationale:** `[ Stakeholder to provide justification ]`
- **Approved By:** `[ Name / Signature ]`
- **Approval Date:** `[ YYYY-MM-DD ]`
- **Status:** `UNRESOLVED`

---

## ADR-013: Legacy Data Migration & Sanitization Strategy

- **Decision ID:** `ADR-013`
- **Topic:** Strategy for Ingesting and Sanitizing 12 Populated Legacy Tables
- **Evidence:**
  - Forensic audit of 60 legacy dumps established that exactly **12 tables are populated** (`ai_agents`, `ai_models`, `departments`, `divisions`, `fire_stations`, `inspection_checklists`, `inventory_items`, `noc_application_categories`, `organizations`, `permissions`, `roles`, `training_courses`, `user_roles`, `users`).
  - Legacy `users` contained unsalted SHA-256 password hashes, leaked plaintext tokens in `metadata_json`, and all 6 users having `is_super_admin = 1`.
  - Section 10.2 specifies data cleaning rules prior to production ingestion.
- **Conflict / Question:**
  What is the authoritative operational strategy for legacy data during production deployment?
- **Available Options:**
  - **Option A:** **Sanitize and Migrate Populated Tables into Production** — Migrate verified records from the 12 populated tables via Flyway script `V1.0.5`, enforcing:
    1. Merge test organizations (`PMC-VAL7`, `PMC-WL3G`) into a verified municipal tenant.
    2. Convert flat station coordinates to PostGIS `Point(4326)`.
    3. Purge plaintext tokens from `users.metadata_json`.
    4. Quarantine legacy SHA-256 hashes (`force_password_reset = TRUE`).
    5. Revoke `is_super_admin = 1` from all non-platform users.
  - **Option B:** **Clean Break (Zero Legacy Migration)** — Deploy fresh schema with pristine seed data; legacy MariaDB data is archived in cold storage and not ingested into the production PostgreSQL database.
  - **Option C:** **Reference / Seed Data Only** — Ingest only static taxonomy tables (`noc_application_categories`, `training_courses`, global `permissions`); discard legacy users, organizations, and roles.
  - **Option D:** **Other** — Another approved migration strategy.
- **Recommended Option:** `Option A (Sanitize and Migrate)` — *PROPOSED — REQUIRES APPROVAL*
- **Approval Required From:** Project Lead, Member 1, & Member 3
- **Selected Option:** `[   ]` *(Stakeholder to fill: A, B, C, or D)*
- **Final Decision:** `[ Pending Stakeholder Input ]`
- **Rationale:** `[ Stakeholder to provide justification ]`
- **Approved By:** `[ Name / Signature ]`
- **Approval Date:** `[ YYYY-MM-DD ]`
- **Status:** `UNRESOLVED`

---

# Phase 2 Entry Criteria Approval Mapping

The following matrix maps every mandatory Category A Phase 2 entry criterion (from [DATABASE_PHASE2_ENTRY_CRITERIA.md](file:///d:/IP%20Folder/DATABASE_PHASE2_ENTRY_CRITERIA.md)) directly to the decision record that satisfies it. 

**Rule:** It is impossible to claim Phase 2 readiness while any mapping in this table remains `UNRESOLVED`.

| Category A Criterion | Decision ID | Current Status | Governing Evidence | Required Approver |
| :--- | :---: | :---: | :--- | :--- |
| **1. 85 vs 91 Table Scope Approved** | `ADR-004` | `UNRESOLVED` | XML Sec 1, 2, 9 vs Appendix A (#46, 57, 64, 66, 67, 83) | Platform Architect & Project Lead |
| **2. Six Missing Dictionaries Supplied** | `ADR-004` | `UNRESOLVED` | Section 6 ends at 6.25; 6 tables un-modeled | Platform Architect |
| **3. `incident_ai_analysis` Approved** | `ADR-005` | `UNRESOLVED` | XML contains 0 occurrences; M3 spec Line 27 | Member 2 (AI Lead) & Architect |
| **4. Canonical Tenant Key Approved** | `ADR-006` | `UNRESOLVED` | Sec 2.2 (`UUID`) vs Sec 6 & 8.1 (`org_id BIGINT`) | Platform Architect & Member 1 |
| **5. RLS Strategy Approved** | `ADR-006` | `UNRESOLVED` | Sec 8.1 RLS policy & session context injection | Member 1 (Backend Lead) |
| **6. Department/User Circular FK Approved**| `ADR-007` | `UNRESOLVED` | Sec 7.2 ER topology vs insertion order | Member 1 (Backend Lead) & M3 |
| **7. `incident_evidence` Semantics Approved** | `ADR-008` | `UNRESOLVED` | Legacy table 25 vs Sec 4.25 vs AI pipeline | Member 2 (Evidence Lead) & M1 |
| **8. SSO Naming Conflict Resolved** | `ADR-009` | `UNRESOLVED` | `database_52.sql` vs App A #71 (`sso_identities`)| Member 1 (Auth Lead) |
| **9. Audit Log Naming Resolved** | `ADR-010` | `UNRESOLVED` | Sec 4.11 / App A #11 vs Sec 9 table | Member 3 & Member 1 |
| **10. Public UUID Standard Approved** | `ADR-011` | `UNRESOLVED` | Sec 6 DDL `uuid UUID UNIQUE NOT NULL` | Platform Architect & Member 1 |
| **11. Spatial Conventions Approved** | `ADR-012` | `UNRESOLVED` | Sec 8.2 SRID 4326 + GiST indexing | Member 2 (GIS Lead) & Member 1 |
| **12. Legacy Data Strategy Approved** | `ADR-013` | `UNRESOLVED` | 12 populated tables vs clean break | Project Lead & Member 1 |

---

## Phase 2 Quality Gate Status

```
+-------------------------------------------------------------------------+
|                  PHASE 2 QUALITY GATE ASSESSMENT                         |
+-------------------------------------------------------------------------+
|  Total Mandatory Category A Criteria:  12 Items                         |
|  Criteria Formally Approved:            0 Items                         |
|  Criteria Pending Stakeholder Input:   12 Items                         |
+-------------------------------------------------------------------------+
|  OVERALL VERDICT: GATE CLOSED — PHASE 2 MUST NOT BEGIN                  |
+-------------------------------------------------------------------------+
```

**Next Action for Stakeholders:**  
Project Lead, Platform Architect, Member 1, and Member 2 must review this document, record their selected options and rationales under Sections ADR-004 through ADR-013, and provide formal signatures. Upon full sign-off of all Category A criteria, Member 3 will unlock the implementation gate and begin authoring Flyway migration scripts.
