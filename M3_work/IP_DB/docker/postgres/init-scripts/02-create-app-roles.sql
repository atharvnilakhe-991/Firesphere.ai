-- =============================================================================
-- FireSphere.ai — Application Roles & RLS Test Role Provisioning
-- Mounted into: /docker-entrypoint-initdb.d/02-create-app-roles.sql
-- =============================================================================

DO $$
BEGIN
    -- 1. Create standard 'postgres' superuser role for M2 application-local.yml default
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'postgres') THEN
        CREATE ROLE postgres WITH SUPERUSER LOGIN PASSWORD 'postgres';
    END IF;

    -- 2. Create non-superuser 'firesphere_app' role for strict Row-Level Security verification (NOBYPASSRLS)
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'firesphere_app') THEN
        CREATE ROLE firesphere_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD 'firesphere_app_password';
    END IF;

    -- Grant basic access to schemas
    GRANT USAGE ON SCHEMA firesphere TO firesphere_app;
    GRANT USAGE ON SCHEMA public TO firesphere_app;
END $$;
