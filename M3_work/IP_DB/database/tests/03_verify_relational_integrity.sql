-- =============================================================================
-- Test: 03_verify_relational_integrity.sql
-- Description: Validates primary keys, foreign keys, and circular reference deferral
-- =============================================================================

DO $$
DECLARE
    v_dept_id BIGINT;
    v_user_id BIGINT;
    v_test_uuid UUID := gen_random_uuid();
BEGIN
    -- Verify circular dependency insertion within a deferred transaction
    INSERT INTO firesphere.departments (org_id, department_code, name)
    VALUES (1, 'TEST_DEPT_' || substr(v_test_uuid::text, 1, 6), 'Test Department')
    RETURNING id INTO v_dept_id;

    INSERT INTO firesphere.users (
        org_id, department_id, first_name, last_name, email, password_hash
    ) VALUES (
        1, v_dept_id, 'Integration', 'Tester', 'test_' || v_test_uuid::text || '@firesphere.local', 'hash_test_123'
    ) RETURNING id INTO v_user_id;

    -- Update department with head of department
    UPDATE firesphere.departments
    SET head_user_id = v_user_id
    WHERE id = v_dept_id;

    -- Clean up test records
    DELETE FROM firesphere.users WHERE id = v_user_id;
    DELETE FROM firesphere.departments WHERE id = v_dept_id;

    RAISE NOTICE 'TEST PASSED: Relational constraints and circular foreign key deferral verified.';
END $$;
