# FireSphere.ai — Multi-Tenant Isolation & Row-Level Security Implementation Audit

**Document Purpose:** In-Depth Forensic Security Audit of Multi-Tenant Database Isolation  
**Auditor:** Member 3 (Database, Infrastructure & DevOps)  
**Date:** October 2026  
**Audit Finding:** **CRITICAL DEFICIENCY IDENTIFIED — RLS POLICIES NOT APPLIED AT DDL LAYER**

---

## 1. Executive Summary & Root Vulnerability

While the relational foundation consistently defines tenant foreign keys (`org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id)`) across domain tables, **Row-Level Security (RLS) is currently completely inactive in the database engine**.

Specifically:
1. `firesphere.set_tenant_context()` was created in migration `V1.0.0`, setting session variables `app.current_org_id` and `app.is_super_admin`.
2. However, **`ALTER TABLE firesphere.<table_name> ENABLE ROW LEVEL SECURITY;` was NEVER executed on any domain table**.
3. **No `CREATE POLICY` statements were executed in any migration script**.

### Immediate Impact:
- **Zero Kernel-Level Isolation:** Any client or backend query executing `SELECT * FROM firesphere.buildings;` without an explicit application-layer `WHERE org_id = :org_id` clause will return records belonging to **ALL** tenant organizations.
- **Vulnerability to SQL Injection:** A read-only SQL injection vulnerability in application code completely bypasses tenant isolation because PostgreSQL kernel RLS is not enabled.
- **Connection Pool Leakage:** If backend database connection pools do not clean up session context (`DISCARD ALL` or resetting `app.current_org_id = ''`), subsequent requests using the same borrowed connection risk executing queries under stale tenant contexts.

---

## 2. Forensic Tenant Isolation Matrix

The following matrix documents every implemented table in the `firesphere` schema, evaluating its tenant key, foreign key enforcement, RLS status, and isolation risk:

