-- =============================================================================
-- Migration: V1.0.8__inspection_checklist_domain_model.sql
-- Description: Phase 16 - Versioned Statutory Checklist Templates, Inspection
--              Checklist Instances, Officer Responses, Linked Photographic
--              Evidence, and AI Finding Advisory Links.
-- Author: Member 3 (Database, Infrastructure & DevOps) & Member 2 Integration
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Enhance Template Catalog (inspection_checklists & inspection_checklist_items)
-- -----------------------------------------------------------------------------
ALTER TABLE firesphere.inspection_checklists
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS inspection_type VARCHAR(50) NOT NULL DEFAULT 'ROUTINE',
    ADD COLUMN IF NOT EXISTS source_authority VARCHAR(100) DEFAULT 'NBC_2016_PART_4',
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_checklists_code_version
    ON firesphere.inspection_checklists(checklist_code, version);

ALTER TABLE firesphere.inspection_checklist_items
    ADD COLUMN IF NOT EXISTS item_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS item_title VARCHAR(200),
    ADD COLUMN IF NOT EXISTS evidence_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ai_category_tag VARCHAR(100),
    ADD COLUMN IF NOT EXISTS help_guidance TEXT;

CREATE INDEX IF NOT EXISTS idx_checklist_items_code
    ON firesphere.inspection_checklist_items(checklist_id, item_code);

-- -----------------------------------------------------------------------------
-- 2. Inspection Checklist Instances (Instantiated for an on-site inspection)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS firesphere.inspection_checklist_instances (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    inspection_id BIGINT NOT NULL REFERENCES firesphere.inspections(id) ON DELETE CASCADE,
    noc_application_id BIGINT REFERENCES firesphere.noc_applications(id) ON DELETE SET NULL,
    template_id BIGINT NOT NULL REFERENCES firesphere.inspection_checklists(id) ON DELETE RESTRICT,
    template_version INT NOT NULL DEFAULT 1,
    lifecycle_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    summary_notes TEXT,
    completed_by_officer_id BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_checklist_lifecycle_status CHECK (
        lifecycle_status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED')
    ),
    CONSTRAINT uq_checklist_inst_insp UNIQUE (inspection_id)
);

CREATE INDEX IF NOT EXISTS idx_insp_checklist_inst_insp ON firesphere.inspection_checklist_instances(inspection_id);
CREATE INDEX IF NOT EXISTS idx_insp_checklist_inst_org ON firesphere.inspection_checklist_instances(org_id);
CREATE INDEX IF NOT EXISTS idx_insp_checklist_inst_status ON firesphere.inspection_checklist_instances(lifecycle_status);
CREATE INDEX IF NOT EXISTS idx_insp_checklist_inst_uuid ON firesphere.inspection_checklist_instances(uuid);

-- -----------------------------------------------------------------------------
-- 3. Inspection Checklist Responses (Authoritative Human Officer Determinations)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS firesphere.inspection_checklist_responses (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    checklist_instance_id BIGINT NOT NULL REFERENCES firesphere.inspection_checklist_instances(id) ON DELETE CASCADE,
    template_item_id BIGINT NOT NULL REFERENCES firesphere.inspection_checklist_items(id) ON DELETE RESTRICT,
    item_code VARCHAR(50) NOT NULL,
    response_status VARCHAR(50) NOT NULL DEFAULT 'NOT_REVIEWED',
    officer_remarks TEXT,
    officer_id BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL,
    responded_at TIMESTAMP WITH TIME ZONE,
    revision_number INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_checklist_response_status CHECK (
        response_status IN ('COMPLIANT', 'NON_COMPLIANT', 'NOT_APPLICABLE', 'NEEDS_FURTHER_INSPECTION', 'NOT_REVIEWED')
    ),
    CONSTRAINT uq_checklist_resp_item UNIQUE (checklist_instance_id, template_item_id)
);

CREATE INDEX IF NOT EXISTS idx_checklist_responses_instance ON firesphere.inspection_checklist_responses(checklist_instance_id);
CREATE INDEX IF NOT EXISTS idx_checklist_responses_item ON firesphere.inspection_checklist_responses(template_item_id);
CREATE INDEX IF NOT EXISTS idx_checklist_responses_status ON firesphere.inspection_checklist_responses(response_status);
CREATE INDEX IF NOT EXISTS idx_checklist_responses_uuid ON firesphere.inspection_checklist_responses(uuid);

-- -----------------------------------------------------------------------------
-- 4. Checklist Response Evidence Link (Supporting Photographic Attachments)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS firesphere.checklist_response_evidence (
    id BIGSERIAL PRIMARY KEY,
    response_id BIGINT NOT NULL REFERENCES firesphere.inspection_checklist_responses(id) ON DELETE CASCADE,
    evidence_id BIGINT NOT NULL REFERENCES firesphere.inspection_evidence(id) ON DELETE CASCADE,
    attached_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_checklist_resp_ev_resp ON firesphere.checklist_response_evidence(response_id);
CREATE INDEX IF NOT EXISTS idx_checklist_resp_ev_ev ON firesphere.checklist_response_evidence(evidence_id);

-- -----------------------------------------------------------------------------
-- 5. Checklist AI Finding Links (Advisory Suggestion Bridge)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS firesphere.checklist_finding_links (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    checklist_response_id BIGINT NOT NULL REFERENCES firesphere.inspection_checklist_responses(id) ON DELETE CASCADE,
    finding_id BIGINT NOT NULL REFERENCES firesphere.ai_findings(id) ON DELETE CASCADE,
    suggested_compliance VARCHAR(50) NOT NULL DEFAULT 'NEEDS_FURTHER_INSPECTION',
    confidence NUMERIC(4, 3) NOT NULL,
    link_provenance VARCHAR(50) NOT NULL DEFAULT 'AI_SUGGESTION',
    link_status VARCHAR(50) NOT NULL DEFAULT 'SUGGESTED',
    review_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',
    officer_comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_finding_link_confidence CHECK (
        confidence >= 0.000 AND confidence <= 1.000
    ),
    CONSTRAINT chk_finding_link_provenance CHECK (
        link_provenance IN ('AI_SUGGESTION', 'OFFICER_CONFIRMED', 'MANUAL_ASSOCIATION')
    ),
    CONSTRAINT chk_finding_link_status CHECK (
        link_status IN ('SUGGESTED', 'ACCEPTED', 'DISMISSED', 'SUPERSEDED')
    ),
    CONSTRAINT chk_finding_link_review_status CHECK (
        review_status IN ('PENDING_REVIEW', 'CONFIRMED', 'DISMISSED')
    )
);

CREATE INDEX IF NOT EXISTS idx_checklist_finding_links_resp ON firesphere.checklist_finding_links(checklist_response_id);
CREATE INDEX IF NOT EXISTS idx_checklist_finding_links_finding ON firesphere.checklist_finding_links(finding_id);
CREATE INDEX IF NOT EXISTS idx_checklist_finding_links_uuid ON firesphere.checklist_finding_links(uuid);

-- -----------------------------------------------------------------------------
-- 6. Row-Level Security (RLS) Policies (Consistent with V1.0.5 & V1.0.7)
-- -----------------------------------------------------------------------------
ALTER TABLE firesphere.inspection_checklist_instances ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_policy ON firesphere.inspection_checklist_instances;
CREATE POLICY tenant_isolation_policy ON firesphere.inspection_checklist_instances
    FOR ALL
    USING (
        org_id = firesphere.current_org_id()
        OR firesphere.is_super_admin()
    )
    WITH CHECK (
        org_id = firesphere.current_org_id()
        OR firesphere.is_super_admin()
    );
