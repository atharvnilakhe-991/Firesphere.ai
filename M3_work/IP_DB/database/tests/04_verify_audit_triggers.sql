-- =============================================================================
-- Test: 04_verify_audit_triggers.sql
-- Description: Validates Change Data Capture (CDC) audit logging triggers:
--              1. INSERT generates audit row
--              2. UPDATE generates audit row with changed_fields calculation
--              3. DELETE generates audit row
--              4. user_id is populated from session context (app.current_user_id)
--              5. Unauthorized direct UPDATE on audit_logs is rejected
--              6. Unauthorized direct DELETE on audit_logs is rejected
-- =============================================================================

DO $$
DECLARE
    v_bldg_id BIGINT;
    v_audit_record RECORD;
    v_audit_count INT;
    v_tamper_failed BOOLEAN;
    v_test_code TEXT := 'TEST_BLDG_' || substr(gen_random_uuid()::text, 1, 6);
    v_test_user_id BIGINT := 999;
BEGIN
    -- 1. Set tenant context AND session user_id
    PERFORM firesphere.set_tenant_context(1, TRUE);
    PERFORM set_config('app.current_user_id', v_test_user_id::TEXT, FALSE);

    -- 2. Insert a test building
    INSERT INTO firesphere.buildings (
        org_id, building_name, occupancy_type, location, address
    ) VALUES (
        1, v_test_code, 'COMMERCIAL', ST_SetSRID(ST_MakePoint(73.8567, 18.5204), 4326), '123 Test Street'
    ) RETURNING id INTO v_bldg_id;

    -- 3. Verify audit log entry exists for INSERT and has populated user_id
    SELECT * INTO v_audit_record
    FROM firesphere.audit_logs
    WHERE table_name = 'buildings' AND entity_id = v_bldg_id AND action = 'INSERT'
    ORDER BY id DESC LIMIT 1;

    IF v_audit_record.id IS NULL THEN
        RAISE EXCEPTION 'TEST FAILED: No audit log generated for INSERT on buildings';
    END IF;

    IF v_audit_record.user_id IS NULL OR v_audit_record.user_id <> v_test_user_id THEN
        RAISE EXCEPTION 'TEST FAILED: audit_logs.user_id was not populated from session context. Expected: %, Got: %',
            v_test_user_id, v_audit_record.user_id;
    END IF;

    -- 4. Update the test building (change building_name and address)
    UPDATE firesphere.buildings
    SET building_name = v_test_code || '_UPDATED',
        address = '456 Updated Boulevard'
    WHERE id = v_bldg_id;

    -- 5. Verify audit log entry exists for UPDATE and changed_fields tracks modified columns
    SELECT * INTO v_audit_record
    FROM firesphere.audit_logs
    WHERE table_name = 'buildings' AND entity_id = v_bldg_id AND action = 'UPDATE'
    ORDER BY id DESC LIMIT 1;

    IF v_audit_record.id IS NULL THEN
        RAISE EXCEPTION 'TEST FAILED: No audit log generated for UPDATE on buildings';
    END IF;

    IF NOT ('building_name' = ANY(v_audit_record.changed_fields)) THEN
        RAISE EXCEPTION 'TEST FAILED: changed_fields did not record building_name change. Got: %', v_audit_record.changed_fields;
    END IF;

    IF NOT ('address' = ANY(v_audit_record.changed_fields)) THEN
        RAISE EXCEPTION 'TEST FAILED: changed_fields did not record address change. Got: %', v_audit_record.changed_fields;
    END IF;

    -- 6. Clean up test building (generates DELETE audit row)
    DELETE FROM firesphere.buildings WHERE id = v_bldg_id;

    SELECT COUNT(*) INTO v_audit_count
    FROM firesphere.audit_logs
    WHERE table_name = 'buildings' AND entity_id = v_bldg_id AND action = 'DELETE';

    IF v_audit_count = 0 THEN
        RAISE EXCEPTION 'TEST FAILED: No audit log generated for DELETE on buildings';
    END IF;

    -- 7. Test Audit Tamper Resistance: Attempt to UPDATE an audit record directly
    v_tamper_failed := FALSE;
    BEGIN
        UPDATE firesphere.audit_logs
        SET action = 'TAMPERED'
        WHERE id = v_audit_record.id;
    EXCEPTION WHEN others THEN
        v_tamper_failed := TRUE;
    END;

    IF NOT v_tamper_failed THEN
        RAISE EXCEPTION 'TEST FAILED: Audit tamper-resistance failed! Direct UPDATE succeeded on audit_logs.';
    END IF;

    -- 8. Test Audit Tamper Resistance: Attempt to DELETE an audit record directly
    v_tamper_failed := FALSE;
    BEGIN
        DELETE FROM firesphere.audit_logs WHERE id = v_audit_record.id;
    EXCEPTION WHEN others THEN
        v_tamper_failed := TRUE;
    END;

    IF NOT v_tamper_failed THEN
        RAISE EXCEPTION 'TEST FAILED: Audit tamper-resistance failed! Direct DELETE succeeded on audit_logs.';
    END IF;

    RAISE NOTICE 'SUCCESS: CDC audit triggers and tamper-resistance protections fully verified.';
END $$;
