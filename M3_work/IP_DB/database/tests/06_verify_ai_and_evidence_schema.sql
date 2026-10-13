-- =============================================================================
-- Test Script: 06_verify_ai_and_evidence_schema.sql
-- Description: Verification script for Flyway V1.0.7 AI & Evidence schema additions.
-- =============================================================================

DO $$
DECLARE
    v_missing_tables TEXT[] := ARRAY[]::TEXT[];
    v_table TEXT;
    v_required_tables TEXT[] := ARRAY[
        'inspection_evidence',
        'ai_analysis_runs',
        'ai_findings',
        'ai_officer_reviews',
        'ai_finding_audits'
    ];
BEGIN
    RAISE NOTICE '=====================================================';
    RAISE NOTICE 'Executing 06: Verification of AI & Evidence Schema';
    RAISE NOTICE '=====================================================';

    FOREACH v_table IN ARRAY v_required_tables LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.tables 
            WHERE table_schema = 'firesphere' AND table_name = v_table
        ) THEN
            v_missing_tables := array_append(v_missing_tables, v_table);
        END IF;
    END LOOP;

    IF array_length(v_missing_tables, 1) > 0 THEN
        RAISE EXCEPTION 'TEST FAILED: Missing tables in schema firesphere: %', array_to_string(v_missing_tables, ', ');
    END IF;

    RAISE NOTICE 'SUCCESS: All 5 tables exist in schema firesphere.';

    -- Verify Statutory Advisory Constraint
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc 
            ON cc.constraint_name = tc.constraint_name 
            AND cc.constraint_schema = tc.constraint_schema
        WHERE tc.table_schema = 'firesphere' 
          AND tc.table_name = 'ai_findings' 
          AND tc.constraint_name = 'chk_ai_finding_advisory'
    ) THEN
        RAISE EXCEPTION 'TEST FAILED: Missing chk_ai_finding_advisory constraint on firesphere.ai_findings';
    END IF;

    RAISE NOTICE 'SUCCESS: Statutory Advisory Constraint verified on firesphere.ai_findings.';

    -- Verify Row Level Security is enabled
    IF NOT EXISTS (
        SELECT 1 FROM pg_tables
        WHERE schemaname = 'firesphere' 
          AND tablename = 'inspection_evidence' 
          AND rowsecurity = TRUE
    ) THEN
        RAISE EXCEPTION 'TEST FAILED: Row Level Security is NOT enabled on firesphere.inspection_evidence';
    END IF;

    RAISE NOTICE 'SUCCESS: Row-Level Security verified on firesphere.inspection_evidence.';
    RAISE NOTICE '=====================================================';
    RAISE NOTICE '06: AI & Evidence Schema Verification COMPLETED SUCCESSFULLY';
    RAISE NOTICE '=====================================================';
END;
$$;
