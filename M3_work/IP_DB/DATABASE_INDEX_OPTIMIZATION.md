# FireSphere.ai — Secondary Foreign Key Index Optimization Report

**Author:** Member 3 — Database, Infrastructure & DevOps  
**Milestone:** P1 Performance & Relational Integrity Hardening  
**Migration:** `V1.0.6__add_secondary_foreign_key_indexes.sql`  
**Date:** October 2026  
**Status:** **VERIFIED & APPLIED**

---

## 1. Executive Summary

In PostgreSQL, foreign key constraints (`REFERENCES`) do not automatically generate underlying indexes on referencing child columns. Without supporting B-tree indexes, relational joins, foreign key lookups, cascading updates/deletes, and tenant-scoped filtering can require sequential table scans across multi-megabyte tables.

Following the forensic analysis in [DATABASE_FINAL_M3_SCHEMA_AUDIT.md](file:///d:/IP%20Folder/DATABASE_FINAL_M3_SCHEMA_AUDIT.md), Member 3 audited all 68 foreign key relationships across the 50 platform tables and authored Flyway migration **`V1.0.6__add_secondary_foreign_key_indexes.sql`** to implement 7 strictly justified, high-impact B-tree indexes.

---

## 2. Inventory of Optimized Indexes

| # | Table Name | Column Name | Target Referenced Table & Column | Index Name | Optimization Justification |
| :-: | :--- | :--- | :--- | :--- | :--- |
| **1** | `firesphere.incident_dispatches` | `dispatched_station_id` | `fire_stations(id)` | `idx_dispatches_station_id` | Accelerates turnouts and apparatus deployment history queries filtered or joined by fire station. Eliminates table scan when stations query active dispatches. |
| **2** | `firesphere.incident_dispatches` | `dispatched_by_user_id` | `users(id)` | `idx_dispatches_user_id` | Accelerates dispatcher audit lookups and responder dispatch tracking by operational officer. Prevents sequential scan during user account maintenance. |
| **3** | `firesphere.vehicle_fitness` | `inspected_by_user_id` | `users(id)` | `idx_veh_fitness_user_id` | Accelerates vehicle fitness certification logs and compliance reports audited by inspecting officer. |
| **4** | `firesphere.vehicle_fuel_logs` | `recorded_by_user_id` | `users(id)` | `idx_veh_fuel_user_id` | Accelerates fuel audit queries, cost reconciliation reports, and operator logging history. |
| **5** | `firesphere.training_courses` | `org_id` | `organizations(id)` | `idx_training_courses_org_id` | Multi-tenant course catalog filtering. Essential for high-performance Row-Level Security (RLS) evaluation under `tenant_isolation_policy`. |
| **6** | `firesphere.building_fire_equipment` | `org_id` | `organizations(id)` | `idx_building_equipment_org_id` | Accelerates tenant-wide life-safety equipment compliance audits and RLS query filtering without requiring full joins to `buildings`. |
| **7** | `firesphere.building_risk_profiles` | `org_id` | `organizations(id)` | `idx_building_risk_org_id` | Accelerates city-wide high-risk building prioritization rankings and tenant-scoped analytics dashboards. |

---

## 3. Forensic Validation & Redundancy Check

Every candidate index was rigorously cross-referenced against all existing migration definitions (`V1.0.1`–`V1.0.5`):

1. **`idx_dispatches_station_id`**: Table `incident_dispatches` previously only had index on `(incident_id)`. Zero collision. No redundant prefix index exists.
2. **`idx_dispatches_user_id`**: No prior index on `dispatched_by_user_id`. Zero collision.
3. **`idx_veh_fitness_user_id`**: Table `vehicle_fitness` previously only had index on `(vehicle_id)`. Zero collision.
4. **`idx_veh_fuel_user_id`**: Table `vehicle_fuel_logs` previously only had index on `(vehicle_id)`. Zero collision.
5. **`idx_training_courses_org_id`**: Table `training_courses` previously had zero indexes on `org_id`. Zero collision.
6. **`idx_building_equipment_org_id`**: Table `building_fire_equipment` previously only had index on `(building_id)`. Adding `idx_building_equipment_org_id` enables direct tenant RLS index scans without joining `buildings`.
7. **`idx_building_risk_org_id`**: Table `building_risk_profiles` previously only had index on `(inspection_priority)` and unique constraint on `(building_id)`. Direct tenant queries now scan B-tree on `org_id`.

---

## 4. Verification of Migration Ordering & Reproducibility

- **Previous Migration:** `V1.0.5__enable_row_level_security.sql` (P0 Hardening: RLS & CDC Audit triggers).
- **Current Migration:** `V1.0.6__add_secondary_foreign_key_indexes.sql` (P1 Secondary FK Index Optimizations).
- **Execution Safety:** Pure DDL extension using `CREATE INDEX IF NOT EXISTS`.
- **Zero Schema Invalidation:** Does not drop, alter, or redefine any existing table columns, constraints, or foreign keys. Fully backward-compatible with JPA `ddl-auto=validate`.

---

## 5. Architectural Boundaries Preserved

- No new business tables created.
- 50-table scope strictly maintained.
- AI schema remains ring-fenced behind [DATABASE_AI_SCHEMA_BLOCKER.md](file:///d:/IP%20Folder/DATABASE_AI_SCHEMA_BLOCKER.md).
- No speculative indexes added.
- Documented in the authoritative cross-member handoff specification: [DATABASE_M3_FINAL_HANDOFF.md](file:///d:/IP%20Folder/DATABASE_M3_FINAL_HANDOFF.md).

