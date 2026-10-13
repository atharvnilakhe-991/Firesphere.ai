-- =============================================================================
-- Migration: V1.0.9__ai_finding_checklist_mapping.sql
-- Description: Phase 17 - Traceable AI Finding to Checklist Mapping: Source
--              Provenance (CV, VLM, CROSS_VALIDATED), Mapping Reason & Audit,
--              and Unique Link Idempotency Constraint.
-- Author: Senior Database Architect, PostgreSQL/PostGIS Engineer
-- =============================================================================

ALTER TABLE firesphere.checklist_finding_links
    ADD COLUMN IF NOT EXISTS source_provenance VARCHAR(50) NOT NULL DEFAULT 'CROSS_VALIDATED',
    ADD COLUMN IF NOT EXISTS mapping_reason TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_finding_link_resp_finding
    ON firesphere.checklist_finding_links(checklist_response_id, finding_id);
