# FireSphere.ai — Column-Level Database Contract Audit

**SOURCE DOCUMENT**: `FireSphere_Database_Documentation_v2.xml`  
**STATUS**: `FORENSIC AUDIT COMPLETED`  
**SCOPE**: Exhaustive audit of all explicitly documented column specifications across verified legacy entities (12 tables) and newly engineered production entities (25 tables).

---

## 1. Verified Legacy Entities Column Contract (12 Tables)

| Table | Column | Type | Nullable | Default | PK | FK | Referenced Table(Column) | Unique | Check / Domain | Tenant-Related | Spatial | Audit Field | Confidence / Status | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `ai_agents` | `id` | BIGINT | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.3 |
| `ai_agents` | `org_id` | BIGINT | NO | - | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | CONFIRMED | 4.3 |
| `ai_agents` | `name` | VARCHAR(150) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.3 |
| `ai_agents` | `agent_type` | VARCHAR(50) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.3 |
| `ai_agents` | `model_id` | BIGINT | NO | - | NO | YES | `ai_models(id)` | NO | - | NO | NO | NO | CONFIRMED | 4.3 |
| `ai_agents` | `system_prompt` | TEXT | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.3 |
| `ai_agents` | `tools_json` | TEXT/JSON | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.3 |
| `ai_agents` | `status` | VARCHAR(20) | NO | 'ACTIVE' | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.3 |
| `ai_agents` | `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | NO | NO | - | NO | - | NO | NO | YES | CONFIRMED | 4.3 |
| `ai_models` | `id` | BIGINT | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.5 |
| `ai_models` | `org_id` | BIGINT | YES | NULL | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | CONFIRMED | 4.5 |
| `ai_models` | `model_name` | VARCHAR(100) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.5 |
| `ai_models` | `provider` | VARCHAR(50) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.5 |
| `ai_models` | `model_family` | VARCHAR(50) | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.5 |
| `ai_models` | `version` | VARCHAR(30) | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.5 |
| `ai_models` | `status` | VARCHAR(20) | NO | 'ACTIVE' | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.5 |
| `ai_models` | `config_json` | TEXT/JSON | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.5 |
| `ai_models` | `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | NO | NO | - | NO | - | NO | NO | YES | CONFIRMED | 4.5 |
| `fire_stations` | `id` | BIGINT | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.21 |
| `fire_stations` | `org_id` | BIGINT | NO | - | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | CONFIRMED | 4.21 |
| `fire_stations` | `division_id` | BIGINT | YES | - | NO | YES | `divisions(id)` | NO | - | NO | NO | NO | CONFIRMED | 4.21 |
| `fire_stations` | `zone_id` | BIGINT | YES | - | NO | YES | `zones(id)` | NO | - | NO | NO | NO | CONFIRMED | 4.21 |
| `fire_stations` | `station_code` | VARCHAR(50) | NO | - | NO | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.21 |
| `fire_stations` | `name` | VARCHAR(150) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.21 |
| `fire_stations` | `address` | TEXT | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.21 |
| `fire_stations` | `latitude` | NUMERIC(10,8) | YES | - | NO | NO | - | NO | Flat float | NO | YES (Legacy) | NO | CONFIRMED | 4.21 |
| `fire_stations` | `longitude` | NUMERIC(11,8) | YES | - | NO | NO | - | NO | Flat float | NO | YES (Legacy) | NO | CONFIRMED | 4.21 |
| `fire_stations` | `city` | VARCHAR(100) | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.21 |
| `fire_stations` | `state` | VARCHAR(100) | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.21 |
| `fire_stations` | `phone` | VARCHAR(20) | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.21 |
| `fire_stations` | `status` | VARCHAR(20) | NO | 'ACTIVE' | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.21 |
| `fire_stations` | `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | NO | NO | - | NO | - | NO | NO | YES | CONFIRMED | 4.21 |
| `fire_stations` | `updated_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | NO | NO | - | NO | - | NO | NO | YES | CONFIRMED | 4.21 |
| `noc_application_categories` | `id` | BIGINT | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.34 |
| `noc_application_categories` | `org_id` | BIGINT | YES | NULL | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | CONFIRMED | 4.34 |
| `noc_application_categories` | `code` | VARCHAR(50) | NO | - | NO | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.34 |
| `noc_application_categories` | `name` | VARCHAR(100) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.34 |
| `noc_application_categories` | `description` | TEXT | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.34 |
| `noc_application_categories` | `fee` | NUMERIC(12,2) | NO | 0.00 | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.34 |
| `noc_application_categories` | `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | NO | NO | - | NO | - | NO | NO | YES | CONFIRMED | 4.34 |
| `org_settings` | `id` | BIGINT | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.39 |
| `org_settings` | `org_id` | BIGINT | NO | - | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | CONFIRMED | 4.39 |
| `org_settings` | `setting_key` | VARCHAR(100) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.39 |
| `org_settings` | `setting_value` | TEXT | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.39 |
| `org_settings` | `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | NO | NO | - | NO | - | NO | NO | YES | CONFIRMED | 4.39 |
| `org_settings` | `updated_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | NO | NO | - | NO | - | NO | NO | YES | CONFIRMED | 4.39 |
| `org_sso_providers` | `id` | BIGINT | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.40 |
| `org_sso_providers` | `org_id` | BIGINT | NO | - | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | CONFIRMED | 4.40 |
| `org_sso_providers` | `provider_name` | VARCHAR(100) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.40 |
| `org_sso_providers` | `provider_type` | VARCHAR(50) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.40 |
| `org_sso_providers` | `domain` | VARCHAR(150) | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.40 |
| `org_sso_providers` | `issuer_url` | TEXT | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.40 |
| `org_sso_providers` | `client_id` | VARCHAR(255) | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.40 |
| `org_sso_providers` | `is_active` | BOOLEAN | NO | TRUE | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.40 |
| `org_sso_providers` | `is_default` | BOOLEAN | NO | FALSE | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.40 |
| `org_sso_providers` | `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | NO | NO | - | NO | - | NO | NO | YES | CONFIRMED | 4.40 |
| `organizations` | `id` | BIGINT | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.41 |
| `organizations` | `tenant_code` | VARCHAR(50) | NO | - | NO | NO | - | YES | - | YES | NO | NO | CONFIRMED | 4.41 |
| `organizations` | `name` | VARCHAR(150) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.41 |
| `organizations` | `legal_name` | VARCHAR(200) | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.41 |
| `organizations` | `org_type` | VARCHAR(50) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.41 |
| `organizations` | `status` | VARCHAR(20) | NO | 'ACTIVE' | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.41 |
| `organizations` | `country` | VARCHAR(50) | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.41 |
| `organizations` | `state` | VARCHAR(50) | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.41 |
| `organizations` | `city` | VARCHAR(50) | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.41 |
| `organizations` | `address` | TEXT | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.41 |
| `organizations` | `branding` | TEXT/JSON | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.41 |
| `organizations` | `config` | TEXT/JSON | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.41 |
| `permissions` | `id` | BIGINT | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.43 |
| `permissions` | `org_id` | BIGINT | YES | NULL | NO | YES | `organizations(id)` | NO | To be removed | YES | NO | NO | CONFIRMED | 4.43 |
| `permissions` | `module_name` | VARCHAR(50) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.43 |
| `permissions` | `permission_key` | VARCHAR(100) | NO | - | NO | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.43 |
| `permissions` | `description` | TEXT | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.43 |
| `permissions` | `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | NO | NO | - | NO | - | NO | NO | YES | CONFIRMED | 4.43 |
| `roles` | `id` | BIGINT | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.45 |
| `roles` | `org_id` | BIGINT | YES | NULL | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | CONFIRMED | 4.45 |
| `roles` | `name` | VARCHAR(100) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.45 |
| `roles` | `code` | VARCHAR(50) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.45 |
| `roles` | `description` | TEXT | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.45 |
| `roles` | `is_system` | BOOLEAN | NO | FALSE | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.45 |
| `roles` | `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | NO | NO | - | NO | - | NO | NO | YES | CONFIRMED | 4.45 |
| `roles` | `updated_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | NO | NO | - | NO | - | NO | NO | YES | CONFIRMED | 4.45 |
| `security_policies` | `id` | BIGINT | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.46 |
| `security_policies` | `org_id` | BIGINT | NO | - | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | CONFIRMED | 4.46 |
| `security_policies` | `policy_name` | VARCHAR(100) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.46 |
| `security_policies` | `policy_type` | VARCHAR(50) | NO | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.46 |
| `security_policies` | `policy_json` | TEXT/JSON | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.46 |
| `security_policies` | `is_active` | BOOLEAN | NO | TRUE | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.46 |
| `security_policies` | `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | NO | NO | - | NO | - | NO | NO | YES | CONFIRMED | 4.46 |
| `security_policies` | `updated_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | NO | NO | - | NO | - | NO | NO | YES | CONFIRMED | 4.46 |
| `user_roles` | `user_id` | BIGINT | NO | - | YES | YES | `users(id)` | NO | - | NO | NO | NO | CONFIRMED | 4.50 |
| `user_roles` | `role_id` | BIGINT | NO | - | YES | YES | `roles(id)` | NO | - | NO | NO | NO | CONFIRMED | 4.50 |
| `user_roles` | `assigned_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | NO | NO | - | NO | - | NO | NO | YES | CONFIRMED | 4.50 |
| `users` | `id` | BIGINT | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.52 |
| `users` | `org_id` | BIGINT | NO | - | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | CONFIRMED | 4.52 |
| `users` | `username` | VARCHAR(100) | NO | - | NO | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.52 |
| `users` | `email` | VARCHAR(150) | NO | - | NO | NO | - | YES | - | NO | NO | NO | CONFIRMED | 4.52 |
| `users` | `phone` | VARCHAR(20) | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.52 |
| `users` | `password_hash`| VARCHAR(255) | NO | - | NO | NO | - | NO | Unsalted SHA-256 in legacy | NO | NO | NO | CONFIRMED | 4.52 |
| `users` | `first_name` | VARCHAR(100) | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.52 |
| `users` | `last_name` | VARCHAR(100) | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.52 |
| `users` | `employee_code`| VARCHAR(50) | YES | - | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.52 |
| `users` | `status` | VARCHAR(20) | NO | 'ACTIVE' | NO | NO | - | NO | - | NO | NO | NO | CONFIRMED | 4.52 |
| `users` | `is_super_admin`| SMALLINT | NO | 0 | NO | NO | - | NO | Set to 1 across all 6 rows | NO | NO | NO | CONFIRMED | 4.52 |
| `users` | `metadata_json`| TEXT/JSON | YES | - | NO | NO | - | NO | Leaked verification tokens | NO | NO | NO | CONFIRMED | 4.52 |

