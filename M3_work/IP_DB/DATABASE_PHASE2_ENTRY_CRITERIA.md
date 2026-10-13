# FireSphere.ai — Database Phase 2 Entry Criteria & Implementation Gate

**Document Status:** Formal Quality Gate & Pre-Implementation Entry Criteria  
**Authority:** Member 3 Governance ([03_MEMBER_3_DATABASE_INFRA_DEVOPS.md](file:///d:/IP%20Folder/03_MEMBER_3_DATABASE_INFRA_DEVOPS.md))  
**Target Milestone:** Phase 2 (Physical Schema Design & Flyway Migration Generation)  
**Date:** October 2026  
**Implementation Gate:** **STRICTLY CLOSED (ENTRY CRITERIA UNMET)**

---

## 1. Definition of Phase 2

In accordance with the Member 3 roadmap, **Phase 2** represents:
1. Drafting canonical PostgreSQL 16+ DDL statements.
2. Generating versioned, immutable Flyway migration scripts in `db/migration/`:
   - `V1.0.0__core_extensions_and_enums.sql`
   - `V1.0.1__ddl_relational_schema.sql`
   - `V1.0.2__postgis_spatial_indexes_and_functions.sql`
   - `V1.0.3__multi_tenant_rls_and_security.sql`
   - `V1.0.4__cdc_audit_triggers.sql`
   - `V1.0.5__seed_reference_data.sql`
3. Authoring database verification tests and schema validation harnesses.

**Mandatory Rule:** Under no circumstances will Flyway migration scripts, database tables, or infrastructure configurations be created until all **Blocking Gate Criteria (Required Before V1 Migration)** are explicitly signed off by designated stakeholder owners.

---

## 2. Gate Criteria Classification

Criteria are strictly partitioned into two categories:
* **Category A: Mandatory Pre-Condition (Required Before V1 Migration):** Schema design, primary key types, tenant isolation, and table inventories cannot be refactored once migration files are committed. These are hard blockers for Phase 2 entry.
* **Category B: Post-V1 Implementation (Can Be Implemented After V1 Migration):** Subsystems that layer on top of a stable relational schema without requiring breaking DDL alterations.

---

## 3. Comprehensive Entry Criteria Checklist

### Category A: Required Before V1 Migration Generation (Phase 2 Blockers)

| # | Gate Condition | Relevant Document / ADR | Required Decision Owner | Current Status |
| :---: | :--- | :--- | :--- | :---: |
| 1 | **85 vs 91 Table Scope Approved**<br>Formal determination whether the initial target schema contains 85 tables (core model) or 91 tables (including procurement/vendors). | [ADR-004](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md#adr-004-85-vs-91-table-production-target-scope)<br>[DATABASE_SOURCE_CONFLICTS.md](file:///d:/IP%20Folder/DATABASE_SOURCE_CONFLICTS.md) (CONFLICT-01) | Platform Architect & Project Lead | `UNMET` |
| 2 | **Six Missing Table Dictionaries Supplied (If 91 Chosen)**<br>If 91 tables are selected, complete column-level dictionaries, nullabilities, and foreign keys for `inventory_categories`, `notification_templates`, `payments`, `purchase_order_items`, `purchase_orders`, and `vendors` must be provided. | [ADR-004](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md#adr-004-85-vs-91-table-production-target-scope)<br>[DATABASE_SOURCE_CONFLICTS.md](file:///d:/IP%20Folder/DATABASE_SOURCE_CONFLICTS.md) (CONFLICT-02) | Platform Architect | `UNMET` |
| 3 | **`incident_ai_analysis` Architectural Decision Approved**<br>Resolution whether incident AI outputs are stored in polymorphic `ai_insights`, a dedicated relational table, or JSONB on `incidents`. | [ADR-005](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md#adr-005-incident-ai-analysis-persistence-incident_ai_analysis)<br>[DATABASE_DECISIONS.md](file:///d:/IP%20Folder/DATABASE_DECISIONS.md) (ADR-002) | Member 2 (AI Lead) & Platform Architect | `UNMET` |
| 4 | **Canonical Tenant Key Name & Data Type Approved**<br>Reconciliation between `organization_id UUID` (Sec 2.2) and `org_id BIGINT` (Sec 6 & 8.1). Formal sign-off on Dual-Key pattern. | [ADR-006](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md#adr-006-tenant-identifier-canonicalization-organization_id-uuid-vs-org_id-bigint)<br>[DATABASE_TENANCY_AUDIT.md](file:///d:/IP%20Folder/DATABASE_TENANCY_AUDIT.md) | Platform Architect & Member 1 (Backend Lead) | `UNMET` |
| 5 | **Row-Level Security (RLS) Strategy Approved**<br>Sign-off on RLS policy expression `(org_id = current_setting('app.current_org_id')::BIGINT)` and zero-trust session context injection pipeline. | [DATABASE_TENANCY_AUDIT.md](file:///d:/IP%20Folder/DATABASE_TENANCY_AUDIT.md) Section 3 | Member 1 (Backend Lead) & Platform Architect | `UNMET` |
| 6 | **Department / User Circular FK Resolution Approved**<br>Sign-off on `DEFERRABLE INITIALLY DEFERRED` constraint on `departments.head_user_id` and nullable `users.department_id`. | [ADR-007](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md#adr-007-department--user-circular-foreign-key-deadlock)<br>[DATABASE_RELATIONSHIP_AUDIT.md](file:///d:/IP%20Folder/DATABASE_RELATIONSHIP_AUDIT.md) | Member 1 (Backend Lead) & Member 3 | `UNMET` |
| 7 | **`incident_evidence` Semantics Approved**<br>Confirmation that `incident_evidence` stores raw media file metadata (MinIO keys, hashes, GPS) and does not store unapproved computer vision columns. | [ADR-008](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md#adr-008-incident_evidence-scope--semantics)<br>[DATABASE_DECISIONS.md](file:///d:/IP%20Folder/DATABASE_DECISIONS.md) (ADR-001) | Member 2 (Evidence Lead) & Member 1 | `UNMET` |
| 8 | **SSO Entity Naming Conflict Resolved**<br>Formal agreement to use `sso_identities` (or `sso_user_identities`) across DDL, Flyway scripts, and entity classes. | [ADR-009](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md#adr-009-sso-entity-canonical-naming)<br>[DATABASE_SOURCE_CONFLICTS.md](file:///d:/IP%20Folder/DATABASE_SOURCE_CONFLICTS.md) (CONFLICT-05) | Member 1 (Backend / Auth Lead) | `UNMET` |
| 9 | **Audit Log Canonical Naming Resolved**<br>Confirmation of canonical table name as `firesphere.audit_logs` (with CDC trigger feeding it). | [ADR-010](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md#adr-010-audit-log-canonical-naming)<br>[DATABASE_SOURCE_CONFLICTS.md](file:///d:/IP%20Folder/DATABASE_SOURCE_CONFLICTS.md) (CONFLICT-06) | Member 3 & Member 1 | `UNMET` |
| 10 | **Required API-Facing Identifiers Aligned**<br>Mandatory `uuid UUID DEFAULT gen_random_uuid() UNIQUE NOT NULL` established across all domain tables to ensure REST/GraphQL APIs never expose internal integer IDs. | [DATABASE_COLUMN_CONTRACT_AUDIT.md](file:///d:/IP%20Folder/DATABASE_COLUMN_CONTRACT_AUDIT.md) Section 3.1 | Member 1 (Backend Lead) & Member 3 | `UNMET` |
| 11 | **PostGIS Spatial Conventions Approved**<br>Formal sign-off on SRID 4326 (WGS 84), `GEOMETRY(Point, 4326)` for hydrants/stations/telemetry, `GEOMETRY(Polygon, 4326)` for zones/beats/footprints, and GIST indexing. | [DATABASE_SPATIAL_AUDIT.md](file:///d:/IP%20Folder/DATABASE_SPATIAL_AUDIT.md) Section 2 | Member 2 (GIS Lead) & Member 1 | `UNMET` |

---

### Category B: Can Be Implemented After V1 Migration (Post-V1 Pipeline)

| # | Condition | Relevant Document | Required Owner | Implementation Phase |
| :---: | :--- | :--- | :--- | :---: |
| 12 | **Legacy-to-Production Data Migration Pipeline Approved**<br>Sanitization and transformation rules for 12 populated legacy tables (org consolidation, SHA-256 password quarantine). | [DATABASE_SECURITY_AUDIT.md](file:///d:/IP%20Folder/DATABASE_SECURITY_AUDIT.md) Sec 2.1<br>XML Section 10.2 | Member 3 & Member 1 | Flyway script `V1.0.5` / Pre-seeding ETL |
| 13 | **Legacy Credential Quarantine Strategy Implemented**<br>Enforcing `force_password_reset = TRUE` and purging leaked verification tokens from `metadata_json`. | [DATABASE_SECURITY_AUDIT.md](file:///d:/IP%20Folder/DATABASE_SECURITY_AUDIT.md) Sec 2.2 | Member 1 & Member 3 | Data Ingestion Script |
| 14 | **Backend / Member 2 Contract Review**<br>Integration review between Spring Boot JPA entity definitions and PostgreSQL 16+ schemas (`firesphere`, `tiger`, `public`). | [DATABASE_INPUT_REQUIREMENTS.md](file:///d:/IP%20Folder/DATABASE_INPUT_REQUIREMENTS.md) Section 3 | Member 1 & Member 2 | Application Integration Phase |
| 15 | **Object Storage Metadata Contract Approved**<br>MinIO S3 bucket naming, object path conventions (`/evidence/{org_id}/{incident_id}/{uuid}.{ext}`), and pre-signed URL policies. | [DATABASE_RELATIONSHIP_AUDIT.md](file:///d:/IP%20Folder/DATABASE_RELATIONSHIP_AUDIT.md) Sec 4.4 | Member 2 & Member 3 | Phase 3 (MinIO Setup) |
| 16 | **CDC Audit Trigger Function Finalization**<br>Pl/pgSQL implementation of `firesphere_audit_trigger()` capturing OLD/NEW JSONB diffs, IP, and user identity. | [DATABASE_SECURITY_AUDIT.md](file:///d:/IP%20Folder/DATABASE_SECURITY_AUDIT.md) Sec 4.1 | Member 3 | Flyway script `V1.0.4` |
| 17 | **Required Reference & Seed Data Strategy Approved**<br>Master seed scripts for standard roles (Super Admin, Fire Chief, Inspector, Dispatcher), 35+ permissions, and NBC hazard occupancy codes. | XML Section 10.1 (`V1.0.5`) | Project Lead & Member 1 | Flyway script `V1.0.5` |

---

## 4. Entry Criteria Verdict

```
+-------------------------------------------------------------------------+
|                  PHASE 2 QUALITY GATE ASSESSMENT                         |
+-------------------------------------------------------------------------+
|  Mandatory Category A Entry Criteria:  11 Items                         |
|  Criteria Satisfied by Evidence:        0 Items                         |
|  Criteria Pending / Blocked:           11 Items                         |
+-------------------------------------------------------------------------+
|  VERDICT: GATE CLOSED — PHASE 2 MUST NOT BEGIN                          |
+-------------------------------------------------------------------------+
```

**Next Action:** Circulate [DATABASE_DECISION_PACKAGE.md](file:///d:/IP%20Folder/DATABASE_DECISION_PACKAGE.md) to designated decision owners to secure formal approvals for Items 1 through 11 before generating `V1.0.0` or `V1.0.1` migration scripts.
