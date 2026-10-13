# FireSphere.ai — New Production Table Inventory & Contract Audit (25 Tables)

**SOURCE DOCUMENT**: `FireSphere_Database_Documentation_v2.xml` (Section 6: New Production Tables Data Dictionary)  
**STATUS**: `FORENSIC AUDIT COMPLETED`  
**SCOPE**: 25 newly engineered tables (Sections 6.1 to 6.25) designed to normalize legacy entities and support PostGIS spatial computing, RLS multi-tenancy, and audit logging.

---

## Detailed Inventory of All 25 New Production Tables

### 1. `building_owners`
- **Source Section**: 6.1
- **Stated Module**: Buildings & GIS
- **Purpose**: Legal ownership records, co-owners, corporate entities, and ownership share percentages for regulated structures.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `uuid`: `UUID` (NOT NULL, DEFAULT `uuid_generate_v4()`, UNIQUE)
  - `org_id`: `BIGINT` (NOT NULL, FK -> `organizations(id)` ON DELETE RESTRICT)
  - `building_id`: `BIGINT` (NOT NULL, FK -> `buildings(id)` ON DELETE CASCADE)
  - `owner_type`: `VARCHAR(30)` (NOT NULL, DEFAULT `'INDIVIDUAL'`)
  - `owner_name`: `VARCHAR(255)` (NOT NULL)
  - `share_percentage`: `NUMERIC(5,2)` (NOT NULL, DEFAULT `100.00`)
  - `is_primary`: `BOOLEAN` (NOT NULL, DEFAULT `TRUE`)
  - `created_at`: `TIMESTAMPTZ` (NOT NULL, DEFAULT `NOW()`)
  - `updated_at`: `TIMESTAMPTZ` (NOT NULL, DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `org_id` -> `organizations(id)`, `building_id` -> `buildings(id)`
- **Unique Constraints**: `uuid` UNIQUE
- **Check Constraints**: `CHECK (owner_type IN ('INDIVIDUAL', 'CORPORATE', 'PARTNERSHIP', 'GOVERNMENT', 'SOCIETY'))`, `CHECK (share_percentage > 0.00 AND share_percentage <= 100.00)`
- **Indexes**: Explicit B-Tree on `(org_id, building_id)` inferred from FKs; RLS index on `org_id`
- **Tenant Key**: `org_id` (`BIGINT`) *(Note: Section 2.2 states tenant key is `organization_id UUID` — Conflict)*
- **Spatial Columns**: None
- **Audit Columns**: `created_at`, `updated_at` (managed via trigger)
- **Relationships**: `buildings` (1) -> (N) `building_owners`
- **Approval Status**: `REQUIRES APPROVAL` (Candidate contract)

---

### 2. `building_contacts`
- **Source Section**: 6.2
- **Stated Module**: Buildings & GIS
- **Purpose**: 24/7 operational emergency contacts, facility managers, security supervisors, and fire safety officers per building.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `uuid`: `UUID` (NOT NULL, UNIQUE)
  - `org_id`: `BIGINT` (NOT NULL, FK -> `organizations(id)`)
  - `building_id`: `BIGINT` (NOT NULL, FK -> `buildings(id)`)
  - `contact_name`: `VARCHAR(150)` (NOT NULL)
  - `role_title`: `VARCHAR(100)`
  - `phone_primary`: `VARCHAR(20)` (NOT NULL)
  - `phone_secondary`: `VARCHAR(20)`
  - `email`: `VARCHAR(150)`
  - `is_emergency_contact`: `BOOLEAN` (DEFAULT `TRUE`)
  - `availability_hours`: `VARCHAR(50)`
  - `created_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
  - `updated_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `org_id` -> `organizations(id)`, `building_id` -> `buildings(id)`
- **Unique Constraints**: `uuid` UNIQUE
- **Check Constraints**: `MISSING — REQUIRED CONFIRMATION`
- **Indexes**: `idx_bldg_contacts_bldg` (`building_id`), `idx_bldg_contacts_emergency` (`building_id`, `is_emergency_contact`)
- **Tenant Key**: `org_id` (`BIGINT`)
- **Spatial Columns**: None
- **Audit Columns**: `created_at`, `updated_at`
- **Relationships**: `buildings` (1) -> (N) `building_contacts`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 3. `building_documents`
- **Source Section**: 6.3
- **Stated Module**: Buildings & GIS / Documents
- **Purpose**: Links architectural blueprints, structural elevation CAD plans, fire egress drawings, and sanctioned layouts to buildings.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `uuid`: `UUID` (NOT NULL, UNIQUE)
  - `org_id`: `BIGINT` (NOT NULL, FK -> `organizations(id)`)
  - `building_id`: `BIGINT` (NOT NULL, FK -> `buildings(id)`)
  - `document_id`: `BIGINT` (NOT NULL, FK -> `documents(id)`)
  - `doc_category`: `VARCHAR(50)` (NOT NULL)
  - `floor_number`: `INT` (NULL)
  - `approval_authority`: `VARCHAR(150)`
  - `approval_reference`: `VARCHAR(100)`
  - `is_verified`: `BOOLEAN` (DEFAULT `FALSE`)
  - `verified_by`: `BIGINT` (FK -> `users(id)`)
  - `created_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
  - `updated_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `org_id` -> `organizations(id)`, `building_id` -> `buildings(id)`, `document_id` -> `documents(id)`, `verified_by` -> `users(id)`
- **Unique Constraints**: `uuid` UNIQUE
- **Check Constraints**: `MISSING — REQUIRED CONFIRMATION`
- **Indexes**: B-Tree on `(building_id, doc_category)`
- **Tenant Key**: `org_id` (`BIGINT`)
- **Spatial Columns**: None
- **Audit Columns**: `created_at`, `updated_at`, `verified_by`
- **Relationships**: `buildings` (1) -> (N) `building_documents`, `documents` (1) -> (N) `building_documents`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 4. `building_risk_profiles`
- **Source Section**: 6.4
- **Stated Module**: Buildings & GIS / AI & Risk Analytics
- **Purpose**: Calculated AI risk scores, hazard classifications, occupant vulnerability ratings, and historical fire vulnerability indexes.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `uuid`: `UUID` (NOT NULL, UNIQUE)
  - `org_id`: `BIGINT` (NOT NULL, FK -> `organizations(id)`)
  - `building_id`: `BIGINT` (NOT NULL, FK -> `buildings(id)`, UNIQUE)
  - `risk_grade`: `VARCHAR(10)` (NOT NULL)
  - `overall_risk_score`: `NUMERIC(5,2)` (NOT NULL)
  - `fire_load_density_mj_sqm`: `NUMERIC(10,2)`
  - `structural_combustibility_index`: `NUMERIC(3,2)`
  - `occupancy_hazard_class`: `VARCHAR(50)`
  - `last_assessed_at`: `TIMESTAMPTZ`
  - `assessment_model_version`: `VARCHAR(50)`
  - `created_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
  - `updated_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `org_id` -> `organizations(id)`, `building_id` -> `buildings(id)`
- **Unique Constraints**: `uuid` UNIQUE, `building_id` UNIQUE (enforces 1:1 relationship)
- **Check Constraints**: `CHECK (risk_grade IN ('CRITICAL', 'HIGH', 'MODERATE', 'LOW'))`, `CHECK (overall_risk_score >= 0 AND overall_risk_score <= 100)`
- **Indexes**: B-Tree on `(org_id, risk_grade)`, B-Tree on `(overall_risk_score DESC)`
- **Tenant Key**: `org_id` (`BIGINT`)
- **Spatial Columns**: None
- **Audit Columns**: `created_at`, `updated_at`, `last_assessed_at`
- **Relationships**: `buildings` (1) <-> (1) `building_risk_profiles`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 5. `building_fire_equipment`
- **Source Section**: 6.5
- **Stated Module**: Buildings & GIS / Compliance
- **Purpose**: Tracks fixed fire protection installations per building (wet risers, sprinklers, fire pumps, alarms, gas suppression).
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `uuid`: `UUID` (NOT NULL, UNIQUE)
  - `org_id`: `BIGINT` (NOT NULL, FK -> `organizations(id)`)
  - `building_id`: `BIGINT` (NOT NULL, FK -> `buildings(id)`)
  - `equipment_type`: `VARCHAR(50)` (NOT NULL)
  - `installed_quantity`: `INT` (NOT NULL)
  - `operational_quantity`: `INT` (NOT NULL)
  - `floor_location`: `VARCHAR(100)`
  - `last_tested_date`: `DATE`
  - `test_status`: `VARCHAR(30)`
  - `expiry_date`: `DATE`
  - `created_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
  - `updated_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `org_id` -> `organizations(id)`, `building_id` -> `buildings(id)`
- **Unique Constraints**: `uuid` UNIQUE
- **Check Constraints**: `CHECK (operational_quantity <= installed_quantity AND operational_quantity >= 0)`
- **Indexes**: B-Tree on `(building_id, equipment_type)`
- **Tenant Key**: `org_id` (`BIGINT`)
- **Spatial Columns**: None
- **Audit Columns**: `created_at`, `updated_at`
- **Relationships**: `buildings` (1) -> (N) `building_fire_equipment`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 6. `noc_application_status_history`
- **Source Section**: 6.6
- **Stated Module**: Fire NOC Workflow
- **Purpose**: Immutable, append-only historical audit trail of all state transitions throughout the Fire NOC lifecycle.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `application_id`: `BIGINT` (NOT NULL, FK -> `noc_applications(id)` ON DELETE CASCADE)
  - `from_status`: `VARCHAR(50)`
  - `to_status`: `VARCHAR(50)` (NOT NULL)
  - `changed_by_user_id`: `BIGINT` (FK -> `users(id)`)
  - `remarks`: `TEXT`
  - `transition_metadata`: `JSONB`
  - `transition_timestamp`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `application_id` -> `noc_applications(id)`, `changed_by_user_id` -> `users(id)`
- **Unique Constraints**: None (append-only ledger)
- **Check Constraints**: `MISSING — REQUIRED CONFIRMATION`
- **Indexes**: B-Tree on `(application_id, transition_timestamp DESC)`
- **Tenant Key**: Indirect via `application_id` (`noc_applications.org_id`)
- **Spatial Columns**: None
- **Audit Columns**: `transition_timestamp`, `changed_by_user_id` (trigger blocks UPDATE/DELETE)
- **Relationships**: `noc_applications` (1) -> (N) `noc_application_status_history`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 7. `noc_certificates`
- **Source Section**: 6.7
- **Stated Module**: Fire NOC Workflow
- **Purpose**: Issued digital Fire Safety Certificates with cryptographic integrity hashes, verification QR tokens, and validity spans.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `uuid`: `UUID` (NOT NULL, UNIQUE)
  - `org_id`: `BIGINT` (NOT NULL, FK -> `organizations(id)`)
  - `application_id`: `BIGINT` (NOT NULL, FK -> `noc_applications(id)`, UNIQUE)
  - `certificate_number`: `VARCHAR(100)` (NOT NULL, UNIQUE)
  - `issue_date`: `DATE` (NOT NULL)
  - `valid_until`: `DATE` (NOT NULL)
  - `issuing_authority_user_id`: `BIGINT` (FK -> `users(id)`)
  - `digital_signature_hash`: `VARCHAR(255)`
  - `qr_verification_url`: `VARCHAR(500)`
  - `pdf_document_id`: `BIGINT` (FK -> `documents(id)`)
  - `status`: `VARCHAR(30)` (DEFAULT `'ACTIVE'`)
  - `created_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
  - `updated_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `org_id` -> `organizations(id)`, `application_id` -> `noc_applications(id)`, `issuing_authority_user_id` -> `users(id)`, `pdf_document_id` -> `documents(id)`
- **Unique Constraints**: `uuid` UNIQUE, `application_id` UNIQUE, `certificate_number` UNIQUE
- **Check Constraints**: `CHECK (valid_until > issue_date)`, `CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED', 'SUPERSEDED'))`
- **Indexes**: B-Tree on `certificate_number`, B-Tree on `(valid_until, status)`
- **Tenant Key**: `org_id` (`BIGINT`)
- **Spatial Columns**: None
- **Audit Columns**: `created_at`, `updated_at`, `issuing_authority_user_id`
- **Relationships**: `noc_applications` (1) <-> (1) `noc_certificates`, `noc_certificates` (1) -> (N) `noc_renewals`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 8. `noc_renewals`
- **Source Section**: 6.8
- **Stated Module**: Fire NOC Workflow
- **Purpose**: Annual and triennial Fire NOC renewal applications, compliance declarations, and transition links to previous certificates.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `uuid`: `UUID` (NOT NULL, UNIQUE)
  - `org_id`: `BIGINT` (NOT NULL, FK -> `organizations(id)`)
  - `previous_certificate_id`: `BIGINT` (NOT NULL, FK -> `noc_certificates(id)`)
  - `renewal_application_id`: `BIGINT` (NOT NULL, FK -> `noc_applications(id)`)
  - `renewal_cycle_year`: `INT` (NOT NULL)
  - `status`: `VARCHAR(50)`
  - `renewal_fee_paid`: `NUMERIC(12,2)`
  - `inspection_required`: `BOOLEAN` (DEFAULT `TRUE`)
  - `created_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
  - `updated_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `org_id` -> `organizations(id)`, `previous_certificate_id` -> `noc_certificates(id)`, `renewal_application_id` -> `noc_applications(id)`
- **Unique Constraints**: `uuid` UNIQUE
- **Check Constraints**: `MISSING — REQUIRED CONFIRMATION`
- **Indexes**: `idx_noc_renewals_cert` (`previous_certificate_id`), `idx_noc_renewals_status` (`org_id`, `status`)
- **Tenant Key**: `org_id` (`BIGINT`)
- **Spatial Columns**: None
- **Audit Columns**: `created_at`, `updated_at`
- **Relationships**: `noc_certificates` (1) -> (N) `noc_renewals`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 9. `inspection_assignments`
- **Source Section**: 6.9
- **Stated Module**: Inspections
- **Purpose**: Multi-officer inspection teams, junior inspector assignments, and specialist safety assessors per inspection event.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `inspection_id`: `BIGINT` (NOT NULL, FK -> `inspections(id)` ON DELETE CASCADE)
  - `inspector_user_id`: `BIGINT` (NOT NULL, FK -> `users(id)`)
  - `assignment_role`: `VARCHAR(50)` (DEFAULT `'INSPECTOR'`)
  - `assigned_by_user_id`: `BIGINT` (FK -> `users(id)`)
  - `is_lead_officer`: `BOOLEAN` (DEFAULT `FALSE`)
  - `assigned_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `inspection_id` -> `inspections(id)`, `inspector_user_id` -> `users(id)`, `assigned_by_user_id` -> `users(id)`
- **Unique Constraints**: `UNIQUE (inspection_id, inspector_user_id)`
- **Check Constraints**: `MISSING — REQUIRED CONFIRMATION`
- **Indexes**: B-Tree on `(inspection_id)`, B-Tree on `(inspector_user_id)`
- **Tenant Key**: Indirect via `inspections.org_id`
- **Spatial Columns**: None
- **Audit Columns**: `assigned_at`, `assigned_by_user_id`
- **Relationships**: `inspections` (1) -> (N) `inspection_assignments`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 10. `inspection_checklist_items`
- **Source Section**: 6.10
- **Stated Module**: Inspections
- **Purpose**: Standardized NBC compliance questions, statutory requirements, and National Building Code clause references.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `checklist_id`: `BIGINT` (NOT NULL, FK -> `inspection_checklists(id)` ON DELETE CASCADE)
  - `item_code`: `VARCHAR(50)` (NOT NULL)
  - `item_text`: `TEXT` (NOT NULL)
  - `clause_reference`: `VARCHAR(100)`
  - `severity_weight`: `INT` (DEFAULT `1`)
  - `input_type`: `VARCHAR(30)` (DEFAULT `'PASS_FAIL'`)
  - `is_mandatory`: `BOOLEAN` (DEFAULT `TRUE`)
  - `sort_order`: `INT` (DEFAULT `0`)
  - `created_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `checklist_id` -> `inspection_checklists(id)`
- **Unique Constraints**: `MISSING — REQUIRED CONFIRMATION` (Likely `UNIQUE (checklist_id, item_code)`)
- **Check Constraints**: `CHECK (input_type IN ('PASS_FAIL', 'NUMERIC', 'TEXT', 'MULTIPLE_CHOICE', 'PHOTO_REQUIRED'))`
- **Indexes**: B-Tree on `(checklist_id, sort_order)`
- **Tenant Key**: Global / System catalog (or indirect via `inspection_checklists.org_id`)
- **Spatial Columns**: None
- **Audit Columns**: `created_at`
- **Relationships**: `inspection_checklists` (1) -> (N) `inspection_checklist_items`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 11. `inspection_findings`
- **Source Section**: 6.11
- **Stated Module**: Inspections
- **Purpose**: Field officer responses, measured values, pass/fail results, and non-compliance observations for each checklist item.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `inspection_id`: `BIGINT` (NOT NULL, FK -> `inspections(id)` ON DELETE CASCADE)
  - `checklist_item_id`: `BIGINT` (NOT NULL, FK -> `inspection_checklist_items(id)`)
  - `result`: `VARCHAR(30)` (NOT NULL)
  - `measured_value`: `VARCHAR(100)`
  - `remarks`: `TEXT`
  - `is_violation`: `BOOLEAN` (DEFAULT `FALSE`)
  - `photo_evidence_count`: `INT` (DEFAULT `0`)
  - `recorded_by`: `BIGINT` (FK -> `users(id)`)
  - `recorded_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `inspection_id` -> `inspections(id)`, `checklist_item_id` -> `inspection_checklist_items(id)`, `recorded_by` -> `users(id)`
- **Unique Constraints**: `UNIQUE (inspection_id, checklist_item_id)`
- **Check Constraints**: `MISSING — REQUIRED CONFIRMATION`
- **Indexes**: B-Tree on `(inspection_id)`, B-Tree on `(is_violation)`
- **Tenant Key**: Indirect via `inspections.org_id`
- **Spatial Columns**: None
- **Audit Columns**: `recorded_at`, `recorded_by`
- **Relationships**: `inspections` (1) -> (N) `inspection_findings`, `inspection_checklist_items` (1) -> (N) `inspection_findings`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 12. `violation_actions`
- **Source Section**: 6.12
- **Stated Module**: Violations & Legal Enforcement
- **Purpose**: Statutory legal notices, compounding fines, stop-work orders, and building sealing actions issued to defaulters.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `uuid`: `UUID` (NOT NULL, UNIQUE)
  - `violation_id`: `BIGINT` (NOT NULL, FK -> `violations(id)` ON DELETE CASCADE)
  - `action_type`: `VARCHAR(50)` (NOT NULL)
  - `notice_reference_number`: `VARCHAR(100)` (UNIQUE)
  - `penalty_amount`: `NUMERIC(12,2)` (DEFAULT `0.00`)
  - `issued_date`: `DATE` (NOT NULL)
  - `compliance_deadline`: `DATE` (NOT NULL)
  - `status`: `VARCHAR(30)` (DEFAULT `'ISSUED'`)
  - `authorized_by_user_id`: `BIGINT` (FK -> `users(id)`)
  - `created_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
  - `updated_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `violation_id` -> `violations(id)`, `authorized_by_user_id` -> `users(id)`
- **Unique Constraints**: `uuid` UNIQUE, `notice_reference_number` UNIQUE
- **Check Constraints**: `CHECK (compliance_deadline >= issued_date)`, `CHECK (action_type IN ('SHOW_CAUSE_NOTICE', 'STOP_WORK_ORDER', 'SEALING_ORDER', 'MONETARY_FINE', 'PROSECUTION'))`
- **Indexes**: B-Tree on `(violation_id)`, B-Tree on `(compliance_deadline, status)`
- **Tenant Key**: Indirect via `violations.org_id`
- **Spatial Columns**: None
- **Audit Columns**: `created_at`, `updated_at`, `authorized_by_user_id`
- **Relationships**: `violations` (1) -> (N) `violation_actions`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 13. `corrective_actions`
- **Source Section**: 6.13
- **Stated Module**: Violations & Legal Enforcement
- **Purpose**: Remedial repairs, equipment rectification, and compliance evidence submitted by building owners to clear code violations.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `uuid`: `UUID` (NOT NULL, UNIQUE)
  - `violation_id`: `BIGINT` (NOT NULL, FK -> `violations(id)` ON DELETE CASCADE)
  - `submission_date`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
  - `description_of_work`: `TEXT` (NOT NULL)
  - `evidence_document_id`: `BIGINT` (FK -> `documents(id)`)
  - `re_inspection_id`: `BIGINT` (FK -> `inspections(id)` NULL)
  - `verification_status`: `VARCHAR(30)` (DEFAULT `'PENDING_REVIEW'`)
  - `verified_by`: `BIGINT` (FK -> `users(id)` NULL)
  - `verified_at`: `TIMESTAMPTZ` (NULL)
  - `created_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
  - `updated_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `violation_id` -> `violations(id)`, `evidence_document_id` -> `documents(id)`, `re_inspection_id` -> `inspections(id)`, `verified_by` -> `users(id)`
- **Unique Constraints**: `uuid` UNIQUE
- **Check Constraints**: `MISSING — REQUIRED CONFIRMATION`
- **Indexes**: B-Tree on `(violation_id)`, B-Tree on `(verification_status)`
- **Tenant Key**: Indirect via `violations.org_id`
- **Spatial Columns**: None
- **Audit Columns**: `created_at`, `updated_at`, `verified_by`, `verified_at`
- **Relationships**: `violations` (1) -> (N) `corrective_actions`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 14. `incident_dispatches`
- **Source Section**: 6.14
- **Stated Module**: Emergency Dispatch & Incidents
- **Purpose**: CAD alarm turnouts, station alerts, dispatch tier levels (1-Alarm to 5-Alarm), and response timestamps.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `uuid`: `UUID` (NOT NULL, UNIQUE)
  - `org_id`: `BIGINT` (NOT NULL, FK -> `organizations(id)`)
  - `incident_id`: `BIGINT` (NOT NULL, FK -> `incidents(id)` ON DELETE CASCADE)
  - `dispatch_number`: `VARCHAR(100)` (NOT NULL, UNIQUE)
  - `station_id`: `BIGINT` (NOT NULL, FK -> `fire_stations(id)`)
  - `alarm_level`: `INT` (DEFAULT `1`)
  - `dispatched_by_user_id`: `BIGINT` (FK -> `users(id)`)
  - `dispatch_timestamp`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
  - `acknowledged_at`: `TIMESTAMPTZ` (NULL)
  - `turnout_time_seconds`: `INT` (NULL)
  - `status`: `VARCHAR(30)` (DEFAULT `'DISPATCHED'`)
  - `created_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `org_id` -> `organizations(id)`, `incident_id` -> `incidents(id)`, `station_id` -> `fire_stations(id)`, `dispatched_by_user_id` -> `users(id)`
- **Unique Constraints**: `uuid` UNIQUE, `dispatch_number` UNIQUE
- **Check Constraints**: `MISSING — REQUIRED CONFIRMATION`
- **Indexes**: B-Tree on `(incident_id)`, B-Tree on `(station_id, dispatch_timestamp)`
- **Tenant Key**: `org_id` (`BIGINT`)
- **Spatial Columns**: None
- **Audit Columns**: `created_at`, `dispatched_by_user_id`
- **Relationships**: `incidents` (1) -> (N) `incident_dispatches`, `fire_stations` (1) -> (N) `incident_dispatches`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 15. `incident_vehicles`
- **Source Section**: 6.15
- **Stated Module**: Emergency Dispatch / Vehicles
- **Purpose**: Apparatus deployment ledger tracking response milestones (En Route, On Scene, Staging, Returning, In Service) per emergency vehicle.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `dispatch_id`: `BIGINT` (NOT NULL, FK -> `incident_dispatches(id)` ON DELETE CASCADE)
  - `vehicle_id`: `BIGINT` (NOT NULL, FK -> `fire_vehicles(id)`)
  - `assigned_role`: `VARCHAR(50)` (DEFAULT `'ATTACK_ENGINE'`)
  - `departed_station_at`: `TIMESTAMPTZ`
  - `arrived_scene_at`: `TIMESTAMPTZ`
  - `left_scene_at`: `TIMESTAMPTZ`
  - `back_in_service_at`: `TIMESTAMPTZ`
  - `travel_time_seconds`: `INT`
  - `water_discharged_liters`: `NUMERIC(10,2)` (DEFAULT `0.00`)
  - `foam_discharged_liters`: `NUMERIC(8,2)` (DEFAULT `0.00`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `dispatch_id` -> `incident_dispatches(id)`, `vehicle_id` -> `fire_vehicles(id)`
- **Unique Constraints**: `UNIQUE (dispatch_id, vehicle_id)`
- **Check Constraints**: `MISSING — REQUIRED CONFIRMATION`
- **Indexes**: B-Tree on `(dispatch_id)`, B-Tree on `(vehicle_id)`
- **Tenant Key**: Indirect via `incident_dispatches.org_id`
- **Spatial Columns**: None
- **Audit Columns**: Milestones timestamps (`departed_station_at`, `arrived_scene_at`, `left_scene_at`, `back_in_service_at`)
- **Relationships**: `incident_dispatches` (1) -> (N) `incident_vehicles`, `fire_vehicles` (1) -> (N) `incident_vehicles`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 16. `incident_crews`
- **Source Section**: 6.16
- **Stated Module**: Emergency Dispatch / Personnel
- **Purpose**: Accountability system tracking individual firefighters, incident commanders, pump operators, and SCBA users deployed on scene.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `incident_vehicle_id`: `BIGINT` (NOT NULL, FK -> `incident_vehicles(id)` ON DELETE CASCADE)
  - `user_id`: `BIGINT` (NOT NULL, FK -> `users(id)`)
  - `crew_position`: `VARCHAR(50)` (NOT NULL)
  - `scba_entry_time`: `TIMESTAMPTZ` (NULL)
  - `scba_exit_time`: `TIMESTAMPTZ` (NULL)
  - `initial_pressure_bar`: `INT` (NULL)
  - `exit_pressure_bar`: `INT` (NULL)
  - `safety_status`: `VARCHAR(30)` (DEFAULT `'ACTIVE'`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `incident_vehicle_id` -> `incident_vehicles(id)`, `user_id` -> `users(id)`
- **Unique Constraints**: `MISSING — REQUIRED CONFIRMATION` (Likely `UNIQUE (incident_vehicle_id, user_id)`)
- **Check Constraints**: `CHECK (crew_position IN ('INCIDENT_COMMANDER', 'DRIVER_OPERATOR', 'NOZZLEMAN', 'SEARCH_RESCUE', 'HYDRANT_MAN'))`
- **Indexes**: B-Tree on `(incident_vehicle_id)`, B-Tree on `(user_id)`
- **Tenant Key**: Indirect via `incident_vehicles -> incident_dispatches.org_id`
- **Spatial Columns**: None
- **Audit Columns**: `scba_entry_time`, `scba_exit_time`
- **Relationships**: `incident_vehicles` (1) -> (N) `incident_crews`, `users` (1) -> (N) `incident_crews`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 17. `incident_timeline`
- **Source Section**: 6.17
- **Stated Module**: Incidents / Operational Audit
- **Purpose**: Immutable real-time chronological event log for legal post-incident inquiries.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `incident_id`: `BIGINT` (NOT NULL, FK -> `incidents(id)` ON DELETE CASCADE)
  - `event_timestamp`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
  - `event_code`: `VARCHAR(50)` (NOT NULL)
  - `event_description`: `TEXT` (NOT NULL)
  - `source`: `VARCHAR(30)` (DEFAULT `'CAD_SYSTEM'`)
  - `logged_by_user_id`: `BIGINT` (FK -> `users(id)`)
  - `telemetry_coordinates`: `GEOMETRY(Point, 4326)` (NULL)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `incident_id` -> `incidents(id)`, `logged_by_user_id` -> `users(id)`
- **Unique Constraints**: None (chronological stream)
- **Check Constraints**: `MISSING — REQUIRED CONFIRMATION`
- **Indexes**: B-Tree on `(incident_id, event_timestamp)`, GIST on `telemetry_coordinates`
- **Tenant Key**: Indirect via `incidents.org_id`
- **Spatial Columns**: `telemetry_coordinates` (`GEOMETRY(Point, 4326)`)
- **Audit Columns**: `event_timestamp`, `logged_by_user_id` (trigger blocks UPDATE/DELETE)
- **Relationships**: `incidents` (1) -> (N) `incident_timeline`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 18. `incident_casualties`
- **Source Section**: 6.18
- **Stated Module**: Incidents / Emergency Medical Services
- **Purpose**: Records injury, fatality, triage classification (Green/Yellow/Red/Black), hospital conveyance, and casualty identity.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `uuid`: `UUID` (NOT NULL, UNIQUE)
  - `incident_id`: `BIGINT` (NOT NULL, FK -> `incidents(id)` ON DELETE CASCADE)
  - `casualty_type`: `VARCHAR(30)` (NOT NULL)
  - `triage_category`: `VARCHAR(20)` (NOT NULL)
  - `full_name`: `VARCHAR(150)`
  - `age`: `INT`
  - `gender`: `VARCHAR(20)`
  - `injury_nature`: `TEXT`
  - `hospital_name`: `VARCHAR(200)`
  - `ambulance_id`: `VARCHAR(50)`
  - `recorded_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `incident_id` -> `incidents(id)`
- **Unique Constraints**: `uuid` UNIQUE
- **Check Constraints**: `CHECK (casualty_type IN ('CIVILIAN', 'FIREFIGHTER', 'POLICE', 'PARAMEDIC'))`, `CHECK (triage_category IN ('IMMEDIATE_RED', 'DELAYED_YELLOW', 'MINOR_GREEN', 'DECEASED_BLACK'))`
- **Indexes**: B-Tree on `(incident_id, triage_category)`
- **Tenant Key**: Indirect via `incidents.org_id`
- **Spatial Columns**: None
- **Audit Columns**: `recorded_at`
- **Relationships**: `incidents` (1) -> (N) `incident_casualties`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 19. `incident_reports`
- **Source Section**: 6.19
- **Stated Module**: Incidents / Post-Incident Investigation
- **Purpose**: Comprehensive post-fire investigation report detailing origin, cause of ignition, loss amount, and CFO signoff.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `uuid`: `UUID` (NOT NULL, UNIQUE)
  - `incident_id`: `BIGINT` (NOT NULL, FK -> `incidents(id)`, UNIQUE)
  - `report_number`: `VARCHAR(100)` (NOT NULL, UNIQUE)
  - `point_of_origin`: `TEXT`
  - `probable_cause`: `VARCHAR(100)`
  - `estimated_loss_amount`: `NUMERIC(14,2)`
  - `estimated_saved_amount`: `NUMERIC(14,2)`
  - `building_owner_statement`: `TEXT`
  - `investigating_officer_id`: `BIGINT` (FK -> `users(id)`)
  - `approved_by_cfo_id`: `BIGINT` (FK -> `users(id)`)
  - `approval_date`: `TIMESTAMPTZ`
  - `document_id`: `BIGINT` (FK -> `documents(id)`)
  - `created_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
  - `updated_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `incident_id` -> `incidents(id)`, `investigating_officer_id` -> `users(id)`, `approved_by_cfo_id` -> `users(id)`, `document_id` -> `documents(id)`
- **Unique Constraints**: `uuid` UNIQUE, `incident_id` UNIQUE, `report_number` UNIQUE
- **Check Constraints**: `MISSING — REQUIRED CONFIRMATION`
- **Indexes**: B-Tree on `report_number`, B-Tree on `investigating_officer_id`
- **Tenant Key**: Indirect via `incidents.org_id`
- **Spatial Columns**: None
- **Audit Columns**: `created_at`, `updated_at`, `approval_date`, `approved_by_cfo_id`
- **Relationships**: `incidents` (1) <-> (1) `incident_reports`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 20. `vehicle_location_history`
- **Source Section**: 6.20
- **Stated Module**: Fire Vehicles / AVL Telematics
- **Purpose**: High-frequency AVL GPS telemetry time-series storage for breadcrumb route playback and response speed analysis.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `vehicle_id`: `BIGINT` (NOT NULL, FK -> `fire_vehicles(id)` ON DELETE CASCADE)
  - `recorded_at`: `TIMESTAMPTZ` (NOT NULL)
  - `location`: `GEOMETRY(Point, 4326)` (NOT NULL)
  - `speed_kmh`: `NUMERIC(5,2)`
  - `heading_degrees`: `NUMERIC(5,2)`
  - `ignition_on`: `BOOLEAN`
  - `battery_voltage`: `NUMERIC(4,2)`
  - `incident_id`: `BIGINT` (FK -> `incidents(id)` NULL)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `vehicle_id` -> `fire_vehicles(id)`, `incident_id` -> `incidents(id)`
- **Unique Constraints**: None (telemetry stream)
- **Check Constraints**: `CHECK (speed_kmh >= 0)`, `CHECK (heading_degrees >= 0 AND heading_degrees <= 360)`
- **Indexes**: GIST index `idx_veh_loc_hist_geom` ON `(location)`, Composite B-Tree ON `(vehicle_id, recorded_at DESC)`
- **Tenant Key**: Indirect via `fire_vehicles.org_id`
- **Spatial Columns**: `location` (`GEOMETRY(Point, 4326)`)
- **Audit Columns**: `recorded_at`
- **Relationships**: `fire_vehicles` (1) -> (N) `vehicle_location_history`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 21. `vehicle_crew`
- **Source Section**: 6.21
- **Stated Module**: Fire Vehicles / Shift Management
- **Purpose**: Daily duty rosters, crew staffing levels, shift rosters, and assigned vehicle apparatus per shift rotation.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `vehicle_id`: `BIGINT` (NOT NULL, FK -> `fire_vehicles(id)` ON DELETE CASCADE)
  - `user_id`: `BIGINT` (NOT NULL, FK -> `users(id)`)
  - `shift_date`: `DATE` (NOT NULL)
  - `shift_code`: `VARCHAR(20)` (NOT NULL)
  - `duty_role`: `VARCHAR(50)`
  - `assigned_by_user_id`: `BIGINT` (FK -> `users(id)`)
  - `created_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `vehicle_id` -> `fire_vehicles(id)`, `user_id` -> `users(id)`, `assigned_by_user_id` -> `users(id)`
- **Unique Constraints**: `UNIQUE (user_id, shift_date, shift_code)`
- **Check Constraints**: `CHECK (shift_code IN ('MORNING', 'EVENING', 'NIGHT', 'GENERAL'))`
- **Indexes**: B-Tree on `(vehicle_id, shift_date)`, B-Tree on `(user_id, shift_date)`
- **Tenant Key**: Indirect via `fire_vehicles.org_id`
- **Spatial Columns**: None
- **Audit Columns**: `created_at`, `assigned_by_user_id`
- **Relationships**: `fire_vehicles` (1) -> (N) `vehicle_crew`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 22. `vehicle_fuel_logs`
- **Source Section**: 6.22
- **Stated Module**: Fire Vehicles / Fleet Logistics
- **Purpose**: Fuel dispense quantities, diesel cost, fuel station slips, odometer readings, and apparatus fuel efficiency.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `uuid`: `UUID` (NOT NULL, UNIQUE)
  - `vehicle_id`: `BIGINT` (NOT NULL, FK -> `fire_vehicles(id)`)
  - `fuel_type`: `VARCHAR(30)` (DEFAULT `'DIESEL'`)
  - `liters_filled`: `NUMERIC(8,2)` (NOT NULL)
  - `cost_total`: `NUMERIC(10,2)` (NOT NULL)
  - `odometer_reading_km`: `INT` (NOT NULL)
  - `fuel_slip_number`: `VARCHAR(50)`
  - `filled_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
  - `logged_by_user_id`: `BIGINT` (FK -> `users(id)`)
  - `created_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `vehicle_id` -> `fire_vehicles(id)`, `logged_by_user_id` -> `users(id)`
- **Unique Constraints**: `uuid` UNIQUE
- **Check Constraints**: `CHECK (liters_filled > 0 AND cost_total >= 0 AND odometer_reading_km >= 0)`
- **Indexes**: B-Tree on `(vehicle_id, filled_at DESC)`
- **Tenant Key**: Indirect via `fire_vehicles.org_id`
- **Spatial Columns**: None
- **Audit Columns**: `created_at`, `logged_by_user_id`
- **Relationships**: `fire_vehicles` (1) -> (N) `vehicle_fuel_logs`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 23. `vehicle_fitness`
- **Source Section**: 6.23
- **Stated Module**: Fire Vehicles / Statutory Compliance
- **Purpose**: RTO vehicle fitness certificates, brake test records, pump testing certifications, and roadworthiness validity.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `uuid`: `UUID` (NOT NULL, UNIQUE)
  - `vehicle_id`: `BIGINT` (NOT NULL, FK -> `fire_vehicles(id)`)
  - `certificate_number`: `VARCHAR(100)` (NOT NULL, UNIQUE)
  - `testing_agency`: `VARCHAR(150)`
  - `passed_test`: `BOOLEAN` (NOT NULL)
  - `issue_date`: `DATE` (NOT NULL)
  - `valid_until`: `DATE` (NOT NULL)
  - `inspection_notes`: `TEXT`
  - `certificate_doc_id`: `BIGINT` (FK -> `documents(id)`)
  - `created_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
  - `updated_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `vehicle_id` -> `fire_vehicles(id)`, `certificate_doc_id` -> `documents(id)`
- **Unique Constraints**: `uuid` UNIQUE, `certificate_number` UNIQUE
- **Check Constraints**: `CHECK (valid_until >= issue_date)`
- **Indexes**: B-Tree on `(vehicle_id, valid_until)`
- **Tenant Key**: Indirect via `fire_vehicles.org_id`
- **Spatial Columns**: None
- **Audit Columns**: `created_at`, `updated_at`
- **Relationships**: `fire_vehicles` (1) -> (N) `vehicle_fitness`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 24. `warehouses`
- **Source Section**: 6.24
- **Stated Module**: Inventory & Fire Store
- **Purpose**: Physical logistics depots, central departmental fire stores, and station equipment locker rooms.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `uuid`: `UUID` (NOT NULL, UNIQUE)
  - `org_id`: `BIGINT` (NOT NULL, FK -> `organizations(id)`)
  - `station_id`: `BIGINT` (FK -> `fire_stations(id)` NULL)
  - `warehouse_code`: `VARCHAR(50)` (NOT NULL)
  - `warehouse_name`: `VARCHAR(150)` (NOT NULL)
  - `warehouse_type`: `VARCHAR(50)` (NOT NULL)
  - `address`: `TEXT`
  - `supervisor_user_id`: `BIGINT` (FK -> `users(id)`)
  - `is_active`: `BOOLEAN` (DEFAULT `TRUE`)
  - `created_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
  - `updated_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `org_id` -> `organizations(id)`, `station_id` -> `fire_stations(id)`, `supervisor_user_id` -> `users(id)`
- **Unique Constraints**: `uuid` UNIQUE, `UNIQUE (org_id, warehouse_code)`
- **Check Constraints**: `CHECK (warehouse_type IN ('CENTRAL_DEPOT', 'STATION_STORE', 'WORKSHOP_STORE', 'MOBILE_STORE'))`
- **Indexes**: B-Tree on `(org_id, warehouse_code)`
- **Tenant Key**: `org_id` (`BIGINT`)
- **Spatial Columns**: None
- **Audit Columns**: `created_at`, `updated_at`
- **Relationships**: `organizations` (1) -> (N) `warehouses`, `warehouses` (1) -> (N) `inventory_stock`
- **Approval Status**: `REQUIRES APPROVAL`

---

### 25. `inventory_stock`
- **Source Section**: 6.25
- **Stated Module**: Inventory & Fire Store
- **Purpose**: Current atomic on-hand stock quantities per item per warehouse, eliminating slow sum aggregation across transactions.
- **Columns & Data Types**:
  - `id`: `BIGSERIAL` (NOT NULL, PK)
  - `warehouse_id`: `BIGINT` (NOT NULL, FK -> `warehouses(id)` ON DELETE RESTRICT)
  - `item_id`: `BIGINT` (NOT NULL, FK -> `inventory_items(id)` ON DELETE RESTRICT)
  - `current_quantity`: `INT` (NOT NULL, DEFAULT `0`)
  - `reserved_quantity`: `INT` (NOT NULL, DEFAULT `0`)
  - `available_quantity`: `INT` (GENERATED ALWAYS AS `(current_quantity - reserved_quantity)` STORED)
  - `batch_lot_number`: `VARCHAR(50)` (NULL)
  - `expiry_date`: `DATE` (NULL)
  - `last_counted_at`: `TIMESTAMPTZ`
  - `updated_at`: `TIMESTAMPTZ` (DEFAULT `NOW()`)
- **Primary Key**: `id` (`BIGSERIAL`)
- **Foreign Keys**: `warehouse_id` -> `warehouses(id)`, `item_id` -> `inventory_items(id)`
- **Unique Constraints**: `UNIQUE (warehouse_id, item_id, batch_lot_number)`
- **Check Constraints**: `CHECK (current_quantity >= 0 AND reserved_quantity >= 0 AND current_quantity >= reserved_quantity)`
- **Indexes**: B-Tree on `(warehouse_id, item_id)`
- **Tenant Key**: Indirect via `warehouses.org_id`
- **Spatial Columns**: None
- **Audit Columns**: `last_counted_at`, `updated_at`
- **Relationships**: `warehouses` (1) -> (N) `inventory_stock`, `inventory_items` (1) -> (N) `inventory_stock`
- **Approval Status**: `REQUIRES APPROVAL`

---

## CRITICAL AUDIT FINDING: 6 Unaccounted "New" Tables in Appendix A

While Section 6 explicitly defines exactly 25 tables above, **Appendix A** lists 6 additional tables designated as `(NEW)`:
1. `inventory_categories` (NEW) - Listed in Appendix A (#46)
2. `notification_templates` (NEW) - Listed in Appendix A (#57)
3. `payments` (NEW) - Listed in Appendix A (#64)
4. `purchase_order_items` (NEW) - Listed in Appendix A (#66)
5. `purchase_orders` (NEW) - Listed in Appendix A (#67)
6. `vendors` (NEW) - Listed in Appendix A (#83)

**Status of these 6 tables**: `MISSING — REQUIRED CONFIRMATION`. They lack column-level data dictionaries, data types, nullability, and explicit constraint specifications in Section 6.
