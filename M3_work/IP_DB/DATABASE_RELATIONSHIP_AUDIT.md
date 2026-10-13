# FireSphere.ai — Relational & Referential Integrity Audit

**SOURCE DOCUMENT**: `FireSphere_Database_Documentation_v2.xml` (Sections 4, 6, 7, 8)  
**STATUS**: `FORENSIC AUDIT COMPLETED`  
**SCOPE**: Exhaustive mapping of all explicitly stated foreign keys, parent-child cardinalities, deletion cascades, circular references, and referential hazards.

---

## 1. Explicitly Stated Foreign Key Relationships

| Source Table | Source Column | Target Table | Target Column | Cardinality | ON DELETE | ON UPDATE | Tenant Implications | Source Section | Status |
|---|---|---|---|---|---|---|---|---|---|
| `ai_agents` | `org_id` | `organizations` | `id` | N:1 | RESTRICT | Not Stated | Direct tenant boundary | 4.3, 7.9 | VERIFIED |
| `ai_agents` | `model_id` | `ai_models` | `id` | N:1 | RESTRICT | Not Stated | Model can be global (`org_id IS NULL`) | 4.3, 7.9 | VERIFIED |
| `ai_models` | `org_id` | `organizations` | `id` | N:1 | SET NULL | Not Stated | Nullable for foundation models | 4.5, 7.9 | VERIFIED |
| `building_owners` | `org_id` | `organizations` | `id` | N:1 | RESTRICT | Not Stated | Enforces tenant scope | 6.1, 7.3 | VERIFIED |
| `building_owners` | `building_id` | `buildings` | `id` | N:1 | CASCADE | Not Stated | Deleting building removes owner records | 6.1, 7.3 | VERIFIED |
| `building_contacts` | `org_id` | `organizations` | `id` | N:1 | RESTRICT | Not Stated | Enforces tenant scope | 6.2, 7.3 | VERIFIED |
| `building_contacts` | `building_id` | `buildings` | `id` | N:1 | CASCADE | Not Stated | Deleting building removes contact records | 6.2, 7.3 | VERIFIED |
| `building_documents` | `org_id` | `organizations` | `id` | N:1 | RESTRICT | Not Stated | Enforces tenant scope | 6.3, 7.3 | VERIFIED |
| `building_documents` | `building_id` | `buildings` | `id` | N:1 | CASCADE | Not Stated | Deleting building cascades to document links | 6.3, 7.3 | VERIFIED |
| `building_documents` | `document_id` | `documents` | `id` | N:1 | RESTRICT | Not Stated | Prevents deleting underlying storage doc | 6.3, 7.3 | VERIFIED |
| `building_documents` | `verified_by` | `users` | `id` | N:1 | SET NULL | Not Stated | User audit reference | 6.3, 7.3 | VERIFIED |
| `building_risk_profiles`| `org_id` | `organizations` | `id` | N:1 | RESTRICT | Not Stated | Enforces tenant scope | 6.4, 7.3 | VERIFIED |
| `building_risk_profiles`| `building_id` | `buildings` | `id` | 1:1 | CASCADE | Not Stated | Strict 1:1; UNIQUE on `building_id` | 6.4, 7.3 | VERIFIED |
| `building_fire_equipment`| `org_id` | `organizations` | `id` | N:1 | RESTRICT | Not Stated | Enforces tenant scope | 6.5, 7.3 | VERIFIED |
| `building_fire_equipment`| `building_id` | `buildings` | `id` | N:1 | CASCADE | Not Stated | Equipment tracking per building | 6.5, 7.3 | VERIFIED |
| `noc_application_status_history`| `application_id` | `noc_applications`| `id` | N:1 | CASCADE | Not Stated | Append-only status transition log | 6.6, 7.4 | VERIFIED |
| `noc_application_status_history`| `changed_by_user_id`| `users` | `id` | N:1 | SET NULL | Not Stated | User audit reference | 6.6, 7.4 | VERIFIED |
| `noc_certificates` | `org_id` | `organizations` | `id` | N:1 | RESTRICT | Not Stated | Enforces tenant scope | 6.7, 7.4 | VERIFIED |
| `noc_certificates` | `application_id` | `noc_applications`| `id` | 1:1 | RESTRICT | Not Stated | Strict 1:1; UNIQUE on `application_id` | 6.7, 7.4 | VERIFIED |
| `noc_certificates` | `issuing_authority_user_id`| `users` | `id` | N:1 | RESTRICT | Not Stated | CFO / authority signoff | 6.7, 7.4 | VERIFIED |
| `noc_certificates` | `pdf_document_id` | `documents` | `id` | N:1 | RESTRICT | Not Stated | Links to S3 signed certificate PDF | 6.7, 7.4 | VERIFIED |
| `noc_renewals` | `org_id` | `organizations` | `id` | N:1 | RESTRICT | Not Stated | Enforces tenant scope | 6.8, 7.4 | VERIFIED |
| `noc_renewals` | `previous_certificate_id`| `noc_certificates`| `id` | N:1 | RESTRICT | Not Stated | Chains certificates over renewal cycles | 6.8, 7.4 | VERIFIED |
| `noc_renewals` | `renewal_application_id`| `noc_applications`| `id` | N:1 | RESTRICT | Not Stated | Links to renewal application | 6.8, 7.4 | VERIFIED |
| `inspection_assignments`| `inspection_id` | `inspections` | `id` | N:1 | CASCADE | Not Stated | Multi-officer inspection team | 6.9, 7.5 | VERIFIED |
| `inspection_assignments`| `inspector_user_id` | `users` | `id` | N:1 | RESTRICT | Not Stated | Assigned inspecting officer | 6.9, 7.5 | VERIFIED |
| `inspection_assignments`| `assigned_by_user_id`| `users` | `id` | N:1 | SET NULL | Not Stated | Supervisor who dispatched team | 6.9, 7.5 | VERIFIED |
| `inspection_checklist_items`| `checklist_id` | `inspection_checklists`| `id` | N:1 | CASCADE | Not Stated | NBC template items | 6.10, 7.5 | VERIFIED |
| `inspection_findings` | `inspection_id` | `inspections` | `id` | N:1 | CASCADE | Not Stated | Inspection findings | 6.11, 7.5 | VERIFIED |
| `inspection_findings` | `checklist_item_id` | `inspection_checklist_items`| `id` | N:1 | RESTRICT | Not Stated | Links to question parameter | 6.11, 7.5 | VERIFIED |
| `inspection_findings` | `recorded_by` | `users` | `id` | N:1 | SET NULL | Not Stated | Officer who recorded finding | 6.11, 7.5 | VERIFIED |
| `violation_actions` | `violation_id` | `violations` | `id` | N:1 | CASCADE | Not Stated | Legal notice / penalty order | 6.12, 7.5 | VERIFIED |
| `violation_actions` | `authorized_by_user_id`| `users` | `id` | N:1 | RESTRICT | Not Stated | Authorizing officer | 6.12, 7.5 | VERIFIED |
| `corrective_actions` | `violation_id` | `violations` | `id` | N:1 | CASCADE | Not Stated | Remedial repair record | 6.13, 7.5 | VERIFIED |
| `corrective_actions` | `evidence_document_id`| `documents` | `id` | N:1 | RESTRICT | Not Stated | Uploaded evidence photos/bills | 6.13, 7.5 | VERIFIED |
| `corrective_actions` | `re_inspection_id` | `inspections` | `id` | N:1 | SET NULL | Not Stated | Verification inspection | 6.13, 7.5 | VERIFIED |
| `corrective_actions` | `verified_by` | `users` | `id` | N:1 | SET NULL | Not Stated | Verifying officer | 6.13, 7.5 | VERIFIED |
| `incident_dispatches`| `org_id` | `organizations` | `id` | N:1 | RESTRICT | Not Stated | Enforces tenant scope | 6.14, 7.6 | VERIFIED |
| `incident_dispatches`| `incident_id` | `incidents` | `id` | N:1 | CASCADE | Not Stated | Alarm turnout instance | 6.14, 7.6 | VERIFIED |
| `incident_dispatches`| `station_id` | `fire_stations` | `id` | N:1 | RESTRICT | Not Stated | Responding station base | 6.14, 7.6 | VERIFIED |
| `incident_dispatches`| `dispatched_by_user_id`| `users` | `id` | N:1 | RESTRICT | Not Stated | CAD dispatch operator | 6.14, 7.6 | VERIFIED |
| `incident_vehicles` | `dispatch_id` | `incident_dispatches`| `id` | N:1 | CASCADE | Not Stated | Deployed apparatus | 6.15, 7.6 | VERIFIED |
| `incident_vehicles` | `vehicle_id` | `fire_vehicles` | `id` | N:1 | RESTRICT | Not Stated | Apparatus master record | 6.15, 7.6 | VERIFIED |
| `incident_crews` | `incident_vehicle_id`| `incident_vehicles`| `id` | N:1 | CASCADE | Not Stated | Firefighter crew assignment | 6.16, 7.6 | VERIFIED |
| `incident_crews` | `user_id` | `users` | `id` | N:1 | RESTRICT | Not Stated | Firefighter user identity | 6.16, 7.6 | VERIFIED |
| `incident_timeline` | `incident_id` | `incidents` | `id` | N:1 | CASCADE | Not Stated | Append-only event sequence | 6.17, 7.6 | VERIFIED |
| `incident_timeline` | `logged_by_user_id` | `users` | `id` | N:1 | SET NULL | Not Stated | User or system actor | 6.17, 7.6 | VERIFIED |
| `incident_casualties`| `incident_id` | `incidents` | `id` | N:1 | CASCADE | Not Stated | EMS triage records | 6.18, 7.6 | VERIFIED |
| `incident_reports` | `incident_id` | `incidents` | `id` | 1:1 | RESTRICT | Not Stated | Strict 1:1; UNIQUE on `incident_id` | 6.19, 7.6 | VERIFIED |
| `incident_reports` | `investigating_officer_id`| `users` | `id` | N:1 | RESTRICT | Not Stated | Fire investigator | 6.19, 7.6 | VERIFIED |
| `incident_reports` | `approved_by_cfo_id` | `users` | `id` | N:1 | RESTRICT | Not Stated | Chief Fire Officer signoff | 6.19, 7.6 | VERIFIED |
| `incident_reports` | `document_id` | `documents` | `id` | N:1 | RESTRICT | Not Stated | Final signed PDF investigation | 6.19, 7.6 | VERIFIED |
| `vehicle_location_history`| `vehicle_id` | `fire_vehicles` | `id` | N:1 | CASCADE | Not Stated | High-frequency GPS telemetry | 6.20, 7.7 | VERIFIED |
| `vehicle_location_history`| `incident_id` | `incidents` | `id` | N:1 | SET NULL | Not Stated | Optional link to active incident | 6.20, 7.7 | VERIFIED |
| `vehicle_crew` | `vehicle_id` | `fire_vehicles` | `id` | N:1 | CASCADE | Not Stated | Shift duty roster | 6.21, 7.7 | VERIFIED |
| `vehicle_crew` | `user_id` | `users` | `id` | N:1 | RESTRICT | Not Stated | Crew member | 6.21, 7.7 | VERIFIED |
| `vehicle_crew` | `assigned_by_user_id`| `users` | `id` | N:1 | SET NULL | Not Stated | Duty officer | 6.21, 7.7 | VERIFIED |
| `vehicle_fuel_logs` | `vehicle_id` | `fire_vehicles` | `id` | N:1 | RESTRICT | Not Stated | Fuel expense tracking | 6.22, 7.7 | VERIFIED |
| `vehicle_fuel_logs` | `logged_by_user_id` | `users` | `id` | N:1 | SET NULL | Not Stated | Station logger | 6.22, 7.7 | VERIFIED |
| `vehicle_fitness` | `vehicle_id` | `fire_vehicles` | `id` | N:1 | RESTRICT | Not Stated | RTO fitness certificates | 6.23, 7.7 | VERIFIED |
| `vehicle_fitness` | `certificate_doc_id` | `documents` | `id` | N:1 | RESTRICT | Not Stated | Scan of fitness certificate | 6.23, 7.7 | VERIFIED |
| `warehouses` | `org_id` | `organizations` | `id` | N:1 | RESTRICT | Not Stated | Enforces tenant scope | 6.24, 7.8 | VERIFIED |
| `warehouses` | `station_id` | `fire_stations` | `id` | N:1 | SET NULL | Not Stated | Optional station affiliation | 6.24, 7.8 | VERIFIED |
| `warehouses` | `supervisor_user_id` | `users` | `id` | N:1 | SET NULL | Not Stated | Depot manager | 6.24, 7.8 | VERIFIED |
| `inventory_stock` | `warehouse_id` | `warehouses` | `id` | N:1 | RESTRICT | Not Stated | Warehouse stock location | 6.25, 7.8 | VERIFIED |
| `inventory_stock` | `item_id` | `inventory_items`| `id` | N:1 | RESTRICT | Not Stated | Stocked catalog item | 6.25, 7.8 | VERIFIED |
| `fire_stations` | `division_id` | `divisions` | `id` | N:1 | RESTRICT | Not Stated | Legacy table relationship | 4.21, 7.3 | VERIFIED |
| `fire_stations` | `zone_id` | `zones` | `id` | N:1 | RESTRICT | Not Stated | Legacy table relationship | 4.21, 7.3 | VERIFIED |
| `user_roles` | `user_id` | `users` | `id` | N:1 | CASCADE | Not Stated | IAM junction | 4.50, 7.2 | VERIFIED |
| `user_roles` | `role_id` | `roles` | `id` | N:1 | RESTRICT | Not Stated | IAM junction | 4.50, 7.2 | VERIFIED |
| `role_permissions` | `role_id` | `roles` | `id` | N:1 | CASCADE | Not Stated | IAM junction | 4.44, 7.2 | VERIFIED |
| `role_permissions` | `permission_id` | `permissions` | `id` | N:1 | RESTRICT | Not Stated | IAM junction | 4.44, 7.2 | VERIFIED |

