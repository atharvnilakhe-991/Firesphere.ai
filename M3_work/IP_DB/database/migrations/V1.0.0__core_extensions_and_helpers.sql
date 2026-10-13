-- =============================================================================
-- Migration: V1.0.0__core_extensions_and_helpers.sql
-- Description: Core PostgreSQL extensions, schema declaration, and utility functions
-- Author: Member 3 (Database, Infrastructure & DevOps)
-- =============================================================================

-- 1. Enable Core Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";
CREATE EXTENSION IF NOT EXISTS "btree_gist";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 2. Schema Declaration
CREATE SCHEMA IF NOT EXISTS firesphere;

-- 3. Utility Function: Automatically manage updated_at timestamps
CREATE OR REPLACE FUNCTION firesphere.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 4. Session Context Helper: Set Tenant and Super-Admin Context for RLS
CREATE OR REPLACE FUNCTION firesphere.set_tenant_context(
    p_org_id BIGINT,
    p_is_super_admin BOOLEAN DEFAULT FALSE
)
RETURNS VOID AS $$
BEGIN
    PERFORM set_config('app.current_org_id', p_org_id::TEXT, FALSE);
    PERFORM set_config('app.is_super_admin', p_is_super_admin::TEXT, FALSE);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 5. Helper Function: Get Current Tenant Context safely
CREATE OR REPLACE FUNCTION firesphere.current_org_id()
RETURNS BIGINT AS $$
DECLARE
    v_org_id TEXT;
BEGIN
    v_org_id := current_setting('app.current_org_id', TRUE);
    IF v_org_id IS NULL OR v_org_id = '' THEN
        RETURN NULL;
    END IF;
    RETURN v_org_id::BIGINT;
END;
$$ LANGUAGE plpgsql STABLE;
