-- =============================================================================
-- Migration: V1.0.5__enable_row_level_security.sql
-- Description: P0 Database Hardening — Multi-tenant Row-Level Security (RLS),
--              Audit trigger user-context fix, changed_fields calculation, and
--              audit log tamper resistance privileges.
-- Author: Member 3 (Database, Infrastructure & DevOps)
-- =============================================================================

-- =============================================================================
-- SECTION 1: ENHANCED TENANT CONTEXT HELPER
-- =============================================================================
-- Update firesphere.current_org_id() and add firesphere.is_super_admin() helper
-- to support clean, secure RLS policy evaluation without typecast exceptions.

CREATE OR REPLACE FUNCTION firesphere.is_super_admin()
RETURNS BOOLEAN AS $$
DECLARE
    v_admin TEXT;
BEGIN
    v_admin := current_setting('app.is_super_admin', TRUE);
    IF v_admin IS NULL OR v_admin = '' THEN
        RETURN FALSE;
    END IF;
    RETURN LOWER(v_admin) IN ('true', 't', '1', 'yes');
EXCEPTION WHEN OTHERS THEN
    RETURN FALSE;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER;

-- Re-declare firesphere.current_org_id() with robust exception handling
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
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER;

-- =============================================================================
-- SECTION 2: ROW-LEVEL SECURITY (RLS) ACTIVATION & POLICIES
-- =============================================================================
-- Applies to all 44 tenant-scoped domain tables having direct org_id column.
-- Super-admins (firesphere.is_super_admin() = true) possess cross-tenant visibility.
-- Normal tenant users can only SELECT, INSERT, UPDATE, DELETE rows matching their org_id.
-- WITH CHECK prevents inserting or updating records into another organization.

DO $$
DECLARE
    tbl TEXT;
    t_tables TEXT[] := ARRAY[
        'divisions',
        'departments',
        'users',
        'zones',
        'beats',
        'fire_stations',
        'hydrants',
        'water_sources',
        'buildings',
        'building_contacts',
        'building_documents',
        'building_fire_equipment',
        'building_owners',
        'building_risk_profiles',
        'noc_applications',
        'noc_application_status_history',
        'noc_certificates',
        'noc_renewals',
        'inspections',
        'inspection_assignments',
        'inspection_findings',
        'corrective_actions',
        'violations',
        'violation_actions',
        'incidents',
        'incident_dispatches',
        'fire_vehicles',
        'incident_vehicles',
        'incident_crews',
        'incident_timeline',
        'incident_casualties',
        'incident_reports',
        'incident_evidence',
        'vehicle_crew',
        'vehicle_fitness',
        'vehicle_fuel_logs',
        'vehicle_location_history',
        'warehouses',
        'inventory_items',
        'inventory_stock',
        'training_courses',
        'roles',
        'inspection_checklists',
        'audit_logs'
    ];
BEGIN
    FOREACH tbl IN ARRAY t_tables LOOP
        -- 1. Enable Row-Level Security
        EXECUTE format('ALTER TABLE firesphere.%I ENABLE ROW LEVEL SECURITY;', tbl);

        -- 2. Drop existing policy if present (idempotency)
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation_policy ON firesphere.%I;', tbl);

        -- 3. Create Tenant Isolation Policy covering ALL commands (SELECT, INSERT, UPDATE, DELETE)
        EXECUTE format(
            'CREATE POLICY tenant_isolation_policy ON firesphere.%I
             FOR ALL
             USING (
                 org_id = firesphere.current_org_id()
                 OR firesphere.is_super_admin()
             )
             WITH CHECK (
                 org_id = firesphere.current_org_id()
                 OR firesphere.is_super_admin()
             );',
            tbl
        );
    END LOOP;
END $$;

-- -----------------------------------------------------------------------------
-- Special Table 1: firesphere.organizations (Root Tenant Directory)
-- -----------------------------------------------------------------------------
-- Tenant users can only view their own organization record (id = current_org_id).
-- Super-admins can view/manage all organizations.
ALTER TABLE firesphere.organizations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_org_isolation_policy ON firesphere.organizations;
CREATE POLICY tenant_org_isolation_policy ON firesphere.organizations
    FOR ALL
    USING (
        id = firesphere.current_org_id()
        OR firesphere.is_super_admin()
    )
    WITH CHECK (
        id = firesphere.current_org_id()
        OR firesphere.is_super_admin()
    );

-- -----------------------------------------------------------------------------
-- Special Table 2: firesphere.user_roles (Junction table scoped via users)
-- -----------------------------------------------------------------------------
ALTER TABLE firesphere.user_roles ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_user_roles_policy ON firesphere.user_roles;
CREATE POLICY tenant_user_roles_policy ON firesphere.user_roles
    FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM firesphere.users u 
            WHERE u.id = user_roles.user_id 
              AND (u.org_id = firesphere.current_org_id() OR firesphere.is_super_admin())
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM firesphere.users u 
            WHERE u.id = user_roles.user_id 
              AND (u.org_id = firesphere.current_org_id() OR firesphere.is_super_admin())
        )
    );