---

## 2. Forensic Relational Integrity Hazards & Flags

### HAZARD-01: Circular Dependency Between `departments` and `users`
- **Location**: Section 4.18 and Section 7.2
- **Description**: `departments.head_user_id` references `users(id)`, while `users.department_id` references `departments(id)`.
- **Impact**: Creating tables with non-deferrable immediate foreign keys causes circular dependency deadlock during initialization and row insertion.
- **Document's Proposed Resolution**: Section 4.18 explicitly states: *"Defer head_user_id constraint to avoid circular dependency with users."*
- **Status**: `FLAGGED — REQUIRES DEFERRABLE CONSTRAINT SPECIFICATION`.

### HAZARD-02: References to Undefined Tables (`payments`, `inventory_categories`, etc.)
- **Location**: Sections 7.4, 7.8, and Appendix A
- **Description**:
  - Section 7.4 states: `noc_applications` branches into `payments (1:N)`. But `payments` has NO column data dictionary in Section 6.
  - Section 7.8 states: `inventory_items links (N:1) to inventory_categories`. But `inventory_categories` has NO column data dictionary in Section 6.
  - Section 4.32 notes: `inventory_transactions` links to `purchase_orders`. But `purchase_orders` has NO column data dictionary in Section 6.
- **Impact**: Attempting to generate DDL with foreign keys to non-existent or undefined tables causes fatal syntax/catalog errors.
- **Status**: `CONFLICT — REFERENCED TABLE LACKS SCHEMA DEFINITION`.

### HAZARD-03: Inconsistent Key Types Between Architectural Specification and DDL
- **Location**: Section 2.2 vs Section 6
- **Description**: Section 2.2 explicitly declares: *"Every tenant-aware table includes an organization_id UUID foreign key referencing organizations(id)."* However, every single table definition in Section 6 uses:
  `org_id BIGINT REFERENCES organizations(id)` and assigns UUIDs to a separate business key column named `uuid UUID`.
- **Impact**: Backend ORM mapping confusion; mismatched data types between connection pool context (`::BIGINT` vs `::UUID`).
- **Status**: `CONFLICT — TYPE MISMATCH BETWEEN ARCHITECTURE AND DDL`.