| Table Name | Tenant Column | Tenant Type | FK to Organizations | RLS Enabled | RLS Forced | Policy Defined | Isolation Risk | Current Status |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- | :---: |
| `organizations` | `id` (PK) | `BIGINT` | N/A (Root) | **NO** | **NO** | None | Cross-tenant enumeration | `DEFICIENT` |
| `divisions` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `departments` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `users` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Critical (PII Leakage) | `DEFICIENT` |
| `roles` | `org_id` | `BIGINT` | `YES (CASCADE)` | **NO** | **NO** | None | High (Privilege leak) | `DEFICIENT` |
| `permissions` | N/A | Global | N/A (Global) | N/A | N/A | None | Safe (Global taxonomy) | `VERIFIED GLOBAL` |
| `role_permissions` | N/A | Global | N/A (Junction) | N/A | N/A | None | Safe (RBAC mapping) | `VERIFIED GLOBAL` |
| `user_roles` | N/A | Joint | Via `users` | **NO** | **NO** | None | High (Role assignment) | `DEFICIENT` |
| `zones` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `beats` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `fire_stations` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `hydrants` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `water_sources` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `buildings` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Critical (Commercial PII)| `DEFICIENT` |
| `building_contacts` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Critical (Citizen PII) | `DEFICIENT` |
| `building_documents` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Critical (S3 Key Leak) | `DEFICIENT` |
| `building_fire_equipment`| `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `building_owners` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Critical (PAN/Tax IDs) | `DEFICIENT` |
| `building_risk_profiles`| `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High (Hazard score) | `DEFICIENT` |
| `noc_application_categories`| N/A | Global | N/A (Taxonomy) | N/A | N/A | None | Safe (Taxonomy) | `VERIFIED GLOBAL` |
| `noc_applications` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Critical (Legal filings)| `DEFICIENT` |
| `noc_application_status_history`| `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `noc_certificates` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Critical (Legal NOCs) | `DEFICIENT` |
| `noc_renewals` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `inspection_checklists`| `org_id` | `BIGINT` | `YES (CASCADE)` | **NO** | **NO** | None | Moderate | `DEFICIENT` |
| `inspection_checklist_items`| N/A | Joint | Via Checklist | **NO** | **NO** | None | Moderate | `DEFICIENT` |
| `inspections` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Critical (Audit logs) | `DEFICIENT` |
| `inspection_assignments`| `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `inspection_findings` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High (Legal penalties) | `DEFICIENT` |
| `corrective_actions` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `violations` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Critical (Legal notices)| `DEFICIENT` |
| `violation_actions` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Critical (Sealing orders)| `DEFICIENT` |
| `incidents` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Critical (112 emergency) | `DEFICIENT` |
| `incident_dispatches` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Critical | `DEFICIENT` |
| `fire_vehicles` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High (Fleet telemetry) | `DEFICIENT` |
| `incident_vehicles` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `incident_crews` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `incident_timeline` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `incident_casualties` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Critical (Medical PII) | `DEFICIENT` |
| `incident_reports` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Critical (Court reports)| `DEFICIENT` |
| `incident_evidence` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Critical (Evidence chain)| `DEFICIENT` |
| `vehicle_crew` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `vehicle_fitness` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Moderate | `DEFICIENT` |
| `vehicle_fuel_logs` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Moderate | `DEFICIENT` |
| `vehicle_location_history`| `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High (Apparatus GPS) | `DEFICIENT` |
| `warehouses` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `inventory_items` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | Moderate | `DEFICIENT` |
| `inventory_stock` | `org_id` | `BIGINT` | `YES (RESTRICT)` | **NO** | **NO** | None | High | `DEFICIENT` |
| `training_courses` | `org_id` | `BIGINT` | `YES (CASCADE)` | **NO** | **NO** | None | Low | `DEFICIENT` |
| `audit_logs` | `org_id` | `BIGINT` | `YES (SET NULL)`| **NO** | **NO** | None | Critical (Audit leakage)| `DEFICIENT` |

---

## 3. Analysis of Failure Modes & Risks

### 3.1 Why RLS Was Not Implemented
During Phase 1B and 1C, ADR-006 (`organization_id UUID` vs `org_id BIGINT`) was documented as a conflict. While the physical migrations adopted `org_id BIGINT` as an implementation assumption, the author of migrations `V1.0.1`–`V1.0.4` omitted the RLS policy script (originally planned for `V1.0.3` in Section 10.1 of candidate documentation).

### 3.2 Cross-Tenant Foreign Key Integrity Vulnerability
- Although foreign keys reference `organizations(id)` with `ON DELETE RESTRICT`, **cross-tenant relational integrity is not enforced between child entities**.
- *Example:* An inspection belonging to Tenant A (`inspections.org_id = 1`) could theoretically reference a building belonging to Tenant B (`inspections.building_id = 999` where `buildings.org_id = 2`).
- *Remedy:* Composite foreign keys `(org_id, id)` or database trigger cross-tenant validation must be added.

### 3.3 Super-Admin Bypass Behavior
The `set_tenant_context` function accepts `p_is_super_admin BOOLEAN`. When RLS is eventually enabled, the policy must explicitly require:
```sql
CREATE POLICY tenant_isolation_policy ON firesphere.<table_name>
    USING (
        org_id = current_setting('app.current_org_id', TRUE)::BIGINT 
        OR current_setting('app.is_super_admin', TRUE)::BOOLEAN = TRUE
    );
```
Without strict RLS tests verifying that non-super-admins cannot set `app.is_super_admin = true`, privilege escalation remains a risk.

---

## 4. Required Remediation (Pre-Integration Blocker P0)

Before any backend microservice connects in a multi-tenant staging or production environment, migration **`V1.0.5__multi_tenant_rls_policies.sql`** must be authored to:
1. Execute `ALTER TABLE firesphere.<table_name> ENABLE ROW LEVEL SECURITY;` on all 47 tenant-sensitive tables.
2. Execute `ALTER TABLE firesphere.<table_name> FORCE ROW LEVEL SECURITY;` to ensure table owners also obey RLS rules.
3. Apply standard isolation policies filtering by `firesphere.current_org_id()`.
