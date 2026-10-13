-- =============================================================================
-- Test: 05_verify_row_level_security.sql
-- Description: Validates multi-tenant Row-Level Security (RLS) policies:
--              1. Tenant A can insert and read own data
--              2. Tenant B cannot see Tenant A data
--              3. Tenant B cannot update or delete Tenant A data
--              4. Cross-tenant insert is rejected by WITH CHECK
--              5. Super-admin can view across tenants
--              6. Session context reset removes access to tenant rows
-- =============================================================================

DO $$
DECLARE
    v_bldg_a_id BIGINT;
    v_bldg_b_id BIGINT;
    v_visible_count INT;
    v_check_failed BOOLEAN;
    v_code_a TEXT := 'RLS_BLDG_A_' || substr(gen_random_uuid()::text, 1, 6);
    v_code_b TEXT := 'RLS_BLDG_B_' || substr(gen_random_uuid()::text, 1, 6);
BEGIN
    -- Ensure test organizations 1 and 2 exist
    INSERT INTO firesphere.organizations (id, code, name, type, status)
    VALUES (2, 'TEST-ORG-2', 'Secondary Test Brigade', 'MUNICIPAL', 'ACTIVE')
    ON CONFLICT (code) DO NOTHING;

    -- -------------------------------------------------------------------------
    -- TEST 1: Tenant A (org_id = 1) can insert and read own data
    -- -------------------------------------------------------------------------
    PERFORM firesphere.set_tenant_context(1, FALSE);

    INSERT INTO firesphere.buildings (
        org_id, building_name, occupancy_type, location, address
    ) VALUES (
        1, v_code_a, 'RESIDENTIAL', ST_SetSRID(ST_MakePoint(73.8500, 18.5200), 4326), '100 Tenant A Street'
    ) RETURNING id INTO v_bldg_a_id;

    SELECT COUNT(*) INTO v_visible_count
    FROM firesphere.buildings
    WHERE id = v_bldg_a_id;

    IF v_visible_count <> 1 THEN
        RAISE EXCEPTION 'RLS TEST 1 FAILED: Tenant A cannot see own inserted building.';
    END IF;

    -- -------------------------------------------------------------------------
    -- TEST 2: Switch to Tenant B (org_id = 2) -> Tenant A data is INVISIBLE
    -- -------------------------------------------------------------------------
    PERFORM firesphere.set_tenant_context(2, FALSE);

    SELECT COUNT(*) INTO v_visible_count
    FROM firesphere.buildings
    WHERE id = v_bldg_a_id;

    IF v_visible_count <> 0 THEN
        RAISE EXCEPTION 'RLS TEST 2 FAILED: Tenant B can see Tenant A building! RLS leak detected.';
    END IF;

    -- -------------------------------------------------------------------------
    -- TEST 3: Tenant B cannot update Tenant A data
    -- -------------------------------------------------------------------------
    UPDATE firesphere.buildings
    SET building_name = 'HACKED_BY_TENANT_B'
    WHERE id = v_bldg_a_id;

    -- Verify that Tenant A building was NOT modified
    PERFORM firesphere.set_tenant_context(1, FALSE);
    SELECT COUNT(*) INTO v_visible_count
    FROM firesphere.buildings
    WHERE id = v_bldg_a_id AND building_name = v_code_a;

    IF v_visible_count <> 1 THEN
        RAISE EXCEPTION 'RLS TEST 3 FAILED: Tenant B was able to modify Tenant A building.';
    END IF;

    -- -------------------------------------------------------------------------
    -- TEST 4: Tenant B cannot delete Tenant A data
    -- -------------------------------------------------------------------------
    PERFORM firesphere.set_tenant_context(2, FALSE);
    DELETE FROM firesphere.buildings WHERE id = v_bldg_a_id;

    PERFORM firesphere.set_tenant_context(1, FALSE);
    SELECT COUNT(*) INTO v_visible_count
    FROM firesphere.buildings
    WHERE id = v_bldg_a_id;

    IF v_visible_count <> 1 THEN
        RAISE EXCEPTION 'RLS TEST 4 FAILED: Tenant B was able to delete Tenant A building.';
    END IF;

    -- -------------------------------------------------------------------------
    -- TEST 5: Cross-tenant INSERT rejection (WITH CHECK clause)
    -- Active context is Tenant 2, attempt to insert row with org_id = 1
    -- -------------------------------------------------------------------------
    PERFORM firesphere.set_tenant_context(2, FALSE);
    v_check_failed := FALSE;

    BEGIN
        INSERT INTO firesphere.buildings (
            org_id, building_name, occupancy_type, location, address
        ) VALUES (
            1, 'MALICIOUS_INJECTION', 'RESIDENTIAL', ST_SetSRID(ST_MakePoint(73.8500, 18.5200), 4326), 'Cross-Tenant Address'
        );
    EXCEPTION WHEN others THEN
        -- RLS policy check violation will raise an exception
        v_check_failed := TRUE;
    END;

    IF NOT v_check_failed THEN
        RAISE EXCEPTION 'RLS TEST 5 FAILED: Cross-tenant INSERT succeeded without rejection.';
    END IF;

    -- -------------------------------------------------------------------------
    -- TEST 6: Super-admin can see both Tenant A and Tenant B data
    -- -------------------------------------------------------------------------
    -- Insert Tenant B building
    INSERT INTO firesphere.buildings (
        org_id, building_name, occupancy_type, location, address
    ) VALUES (
        2, v_code_b, 'COMMERCIAL', ST_SetSRID(ST_MakePoint(73.8600, 18.5300), 4326), '200 Tenant B Avenue'
    ) RETURNING id INTO v_bldg_b_id;

    -- Set super-admin context
    PERFORM firesphere.set_tenant_context(1, TRUE);

    SELECT COUNT(*) INTO v_visible_count
    FROM firesphere.buildings
    WHERE id IN (v_bldg_a_id, v_bldg_b_id);

    IF v_visible_count <> 2 THEN
        RAISE EXCEPTION 'RLS TEST 6 FAILED: Super-admin cannot see cross-tenant records (count: %).', v_visible_count;
    END IF;

    -- -------------------------------------------------------------------------
    -- TEST 7: Session context reset leaves tenant rows invisible
    -- -------------------------------------------------------------------------
    PERFORM set_config('app.current_org_id', '', FALSE);
    PERFORM set_config('app.is_super_admin', 'false', FALSE);

    SELECT COUNT(*) INTO v_visible_count
    FROM firesphere.buildings
    WHERE id IN (v_bldg_a_id, v_bldg_b_id);

    IF v_visible_count <> 0 THEN
        RAISE EXCEPTION 'RLS TEST 7 FAILED: Unauthenticated/reset session can see tenant rows! Leak detected.';
    END IF;

    -- Cleanup test buildings under super-admin mode
    PERFORM firesphere.set_tenant_context(1, TRUE);
    DELETE FROM firesphere.buildings WHERE id IN (v_bldg_a_id, v_bldg_b_id);

    RAISE NOTICE 'SUCCESS: All 7 Row-Level Security (RLS) isolation tests PASSED.';
END $$;
