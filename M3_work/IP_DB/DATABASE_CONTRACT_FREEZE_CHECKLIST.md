# FireSphere.ai — Database Contract Freeze Checklist

**Document Status:** Pre-Implementation Specification Checklist  
**Authority:** Member 3 Architectural Contract Freeze Package (Phase 1C)  
**Date:** October 2026  
**Auditor / Custodian:** Member 3 (Database, Infrastructure & DevOps Engineer)

---

## Instructions for Stakeholders
This checklist defines the complete set of architectural conventions, standards, and contracts that must be frozen before production schema DDL is finalized. Stakeholders must review each item and verify compliance across their respective domain areas.

---

### 1. Identity & Primary Key Conventions
- [ ] **Internal Primary Key (PK) Type:** Standardized as `id BIGSERIAL PRIMARY KEY` (64-bit integer) across all relational tables for optimal B-Tree index density, compact foreign keys, and high join performance.
- [ ] **External Public Identifier:** Standardized as `uuid UUID DEFAULT gen_random_uuid() UNIQUE NOT NULL` on all domain entities exposed via REST APIs, GraphQL, mobile synchronization, or webhooks (prevents enumeration attacks).
- [ ] **Foreign Key (FK) Data Types:** All relational child references strictly mirror the parent's internal primary key (`BIGINT`).
- [ ] **Naming Conventions:**
  - Primary keys named `id`.
  - External UUIDs named `uuid`.
  - Foreign keys named `<singular_target_entity>_id` (e.g., `building_id`, `inspection_id`, `incident_id`).
  - Junction tables named `<entity_a>_<entity_b>` (e.g., `user_roles`, `role_permissions`).
  - Timestamps named `<action>_at` (e.g., `created_at`, `updated_at`, `deleted_at`, `verified_at`).

---

### 2. Multi-Tenancy & Isolation Architecture
- [ ] **Canonical Tenant Column:** Standardized as `org_id BIGINT NOT NULL REFERENCES organizations(id)` across all tenant-aware tables.
- [ ] **Organizations Root Relationship:** Core tenant master entity defined in `organizations (id BIGSERIAL PK, uuid UUID UNIQUE, code VARCHAR(50) UNIQUE, name VARCHAR(200))`.
- [ ] **Row-Level Security (RLS) Policy:** Every tenant table explicitly enables RLS:
  ```sql
  ALTER TABLE firesphere.<table_name> ENABLE ROW LEVEL SECURITY;
  CREATE POLICY tenant_isolation_policy ON firesphere.<table_name>
    USING (org_id = current_setting('app.current_org_id')::BIGINT);
  ```
- [ ] **Zero-Trust Context Injection:** Client-supplied tenant IDs are rejected. The server derives tenant context solely from validated JWT claims and issues:
  ```sql
  SELECT set_tenant_context(p_org_id, p_is_super_admin);
  ```
- [ ] **Super-Admin Bypass Protection:** Platform engineers access multi-tenant data only when `p_is_super_admin = TRUE` is explicitly set via audited break-glass procedures; legacy users with `is_super_admin = 1` are stripped upon import.

---

### 3. Change Data Capture (CDC) & Audit Logging
- [ ] **Canonical Audit Table Name:** Designated as `firesphere.audit_logs`.
- [ ] **Audit Trigger Strategy:** PostgreSQL `firesphere_audit_trigger()` attached BEFORE/AFTER `INSERT`, `UPDATE`, `DELETE` on all statutory entities (`buildings`, `noc_applications`, `inspections`, `violations`, `incidents`, `payments`).
- [ ] **Immutability Controls:** Table privileges on `audit_logs` restricted to `SELECT` and `INSERT` only (`UPDATE`, `DELETE`, and `TRUNCATE` revoked from application connection roles).
- [ ] **Actor Identity Recorded:** Audited records capture `user_id BIGINT`, `organization_id BIGINT`, `client_ip INET`, and `user_agent TEXT`.
- [ ] **State Diffs Recorded:** Complete previous and updated entity states stored in `old_values JSONB` and `new_values JSONB`, with modified columns indexed in `changed_fields TEXT[]`.

---

### 4. Security & Cryptographic Controls
- [ ] **Password Hashing:** Production storage in `users.password_hash` strictly uses **bcrypt (cost factor 12)** or Argon2id.
- [ ] **Legacy Account Quarantine:** All legacy accounts imported from `database_53.sql` with unsalted SHA-256 hashes are flagged with `force_password_reset = TRUE` and require an OTP password reset before login.
- [ ] **Token Sanitization:** Leaked plaintext tokens in legacy `users.metadata_json` are purged during migration ETL.
- [ ] **API Key Storage:** Raw keys (`fs_live_***`) never persisted. Database stores only SHA-256 hashes in `api_keys.key_hash` with prefix lookups via `api_keys.key_prefix`.
- [ ] **Secret Encryption at Rest (`pgcrypto`):** Third-party credentials in `org_api_credentials.api_secret` and `integrations.config_json` encrypted via `pgp_sym_encrypt` with master keys passed through environment variables (never committed to repository).

