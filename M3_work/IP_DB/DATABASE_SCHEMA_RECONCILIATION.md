# FireSphere.ai — Database Schema Reconciliation Matrix

**Document Purpose:** Detailed Forensic Reconciliation of Actual Implemented Database Objects vs Architectural Specifications  
**Auditor:** Member 3 (Database, Infrastructure & DevOps)  
**Date:** October 2026  
**Status:** **Forensic Reconciliation Complete**

---

## 1. Migration Component Reconciliation Matrix

The following table itemizes every database component across the 5 Flyway migrations (`V1.0.0` through `V1.0.4`) and seed script (`R__01`):

| Component / Object | Expected Specification | Actually Implemented in SQL | Difference / Variance | Forensic Status |
| :--- | :--- | :--- | :--- | :---: |
| **Extension `uuid-ossp`** | Active in PostgreSQL 16+ | `CREATE EXTENSION IF NOT EXISTS "uuid-ossp";` (`V1.0.0`) | None | `VERIFIED` |
| **Extension `postgis`** | Active PostGIS 3.4+ | `CREATE EXTENSION IF NOT EXISTS "postgis";` (`V1.0.0`) | None | `VERIFIED` |
| **Extension `btree_gist`** | Active composite index support | `CREATE EXTENSION IF NOT EXISTS "btree_gist";` (`V1.0.0`) | None | `VERIFIED` |
| **Extension `pgcrypto`** | Cryptographic hashing & encryption | `CREATE EXTENSION IF NOT EXISTS "pgcrypto";` (`V1.0.0`) | None | `VERIFIED` |
| **Schema `firesphere`** | Primary relational platform schema | `CREATE SCHEMA IF NOT EXISTS firesphere;` (`V1.0.0`) | None | `VERIFIED` |
| **Function `update_updated_at_column()`** | Auto-update `updated_at` timestamps | PL/pgSQL trigger function setting `NEW.updated_at = CURRENT_TIMESTAMP;` (`V1.0.0`) | None | `VERIFIED` |
| **Function `set_tenant_context()`** | Set session tenant context for RLS | PL/pgSQL function setting `app.current_org_id` and `app.is_super_admin` (`V1.0.0`) | None | `VERIFIED` |
| **Function `current_org_id()`** | Get current session tenant context | PL/pgSQL STABLE function returning `current_setting('app.current_org_id', TRUE)::BIGINT` (`V1.0.0`) | None | `VERIFIED` |
| **Table `organizations`** | Root tenant entity with UUID & metadata | `id BIGSERIAL PK`, `uuid UUID UNIQUE`, `code`, `name`, `type`, `status`, `metadata_json` (`V1.0.1`) | None | `VERIFIED` |
| **Table `divisions`** | Administrative geographic zones | `id BIGSERIAL PK`, `uuid UUID UNIQUE`, `org_id BIGINT FK`, `division_code`, `name` (`V1.0.1`) | None | `VERIFIED` |
| **Table `departments`** | Operational departments | `id BIGSERIAL PK`, `uuid UUID UNIQUE`, `org_id BIGINT FK`, `division_id BIGINT FK`, `head_user_id BIGINT` (`V1.0.1`) | None | `VERIFIED` |
| **Table `users`** | Responders & platform operators | `id BIGSERIAL PK`, `uuid UUID UNIQUE`, `org_id BIGINT FK`, `email UNIQUE`, `password_hash`, `is_super_admin`, `force_password_reset` (`V1.0.1`) | None | `VERIFIED` |
| **Constraint `fk_departments_head_user`** | Circular FK resolution | `FOREIGN KEY (head_user_id) REFERENCES firesphere.users(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;` (`V1.0.1`) | None | `VERIFIED` |
| **Table `roles`** | RBAC roles | `id BIGSERIAL PK`, `uuid UUID UNIQUE`, `org_id BIGINT FK`, `role_code`, `role_name`, `is_system` (`V1.0.1`) | None | `VERIFIED` |
| **Table `permissions`** | Granular permissions | `id BIGSERIAL PK`, `uuid UUID UNIQUE`, `permission_code UNIQUE`, `module` (`V1.0.1`) | None | `VERIFIED` |
| **Table `role_permissions`** | Role-Permission mapping | `PRIMARY KEY (role_id, permission_id)`, both FKs CASCADE (`V1.0.1`) | None | `VERIFIED` |
| **Table `user_roles`** | User-Role mapping | `PRIMARY KEY (user_id, role_id)`, both FKs CASCADE (`V1.0.1`) | None | `VERIFIED` |
| **Table `zones`** | Risk zones with polygon boundary | `id BIGSERIAL PK`, `org_id BIGINT FK`, `boundary_geom GEOMETRY(Polygon, 4326)` with GIST index (`V1.0.2`) | None | `VERIFIED` |
| **Table `beats`** | Micro-patrol territories | `id BIGSERIAL PK`, `org_id BIGINT FK`, `boundary_geom GEOMETRY(Polygon, 4326)` with GIST index (`V1.0.2`) | None | `VERIFIED` |
| **Table `fire_stations`** | Turnout stations | `id BIGSERIAL PK`, `org_id BIGINT FK`, `location Point(4326)`, `jurisdiction_geom Polygon(4326)` with GIST indexes (`V1.0.2`) | None | `VERIFIED` |
| **Table `hydrants`** | Municipal water hydrants | `id BIGSERIAL PK`, `org_id BIGINT FK`, `location Point(4326)` with GIST index, `flow_rate_lpm`, `pressure_bar`, `status` (`V1.0.2`) | None | `VERIFIED` |
| **Table `water_sources`** | Emergency reservoirs & sumps | `id BIGSERIAL PK`, `org_id BIGINT FK`, `location Point(4326)` with GIST index, `capacity_liters` (`V1.0.2`) | None | `VERIFIED` |
| **Table `buildings`** | Inspected structural assets | `id BIGSERIAL PK`, `org_id BIGINT FK`, `location Point(4326)`, `building_footprint Polygon(4326)` with GIST indexes, NBC occupancy (`V1.0.2`) | None | `VERIFIED` |
| **Function `find_nearest_operational_hydrants`** | Sub-5ms KNN hydrant locator | PL/pgSQL function using `ORDER BY location <-> ST_SetSRID(...)` (`V1.0.2`) | Performance claim unverified by benchmark | `VERIFIED (CLAIM UNVERIFIED)` |
| **Table `building_contacts`** | Facility contacts (Sec 6.1) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `building_id BIGINT FK`, `contact_name`, `phone_primary` (`V1.0.3`) | None | `VERIFIED` |
| **Table `building_documents`** | Architectural plans / S3 (Sec 6.2) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `building_id BIGINT FK`, `file_path`, `file_size_bytes`, `mime_type`, `file_hash_sha256` (`V1.0.3`) | None | `VERIFIED` |
| **Table `building_fire_equipment`** | Installed fire equipment (Sec 6.3) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `building_id BIGINT FK`, `equipment_type`, `operational_status` (`V1.0.3`) | None | `VERIFIED` |
| **Table `building_owners`** | Property ownership (Sec 6.4) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `building_id BIGINT FK`, `owner_name`, `owner_type`, `phone` (`V1.0.3`) | None | `VERIFIED` |
| **Table `building_risk_profiles`** | AI risk score / priority (Sec 6.5) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `building_id BIGINT FK UNIQUE`, `overall_risk_score`, `inspection_priority` (`V1.0.3`) | None | `VERIFIED` |
| **Table `noc_application_categories`** | NOC categories (Legacy Table 34) | `id BIGSERIAL PK`, `category_code UNIQUE`, `standard_fee`, `validity_years` (`V1.0.3`) | None | `VERIFIED` |
| **Table `noc_applications`** | Core Fire NOC application | `id BIGSERIAL PK`, `org_id BIGINT FK`, `building_id BIGINT FK`, `category_id BIGINT FK`, `current_status` (`V1.0.3`) | None | `VERIFIED` |
| **Table `noc_application_status_history`** | Immutable NOC audit (Sec 6.17) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `application_id BIGINT FK`, `previous_status`, `new_status`, `transitioned_at` (`V1.0.3`) | None | `VERIFIED` |
| **Table `noc_certificates`** | Issued statutory certs (Sec 6.18) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `application_id BIGINT FK UNIQUE`, `certificate_number UNIQUE`, `qr_code_token UNIQUE` (`V1.0.3`) | None | `VERIFIED` |
| **Table `noc_renewals`** | Annual renewals (Sec 6.19) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `certificate_id BIGINT FK`, `renewal_application_number UNIQUE`, `renewal_status` (`V1.0.3`) | None | `VERIFIED` |
| **Table `inspection_checklists`** | Checklists (Legacy Table 26) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `checklist_code`, `title`, `occupancy_type` (`V1.0.3`) | None | `VERIFIED` |
| **Table `inspection_checklist_items`** | Checklist items (Sec 6.13) | `id BIGSERIAL PK`, `checklist_id BIGINT FK`, `item_sequence`, `parameter_description`, `requirement_type` (`V1.0.3`) | None | `VERIFIED` |
| **Table `inspections`** | Field inspections | `id BIGSERIAL PK`, `org_id BIGINT FK`, `building_id BIGINT FK`, `noc_application_id BIGINT FK`, `inspection_status` (`V1.0.3`) | None | `VERIFIED` |
| **Table `inspection_assignments`** | Inspector assignments (Sec 6.12) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `inspection_id BIGINT FK`, `officer_user_id BIGINT FK`, `assignment_role` (`V1.0.3`) | None | `VERIFIED` |
| **Table `inspection_findings`** | NBC non-compliances (Sec 6.14) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `inspection_id BIGINT FK`, `is_compliant`, `severity_grade`, `finding_description` (`V1.0.3`) | None | `VERIFIED` |
| **Table `corrective_actions`** | Mandated rectifications (Sec 6.15) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `finding_id BIGINT FK`, `deadline_date`, `action_status` (`V1.0.3`) | None | `VERIFIED` |
| **Table `violations`** | Legal violation records | `id BIGSERIAL PK`, `org_id BIGINT FK`, `building_id BIGINT FK`, `violation_number UNIQUE`, `severity`, `status` (`V1.0.3`) | None | `VERIFIED` |
| **Table `violation_actions`** | Enforcement notices (Sec 6.16) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `violation_id BIGINT FK`, `action_type`, `penalty_amount`, `is_executed` (`V1.0.3`) | None | `VERIFIED` |
| **Table `incidents`** | 112/911 emergency response | `id BIGSERIAL PK`, `org_id BIGINT FK`, `incident_number UNIQUE`, `location Point(4326)` with GIST index, `incident_status` (`V1.0.4`) | None | `VERIFIED` |
| **Table `incident_dispatches`** | Emergency dispatches (Sec 6.6) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `incident_id BIGINT FK`, `dispatch_code UNIQUE`, `dispatched_station_id BIGINT FK` (`V1.0.4`) | None | `VERIFIED` |
| **Table `fire_vehicles`** | Apparatus fleet registry | `id BIGSERIAL PK`, `org_id BIGINT FK`, `registration_number UNIQUE`, `call_sign`, `vehicle_type`, `operational_status` (`V1.0.4`) | None | `VERIFIED` |
| **Table `incident_vehicles`** | Mobilized apparatus (Sec 6.7) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `incident_id BIGINT FK`, `vehicle_id BIGINT FK`, `status` (`V1.0.4`) | None | `VERIFIED` |
| **Table `incident_crews`** | Responders on scene (Sec 6.8) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `incident_id BIGINT FK`, `responder_user_id BIGINT FK`, `role_in_incident` (`V1.0.4`) | None | `VERIFIED` |
| **Table `incident_timeline`** | Incident events log (Sec 6.9) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `incident_id BIGINT FK`, `event_timestamp`, `event_type`, `description` (`V1.0.4`) | None | `VERIFIED` |
| **Table `incident_casualties`** | Injury & casualty triage (Sec 6.10) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `incident_id BIGINT FK`, `person_type`, `injury_severity` (`V1.0.4`) | None | `VERIFIED` |
| **Table `incident_reports`** | Forensic investigation (Sec 6.11) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `incident_id BIGINT FK UNIQUE`, `report_number UNIQUE`, `cause_of_fire` (`V1.0.4`) | None | `VERIFIED` |
| **Table `incident_evidence`** | Raw media metadata store (Sec 5) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `incident_id BIGINT FK`, `file_path`, `file_hash_sha256`, `location Point(4326)` (`V1.0.4`) | Raw store only; no AI columns | `VERIFIED` |
| **Table `vehicle_crew`** | Shift apparatus crew (Sec 6.20) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `vehicle_id BIGINT FK`, `crew_member_user_id BIGINT FK`, `shift_date` (`V1.0.4`) | None | `VERIFIED` |
| **Table `vehicle_fitness`** | Pump & RTO testing (Sec 6.21) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `vehicle_id BIGINT FK`, `pump_test_result`, `fitness_certificate_expiry` (`V1.0.4`) | None | `VERIFIED` |
| **Table `vehicle_fuel_logs`** | Fuel issuance logs (Sec 6.22) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `vehicle_id BIGINT FK`, `fuel_liters`, `odometer_reading_km` (`V1.0.4`) | None | `VERIFIED` |
| **Table `vehicle_location_history`**| Live GPS telemetry (Sec 6.23) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `vehicle_id BIGINT FK`, `location Point(4326)` with GIST index, `speed_kmh` (`V1.0.4`) | None | `VERIFIED` |
| **Table `warehouses`** | Supply depots (Sec 6.25) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `warehouse_code`, `warehouse_name`, `is_active` (`V1.0.4`) | None | `VERIFIED` |
| **Table `inventory_items`** | Supply catalog (Legacy Table 31) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `item_code`, `item_name`, `reorder_threshold` (`V1.0.4`) | None | `VERIFIED` |
| **Table `inventory_stock`** | Warehouse balances (Sec 6.24) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `warehouse_id BIGINT FK`, `item_id BIGINT FK`, `quantity_on_hand` (`V1.0.4`) | None | `VERIFIED` |
| **Table `training_courses`** | Fire Academy courses (Legacy 47) | `id BIGSERIAL PK`, `org_id BIGINT FK`, `course_code`, `title`, `duration_hours` (`V1.0.4`) | None | `VERIFIED` |
| **Table `audit_logs`** | Canonical CDC audit table | `id BIGSERIAL PK`, `org_id BIGINT FK`, `table_name`, `entity_id`, `action`, `old_values JSONB`, `new_values JSONB` (`V1.0.4`) | None | `VERIFIED` |
| **Function `firesphere_audit_trigger()`**| CDC trigger for entity state diffs | Pl/pgSQL trigger function inserting into `firesphere.audit_logs` (`V1.0.4`) | `v_user_id` never assigned; always inserts NULL | `DEFECT (PARTIALLY IMPLEMENTED)` |
| **Row-Level Security Policies** | Kernel-level multi-tenant isolation | Expected `ALTER TABLE ... ENABLE ROW LEVEL SECURITY;` on all tenant tables | **NOT IMPLEMENTED IN SQL** | `MISSING` |

