-- =============================================================================
-- Test: 01_verify_extensions_and_schemas.sql
-- Description: Validates required PostgreSQL extensions and schema availability
-- =============================================================================

DO $$
DECLARE
    v_missing_ext TEXT;
BEGIN
    -- Verify Extensions
    SELECT extname INTO v_missing_ext
    FROM (VALUES ('postgis'), ('uuid-ossp'), ('btree_gist'), ('pgcrypto')) AS req(extname)
    WHERE NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = req.extname)
    LIMIT 1;

    IF v_missing_ext IS NOT NULL THEN
        RAISE EXCEPTION 'TEST FAILED: Required extension % is not installed', v_missing_ext;
    END IF;

    -- Verify Schema
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'firesphere') THEN
        RAISE EXCEPTION 'TEST FAILED: Target schema firesphere does not exist';
    END IF;

    RAISE NOTICE 'TEST PASSED: Core extensions and firesphere schema verified successfully.';
END $$;