---

### 5. PostGIS Spatial & GIS Engine
- [ ] **Spatial Coordinate Reference System:** All spatial geometries standardized to **WGS 84 (SRID 4326)**.
- [ ] **Native Geometry Types:**
  - `GEOMETRY(Point, 4326)` for point locations (`fire_stations.location`, `hydrants.location`, `water_sources.location`, `buildings.location`, `incidents.location`, `vehicle_location_history.location`).
  - `GEOMETRY(Polygon, 4326)` for boundary polygons (`fire_stations.jurisdiction_geom`, `buildings.building_footprint`, `zones.boundary_geom`, `beats.boundary_geom`).
- [ ] **Spatial Indexing:** Mandatory `GIST` indexes created on all spatial geometry columns.
- [ ] **Coordinate Conventions:** Strict longitude-first convention (`ST_MakePoint(longitude, latitude)`) applied in all DDL, SQL queries, and API GeoJSON payloads.

---

### 6. Object Storage & Binary Asset Metadata
- [ ] **Object Storage Engine:** MinIO (local dev / test) and S3-compatible cloud storage (production).
- [ ] **Storage Key Schema:** Object keys follow strict tenant-isolated hierarchy:
  `/{bucket_name}/{org_id}/{entity_type}/{entity_uuid}/{filename}`.
- [ ] **Metadata Persisted in Relational DB:** Database records `file_path TEXT`, `file_size_bytes BIGINT`, `mime_type VARCHAR(100)`, `file_hash_sha256 VARCHAR(64)`, and `uploaded_by BIGINT`.
- [ ] **Pre-Signed URLs:** Raw files served exclusively via expiring pre-signed S3 URLs (TTL <= 15 minutes); direct public read access to buckets blocked.
- [ ] **Integrity Verification:** Client uploads validated via SHA-256 checksums matching database records.

---

### 7. Core Operational Lifecycles & Workflow State Machines
- [ ] **NOC Application Lifecycle:** State machine transitions audited in `noc_application_status_history` (`DRAFT` -> `SUBMITTED` -> `UNDER_INSPECTION` -> `APPROVED` / `REJECTED` -> `ISSUED` -> `EXPIRED`).
- [ ] **Inspection Lifecycle:** Audited from dispatch to compliance (`ASSIGNED` -> `SCHEDULED` -> `IN_PROGRESS` -> `COMPLETED` -> `NON_COMPLIANT` / `COMPLIANT`).
- [ ] **Violations & Corrective Actions:** Formal tracking from issuance to abatement (`NOTICE_ISSUED` -> `PENDING_RECTIFICATION` -> `REINSPECTED` -> `RESOLVED` / `ESCALATED_TO_SEAL_ORDER`).
- [ ] **Fire NOC Certificate Lifecycle:** Immutable digital certificates issued with unique certificate numbers, SHA-256 verification hashes, and QR code validation tokens in `noc_certificates`.
- [ ] **Incident Dispatch Lifecycle:** Emergency call response tracked chronologically in `incident_timeline` (`CALL_RECEIVED` -> `DISPATCHED` -> `EN_ROUTE` -> `ON_SCENE` -> `CONTAINED` -> `CLOSED`).

---

### 8. AI / ML Inference Subsystem
- [ ] **AI Run Tracking:** Every model inference logged in `ai_runs` with unique run ID, model ID, prompt ID, execution latency, and token consumption.
- [ ] **Model Version Tracking:** Models registered in `ai_models` with provider, model family, version tag, and operational status.
- [ ] **Structured Insights:** Outputs written to `ai_insights` with `confidence_score NUMERIC(5,2)`, `insight_summary TEXT`, and structured metadata.
- [ ] **Evidence Linkage:** AI vision detections linked directly to `inspection_photos` or `incident_evidence`.
- [ ] **Human-in-the-Loop Review:** Officer review status recorded (`PENDING`, `ACCEPTED`, `DISMISSED`) with reviewing officer ID and timestamp.
- [ ] **Incident AI Persistence Resolution:** Final sign-off on whether incident AI models write to `ai_insights` or a dedicated `incident_ai_analysis` table (ADR-005).

---

### 9. Canonical Entity Naming Standards
- [ ] **SSO Identity Table:** Canonical name confirmed as `sso_identities` (legacy `sso_user_identities` mapped during migration).
- [ ] **Audit Logging Table:** Canonical name confirmed as `audit_logs` (schema `firesphere.audit_logs`).
- [ ] **Tenant Organization Table:** Canonical name confirmed as `organizations`.
- [ ] **Incident Subsystem Tables:** Canonical names confirmed as:
  - `incidents`
  - `incident_dispatches`
  - `incident_vehicles`
  - `incident_crews`
  - `incident_timeline`
  - `incident_casualties`
  - `incident_reports`
  - `incident_evidence`