---

## 2. Table Scope Reconciliation Summary

```
Total Tables in Architecture Candidate Scope: 85 (Claimed) vs 91 (Appendix A)
  ├── Implemented in Migrations V1.0.1 to V1.0.4:  50 Tables
  │     ├── Documented New Tables (Section 6.1–6.25): 25 Tables (100% of Section 6)
  │     ├── Populated Legacy Tables:                  13 Tables
  │     └── Core Unpopulated Legacy Tables:           12 Tables
  ├── Un-modeled Appendix A Extra Tables:            6 Tables (BLOCKED / OMITTED)
  │     (inventory_categories, notification_templates, payments,
  │      purchase_order_items, purchase_orders, vendors)
  ├── Un-modeled AI Entity:                          1 Table (BLOCKED / OMITTED)
  │     (incident_ai_analysis)
  └── Remaining Legacy Auxiliary Tables:             34 Tables (DEFERRED)
        (e.g., ai_models, ai_agents, ai_insights, citizens, complaints,
         integrations, api_keys, user_sessions, etc.)
```

---

## 3. Discrepancy & Gap Analysis

1. **Table Count Mismatch Against Section 6:**
   All 25 new required production tables specified in Sections 6.1 through 6.25 of `FireSphere_Database_Documentation_v2.xml` are **100% implemented** with exact primary keys, foreign keys, and column attributes.
2. **Missing AI Subsystem in Initial Migrations:**
   Legacy table 4 (`ai_insights`) and legacy table 5 (`ai_models`) were not included in `V1.0.1`–`V1.0.4`. Consequently, the claims in `DATABASE_INTEGRATION_CONTRACT.md` regarding Member 4 using `ai_insights` are currently invalid against the deployed database schema.
3. **No Unapproved Invented Tables:**
   Zero speculative tables were invented. Every implemented table maps directly to either a Section 6 specification or a verified legacy entity.
