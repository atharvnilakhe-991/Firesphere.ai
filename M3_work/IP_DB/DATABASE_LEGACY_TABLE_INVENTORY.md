# FireSphere.ai — Legacy Table Inventory & Forensic Audit (60 Tables)

**SOURCE DOCUMENT**: `FireSphere_Database_Documentation_v2.xml` (Sections 3, 4, 5)  
**STATUS**: `FORENSIC AUDIT COMPLETED`  
**LEGACY ENGINE**: MariaDB 11.8.9 (Data-only export without DDL statements)  
**SUMMARY**: 12 tables populated with verified data; 48 tables empty (0 rows) with schema marked "Not verifiable from supplied SQL dump — requires schema inspection".

---

## Legacy Tables Detailed Inventory

| # | Legacy Table Name | Source Export | Populated / Empty | Known Data Characteristics | Architectural Decision | Target Production Table(s) | Confidence / Status | Source Section |
|---|---|---|---|---|---|---|---|---|
| 1 | `ai_agent_workflow_steps` | `database_2.sql` | Empty (0 rows) | Unverifiable from dump; inferred execution sequence for multi-agent chains | MODIFY | `ai_agent_workflow_steps` | `INFERRED — NOT AUTHORITATIVE` | 4.1, 5.1 |
| 2 | `ai_agent_workflows` | `database_3.sql` | Empty (0 rows) | Unverifiable from dump; inferred autonomous orchestration pipelines | MODIFY | `ai_agent_workflows` | `INFERRED — NOT AUTHORITATIVE` | 4.2, 5.2 |
| 3 | `ai_agents` | `database_4.sql` | Populated (2 rows) | Verified 2 rows: 'Fire Inspection Co-Pilot', 'Building Code Assistant' | MODIFY | `ai_agents` | `CONFIRMED (DATA VERIFIED)` | 4.3, 5.3 |
| 4 | `ai_insights` | `database_5.sql` | Empty (0 rows) | Unverifiable from dump; inferred predictive risk & compliance findings | MODIFY | `ai_insights` | `INFERRED — NOT AUTHORITATIVE` | 4.4, 5.4 |
| 5 | `ai_models` | `database_6.sql` | Populated (2 rows) | Verified 2 rows: 'gpt-4o-mini', 'fire-inspection-v1' | MODIFY | `ai_models` | `CONFIRMED (DATA VERIFIED)` | 4.5, 5.5 |
| 6 | `ai_prompts` | `database_7.sql` | Empty (0 rows) | Unverifiable from dump; inferred parameterized prompt templates | KEEP | `ai_prompts` | `INFERRED — NOT AUTHORITATIVE` | 4.6, 5.6 |
| 7 | `ai_runs` | `database_8.sql` | Empty (0 rows) | Unverifiable from dump; inferred audit trace of AI token consumption & latency | MODIFY | `ai_runs` | `INFERRED — NOT AUTHORITATIVE` | 4.7, 5.7 |
| 8 | `ai_sessions` | `database_9.sql` | Empty (0 rows) | Unverifiable from dump; inferred interactive conversational contexts | MODIFY | `ai_sessions` | `INFERRED — NOT AUTHORITATIVE` | 4.8, 5.8 |
| 9 | `api_clients` | `database_10.sql` | Empty (0 rows) | Unverifiable from dump; inferred OAuth2/M2M external credentials | MODIFY | `api_clients` | `INFERRED — NOT AUTHORITATIVE` | 4.9, 5.9 |
| 10 | `api_keys` | `database_11.sql` | Empty (0 rows) | Unverifiable from dump; inferred programmatic API tokens | MODIFY | `api_keys` | `INFERRED — NOT AUTHORITATIVE` | 4.10, 5.10 |
| 11 | `audit_logs` | `database_12.sql` | Empty (0 rows) | Unverifiable from dump; inferred transactional audit log | REPLACE | `audit_logs` (CDC) | `INFERRED — NOT AUTHORITATIVE` | 4.11, 5.11 |
| 12 | `audits` | `database_13.sql` | Empty (0 rows) | Unverifiable from dump; inferred fire station operational safety audits | MODIFY | `audits`, `audit_findings` | `INFERRED — NOT AUTHORITATIVE` | 4.12, 5.12 |
| 13 | `beats` | `database_14.sql` | Empty (0 rows) | Unverifiable from dump; inferred patrol territories within zones | MODIFY | `beats` | `INFERRED — NOT AUTHORITATIVE` | 4.13, 5.13 |
| 14 | `buildings` | `database_15.sql` | Empty (0 rows) | Unverifiable from dump; inferred commercial/residential registry | SPLIT | `buildings`, `building_owners`, `building_contacts`, `building_documents`, `building_fire_equipment` | `INFERRED — NOT AUTHORITATIVE` | 4.14, 5.14 |
| 15 | `citizens` | `database_16.sql` | Empty (0 rows) | Unverifiable from dump; inferred public portal users | MODIFY | `citizens` | `INFERRED — NOT AUTHORITATIVE` | 4.15, 5.15 |
| 16 | `complaints` | `database_17.sql` | Empty (0 rows) | Unverifiable from dump; inferred citizen grievance reports | MODIFY | `complaints` | `INFERRED — NOT AUTHORITATIVE` | 4.16, 5.16 |
| 17 | `dashboard_metrics` | `database_18.sql` | Empty (0 rows) | Unverifiable from dump; inferred daily KPI aggregates | KEEP | `dashboard_metrics` | `INFERRED — NOT AUTHORITATIVE` | 4.17, 5.17 |
| 18 | `departments` | `database_19.sql` | Empty (0 rows) | Unverifiable from dump; inferred internal administrative branches | MODIFY | `departments` | `INFERRED — NOT AUTHORITATIVE` | 4.18, 5.18 |
| 19 | `divisions` | `database_20.sql` | Empty (0 rows) | Unverifiable from dump; inferred municipal regional operational divisions | MODIFY | `divisions` | `INFERRED — NOT AUTHORITATIVE` | 4.19, 5.19 |
| 20 | `documents` | `database_21.sql` | Empty (0 rows) | Unverifiable from dump; inferred file catalog for object storage | MODIFY | `documents` | `INFERRED — NOT AUTHORITATIVE` | 4.20, 5.20 |
| 21 | `fire_stations` | `database_22.sql` | Populated (1 row) | Verified 1 row: 'Pune Central Fire Station' (lat 18.5204, lng 73.8567) | MODIFY | `fire_stations` | `CONFIRMED (DATA VERIFIED)` | 4.21, 5.21 |
| 22 | `fire_vehicles` | `database_23.sql` | Empty (0 rows) | Unverifiable from dump; inferred apparatus inventory | SPLIT | `fire_vehicles`, `vehicle_location_history`, `vehicle_crew`, `vehicle_fuel_logs`, `vehicle_fitness` | `INFERRED — NOT AUTHORITATIVE` | 4.22, 5.22 |
| 23 | `generated_reports` | `database_24.sql` | Empty (0 rows) | Unverifiable from dump; inferred official PDF reports & parameters | MODIFY | `generated_reports` | `INFERRED — NOT AUTHORITATIVE` | 4.23, 5.23 |
| 24 | `hydrants` | `database_25.sql` | Empty (0 rows) | Unverifiable from dump; inferred municipal hydrants & flow ratings | MODIFY | `hydrants` | `INFERRED — NOT AUTHORITATIVE` | 4.24, 5.24 |
| 25 | `incident_evidence` | `database_26.sql` | Empty (0 rows) | Unverifiable from dump; inferred scene photos, 911 audio, investigation files | MODIFY | `incident_evidence` | `INFERRED — NOT AUTHORITATIVE` | 4.25, 5.25 |
| 26 | `incidents` | `database_27.sql` | Empty (0 rows) | Unverifiable from dump; inferred emergency calls & fire dispatches | SPLIT | `incidents`, `incident_dispatches`, `incident_vehicles`, `incident_crews`, `incident_timeline`, `incident_casualties`, `incident_reports` | `INFERRED — NOT AUTHORITATIVE` | 4.26, 5.26 |
| 27 | `inspection_checklists` | `database_28.sql` | Empty (0 rows) | Unverifiable from dump; inferred NBC inspection templates | KEEP | `inspection_checklists` | `INFERRED — NOT AUTHORITATIVE` | 4.27, 5.27 |
| 28 | `inspection_photos` | `database_29.sql` | Empty (0 rows) | Unverifiable from dump; inferred mobile photographic evidence | MODIFY | `inspection_photos` | `INFERRED — NOT AUTHORITATIVE` | 4.28, 5.28 |
| 29 | `inspections` | `database_30.sql` | Empty (0 rows) | Unverifiable from dump; inferred routine & statutory audits | SPLIT | `inspections`, `inspection_assignments`, `inspection_findings` | `INFERRED — NOT AUTHORITATIVE` | 4.29, 5.29 |
| 30 | `integrations` | `database_31.sql` | Empty (0 rows) | Unverifiable from dump; inferred external webhooks & connectors | MODIFY | `integrations` | `INFERRED — NOT AUTHORITATIVE` | 4.30, 5.30 |
| 31 | `inventory_items` | `database_32.sql` | Empty (0 rows) | Unverifiable from dump; inferred firefighting gear & PPE catalog | MODIFY | `inventory_items`, `inventory_categories` | `INFERRED — NOT AUTHORITATIVE` | 4.31, 5.31 |
| 32 | `inventory_transactions` | `database_33.sql` | Empty (0 rows) | Unverifiable from dump; inferred stock receipts & issue logs | MODIFY | `inventory_transactions`, `inventory_stock` | `INFERRED — NOT AUTHORITATIVE` | 4.32, 5.32 |
| 33 | `mock_drills` | `database_34.sql` | Empty (0 rows) | Unverifiable from dump; inferred community & school drill records | MODIFY | `mock_drills`, `mock_drill_evaluations` | `INFERRED — NOT AUTHORITATIVE` | 4.33, 5.33 |
| 34 | `noc_application_categories` | `database_35.sql` | Populated (5 rows) | Verified 5 rows: Commercial, Residential, Industrial, Institutional, Hazardous | MODIFY | `noc_application_categories` | `CONFIRMED (DATA VERIFIED)` | 4.34, 5.34 |
| 35 | `noc_application_documents` | `database_36.sql` | Empty (0 rows) | Unverifiable from dump; inferred architectural drawings & certificates | MODIFY | `noc_application_documents` | `INFERRED — NOT AUTHORITATIVE` | 4.35, 5.35 |
| 36 | `noc_applications` | `database_37.sql` | Empty (0 rows) | Unverifiable from dump; inferred statutory NOC lifecycle | SPLIT | `noc_applications`, `noc_application_status_history`, `noc_certificates`, `noc_renewals` | `INFERRED — NOT AUTHORITATIVE` | 4.36, 5.36 |
| 37 | `notifications` | `database_38.sql` | Empty (0 rows) | Unverifiable from dump; inferred SMS, push, email dispatch logs | MODIFY | `notifications`, `notification_templates` | `INFERRED — NOT AUTHORITATIVE` | 4.37, 5.37 |
| 38 | `org_api_credentials` | `database_39.sql` | Empty (0 rows) | Unverifiable from dump; inferred tenant third-party API secrets | MODIFY | `org_api_credentials` | `INFERRED — NOT AUTHORITATIVE` | 4.38, 5.38 |
| 39 | `org_settings` | `database_40.sql` | Populated (9 rows) | Verified 9 key-value config rows across test orgs | MODIFY | `org_settings` | `CONFIRMED (DATA VERIFIED)` | 4.39, 5.39 |
| 40 | `org_sso_providers` | `database_41.sql` | Populated (4 rows) | Verified 4 rows: Google Workspace SSO configurations | MODIFY | `org_sso_providers` | `CONFIRMED (DATA VERIFIED)` | 4.40, 5.40 |
| 41 | `organizations` | `database_42.sql` | Populated (6 rows) | Verified 6 rows: PMC, TMC, PCMC, NMMC test tenants | MODIFY | `organizations` | `CONFIRMED (DATA VERIFIED)` | 4.41, 5.41 |
| 42 | `payment_transactions` | `database_43.sql` | Empty (0 rows) | Unverifiable from dump; inferred gateway callback payloads | MODIFY | `payments`, `payment_transactions` | `INFERRED — NOT AUTHORITATIVE` | 4.42, 5.42 |
| 43 | `permissions` | `database_44.sql` | Populated (10 rows) | Verified 10 rows: all hardcoded to `org_id = 1` | MODIFY | `permissions` | `CONFIRMED (DATA VERIFIED)` | 4.43, 5.43 |
| 44 | `role_permissions` | `database_45.sql` | Empty (0 rows) | Unverifiable from dump; RBAC completely empty in dump | KEEP | `role_permissions` | `INFERRED — NOT AUTHORITATIVE` | 4.44, 5.44 |
| 45 | `roles` | `database_46.sql` | Populated (32 rows) | Verified 32 rows: duplicate roles duplicated across 6 orgs | MODIFY | `roles` | `CONFIRMED (DATA VERIFIED)` | 4.45, 5.45 |
| 46 | `security_policies` | `database_47.sql` | Populated (4 rows) | Verified 4 rows: RBAC, Session, MFA, Zero Trust rules | MODIFY | `security_policies` | `CONFIRMED (DATA VERIFIED)` | 4.46, 5.46 |
| 47 | `sso_user_identities` | `database_48.sql` | Empty (0 rows) | Unverifiable from dump; inferred external identity claims | REPLACE | `sso_identities` | `INFERRED — NOT AUTHORITATIVE` | 4.47, 5.47 |
| 48 | `training_courses` | `database_49.sql` | Empty (0 rows) | Unverifiable from dump; inferred firefighter academy curriculum | KEEP | `training_courses` | `INFERRED — NOT AUTHORITATIVE` | 4.48, 5.48 |
| 49 | `training_enrollments` | `database_50.sql` | Empty (0 rows) | Unverifiable from dump; inferred trainee exam scores & certificates | MODIFY | `training_enrollments` | `INFERRED — NOT AUTHORITATIVE` | 4.49, 5.49 |
| 50 | `user_roles` | `database_51.sql` | Populated (6 rows) | Verified 6 user-to-role assignment rows | MODIFY | `user_roles` | `CONFIRMED (DATA VERIFIED)` | 4.50, 5.50 |
| 51 | `user_sessions` | `database_52.sql` | Empty (0 rows) | Unverifiable from dump; inferred JWT refresh tokens & sessions | MODIFY | `user_sessions` | `INFERRED — NOT AUTHORITATIVE` | 4.51, 5.51 |
| 52 | `users` | `database_53.sql` | Populated (6 rows) | Verified 6 rows; unsalted SHA-256 hashes, plaintext tokens leaked in `metadata_json`, all `is_super_admin = 1` | MODIFY | `users` | `CONFIRMED (DATA VERIFIED)` | 4.52, 5.52 |
| 53 | `vehicle_maintenance` | `database_54.sql` | Empty (0 rows) | Unverifiable from dump; inferred fleet repair & service logs | MODIFY | `vehicle_maintenance` | `INFERRED — NOT AUTHORITATIVE` | 4.53, 5.53 |
| 54 | `vehicle_trip_logs` | `database_55.sql` | Empty (0 rows) | Unverifiable from dump; inferred apparatus emergency trips & odometers | MODIFY | `vehicle_trip_logs` | `INFERRED — NOT AUTHORITATIVE` | 4.54, 5.54 |
| 55 | `violations` | `database_56.sql` | Empty (0 rows) | Unverifiable from dump; inferred statutory non-compliance notices | MODIFY | `violations`, `violation_actions`, `corrective_actions` | `INFERRED — NOT AUTHORITATIVE` | 4.55, 5.55 |
| 56 | `water_sources` | `database_57.sql` | Empty (0 rows) | Unverifiable from dump; inferred lakes, suction tanks, reservoirs | MODIFY | `water_sources` | `INFERRED — NOT AUTHORITATIVE` | 4.56, 5.56 |
| 57 | `workflow_definitions` | `database_58.sql` | Empty (0 rows) | Unverifiable from dump; inferred multi-stage approval processes | MODIFY | `workflow_definitions` | `INFERRED — NOT AUTHORITATIVE` | 4.57, 5.57 |
| 58 | `workflow_runs` | `database_59.sql` | Empty (0 rows) | Unverifiable from dump; inferred runtime workflow execution instances | MODIFY | `workflow_runs` | `INFERRED — NOT AUTHORITATIVE` | 4.58, 5.58 |
| 59 | `workflow_steps` | `database_60.sql` | Empty (0 rows) | Unverifiable from dump; inferred transition rules & SLA hours | MODIFY | `workflow_steps` | `INFERRED — NOT AUTHORITATIVE` | 4.59, 5.59 |
| 60 | `zones` | `database_61.sql` | Empty (0 rows) | Unverifiable from dump; inferred geographic operational boundaries | MODIFY | `zones` | `INFERRED — NOT AUTHORITATIVE` | 4.60, 5.60 |

---

## Forensic Audit Summary of 60 Legacy Tables

- **Total Legacy Tables Extracted**: 60
- **Populated Tables (Data Verified)**: 12 (20.0%)
- **Empty Tables (Not Verifiable from Dumps / Schema Inferred)**: 48 (80.0%)
- **Architectural Action Breakdown**:
  - `KEEP`: 4 tables (`ai_prompts`, `dashboard_metrics`, `inspection_checklists`, `role_permissions`, `training_courses`) *(Note: Table 9 summary says 4, but list shows 5)*
  - `MODIFY`: 48 tables (PK upgrade to UUID/BIGSERIAL, RLS tenant FK, PostGIS geometry types, check constraints, JSONB conversion)
  - `SPLIT`: 5 tables (`buildings`, `fire_vehicles`, `incidents`, `inspections`, `noc_applications`)
  - `REPLACE`: 2 tables (`audit_logs` -> trigger CDC; `sso_user_identities` -> `sso_identities`)
  - `MERGE`: 0 tables
  - `REMOVE`: 0 tables