-- -----------------------------------------------------------------------------
-- Special Table 3: firesphere.inspection_checklist_items (Scoped via checklists)
-- -----------------------------------------------------------------------------
ALTER TABLE firesphere.inspection_checklist_items ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_checklist_items_policy ON firesphere.inspection_checklist_items;
CREATE POLICY tenant_checklist_items_policy ON firesphere.inspection_checklist_items
    FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM firesphere.inspection_checklists c 
            WHERE c.id = inspection_checklist_items.checklist_id 
              AND (c.org_id = firesphere.current_org_id() OR c.org_id IS NULL OR firesphere.is_super_admin())
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM firesphere.inspection_checklists c 
            WHERE c.id = inspection_checklist_items.checklist_id 
              AND (c.org_id = firesphere.current_org_id() OR firesphere.is_super_admin())
        )
    );

-- Note on Global Reference Tables:
-- firesphere.permissions, firesphere.role_permissions, and firesphere.noc_application_categories
-- are system-wide shared taxonomy and do NOT have RLS enabled (readable by all authenticated tenants).

-- =============================================================================
-- SECTION 3: REVISED AUDIT TRIGGER (FIX USER_ID & CHANGED_FIELDS)
-- =============================================================================

CREATE OR REPLACE FUNCTION firesphere.firesphere_audit_trigger()
RETURNS TRIGGER AS $$
DECLARE
    v_user_id BIGINT := NULL;
    v_user_text TEXT;
    v_org_id BIGINT;
    v_old_values JSONB := NULL;
    v_new_values JSONB := NULL;
    v_changed_fields TEXT[] := NULL;
    v_entity_id BIGINT;
    k TEXT;
BEGIN
    -- 1. Safely extract user_id from session context
    BEGIN
        v_user_text := current_setting('app.current_user_id', TRUE);
        IF v_user_text IS NOT NULL AND v_user_text <> '' THEN
            v_user_id := v_user_text::BIGINT;
        END IF;
    EXCEPTION WHEN OTHERS THEN
        v_user_id := NULL;
    END;

    -- 2. Extract tenant organization context
    v_org_id := firesphere.current_org_id();

    -- 3. Capture row states and calculate changed_fields on UPDATE
    IF TG_OP = 'INSERT' THEN
        v_entity_id := NEW.id;
        v_new_values := to_jsonb(NEW);
    ELSIF TG_OP = 'UPDATE' THEN
        v_entity_id := NEW.id;
        v_old_values := to_jsonb(OLD);
        v_new_values := to_jsonb(NEW);

        -- Exclude sensitive credential fields from diffing/auditing
        IF TG_TABLE_NAME = 'users' THEN
            v_old_values := v_old_values - 'password_hash';
            v_new_values := v_new_values - 'password_hash';
        END IF;

        -- Identify modified fields
        SELECT array_agg(key) INTO v_changed_fields
        FROM (
            SELECT key FROM jsonb_each(v_old_values)
            EXCEPT
            SELECT key FROM jsonb_each(v_new_values)
            UNION
            SELECT key FROM (
                SELECT key, value FROM jsonb_each(v_new_values)
                EXCEPT
                SELECT key, value FROM jsonb_each(v_old_values)
            ) s
        ) diff_keys;

    ELSIF TG_OP = 'DELETE' THEN
        v_entity_id := OLD.id;
        v_old_values := to_jsonb(OLD);
        IF TG_TABLE_NAME = 'users' THEN
            v_old_values := v_old_values - 'password_hash';
        END IF;
    END IF;

    -- 4. Insert into audit_logs table
    INSERT INTO firesphere.audit_logs (
        org_id,
        table_name,
        entity_id,
        action,
        old_values,
        new_values,
        changed_fields,
        user_id
    ) VALUES (
        COALESCE(v_org_id, CASE WHEN TG_OP = 'DELETE' THEN OLD.org_id ELSE NEW.org_id END),
        TG_TABLE_NAME,
        v_entity_id,
        TG_OP,
        v_old_values,
        v_new_values,
        v_changed_fields,
        v_user_id
    );

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =============================================================================
-- SECTION 4: AUDIT LOG TAMPER-RESISTANCE PRIVILEGE MODEL
-- =============================================================================
-- Ensure firesphere.audit_logs can only receive INSERT statements.
-- UPDATE, DELETE, and TRUNCATE are strictly prohibited to ensure tamper resistance.

CREATE OR REPLACE FUNCTION firesphere.prevent_audit_log_tampering()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'SECURITY VIOLATION: firesphere.audit_logs is an immutable append-only ledger. % operations are prohibited.', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_protect_audit_logs ON firesphere.audit_logs;
CREATE TRIGGER trg_protect_audit_logs
    BEFORE UPDATE OR DELETE OR TRUNCATE ON firesphere.audit_logs
    FOR EACH STATEMENT
    EXECUTE FUNCTION firesphere.prevent_audit_log_tampering();

-- Revoke mutation permissions from public/unprivileged roles if applicable
REVOKE UPDATE, DELETE, TRUNCATE ON firesphere.audit_logs FROM PUBLIC;