---

## 2. New Production Entities Column Contract (25 Tables)

*(Extracted directly from Section 6.1 through 6.25 of the source document)*

| Table | Column | Type | Nullable | Default | PK | FK | Referenced Table(Column) | Unique | Check / Domain Constraint | Tenant-Related | Spatial | Audit Field | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `building_owners` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `building_owners` | `uuid` | UUID | NO | uuid_generate_v4() | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `building_owners` | `org_id` | BIGINT | NO | - | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | DOCUMENTED |
| `building_owners` | `building_id` | BIGINT | NO | - | NO | YES | `buildings(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `building_owners` | `owner_type` | VARCHAR(30) | NO | 'INDIVIDUAL' | NO | NO | - | NO | IN ('INDIVIDUAL','CORPORATE','PARTNERSHIP','GOVERNMENT','SOCIETY') | NO | NO | NO | DOCUMENTED |
| `building_owners` | `owner_name` | VARCHAR(255) | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_owners` | `share_percentage` | NUMERIC(5,2) | NO | 100.00 | NO | NO | - | NO | > 0.00 AND <= 100.00 | NO | NO | NO | DOCUMENTED |
| `building_owners` | `is_primary` | BOOLEAN | NO | TRUE | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_owners` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `building_owners` | `updated_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `building_contacts` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `building_contacts` | `uuid` | UUID | NO | uuid_generate_v4() | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `building_contacts` | `org_id` | BIGINT | NO | - | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | DOCUMENTED |
| `building_contacts` | `building_id` | BIGINT | NO | - | NO | YES | `buildings(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `building_contacts` | `contact_name` | VARCHAR(150) | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_contacts` | `role_title` | VARCHAR(100) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_contacts` | `phone_primary` | VARCHAR(20) | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_contacts` | `phone_secondary` | VARCHAR(20) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_contacts` | `email` | VARCHAR(150) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_contacts` | `is_emergency_contact` | BOOLEAN | NO | TRUE | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_contacts` | `availability_hours` | VARCHAR(50) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_contacts` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `building_contacts` | `updated_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `building_documents` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `building_documents` | `uuid` | UUID | NO | uuid_generate_v4() | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `building_documents` | `org_id` | BIGINT | NO | - | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | DOCUMENTED |
| `building_documents` | `building_id` | BIGINT | NO | - | NO | YES | `buildings(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `building_documents` | `document_id` | BIGINT | NO | - | NO | YES | `documents(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `building_documents` | `doc_category` | VARCHAR(50) | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_documents` | `floor_number` | INT | YES | NULL | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_documents` | `approval_authority` | VARCHAR(150) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_documents` | `approval_reference` | VARCHAR(100) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_documents` | `is_verified` | BOOLEAN | NO | FALSE | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_documents` | `verified_by` | BIGINT | YES | NULL | NO | YES | `users(id)` | NO | - | NO | NO | YES | DOCUMENTED |
| `building_documents` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `building_documents` | `updated_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `building_risk_profiles` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `building_risk_profiles` | `uuid` | UUID | NO | uuid_generate_v4() | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `building_risk_profiles` | `org_id` | BIGINT | NO | - | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | DOCUMENTED |
| `building_risk_profiles` | `building_id` | BIGINT | NO | - | NO | YES | `buildings(id)` | YES | 1:1 relationship | NO | NO | NO | DOCUMENTED |
| `building_risk_profiles` | `risk_grade` | VARCHAR(10) | NO | - | NO | NO | - | NO | IN ('CRITICAL','HIGH','MODERATE','LOW') | NO | NO | NO | DOCUMENTED |
| `building_risk_profiles` | `overall_risk_score` | NUMERIC(5,2) | NO | - | NO | NO | - | NO | >= 0 AND <= 100 | NO | NO | NO | DOCUMENTED |
| `building_risk_profiles` | `fire_load_density_mj_sqm`| NUMERIC(10,2) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_risk_profiles` | `structural_combustibility_index`| NUMERIC(3,2)| YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_risk_profiles` | `occupancy_hazard_class` | VARCHAR(50) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_risk_profiles` | `last_assessed_at` | TIMESTAMPTZ | YES | - | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `building_risk_profiles` | `assessment_model_version`| VARCHAR(50) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_risk_profiles` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `building_risk_profiles` | `updated_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `building_fire_equipment` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `building_fire_equipment` | `uuid` | UUID | NO | uuid_generate_v4() | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `building_fire_equipment` | `org_id` | BIGINT | NO | - | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | DOCUMENTED |
| `building_fire_equipment` | `building_id` | BIGINT | NO | - | NO | YES | `buildings(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `building_fire_equipment` | `equipment_type` | VARCHAR(50) | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_fire_equipment` | `installed_quantity` | INT | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_fire_equipment` | `operational_quantity` | INT | NO | - | NO | NO | - | NO | <= installed AND >= 0 | NO | NO | NO | DOCUMENTED |
| `building_fire_equipment` | `floor_location` | VARCHAR(100) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_fire_equipment` | `last_tested_date` | DATE | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_fire_equipment` | `test_status` | VARCHAR(30) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_fire_equipment` | `expiry_date` | DATE | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `building_fire_equipment` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `building_fire_equipment` | `updated_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `noc_application_status_history` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `noc_application_status_history` | `application_id` | BIGINT | NO | - | NO | YES | `noc_applications(id)` | NO | - | Indirect | NO | NO | DOCUMENTED |
| `noc_application_status_history` | `from_status` | VARCHAR(50) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `noc_application_status_history` | `to_status` | VARCHAR(50) | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `noc_application_status_history` | `changed_by_user_id` | BIGINT | YES | - | NO | YES | `users(id)` | NO | - | NO | NO | YES | DOCUMENTED |
| `noc_application_status_history` | `remarks` | TEXT | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `noc_application_status_history` | `transition_metadata` | JSONB | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `noc_application_status_history` | `transition_timestamp` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `noc_certificates` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `noc_certificates` | `uuid` | UUID | NO | uuid_generate_v4() | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `noc_certificates` | `org_id` | BIGINT | NO | - | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | DOCUMENTED |
| `noc_certificates` | `application_id` | BIGINT | NO | - | NO | YES | `noc_applications(id)` | YES | 1:1 relationship | Indirect | NO | NO | DOCUMENTED |
| `noc_certificates` | `certificate_number` | VARCHAR(100) | NO | - | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `noc_certificates` | `issue_date` | DATE | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `noc_certificates` | `valid_until` | DATE | NO | - | NO | NO | - | NO | > issue_date | NO | NO | NO | DOCUMENTED |
| `noc_certificates` | `issuing_authority_user_id` | BIGINT | YES | - | NO | YES | `users(id)` | NO | - | NO | NO | YES | DOCUMENTED |
| `noc_certificates` | `digital_signature_hash` | VARCHAR(255) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `noc_certificates` | `qr_verification_url` | VARCHAR(500) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `noc_certificates` | `pdf_document_id` | BIGINT | YES | - | NO | YES | `documents(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `noc_certificates` | `status` | VARCHAR(30) | NO | 'ACTIVE' | NO | NO | - | NO | IN ('ACTIVE','EXPIRED','REVOKED','SUPERSEDED') | NO | NO | NO | DOCUMENTED |
| `noc_certificates` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `noc_certificates` | `updated_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `noc_renewals` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `noc_renewals` | `uuid` | UUID | NO | uuid_generate_v4() | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `noc_renewals` | `org_id` | BIGINT | NO | - | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | DOCUMENTED |
| `noc_renewals` | `previous_certificate_id`| BIGINT | NO | - | NO | YES | `noc_certificates(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `noc_renewals` | `renewal_application_id` | BIGINT | NO | - | NO | YES | `noc_applications(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `noc_renewals` | `renewal_cycle_year` | INT | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `noc_renewals` | `status` | VARCHAR(50) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `noc_renewals` | `renewal_fee_paid` | NUMERIC(12,2)| YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `noc_renewals` | `inspection_required` | BOOLEAN | NO | TRUE | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `noc_renewals` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `noc_renewals` | `updated_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `inspection_assignments` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `inspection_assignments` | `inspection_id` | BIGINT | NO | - | NO | YES | `inspections(id)` | NO | - | Indirect | NO | NO | DOCUMENTED |
| `inspection_assignments` | `inspector_user_id` | BIGINT | NO | - | NO | YES | `users(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `inspection_assignments` | `assignment_role` | VARCHAR(50) | NO | 'INSPECTOR' | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `inspection_assignments` | `assigned_by_user_id`| BIGINT | YES | - | NO | YES | `users(id)` | NO | - | NO | NO | YES | DOCUMENTED |
| `inspection_assignments` | `is_lead_officer` | BOOLEAN | NO | FALSE | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `inspection_assignments` | `assigned_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `inspection_checklist_items` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `inspection_checklist_items` | `checklist_id` | BIGINT | NO | - | NO | YES | `inspection_checklists(id)`| NO | - | Indirect | NO | NO | DOCUMENTED |
| `inspection_checklist_items` | `item_code` | VARCHAR(50) | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `inspection_checklist_items` | `item_text` | TEXT | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `inspection_checklist_items` | `clause_reference` | VARCHAR(100) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `inspection_checklist_items` | `severity_weight` | INT | NO | 1 | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `inspection_checklist_items` | `input_type` | VARCHAR(30) | NO | 'PASS_FAIL'| NO | NO | - | NO | IN ('PASS_FAIL','NUMERIC','TEXT','MULTIPLE_CHOICE','PHOTO_REQUIRED') | NO | NO | NO | DOCUMENTED |
| `inspection_checklist_items` | `is_mandatory` | BOOLEAN | NO | TRUE | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `inspection_checklist_items` | `sort_order` | INT | NO | 0 | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `inspection_checklist_items` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `inspection_findings` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `inspection_findings` | `inspection_id` | BIGINT | NO | - | NO | YES | `inspections(id)` | NO | - | Indirect | NO | NO | DOCUMENTED |
| `inspection_findings` | `checklist_item_id` | BIGINT | NO | - | NO | YES | `inspection_checklist_items(id)`| NO | - | NO | NO | NO | DOCUMENTED |
| `inspection_findings` | `result` | VARCHAR(30) | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `inspection_findings` | `measured_value` | VARCHAR(100) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `inspection_findings` | `remarks` | TEXT | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `inspection_findings` | `is_violation` | BOOLEAN | NO | FALSE | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `inspection_findings` | `photo_evidence_count`| INT | NO | 0 | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `inspection_findings` | `recorded_by` | BIGINT | YES | - | NO | YES | `users(id)` | NO | - | NO | NO | YES | DOCUMENTED |
| `inspection_findings` | `recorded_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `violation_actions` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `violation_actions` | `uuid` | UUID | NO | uuid_generate_v4() | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `violation_actions` | `violation_id` | BIGINT | NO | - | NO | YES | `violations(id)` | NO | - | Indirect | NO | NO | DOCUMENTED |
| `violation_actions` | `action_type` | VARCHAR(50) | NO | - | NO | NO | - | NO | IN ('SHOW_CAUSE_NOTICE','STOP_WORK_ORDER','SEALING_ORDER','MONETARY_FINE','PROSECUTION') | NO | NO | NO | DOCUMENTED |
| `violation_actions` | `notice_reference_number`| VARCHAR(100)| YES | - | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `violation_actions` | `penalty_amount` | NUMERIC(12,2)| NO | 0.00 | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `violation_actions` | `issued_date` | DATE | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `violation_actions` | `compliance_deadline` | DATE | NO | - | NO | NO | - | NO | >= issued_date | NO | NO | NO | DOCUMENTED |
| `violation_actions` | `status` | VARCHAR(30) | NO | 'ISSUED' | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `violation_actions` | `authorized_by_user_id`| BIGINT | YES | - | NO | YES | `users(id)` | NO | - | NO | NO | YES | DOCUMENTED |
| `violation_actions` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `violation_actions` | `updated_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `corrective_actions` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `corrective_actions` | `uuid` | UUID | NO | uuid_generate_v4() | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `corrective_actions` | `violation_id` | BIGINT | NO | - | NO | YES | `violations(id)` | NO | - | Indirect | NO | NO | DOCUMENTED |
| `corrective_actions` | `submission_date` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `corrective_actions` | `description_of_work` | TEXT | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `corrective_actions` | `evidence_document_id` | BIGINT | YES | - | NO | YES | `documents(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `corrective_actions` | `re_inspection_id` | BIGINT | YES | NULL | NO | YES | `inspections(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `corrective_actions` | `verification_status` | VARCHAR(30) | NO | 'PENDING_REVIEW'| NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `corrective_actions` | `verified_by` | BIGINT | YES | NULL | NO | YES | `users(id)` | NO | - | NO | NO | YES | DOCUMENTED |
| `corrective_actions` | `verified_at` | TIMESTAMPTZ | YES | NULL | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `corrective_actions` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `corrective_actions` | `updated_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_dispatches` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `incident_dispatches` | `uuid` | UUID | NO | uuid_generate_v4() | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `incident_dispatches` | `org_id` | BIGINT | NO | - | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | DOCUMENTED |
| `incident_dispatches` | `incident_id` | BIGINT | NO | - | NO | YES | `incidents(id)` | NO | - | Indirect | NO | NO | DOCUMENTED |
| `incident_dispatches` | `dispatch_number` | VARCHAR(100) | NO | - | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `incident_dispatches` | `station_id` | BIGINT | NO | - | NO | YES | `fire_stations(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_dispatches` | `alarm_level` | INT | NO | 1 | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_dispatches` | `dispatched_by_user_id`| BIGINT | YES | - | NO | YES | `users(id)` | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_dispatches` | `dispatch_timestamp` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_dispatches` | `acknowledged_at` | TIMESTAMPTZ | YES | NULL | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_dispatches` | `turnout_time_seconds`| INT | YES | NULL | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_dispatches` | `status` | VARCHAR(30) | NO | 'DISPATCHED' | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_dispatches` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_vehicles` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `incident_vehicles` | `dispatch_id` | BIGINT | NO | - | NO | YES | `incident_dispatches(id)`| NO | - | Indirect | NO | NO | DOCUMENTED |
| `incident_vehicles` | `vehicle_id` | BIGINT | NO | - | NO | YES | `fire_vehicles(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_vehicles` | `assigned_role` | VARCHAR(50) | NO | 'ATTACK_ENGINE' | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_vehicles` | `departed_station_at` | TIMESTAMPTZ | YES | - | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_vehicles` | `arrived_scene_at` | TIMESTAMPTZ | YES | - | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_vehicles` | `left_scene_at` | TIMESTAMPTZ | YES | - | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_vehicles` | `back_in_service_at` | TIMESTAMPTZ | YES | - | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_vehicles` | `travel_time_seconds`| INT | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_vehicles` | `water_discharged_liters`| NUMERIC(10,2)| NO | 0.00 | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_vehicles` | `foam_discharged_liters` | NUMERIC(8,2) | NO | 0.00 | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_crews` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `incident_crews` | `incident_vehicle_id` | BIGINT | NO | - | NO | YES | `incident_vehicles(id)`| NO | - | Indirect | NO | NO | DOCUMENTED |
| `incident_crews` | `user_id` | BIGINT | NO | - | NO | YES | `users(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_crews` | `crew_position` | VARCHAR(50) | NO | - | NO | NO | - | NO | IN ('INCIDENT_COMMANDER','DRIVER_OPERATOR','NOZZLEMAN','SEARCH_RESCUE','HYDRANT_MAN') | NO | NO | NO | DOCUMENTED |
| `incident_crews` | `scba_entry_time` | TIMESTAMPTZ | YES | NULL | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_crews` | `scba_exit_time` | TIMESTAMPTZ | YES | NULL | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_crews` | `initial_pressure_bar`| INT | YES | NULL | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_crews` | `exit_pressure_bar` | INT | YES | NULL | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_crews` | `safety_status` | VARCHAR(30) | NO | 'ACTIVE' | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_timeline` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `incident_timeline` | `incident_id` | BIGINT | NO | - | NO | YES | `incidents(id)` | NO | - | Indirect | NO | NO | DOCUMENTED |
| `incident_timeline` | `event_timestamp` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_timeline` | `event_code` | VARCHAR(50) | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_timeline` | `event_description` | TEXT | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_timeline` | `source` | VARCHAR(30) | NO | 'CAD_SYSTEM' | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_timeline` | `logged_by_user_id` | BIGINT | YES | - | NO | YES | `users(id)` | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_timeline` | `telemetry_coordinates`| GEOMETRY(Point, 4326)| YES | NULL | NO | NO | - | NO | SRID 4326 | NO | YES | NO | DOCUMENTED |
| `incident_casualties` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `incident_casualties` | `uuid` | UUID | NO | uuid_generate_v4() | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `incident_casualties` | `incident_id` | BIGINT | NO | - | NO | YES | `incidents(id)` | NO | - | Indirect | NO | NO | DOCUMENTED |
| `incident_casualties` | `casualty_type` | VARCHAR(30) | NO | - | NO | NO | - | NO | IN ('CIVILIAN','FIREFIGHTER','POLICE','PARAMEDIC') | NO | NO | NO | DOCUMENTED |
| `incident_casualties` | `triage_category` | VARCHAR(20) | NO | - | NO | NO | - | NO | IN ('IMMEDIATE_RED','DELAYED_YELLOW','MINOR_GREEN','DECEASED_BLACK') | NO | NO | NO | DOCUMENTED |
| `incident_casualties` | `full_name` | VARCHAR(150) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_casualties` | `age` | INT | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_casualties` | `gender` | VARCHAR(20) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_casualties` | `injury_nature` | TEXT | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_casualties` | `hospital_name` | VARCHAR(200) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_casualties` | `ambulance_id` | VARCHAR(50) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_casualties` | `recorded_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_reports` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `incident_reports` | `uuid` | UUID | NO | uuid_generate_v4() | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `incident_reports` | `incident_id` | BIGINT | NO | - | NO | YES | `incidents(id)` | YES | 1:1 relationship | Indirect | NO | NO | DOCUMENTED |
| `incident_reports` | `report_number` | VARCHAR(100) | NO | - | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `incident_reports` | `point_of_origin` | TEXT | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_reports` | `probable_cause` | VARCHAR(100) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_reports` | `estimated_loss_amount`| NUMERIC(14,2)| YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_reports` | `estimated_saved_amount`| NUMERIC(14,2)| YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_reports` | `building_owner_statement`| TEXT | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_reports` | `investigating_officer_id`| BIGINT | YES | - | NO | YES | `users(id)` | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_reports` | `approved_by_cfo_id` | BIGINT | YES | - | NO | YES | `users(id)` | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_reports` | `approval_date` | TIMESTAMPTZ | YES | NULL | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_reports` | `document_id` | BIGINT | YES | - | NO | YES | `documents(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `incident_reports` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `incident_reports` | `updated_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `vehicle_location_history` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `vehicle_location_history` | `vehicle_id` | BIGINT | NO | - | NO | YES | `fire_vehicles(id)` | NO | - | Indirect | NO | NO | DOCUMENTED |
| `vehicle_location_history` | `recorded_at` | TIMESTAMPTZ | NO | - | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `vehicle_location_history` | `location` | GEOMETRY(Point, 4326)| NO| - | NO | NO | - | NO | SRID 4326 | NO | YES | NO | DOCUMENTED |
| `vehicle_location_history` | `speed_kmh` | NUMERIC(5,2) | YES | - | NO | NO | - | NO | >= 0 | NO | NO | NO | DOCUMENTED |
| `vehicle_location_history` | `heading_degrees` | NUMERIC(5,2) | YES | - | NO | NO | - | NO | >= 0 AND <= 360 | NO | NO | NO | DOCUMENTED |
| `vehicle_location_history` | `ignition_on` | BOOLEAN | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `vehicle_location_history` | `battery_voltage` | NUMERIC(4,2) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `vehicle_location_history` | `incident_id` | BIGINT | YES | NULL | NO | YES | `incidents(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `vehicle_crew` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `vehicle_crew` | `vehicle_id` | BIGINT | NO | - | NO | YES | `fire_vehicles(id)` | NO | - | Indirect | NO | NO | DOCUMENTED |
| `vehicle_crew` | `user_id` | BIGINT | NO | - | NO | YES | `users(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `vehicle_crew` | `shift_date` | DATE | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `vehicle_crew` | `shift_code` | VARCHAR(20) | NO | - | NO | NO | - | NO | IN ('MORNING','EVENING','NIGHT','GENERAL') | NO | NO | NO | DOCUMENTED |
| `vehicle_crew` | `duty_role` | VARCHAR(50) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `vehicle_crew` | `assigned_by_user_id` | BIGINT | YES | - | NO | YES | `users(id)` | NO | - | NO | NO | YES | DOCUMENTED |
| `vehicle_crew` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `vehicle_fuel_logs` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `vehicle_fuel_logs` | `uuid` | UUID | NO | uuid_generate_v4() | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `vehicle_fuel_logs` | `vehicle_id` | BIGINT | NO | - | NO | YES | `fire_vehicles(id)` | NO | - | Indirect | NO | NO | DOCUMENTED |
| `vehicle_fuel_logs` | `fuel_type` | VARCHAR(30) | NO | 'DIESEL' | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `vehicle_fuel_logs` | `liters_filled` | NUMERIC(8,2) | NO | - | NO | NO | - | NO | > 0 | NO | NO | NO | DOCUMENTED |
| `vehicle_fuel_logs` | `cost_total` | NUMERIC(10,2)| NO | - | NO | NO | - | NO | >= 0 | NO | NO | NO | DOCUMENTED |
| `vehicle_fuel_logs` | `odometer_reading_km`| INT | NO | - | NO | NO | - | NO | >= 0 | NO | NO | NO | DOCUMENTED |
| `vehicle_fuel_logs` | `fuel_slip_number` | VARCHAR(50) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `vehicle_fuel_logs` | `filled_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `vehicle_fuel_logs` | `logged_by_user_id` | BIGINT | YES | - | NO | YES | `users(id)` | NO | - | NO | NO | YES | DOCUMENTED |
| `vehicle_fuel_logs` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `vehicle_fitness` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `vehicle_fitness` | `uuid` | UUID | NO | uuid_generate_v4() | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `vehicle_fitness` | `vehicle_id` | BIGINT | NO | - | NO | YES | `fire_vehicles(id)` | NO | - | Indirect | NO | NO | DOCUMENTED |
| `vehicle_fitness` | `certificate_number` | VARCHAR(100) | NO | - | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `vehicle_fitness` | `testing_agency` | VARCHAR(150) | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `vehicle_fitness` | `passed_test` | BOOLEAN | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `vehicle_fitness` | `issue_date` | DATE | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `vehicle_fitness` | `valid_until` | DATE | NO | - | NO | NO | - | NO | >= issue_date | NO | NO | NO | DOCUMENTED |
| `vehicle_fitness` | `inspection_notes` | TEXT | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `vehicle_fitness` | `certificate_doc_id` | BIGINT | YES | - | NO | YES | `documents(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `vehicle_fitness` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `vehicle_fitness` | `updated_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `warehouses` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `warehouses` | `uuid` | UUID | NO | uuid_generate_v4() | NO | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `warehouses` | `org_id` | BIGINT | NO | - | NO | YES | `organizations(id)` | NO | - | YES | NO | NO | DOCUMENTED |
| `warehouses` | `station_id` | BIGINT | YES | NULL | NO | YES | `fire_stations(id)` | NO | - | NO | NO | NO | DOCUMENTED |
| `warehouses` | `warehouse_code` | VARCHAR(50) | NO | - | NO | NO | - | NO | UNIQUE with org_id | NO | NO | NO | DOCUMENTED |
| `warehouses` | `warehouse_name` | VARCHAR(150) | NO | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `warehouses` | `warehouse_type` | VARCHAR(50) | NO | - | NO | NO | - | NO | IN ('CENTRAL_DEPOT','STATION_STORE','WORKSHOP_STORE','MOBILE_STORE') | NO | NO | NO | DOCUMENTED |
| `warehouses` | `address` | TEXT | YES | - | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `warehouses` | `supervisor_user_id` | BIGINT | YES | - | NO | YES | `users(id)` | NO | - | NO | NO | YES | DOCUMENTED |
| `warehouses` | `is_active` | BOOLEAN | NO | TRUE | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `warehouses` | `created_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `warehouses` | `updated_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `inventory_stock` | `id` | BIGSERIAL | NO | nextval() | YES | NO | - | YES | - | NO | NO | NO | DOCUMENTED |
| `inventory_stock` | `warehouse_id` | BIGINT | NO | - | NO | YES | `warehouses(id)` | NO | ON DELETE RESTRICT | Indirect | NO | NO | DOCUMENTED |
| `inventory_stock` | `item_id` | BIGINT | NO | - | NO | YES | `inventory_items(id)` | NO | ON DELETE RESTRICT | NO | NO | NO | DOCUMENTED |
| `inventory_stock` | `current_quantity` | INT | NO | 0 | NO | NO | - | NO | >= 0 | NO | NO | NO | DOCUMENTED |
| `inventory_stock` | `reserved_quantity` | INT | NO | 0 | NO | NO | - | NO | >= 0 | NO | NO | NO | DOCUMENTED |
| `inventory_stock` | `available_quantity`| INT | NO | GENERATED STORED | NO | NO | - | NO | (current - reserved) | NO | NO | NO | DOCUMENTED |
| `inventory_stock` | `batch_lot_number` | VARCHAR(50) | YES | NULL | NO | NO | - | NO | UNIQUE composite | NO | NO | NO | DOCUMENTED |
| `inventory_stock` | `expiry_date` | DATE | YES | NULL | NO | NO | - | NO | - | NO | NO | NO | DOCUMENTED |
| `inventory_stock` | `last_counted_at` | TIMESTAMPTZ | YES | - | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
| `inventory_stock` | `updated_at` | TIMESTAMPTZ | NO | NOW() | NO | NO | - | NO | - | NO | NO | YES | DOCUMENTED |
