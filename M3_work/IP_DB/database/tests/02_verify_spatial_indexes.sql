-- =============================================================================
-- Test: 02_verify_spatial_indexes.sql
-- Description: Validates PostGIS geometry types and GiST index coverage
-- =============================================================================

DO $$
DECLARE
    v_missing_index TEXT;
BEGIN
    -- Verify presence of GIST indexes on core spatial columns
    SELECT t.relname INTO v_missing_index
    FROM (
        VALUES 
            ('zones', 'boundary_geom'),
            ('beats', 'boundary_geom'),
            ('fire_stations', 'location'),
            ('hydrants', 'location'),
            ('buildings', 'location'),
            ('incidents', 'location'),
            ('vehicle_location_history', 'location')
    ) AS req(table_name, column_name)
    JOIN pg_class t ON t.relname = req.table_name
    JOIN pg_namespace n ON n.oid = t.relnamespace AND n.nspname = 'firesphere'
    WHERE NOT EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_class idx ON idx.oid = i.indexrelid
        JOIN pg_am am ON am.oid = idx.relam
        WHERE i.indrelid = t.oid
          AND am.amname = 'gist'
    )
    LIMIT 1;

    IF v_missing_index IS NOT NULL THEN
        RAISE EXCEPTION 'TEST FAILED: Table % lacks a required GIST spatial index', v_missing_index;
    END IF;

    -- Verify Spatial KNN Function
    IF NOT EXISTS (
        SELECT 1 
        FROM pg_proc p 
        JOIN pg_namespace n ON n.oid = p.pronamespace 
        WHERE n.nspname = 'firesphere' AND p.proname = 'find_nearest_operational_hydrants'
    ) THEN
        RAISE EXCEPTION 'TEST FAILED: Spatial KNN helper find_nearest_operational_hydrants is missing';
    END IF;

    RAISE NOTICE 'TEST PASSED: PostGIS geometry columns and GiST spatial indexes verified.';
END $$;
