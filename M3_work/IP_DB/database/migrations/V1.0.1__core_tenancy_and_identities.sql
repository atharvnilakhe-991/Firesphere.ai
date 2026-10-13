-- =============================================================================
-- Migration: V1.0.1__core_tenancy_and_identities.sql
-- Description: Multi-tenant organizational hierarchy, users, roles, and permissions
-- Author: Member 3 (Database, Infrastructure & DevOps)
-- =============================================================================

-- 1. Organizations (Root Multi-Tenant Master Entity)
CREATE TABLE IF NOT EXISTS firesphere.organizations (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'MUNICIPAL', -- 'MUNICIPAL', 'INDUSTRIAL', 'STATE'
    jurisdiction_area VARCHAR(100),
    contact_email VARCHAR(150),
    contact_phone VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',  -- 'ACTIVE', 'SUSPENDED', 'PENDING'
    metadata_json JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_organizations_code ON firesphere.organizations(code);
CREATE INDEX idx_organizations_status ON firesphere.organizations(status);

-- 2. Divisions (Geographic/Administrative Subdivisions)
CREATE TABLE IF NOT EXISTS firesphere.divisions (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    division_code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_divisions_org_code UNIQUE (org_id, division_code)
);

CREATE INDEX idx_divisions_org_id ON firesphere.divisions(org_id);

-- 3. Departments (Functional Operational Units)
-- Circular reference with users.department_id resolved via DEFERRABLE constraint
CREATE TABLE IF NOT EXISTS firesphere.departments (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    division_id BIGINT REFERENCES firesphere.divisions(id) ON DELETE SET NULL,
    department_code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    head_user_id BIGINT, -- FK to users(id) added after users table creation
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_departments_org_code UNIQUE (org_id, department_code)
);

CREATE INDEX idx_departments_org_id ON firesphere.departments(org_id);
CREATE INDEX idx_departments_division_id ON firesphere.departments(division_id);

-- 4. Users (System Actors & Responders)
CREATE TABLE IF NOT EXISTS firesphere.users (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    department_id BIGINT REFERENCES firesphere.departments(id) ON DELETE SET NULL,
    employee_code VARCHAR(50),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone VARCHAR(50),
    password_hash VARCHAR(255) NOT NULL, -- bcrypt cost 12
    designation VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'INACTIVE', 'SUSPENDED'
    is_super_admin BOOLEAN NOT NULL DEFAULT FALSE,
    force_password_reset BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_org_id ON firesphere.users(org_id);
CREATE INDEX idx_users_email ON firesphere.users(email);
CREATE INDEX idx_users_department_id ON firesphere.users(department_id);

-- Add deferred foreign key from departments to users
ALTER TABLE firesphere.departments 
    ADD CONSTRAINT fk_departments_head_user 
    FOREIGN KEY (head_user_id) 
    REFERENCES firesphere.users(id) 
    ON DELETE SET NULL 
    DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX idx_departments_head_user_id ON firesphere.departments(head_user_id);

-- 5. Roles (RBAC Hierarchy)
CREATE TABLE IF NOT EXISTS firesphere.roles (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT REFERENCES firesphere.organizations(id) ON DELETE CASCADE,
    role_code VARCHAR(50) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_roles_org_code UNIQUE (org_id, role_code)
);

CREATE INDEX idx_roles_org_id ON firesphere.roles(org_id);

-- 6. Permissions (Global Granular Operations)
CREATE TABLE IF NOT EXISTS firesphere.permissions (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    permission_code VARCHAR(100) NOT NULL UNIQUE,
    module VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_permissions_module ON firesphere.permissions(module);

-- 7. Role Permissions (RBAC Junction)
CREATE TABLE IF NOT EXISTS firesphere.role_permissions (
    role_id BIGINT NOT NULL REFERENCES firesphere.roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES firesphere.permissions(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id)
);

-- 8. User Roles (User-to-Role Assignment)
CREATE TABLE IF NOT EXISTS firesphere.user_roles (
    user_id BIGINT NOT NULL REFERENCES firesphere.users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES firesphere.roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

-- 9. Attach automatic updated_at triggers
CREATE TRIGGER trg_organizations_updated_at BEFORE UPDATE ON firesphere.organizations
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();

CREATE TRIGGER trg_divisions_updated_at BEFORE UPDATE ON firesphere.divisions
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();

CREATE TRIGGER trg_departments_updated_at BEFORE UPDATE ON firesphere.departments
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON firesphere.users
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();

CREATE TRIGGER trg_roles_updated_at BEFORE UPDATE ON firesphere.roles
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();
