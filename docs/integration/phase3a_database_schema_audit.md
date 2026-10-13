# FireSphere.ai — Phase 3A: Database Schema Audit & Entity Compatibility
**Document Reference:** `docs/integration/phase3a_database_schema_audit.md`  
**Status:** COMPLETE AUDIT  
**Authors:** Senior Database Architect, Spring Boot Persistence Engineer, PostgreSQL/PostGIS Engineer  
**Date:** October 2026  

---

## 1. Executive Summary

This report delivers a forensic audit of the physical database migrations created by Member 3 (`M3_work/IP_DB/database/migrations`), the JPA persistence entities maintained by Member 2 (`M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend`), and the storage abstractions in Member 4 (`ai-service`).

### Key Audit Findings:
1. **Schema Namespace Mismatch:** M3 defines all 44 tables in a dedicated schema (`CREATE SCHEMA IF NOT EXISTS firesphere`), while M2 entities lack `@Table(schema = "firesphere")` and M2 configuration lacks `currentSchema=firesphere`.
2. **Identifier Type Conflict:** M3 defines primary keys as `id BIGSERIAL PRIMARY KEY` (`BIGINT`/`int8`) with `uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE`. In direct contradiction, all M2 JPA entities define `@Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id`.
3. **Missing AI Tables:** Neither `ai_analysis_runs`, `ai_analysis_findings`, nor the proposed `ai_insights` table exists in M3's migrations (`V1.0.0` through `V1.0.6`).
4. **Missing Inspection Evidence Table:** M3 defines `incident_evidence` (emergency dispatch) and `building_documents` (architectural files), but no dedicated `inspection_evidence` table linking multiple field photographs to an inspection session.
5. **Relational Structure Divergence:** M2 models NOC applications with flat building string fields (`buildingName`, `address`), whereas M3 enforces strict normalization requiring foreign keys to `firesphere.buildings(id)` and `firesphere.noc_application_categories(id)`.

---

## 2. Member 3 (M3) Flyway Migration Inventory

M3's database implementation is structured into seven Flyway migrations in `M3_work/IP_DB/database/migrations`:

| Migration File | Version | Core Responsibility | Tables Created | Key Types & Constraints |
| :--- | :--- | :--- | :--- | :--- |
| `V1.0.0__core_extensions_and_helpers.sql` | 1.0.0 | Extensions & utilities | None (Schema `firesphere`, functions) | `uuid-ossp`, `postgis`, `btree_gist`, `pgcrypto`. Sets up `current_org_id()` returning `BIGINT`. |
| `V1.0.1__core_tenancy_and_identities.sql` | 1.0.1 | Tenancy, users & RBAC | `organizations`, `divisions`, `departments`, `users`, `roles`, `permissions`, `role_permissions`, `user_roles` | All tables have `id BIGSERIAL PK`, `uuid UUID UNIQUE`. Foreign keys use `BIGINT`. |
| `V1.0.2__spatial_infrastructure_and_locations.sql` | 1.0.2 | PostGIS GIS tables | `zones`, `beats`, `fire_stations`, `hydrants`, `water_sources`, `buildings` | Spatial geometries (`Polygon`, `Point` in SRID 4326). All PKs are `BIGSERIAL`. |
| `V1.0.3__operations_noc_and_inspections.sql` | 1.0.3 | NOC workflows & inspections | `building_contacts`, `building_documents`, `building_fire_equipment`, `building_owners`, `building_risk_profiles`, `noc_application_categories`, `noc_applications`, `noc_application_status_history`, `noc_certificates`, `noc_renewals`, `inspection_checklists`, `inspection_checklist_items`, `inspections`, `inspection_assignments`, `inspection_findings`, `corrective_actions`, `violations`, `violation_actions` | Strict normalization. `inspections` references `noc_applications(id)` and `buildings(id)`. `photo_evidence_s3_key TEXT` on findings. |
| `V1.0.4__emergency_incidents_fleet_and_audit.sql` | 1.0.4 | Emergency response & audit | `incidents`, `incident_dispatches`, `fire_vehicles`, `incident_vehicles`, `incident_crews`, `incident_timeline`, `incident_casualties`, `incident_reports`, `incident_evidence`, `vehicle_crew`, `vehicle_fitness`, `vehicle_fuel_logs`, `vehicle_location_history`, `warehouses`, `inventory_items`, `inventory_stock`, `training_courses`, `audit_logs` | `incident_evidence` stores S3 keys (`file_path`). Central `audit_logs` CDC trigger. |
| `V1.0.5__enable_row_level_security.sql` | 1.0.5 | Multi-tenant isolation | None (Alters 44 tables) | Enables RLS across all tables; policies enforce `org_id = firesphere.current_org_id()`. |
| `V1.0.6__add_secondary_foreign_key_indexes.sql` | 1.0.6 | Performance optimization | None (Adds 7 B-tree indexes) | Adds secondary FK indexes on dispatches, fitness, fuel logs, and equipment. |

