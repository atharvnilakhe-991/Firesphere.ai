# FireSphere.ai — Database Source Document Conflict Register

**Document Status:** Complete Forensic Conflict Register  
**Authority:** Architectural Audit of `FireSphere_Database_Documentation_v2.xml`  
**Date:** October 2026  
**Auditor:** Member 3 (Database, Infrastructure & DevOps Engineer)

---

## Executive Summary

During the forensic audit of `FireSphere_Database_Documentation_v2.xml`, several critical internal contradictions, omissions, and architectural discrepancies were discovered within the document itself. These conflicts directly impact table counts, schema generation, foreign key relational topologies, and multi-tenant isolation. 

Until these conflicts are formally reconciled by the Platform Architect and upstream stakeholders, production Flyway migration scripts cannot be generated.

---

## Documented Conflicts

### CONFLICT-01: Total Table Count Mismatch (85 Claimed vs 91 Listed in Appendix A)
- **Location in Doc**: 
  - Section 1 (Executive Summary, Line 3 of XML): *"resulting in exactly 85 tables across 17 functional modules."*
  - Section 2 (Scope): *"covering all 60 existing tables and 25 new required tables (85 total tables)."*
  - Section 9 (Master Metrics Table): *"Total Tables in Final Production Database (Architecture v2.0): 85 Tables."*
  - Appendix A (Preamble & Registry): *"Appendix A: Complete Master Table Registry (All 85 Tables)... 1. ai_agent_workflow_steps ... 91. zones."*
- **Contradiction**: 
  The document repeatedly claims a mathematical total of **85 tables** (60 legacy + 25 newly required). However, Appendix A explicitly enumerates **91 numbered tables** (1 to 91).
- **Evidence**:
  ```xml
  Appendix A: Complete Master Table Registry (All 85 Tables)
  The following inventory provides an alphabetical directory of all 85 tables in the FireSphere.ai production architecture:
  1. ai_agent_workflow_steps | ... | 91. zones.
  ```
- **Impact**: 
  There is a 6-table discrepancy between the architectural model (85) and the appendix catalog (91). This causes uncertainty in pipeline sizing, migration sequencing, and microservice domain boundaries.
- **Recommendation**: 
  The Platform Architect must officially declare whether FireSphere.ai Architecture v2.0 contains 85 or 91 tables. If the 6 additional tables in Appendix A are part of the target production architecture, the total must be updated to 91, and full data dictionaries must be provided (see CONFLICT-02).

---

### CONFLICT-02: Six (6) Appendix A Tables Have No Column Data Dictionary in Section 6
- **Location in Doc**: 
  - Appendix A vs Section 6 (Detailed Column Data Dictionary for All 25 New Required Tables).
- **Contradiction**: 
  Appendix A labels **31 tables** as `(NEW)`, but Section 6 provides column data dictionaries for **only 25 tables** (Subsections 6.1 to 6.25). The following six (6) tables appear in Appendix A but have zero column definitions in Section 6:
  1. `inventory_categories (NEW)` — Appendix A #46
  2. `notification_templates (NEW)` — Appendix A #57
  3. `payments (NEW)` — Appendix A #64
  4. `purchase_order_items (NEW)` — Appendix A #66
  5. `purchase_orders (NEW)` — Appendix A #67
  6. `vendors (NEW)` — Appendix A #83
- **Evidence**:
  Section 6 ends at `6.25 warehouses (NEW)`. Tables 6.1 through 6.25 account for exactly 25 tables. Tables #46, #57, #64, #66, #67, and #83 have no subsection, no column table, no data types, and no constraints anywhere in Section 6.
- **Impact**: 
  **CRITICAL BLOCKER FOR CODE GENERATION**. Member 3 cannot create DDL or Flyway migration scripts for these 6 tables without guessing columns, data types, nullabilities, and foreign keys—which violates Member 3 Rule 3.
- **Recommendation**: 
  The Platform Architect must supply full column-level data dictionaries for these 6 tables or formally remove them from the target release scope.

---

### CONFLICT-03: `incident_ai_analysis` Mandated in Project Specs is Completely Absent from Source XML
- **Location in Doc**: 
  - Entire XML document (`FireSphere_Database_Documentation_v2.xml`).
- **Contradiction**: 
  `03_MEMBER_3_DATABASE_INFRA_DEVOPS.md` (Line 27) explicitly mandates the resolution of ambiguous `incident_ai_analysis`. However, an exhaustive regex and full-text search across `FireSphere_Database_Documentation_v2.xml` yielded **zero (0) occurrences** of `incident_ai_analysis`.
- **Evidence**:
  Grep search for `incident_ai_analysis` across `FireSphere_Database_Documentation_v2.xml`:
  `No results found (0 matches across 473,748 bytes)`.
- **Impact**: 
  **ARCHITECTURAL DISCONNECT**. The candidate database source document completely omits the table flagged by the project team. It is unknown whether the architect intended to subsume it into `ai_insights` (via polymorphic foreign keys) or accidentally omitted it.
