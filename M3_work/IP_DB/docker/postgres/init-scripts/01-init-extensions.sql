-- =============================================================================
-- FireSphere.ai — PostgreSQL Initialization Script
-- Role: Member 3 (Database, Infrastructure & DevOps)
-- Mounted into: /docker-entrypoint-initdb.d/01-init-extensions.sql
-- =============================================================================

-- Ensure required PostgreSQL extensions are active in the database
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";
CREATE EXTENSION IF NOT EXISTS "btree_gist";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Create target schema for FireSphere.ai platform
CREATE SCHEMA IF NOT EXISTS firesphere;

-- Grant schema access to administrative role
GRANT ALL ON SCHEMA firesphere TO CURRENT_USER;
GRANT ALL ON SCHEMA public TO CURRENT_USER;

-- Set default search path
ALTER DATABASE firesphere SET search_path TO firesphere, public;