---

## 3. Member 2 (M2) JPA Entity Inventory

M2 defines 13 entity classes located in `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend`:

| Entity Class | Java Package | `@Table(name=...)` | Primary Key Java Type | Foreign Key References (Java Type) |
| :--- | :--- | :--- | :--- | :--- |
| `Organization` | `organization.entity` | `organizations` | `UUID` (`GenerationType.UUID`) | None |
| `User` | `auth.entity` | `users` | `UUID` (`GenerationType.UUID`) | `organizationId: UUID` |
| `NocApplication` | `noc.entity` | `noc_applications` | `UUID` (`GenerationType.UUID`) | `applicantId: UUID`, `organizationId: UUID` |
| `NocInspection` | `inspection.entity` | `noc_inspections` | `UUID` (`GenerationType.UUID`) | `applicationId: UUID`, `organizationId: UUID`, `assignedOfficer: UUID` |
| `NocDocument` | `document.entity` | `noc_documents` | `UUID` (`GenerationType.UUID`) | `applicationId: UUID`, `organizationId: UUID`, `uploadedBy: UUID` |
| `NocCertificate` | `certificate.entity` | `noc_certificates` | `UUID` (`GenerationType.UUID`) | `applicationId: UUID`, `organizationId: UUID` |
| `NocChecklist` | `checklist.entity` | `noc_checklists` | `UUID` (`GenerationType.UUID`) | `applicationId: UUID`, `organizationId: UUID` |
| `ChecklistItem` | `checklist.entity` | `noc_checklist_items`| `UUID` (`GenerationType.UUID`) | `checklist_id: UUID` (`@ManyToOne NocChecklist`) |
| `ChecklistRule` | `checklist.entity` | `noc_checklist_rules`| `UUID` (`GenerationType.UUID`) | `item_id: UUID` (`@ManyToOne ChecklistItem`) |
| `PaymentRecord` | `payment.entity` | `payment_records` | `UUID` (`GenerationType.UUID`) | `applicationId: UUID`, `organizationId: UUID` |
| `IdempotencyRecord` | `common.idempotency.entity`| `idempotency_records`| `UUID` (`GenerationType.UUID`) | `organizationId: UUID`, `userId: UUID` |
| `AiAnalysisRun` | `ai.entity` | `ai_analysis_runs` | `UUID` (`GenerationType.UUID`) | `applicationId: UUID`, `documentId: UUID`, `organizationId: UUID` |
| `AiAnalysisFinding`| `ai.entity` | `ai_analysis_findings` | `UUID` (`GenerationType.UUID`) | `run_id: UUID` (`@ManyToOne AiAnalysisRun`) |

---

## 4. Forensic Entity-to-Schema Mapping & Gap Analysis

