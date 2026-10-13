# FireSphere.ai — Unsupported, Invented & Deficient Implementation Audit

**Document Purpose:** Forensic Audit of Unsupported Components, Unverified Claims, Incomplete Logic, and Unapproved Assumptions  
**Auditor:** Member 3 (Database, Infrastructure & DevOps)  
**Date:** October 2026  
**Status:** **Identified Implementation Anomalies & Deficiencies Documented**

---

## 1. Overview

This document itemizes every component, column, trigger, claim, or architectural decision discovered in the physical implementation that either:
1. Lacks authoritative backing from the audited source document (`FireSphere_Database_Documentation_v2.xml`),
2. Contains logical coding defects or omissions,
3. Represents an unapproved assumption encoded without formal stakeholder sign-off, OR
4. Generates an unverified performance or architectural claim.

---

## 2. Unsupported & Deficient Components Register

| # | Component / Object | Exact File & Location | Why It Was Introduced | Source Support in Candidate XML | Risk Assessment | Recommended Action |
| :-: | :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | **Audit Trigger User ID Omission (`v_user_id` defect)** | `database/migrations/V1.0.4__emergency_incidents_fleet_and_audit.sql` (Lines 321, 357) | Intended to record the actor modifying audited entities in `audit_logs.user_id`. | Section 8.3 explicitly specifies recording user context in audit logs. | **HIGH (AUDIT DEFEATED)**: Variable `v_user_id` is declared but never populated; all audit records permanently record `user_id = NULL`. | **P0 Fix**: Update trigger function to assign `v_user_id := current_setting('app.current_user_id', TRUE)::BIGINT;` in next migration. |
| **2** | **Contract Claim: `ai_insights` Table Availability** | `DATABASE_INTEGRATION_CONTRACT.md` (Section 4.2) | Intended to provide Member 4 (AI Lead) a working boundary for storing incident AI predictions. | Legacy Table 4 in dump. In XML Section 4.4, documented as empty table with inferred columns. | **HIGH (INTEGRATION BREAK)**: `ai_insights` was **omitted from migrations V1.0.1–V1.0.4**. Member 4 queries against it will fail with `relation does not exist`. | **P0 Fix**: Author `ai_insights` table in migration `V1.0.5` or amend M4 contract to hold AI records in local files until ADR-005 resolves. |
| **3** | **Row-Level Security (RLS) Policy Omission** | Entire DDL stack (`V1.0.1` through `V1.0.4`) | RLS helper functions were created in `V1.0.0`, but table-level activation was skipped. | Section 2.2 and Section 8.1 mandate RLS on all tenant tables. | **CRITICAL (TENANT LEAK)**: Kernel isolation is inactive. Raw queries without application-layer `WHERE org_id = :org_id` leak cross-tenant records. | **P0 Fix**: Author migration `V1.0.5__multi_tenant_rls_policies.sql` to execute `ENABLE ROW LEVEL SECURITY` and `CREATE POLICY` on all 47 tenant tables. |
| **4** | **Sub-5ms KNN Performance Guarantee Claim** | `database/migrations/V1.0.2` (Line 138), `DATABASE_SPATIAL_AUDIT.md`, `DATABASE_INTEGRATION_CONTRACT.md` | PostGIS spatial search function `find_nearest_operational_hydrants()`. | XML Section 8.2 claims sub-5ms KNN query speed over 100,000 hydrants. | **MODERATE**: No benchmark script or synthetic dataset exists in the repository to substantiate the sub-5ms SLA. | **P1 Action**: Reclassify claim as `PERFORMANCE CLAIM NOT VERIFIED`. Author an empirical benchmark test under `database/tests/` before production SLA sign-off. |
| **5** | **Implemented Assumption: Dual-Key Pattern (`org_id BIGINT` + `uuid UUID`)** | `V1.0.1`–`V1.0.4` across all 50 tables | To reconcile Section 2.2 (`organization_id UUID`) with Section 6 (`org_id BIGINT`). | Conflicting between Section 2.2 narrative and Section 6 DDL specifications. | **MODERATE**: Implemented without formal approval of ADR-006 / ADR-011. If Architect insists on pure UUID, schema must be rewritten. | **P1 Action**: Formally obtain Platform Architect sign-off on ADR-006 (Option C) in `DATABASE_STAKEHOLDER_DECISION_FORM.md`. |
| **6** | **Implemented Assumption: `DEFERRABLE INITIALLY DEFERRED` Circular FK** | `database/migrations/V1.0.1` (Line 88) | To resolve the mutual foreign key deadlock between `departments` and `users`. | Section 7.2 declares circular links but specifies no SQL execution pattern. | **LOW**: Technically sound standard PostgreSQL pattern, but was encoded without formal Member 1 sign-off on ADR-007. | **P1 Action**: Formally obtain Member 1 sign-off on ADR-007 (Option A) in `DATABASE_STAKEHOLDER_DECISION_FORM.md`. |
| **7** | **Implemented Assumption: Raw Media Only in `incident_evidence`** | `database/migrations/V1.0.4` (Line 193) | To store incident photos and audio files in MinIO without speculating on AI bounding boxes. | Legacy Table 25 (empty); Section 4.25 / Section 5 marked `MODIFY`. | **MODERATE**: If Member 2 expects computer vision detection columns in `incident_evidence`, schema will need alteration. | **P1 Action**: Formally obtain Member 2 sign-off on ADR-008 (Option A) in `DATABASE_STAKEHOLDER_DECISION_FORM.md`. |

---

## 3. Invented Business Field / Column Audit

Across the 50 implemented tables:
- **No invented business tables** were created (e.g. no random tables for payments or procurement were fabricated).
- In `firesphere.organizations`, columns `jurisdiction_area`, `contact_email`, `contact_phone`, and `status` were included based on legacy MariaDB table 41 data exports (`database_42.sql`).
- In `firesphere.users`, columns `phone`, `designation`, `mfa_enabled`, `force_password_reset`, and `last_login_at` were derived from legacy table 52 data and security recommendations.
- In `firesphere.incidents`, columns `caller_name`, `caller_phone`, and `address_text` were derived from standard 112/911 dispatch requirements.

---

## 4. Prioritized Action Plan

1. **P0 (Must Fix Before Multi-Service Integration):**
   - Author Flyway migration `V1.0.5` to apply `ENABLE ROW LEVEL SECURITY` and `FORCE ROW LEVEL SECURITY` across all tenant domain tables.
   - Patch `firesphere_audit_trigger()` in `V1.0.5` to capture `current_setting('app.current_user_id', TRUE)::BIGINT`.
   - Resolve `ai_insights` DDL table availability for Member 4.
2. **P1 (Should Fix Before Staging Deployment):**
   - Execute formal stakeholder sign-offs in `DATABASE_STAKEHOLDER_DECISION_FORM.md` for ADR-006, ADR-007, ADR-008, ADR-011, and ADR-012.
   - Run empirical performance benchmark on PostGIS KNN hydrant query.
3. **P2 (Can Address During Feature Iteration):**
   - Populate `changed_fields`, `client_ip`, and `user_agent` in `audit_logs`.
