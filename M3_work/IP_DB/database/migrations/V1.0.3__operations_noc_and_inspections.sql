-- =============================================================================
-- Migration: V1.0.3__operations_noc_and_inspections.sql
-- Description: Life-safety building sub-entities, Fire NOC workflows, and inspection audit tables
-- Author: Member 3 (Database, Infrastructure & DevOps)
-- =============================================================================

-- 1. Building Contacts (Section 6.1)
CREATE TABLE IF NOT EXISTS firesphere.building_contacts (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    building_id BIGINT NOT NULL REFERENCES firesphere.buildings(id) ON DELETE CASCADE,
    contact_name VARCHAR(150) NOT NULL,
    designation VARCHAR(100), -- 'FACILITY_MANAGER', 'CHIEF_SECURITY_OFFICER', etc.
    phone_primary VARCHAR(50) NOT NULL,
    phone_secondary VARCHAR(50),
    email VARCHAR(150),
    is_primary_emergency_contact BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_building_contacts_building ON firesphere.building_contacts(building_id);
CREATE INDEX idx_building_contacts_org ON firesphere.building_contacts(org_id);

-- 2. Building Documents (Section 6.2 - S3 Object Key Storage)
CREATE TABLE IF NOT EXISTS firesphere.building_documents (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    building_id BIGINT NOT NULL REFERENCES firesphere.buildings(id) ON DELETE CASCADE,
    document_type VARCHAR(100) NOT NULL, -- 'FLOOR_PLAN', 'FIRE_SAFETY_SCHEMATIC', 'SANCTION_PLAN', etc.
    document_title VARCHAR(200) NOT NULL,
    file_path TEXT NOT NULL, -- S3/MinIO Object Key
    file_size_bytes BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_hash_sha256 VARCHAR(64) NOT NULL,
    uploaded_by BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_building_documents_building ON firesphere.building_documents(building_id);
CREATE INDEX idx_building_documents_org ON firesphere.building_documents(org_id);

-- 3. Building Fire Equipment (Section 6.3)
CREATE TABLE IF NOT EXISTS firesphere.building_fire_equipment (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    building_id BIGINT NOT NULL REFERENCES firesphere.buildings(id) ON DELETE CASCADE,
    equipment_type VARCHAR(100) NOT NULL, -- 'SPRINKLER', 'FIRE_EXTINGUISHER', 'HOSE_REEL', 'RISER', 'FIRE_ALARM'
    location_in_building VARCHAR(200),
    quantity INT NOT NULL DEFAULT 1,
    installation_date DATE,
    last_service_date DATE,
    next_service_due DATE,
    operational_status VARCHAR(50) NOT NULL DEFAULT 'FUNCTIONAL', -- 'FUNCTIONAL', 'DEFECTIVE', 'SERVICING_REQUIRED'
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_building_equipment_building ON firesphere.building_fire_equipment(building_id);

-- 4. Building Owners (Section 6.4)
CREATE TABLE IF NOT EXISTS firesphere.building_owners (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    building_id BIGINT NOT NULL REFERENCES firesphere.buildings(id) ON DELETE CASCADE,
    owner_type VARCHAR(50) NOT NULL DEFAULT 'INDIVIDUAL', -- 'INDIVIDUAL', 'CORPORATE', 'GOVERNMENT', 'SOCIETY'
    owner_name VARCHAR(200) NOT NULL,
    pan_tax_identifier VARCHAR(50),
    phone VARCHAR(50) NOT NULL,
    email VARCHAR(150),
    postal_address TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_building_owners_building ON firesphere.building_owners(building_id);

-- 5. Building Risk Profiles (Section 6.5)
CREATE TABLE IF NOT EXISTS firesphere.building_risk_profiles (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    building_id BIGINT NOT NULL UNIQUE REFERENCES firesphere.buildings(id) ON DELETE CASCADE,
    overall_risk_score NUMERIC(5,2) NOT NULL DEFAULT 50.00, -- 0.00 to 100.00
    structural_risk_score NUMERIC(5,2),
    occupancy_load_risk_score NUMERIC(5,2),
    hazard_materials_risk_score NUMERIC(5,2),
    last_calculated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    inspection_priority VARCHAR(50) NOT NULL DEFAULT 'NORMAL', -- 'LOW', 'NORMAL', 'HIGH', 'CRITICAL'
    ai_risk_assessment_summary TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_building_risk_priority ON firesphere.building_risk_profiles(inspection_priority);

-- 6. NOC Application Categories (Legacy Verified Table 34)
CREATE TABLE IF NOT EXISTS firesphere.noc_application_categories (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    category_code VARCHAR(50) NOT NULL UNIQUE,
    category_name VARCHAR(150) NOT NULL,
    standard_fee NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    validity_years INT NOT NULL DEFAULT 1,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. NOC Applications (Core NOC Entity)
CREATE TABLE IF NOT EXISTS firesphere.noc_applications (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    application_number VARCHAR(100) NOT NULL UNIQUE,
    building_id BIGINT NOT NULL REFERENCES firesphere.buildings(id) ON DELETE RESTRICT,
    category_id BIGINT NOT NULL REFERENCES firesphere.noc_application_categories(id) ON DELETE RESTRICT,
    applicant_name VARCHAR(150) NOT NULL,
    applicant_phone VARCHAR(50) NOT NULL,
    applicant_email VARCHAR(150),
    current_status VARCHAR(50) NOT NULL DEFAULT 'SUBMITTED', -- 'DRAFT', 'SUBMITTED', 'UNDER_INSPECTION', 'APPROVED', 'REJECTED'
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP WITH TIME ZONE,
    approved_by BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_noc_apps_org ON firesphere.noc_applications(org_id);
CREATE INDEX idx_noc_apps_building ON firesphere.noc_applications(building_id);
CREATE INDEX idx_noc_apps_status ON firesphere.noc_applications(current_status);

-- 8. NOC Application Status History (Section 6.17 - Immutable Transition Trail)
CREATE TABLE IF NOT EXISTS firesphere.noc_application_status_history (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    application_id BIGINT NOT NULL REFERENCES firesphere.noc_applications(id) ON DELETE CASCADE,
    previous_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    transition_remarks TEXT,
    action_by_user_id BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL,
    transitioned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_noc_status_hist_app ON firesphere.noc_application_status_history(application_id);

-- 9. NOC Certificates (Section 6.18 - Issued Statutory Documents)
CREATE TABLE IF NOT EXISTS firesphere.noc_certificates (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    application_id BIGINT NOT NULL UNIQUE REFERENCES firesphere.noc_applications(id) ON DELETE RESTRICT,
    certificate_number VARCHAR(100) NOT NULL UNIQUE,
    issue_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    qr_code_token VARCHAR(255) NOT NULL UNIQUE,
    certificate_pdf_path TEXT, -- MinIO/S3 object key
    issuing_authority_id BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revocation_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_noc_certs_cert_no ON firesphere.noc_certificates(certificate_number);
CREATE INDEX idx_noc_certs_expiry ON firesphere.noc_certificates(expiry_date);

-- 10. NOC Renewals (Section 6.19 - Annual Renewal Workflows)
CREATE TABLE IF NOT EXISTS firesphere.noc_renewals (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    certificate_id BIGINT NOT NULL REFERENCES firesphere.noc_certificates(id) ON DELETE RESTRICT,
    renewal_application_number VARCHAR(100) NOT NULL UNIQUE,
    renewal_status VARCHAR(50) NOT NULL DEFAULT 'REQUESTED', -- 'REQUESTED', 'INSPECTING', 'APPROVED', 'REJECTED'
    previous_expiry_date DATE NOT NULL,
    new_expiry_date DATE,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_noc_renewals_cert ON firesphere.noc_renewals(certificate_id);

-- 11. Inspection Checklists (Legacy Verified Table 26)
CREATE TABLE IF NOT EXISTS firesphere.inspection_checklists (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT REFERENCES firesphere.organizations(id) ON DELETE CASCADE,
    checklist_code VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    occupancy_type VARCHAR(100) NOT NULL,
    nbc_reference_clause VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 12. Inspection Checklist Items (Section 6.13)
CREATE TABLE IF NOT EXISTS firesphere.inspection_checklist_items (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    checklist_id BIGINT NOT NULL REFERENCES firesphere.inspection_checklists(id) ON DELETE CASCADE,
    item_sequence INT NOT NULL,
    parameter_description TEXT NOT NULL,
    requirement_type VARCHAR(50) NOT NULL DEFAULT 'MANDATORY', -- 'MANDATORY', 'RECOMMENDED', 'OPTIONAL'
    verification_method VARCHAR(50) DEFAULT 'PHYSICAL_INSPECTION',
    penalty_points INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_checklist_items_checklist ON firesphere.inspection_checklist_items(checklist_id);

-- 13. Inspections (Core Field Inspection Record)
CREATE TABLE IF NOT EXISTS firesphere.inspections (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    inspection_number VARCHAR(100) NOT NULL UNIQUE,
    building_id BIGINT NOT NULL REFERENCES firesphere.buildings(id) ON DELETE RESTRICT,
    noc_application_id BIGINT REFERENCES firesphere.noc_applications(id) ON DELETE SET NULL,
    inspection_type VARCHAR(50) NOT NULL DEFAULT 'ROUTINE', -- 'ROUTINE', 'NOC_VERIFICATION', 'COMPLAINT', 'SURPRISE'
    inspection_status VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED', -- 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'
    overall_compliance_status VARCHAR(50) DEFAULT 'PENDING',    -- 'PENDING', 'COMPLIANT', 'NON_COMPLIANT'
    scheduled_date DATE NOT NULL,
    conducted_at TIMESTAMP WITH TIME ZONE,
    summary_report TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inspections_org ON firesphere.inspections(org_id);
CREATE INDEX idx_inspections_building ON firesphere.inspections(building_id);
CREATE INDEX idx_inspections_status ON firesphere.inspections(inspection_status);

-- 14. Inspection Assignments (Section 6.12)
CREATE TABLE IF NOT EXISTS firesphere.inspection_assignments (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    inspection_id BIGINT NOT NULL REFERENCES firesphere.inspections(id) ON DELETE CASCADE,
    officer_user_id BIGINT NOT NULL REFERENCES firesphere.users(id) ON DELETE RESTRICT,
    assignment_role VARCHAR(50) NOT NULL DEFAULT 'LEAD_INSPECTOR', -- 'LEAD_INSPECTOR', 'ASSISTANT_INSPECTOR'
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_insp_assignments_insp ON firesphere.inspection_assignments(inspection_id);
CREATE INDEX idx_insp_assignments_user ON firesphere.inspection_assignments(officer_user_id);

-- 15. Inspection Findings (Section 6.14)
CREATE TABLE IF NOT EXISTS firesphere.inspection_findings (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    inspection_id BIGINT NOT NULL REFERENCES firesphere.inspections(id) ON DELETE CASCADE,
    checklist_item_id BIGINT REFERENCES firesphere.inspection_checklist_items(id) ON DELETE SET NULL,
    is_compliant BOOLEAN NOT NULL DEFAULT FALSE,
    severity_grade VARCHAR(50) NOT NULL DEFAULT 'MODERATE', -- 'MINOR', 'MODERATE', 'MAJOR', 'LIFE_THREATENING'
    finding_description TEXT NOT NULL,
    nbc_code_violated VARCHAR(100),
    photo_evidence_s3_key TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_insp_findings_insp ON firesphere.inspection_findings(inspection_id);

-- 16. Corrective Actions (Section 6.15)
CREATE TABLE IF NOT EXISTS firesphere.corrective_actions (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    finding_id BIGINT NOT NULL REFERENCES firesphere.inspection_findings(id) ON DELETE CASCADE,
    remediation_required TEXT NOT NULL,
    deadline_date DATE NOT NULL,
    action_status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'IN_PROGRESS', 'RESOLVED', 'DEFAULTED'
    rectification_verified_at TIMESTAMP WITH TIME ZONE,
    verified_by_user_id BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_corr_actions_finding ON firesphere.corrective_actions(finding_id);
CREATE INDEX idx_corr_actions_status ON firesphere.corrective_actions(action_status);

-- 17. Violations (Core Violation Legal Registry)
CREATE TABLE IF NOT EXISTS firesphere.violations (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    violation_number VARCHAR(100) NOT NULL UNIQUE,
    building_id BIGINT NOT NULL REFERENCES firesphere.buildings(id) ON DELETE RESTRICT,
    inspection_id BIGINT REFERENCES firesphere.inspections(id) ON DELETE SET NULL,
    severity VARCHAR(50) NOT NULL DEFAULT 'MEDIUM', -- 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',     -- 'OPEN', 'RECTIFIED', 'COMPOUNDED', 'ESCALATED'
    notice_issued_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_violations_building ON firesphere.violations(building_id);
CREATE INDEX idx_violations_org ON firesphere.violations(org_id);

-- 18. Violation Actions (Section 6.16)
CREATE TABLE IF NOT EXISTS firesphere.violation_actions (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    violation_id BIGINT NOT NULL REFERENCES firesphere.violations(id) ON DELETE CASCADE,
    action_type VARCHAR(100) NOT NULL, -- 'SHOW_CAUSE_NOTICE', 'COMPOUNDING_FINE', 'DISCONNECTION_POWER_WATER', 'SEAL_BUILDING'
    penalty_amount NUMERIC(12,2) DEFAULT 0.00,
    action_order_number VARCHAR(100),
    issued_date DATE NOT NULL,
    compliance_due_date DATE,
    served_to_name VARCHAR(150),
    is_executed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_violation_actions_violation ON firesphere.violation_actions(violation_id);

-- Attach updated_at triggers
CREATE TRIGGER trg_building_contacts_updated_at BEFORE UPDATE ON firesphere.building_contacts
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();

CREATE TRIGGER trg_noc_applications_updated_at BEFORE UPDATE ON firesphere.noc_applications
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();

CREATE TRIGGER trg_inspections_updated_at BEFORE UPDATE ON firesphere.inspections
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();

CREATE TRIGGER trg_corrective_actions_updated_at BEFORE UPDATE ON firesphere.corrective_actions
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();

CREATE TRIGGER trg_violations_updated_at BEFORE UPDATE ON firesphere.violations
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();