```
+-----------------------------------+----------------------------------------+---------------------------------------+
| Architectural Domain              | Member 2 JPA Entity Definition         | Member 3 Physical DDL Definition      |
+-----------------------------------+----------------------------------------+---------------------------------------+
| Database Schema Namespace         | public (Unqualified default)           | firesphere (Explicit schema)          |
| Primary Key Types                 | UUID (java.util.UUID)                  | BIGINT (BIGSERIAL sequence, int8)     |
| Alternate Public Identifier       | None (id is public UUID)               | uuid UUID NOT NULL DEFAULT gen_...    |
| Foreign Key Types                 | UUID (Loose untyped UUID columns)      | BIGINT REFERENCES firesphere.tbl(id)  |
| Tenant Scoping (org_id)           | organizationId: UUID                   | org_id: BIGINT REFERENCES orgs(id)    |
| AI Run Table                      | ai_analysis_runs (JPA Entity exists)   | MISSING (Does not exist in M3)        |
| AI Finding Table                  | ai_analysis_findings (JPA Entity)      | MISSING (Neither findings nor insights)|
| Inspection Evidence Photos        | None (M2 has only noc_documents)       | MISSING (M3 has only incident_evidence)|
| Application Table Name            | noc_applications                       | firesphere.noc_applications           |
| Inspection Table Name             | noc_inspections                        | firesphere.inspections                |
| Inspection Status Column          | status                                 | inspection_status                     |
| Application Status Column         | status                                 | current_status                        |
| User Password Column              | password                               | password_hash                         |
| Building Association              | Flat Strings (buildingName, address)   | Normalized: building_id BIGINT        |
| Application Category              | Missing (Not modeled in M2)            | Normalized: category_id BIGINT        |
| Assigned Officer Model            | assignedOfficer UUID on inspection     | firesphere.inspection_assignments tbl |
+-----------------------------------+----------------------------------------+---------------------------------------+
```

---

## 5. Confirmed Critical Incompatibilities

### Incompatibility 1: Schema Namespace Disconnect
- **M3:** All DDL declares `firesphere.<table_name>`.
- **M2:** Entities declare `@Table(name = "noc_applications")`. Hibernate generates SQL targeting the default schema `public`.
- **Impact:** PostgreSQL returns error `42P01: relation "noc_applications" does not exist`.
- **Resolution Required:** Configure `currentSchema=firesphere` in JDBC URL or declare `hibernate.default_schema: firesphere` in `application.yml`.

### Incompatibility 2: Primary Key Type Clash (UUID vs. BIGSERIAL)
- **M3:** Declares `id BIGSERIAL PRIMARY KEY`.
- **M2:** Declares `@Id private UUID id`.
- **Impact:** When Spring Boot starts with `ddl-auto: validate` (as configured in `application-local.yml`), Hibernate throws `SchemaManagementException: Wrong column type in firesphere.organizations for column id. Found: int8, expected: uuid`.
- **Execution Failure:** Insertion attempts produce `operator does not exist: bigint = uuid`.

### Incompatibility 3: Foreign Key Type Incompatibility
- **M3:** All foreign keys enforce referential integrity using `BIGINT REFERENCES firesphere.<table>(id)`.
- **M2:** References use `UUID` fields (e.g., `applicationId: UUID`, `organizationId: UUID`).
- **Impact:** M2 cannot join tables using M3's foreign key definitions.

### Incompatibility 4: Completely Missing AI Persistence Tables in M3
- **M2:** Requires `ai_analysis_runs` and `ai_analysis_findings`.
- **M4:** Requires durable persistence for AI analysis runs, rich findings, spatial coordinates, risk scores, and officer verification reviews.
- **M3:** Possesses **zero** AI-related tables in `V1.0.0` through `V1.0.6`. `firesphere.ai_insights` was never authored.
- **Impact:** Any AI analysis execution results in persistence failure if written to database.

### Incompatibility 5: Missing Inspection Evidence Table
- **M4 API Contract:** Requires `inspectionId: UUID` and `evidenceIds: List[UUID]`.
- **M2 Backend:** Only possesses `noc_documents` linked to `application_id`.
- **M3 Database:** Has `incident_evidence` (emergency incident response) and `building_documents` (architectural master plans), but lacks an `inspection_evidence` table representing photos captured during an inspection.
- **Impact:** M2 cannot legitimately resolve inspection evidence without a dedicated persistence entity.

---

## 6. Audit Conclusion

The database schema (M3) and the backend persistence layer (M2) are **currently incompatible in both schema naming and identifier data types**. M2 cannot run against M3's database without deliberate schema and persistence adaptation.

A unified identifier strategy and a formal AI persistence migration (`V1.0.7`) must be agreed upon and approved before database implementation begins.
