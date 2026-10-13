-- =============================================================================
-- Migration: V1.0.7__create_inspection_evidence_and_ai_tables.sql
-- Description: Phase 3B - Inspection Photographic Evidence, AI Analysis Runs,
--              Advisory Findings, Officer Reviews, and Append-Only Finding Audits.
-- Author: Senior Database Architect, PostgreSQL/PostGIS Engineer
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Inspection Field Evidence (On-site Photographic Assets)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS firesphere.inspection_evidence (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    inspection_id BIGINT NOT NULL REFERENCES firesphere.inspections(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    storage_bucket VARCHAR(100) NOT NULL DEFAULT 'firesphere-evidence',
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    sha256_hash VARCHAR(64) NOT NULL,
    capture_timestamp TIMESTAMP WITH TIME ZONE,
    gps_latitude NUMERIC(10, 7),
    gps_longitude NUMERIC(10, 7),
    captured_by_user_id BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_evidence_content_type CHECK (
        content_type IN ('image/jpeg', 'image/png', 'image/webp')
    ),
    CONSTRAINT chk_evidence_file_size CHECK (
        file_size_bytes > 0 AND file_size_bytes <= 10485760
    ),
    CONSTRAINT chk_evidence_status CHECK (
        status IN ('ACTIVE', 'ARCHIVED', 'CORRUPTED', 'DELETED')
    )
);

CREATE INDEX idx_inspection_evidence_org ON firesphere.inspection_evidence(org_id);
CREATE INDEX idx_inspection_evidence_insp ON firesphere.inspection_evidence(inspection_id);
CREATE INDEX idx_inspection_evidence_uuid ON firesphere.inspection_evidence(uuid);
CREATE INDEX idx_inspection_evidence_hash ON firesphere.inspection_evidence(org_id, sha256_hash);

-- -----------------------------------------------------------------------------
-- 2. AI Analysis Runs (Inference Execution Lifecycle & Model Provenance)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS firesphere.ai_analysis_runs (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    inspection_id BIGINT NOT NULL REFERENCES firesphere.inspections(id) ON DELETE CASCADE,
    job_id UUID UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PROCESSING',
    failure_reason TEXT,
    model_name VARCHAR(100),
    model_version VARCHAR(50),
    model_provider VARCHAR(50),
    execution_time_ms INT,
    requested_by_user_id BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_ai_run_status CHECK (
        status IN ('PROCESSING', 'COMPLETED', 'FAILED', 'TIMED_OUT')
    ),
    CONSTRAINT chk_ai_run_exec_time CHECK (
        execution_time_ms IS NULL OR execution_time_ms >= 0
    )
);

CREATE INDEX idx_ai_analysis_runs_org ON firesphere.ai_analysis_runs(org_id);
CREATE INDEX idx_ai_analysis_runs_insp ON firesphere.ai_analysis_runs(inspection_id);
CREATE INDEX idx_ai_analysis_runs_uuid ON firesphere.ai_analysis_runs(uuid);
CREATE INDEX idx_ai_analysis_runs_status ON firesphere.ai_analysis_runs(status);

-- -----------------------------------------------------------------------------
-- 3. AI Findings (Model Observations Linked to Evidence & Statutory Advisory Guard)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS firesphere.ai_findings (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    run_id BIGINT NOT NULL REFERENCES firesphere.ai_analysis_runs(id) ON DELETE CASCADE,
    evidence_id BIGINT REFERENCES firesphere.inspection_evidence(id) ON DELETE RESTRICT,
    finding_type VARCHAR(100) NOT NULL,
    severity VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    confidence NUMERIC(4, 3) NOT NULL,
    bounding_box JSONB,
    description TEXT,
    verification_guidance TEXT,
    is_advisory BOOLEAN NOT NULL DEFAULT TRUE,
    review_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_ai_finding_advisory CHECK (
        is_advisory = TRUE
    ),
    CONSTRAINT chk_ai_finding_confidence CHECK (
        confidence >= 0.000 AND confidence <= 1.000
    ),
    CONSTRAINT chk_ai_finding_severity CHECK (
        severity IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),
    CONSTRAINT chk_ai_finding_review_status CHECK (
        review_status IN ('PENDING_REVIEW', 'CONFIRMED', 'DISMISSED', 'MODIFIED')
    )
);

CREATE INDEX idx_ai_findings_run ON firesphere.ai_findings(run_id);
CREATE INDEX idx_ai_findings_evidence ON firesphere.ai_findings(evidence_id);
CREATE INDEX idx_ai_findings_uuid ON firesphere.ai_findings(uuid);
CREATE INDEX idx_ai_findings_review ON firesphere.ai_findings(review_status);

-- -----------------------------------------------------------------------------
-- 4. AI Officer Reviews (Statutory Human Determination Records)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS firesphere.ai_officer_reviews (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    finding_id BIGINT NOT NULL REFERENCES firesphere.ai_findings(id) ON DELETE CASCADE,
    officer_id BIGINT NOT NULL REFERENCES firesphere.users(id) ON DELETE RESTRICT,
    review_status VARCHAR(50) NOT NULL,
    officer_notes TEXT,
    hazard_verified BOOLEAN NOT NULL DEFAULT FALSE,
    reviewed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_officer_review_status CHECK (
        review_status IN ('CONFIRMED', 'DISMISSED', 'MODIFIED')
    )
);

CREATE INDEX idx_ai_officer_reviews_finding ON firesphere.ai_officer_reviews(finding_id);
CREATE INDEX idx_ai_officer_reviews_officer ON firesphere.ai_officer_reviews(officer_id);
CREATE INDEX idx_ai_officer_reviews_uuid ON firesphere.ai_officer_reviews(uuid);

-- -----------------------------------------------------------------------------
-- 5. AI Finding Audits (Append-Only Forensic Mutation Trail)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS firesphere.ai_finding_audits (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    finding_id BIGINT NOT NULL REFERENCES firesphere.ai_findings(id) ON DELETE CASCADE,
    correlation_id UUID,
    action VARCHAR(50) NOT NULL,
    previous_state VARCHAR(50),
    new_state VARCHAR(50) NOT NULL,
    performed_by_user_id BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL,
    performed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_finding_audits_finding ON firesphere.ai_finding_audits(finding_id);
CREATE INDEX idx_ai_finding_audits_corr ON firesphere.ai_finding_audits(correlation_id);
CREATE INDEX idx_ai_finding_audits_uuid ON firesphere.ai_finding_audits(uuid);

-- -----------------------------------------------------------------------------
-- 6. Row-Level Security (RLS) Policies (Consistent with V1.0.5)
-- -----------------------------------------------------------------------------
ALTER TABLE firesphere.inspection_evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE firesphere.ai_analysis_runs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_policy ON firesphere.inspection_evidence;
CREATE POLICY tenant_isolation_policy ON firesphere.inspection_evidence
    FOR ALL
    USING (
        org_id = firesphere.current_org_id()
        OR firesphere.is_super_admin()
    )
    WITH CHECK (
        org_id = firesphere.current_org_id()
        OR firesphere.is_super_admin()
    );

DROP POLICY IF EXISTS tenant_isolation_policy ON firesphere.ai_analysis_runs;
CREATE POLICY tenant_isolation_policy ON firesphere.ai_analysis_runs
    FOR ALL
    USING (
        org_id = firesphere.current_org_id()
        OR firesphere.is_super_admin()
    )
    WITH CHECK (
        org_id = firesphere.current_org_id()
        OR firesphere.is_super_admin()
    );