- **Recommendation**: 
  Clarification required from Member 2 (AI Lead) and Platform Architect. If `incident_ai_analysis` is required as a separate relational table, its DDL contract must be authored and approved before Phase 2.

---

### CONFLICT-04: Tenant Isolation Key Type Inconsistency (`organization_id UUID` vs `org_id BIGINT`)
- **Location in Doc**: 
  - Section 2.2 vs Section 6 (DDL specifications) and Section 8.1.
- **Contradiction**: 
  - In Section 2.2: *"Every tenant-aware table includes an organization_id UUID foreign key referencing organizations(id)."*
  - In Section 6: `organizations.id` is defined as `BIGSERIAL` (BIGINT), and child tables (e.g. 6.1 `building_contacts`, 6.6 `incident_dispatches`, 6.19 `vehicle_crew`) define the tenant key as `org_id BIGINT REFERENCES organizations(id)`.
  - In Section 8.1: RLS policy is defined as:
    `WHERE (org_id = current_setting('app.current_org_id')::BIGINT)`
- **Evidence**:
  Section 2.2 specifies `organization_id UUID`, while Section 6 and Section 8.1 implement `org_id BIGINT`.
- **Impact**: 
  Inconsistent tenant column naming (`organization_id` vs `org_id`) and conflicting data types (`UUID` vs `BIGINT`) will break automated RLS policy generation and ORM entity mapping across backend microservices.
- **Recommendation**: 
  Standardize on:
  1. Internal primary keys and foreign keys: `id BIGSERIAL` and `org_id BIGINT` for high-throughput relational indexing and join efficiency.
  2. Public external identifiers: `uuid UUID DEFAULT gen_random_uuid() UNIQUE` on all entities for REST API endpoints and mobile sync.
  3. Formally update Section 2.2 text to match Section 6 DDL specifications.

---

### CONFLICT-05: Entity Naming Contradiction (`sso_user_identities` vs `sso_identities`)
- **Location in Doc**: 
  - Section 4.51 vs Section 5, Section 9, and Appendix A (#71).
- **Contradiction**: 
  - Legacy table 51 is named `sso_user_identities` (`database_52.sql`).
  - Section 5 and Section 9 state: *"REPLACE: sso_user_identities -> sso_identities"*.
  - Appendix A (#71) lists: *"sso_identities (REPLACED)"*.
- **Evidence**:
  Legacy dump file is `database_52.sql` (`sso_user_identities`), while final production catalog lists `sso_identities`.
- **Impact**: 
  Data migration scripts migrating legacy records into the new schema will fail if table names are mismatched between Flyway migration definitions and legacy extraction scripts.
- **Recommendation**: 
  Canonical name in schema `firesphere` is designated as `sso_identities`. Legacy migration script `V1.0.5` must map `sso_user_identities` -> `sso_identities`.

---

### CONFLICT-06: Audit Table Canonical Name Contradiction (`audit_logs` vs `cdc_audit_logs`)
- **Location in Doc**: 
  - Section 4.11 and Appendix A (#11) vs Section 9 and Section 10.1.
- **Contradiction**: 
  - In Section 4.11 and Appendix A (#11): Listed as `audit_logs`.
  - In Section 9 Transformation Summary Table: *"Original Tables Replaced With Enhanced Architectures (REPLACE): 2 (sso_user_identities -> sso_identities; audit_logs -> cdc_audit_logs)"*.
  - In Section 10.1: Flyway migration is named `V1.0.4__cdc_audit_triggers.sql`, while Section 8.3 refers to `audit_logs`.
- **Evidence**:
  Section 9 explicitly claims `audit_logs` is replaced by `cdc_audit_logs`, but Appendix A retains `audit_logs`.
- **Impact**: 
  Database triggers defined in `V1.0.4` will target an invalid table name if the schema defines `audit_logs` while triggers write to `cdc_audit_logs`.
- **Recommendation**: 
  Designate `firesphere.audit_logs` as the canonical table name. The transformation in Section 9 should be clarified as replacing the legacy unpartitioned audit log structure with the trigger-driven CDC architecture under table `firesphere.audit_logs`.

---

### CONFLICT-07: Circular Relational Dependency Between `departments` and `users`
- **Location in Doc**: 
  - Section 7.2 (Relational Topology) and Section 4.23 (`departments`) / Section 4.54 (`users`).
- **Contradiction**: 
  `departments.head_user_id` holds a foreign key referencing `users(id)`, while `users.department_id` holds a foreign key referencing `departments(id)`.
- **Evidence**:
  Section 7.2 states: *"departments (head_user_id) links (N:1) to users. users (department_id) links (N:1) to departments."*
- **Impact**: 
  Circular foreign key dependencies prevent initial table creation and data seeding in standard SQL unless constraints are explicitly made nullable and `DEFERRABLE INITIALLY DEFERRED`.
- **Recommendation**: 
  In the initial Flyway DDL:
  1. Define `departments.head_user_id` as `BIGINT NULL REFERENCES users(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED`.
  2. Define `users.department_id` as `BIGINT NULL REFERENCES departments(id) ON DELETE SET NULL`.
