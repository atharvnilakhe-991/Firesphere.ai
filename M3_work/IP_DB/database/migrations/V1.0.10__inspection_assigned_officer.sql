-- =============================================================================
-- Migration: V1.0.10__inspection_assigned_officer.sql
-- Description: Phase 18 - Persist assigned officer on inspection record for
--              strict authorization and review enforcement.
-- Author: Member 3 (Database, Infrastructure & DevOps) & Member 2 Integration
-- =============================================================================

ALTER TABLE firesphere.inspections
    ADD COLUMN IF NOT EXISTS assigned_officer UUID;

CREATE INDEX IF NOT EXISTS idx_inspections_assigned_officer
    ON firesphere.inspections(assigned_officer);
