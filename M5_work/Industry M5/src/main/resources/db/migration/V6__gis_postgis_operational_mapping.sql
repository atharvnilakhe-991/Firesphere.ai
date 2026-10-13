-- ============================================================================
-- FireSphere.ai — Member 5: Phase 6 GIS / PostGIS Operational Mapping Migration
-- Migration: V6__gis_postgis_operational_mapping.sql
-- ============================================================================

-- 1. Enable PostGIS extension for spatial geometric types and functions
CREATE EXTENSION IF NOT EXISTS postgis;

-- 2. Extend gis_hydrants with spatial geometry column and GIST indexes
ALTER TABLE gis_hydrants ADD COLUMN IF NOT EXISTS geom GEOMETRY(Point, 4326);

-- Backfill geometry from coordinates for existing hydrants
UPDATE gis_hydrants
SET geom = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
WHERE geom IS NULL AND latitude IS NOT NULL AND longitude IS NOT NULL;

-- Spatial GIST indexes on hydrant geometry and geography for proximity queries
CREATE INDEX IF NOT EXISTS idx_gis_hydrants_geom ON gis_hydrants USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_gis_hydrants_geog ON gis_hydrants USING GIST ((geom::geography));
CREATE INDEX IF NOT EXISTS idx_gis_hydrants_tenant_operable ON gis_hydrants (tenant_id, is_operable);

-- 3. Extend gis_hazard_zones with zone_code, active status, spatial geometry, and GIST indexes
ALTER TABLE gis_hazard_zones ADD COLUMN IF NOT EXISTS zone_code VARCHAR(64);
ALTER TABLE gis_hazard_zones ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE gis_hazard_zones ADD COLUMN IF NOT EXISTS geom GEOMETRY(Geometry, 4326);

-- Spatial GIST indexes on hazard zone geometry and geography for point-in-zone queries
CREATE INDEX IF NOT EXISTS idx_gis_hazard_zones_geom ON gis_hazard_zones USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_gis_hazard_zones_geog ON gis_hazard_zones USING GIST ((geom::geography));
CREATE INDEX IF NOT EXISTS idx_gis_hazard_zones_tenant_active ON gis_hazard_zones (tenant_id, is_active);
CREATE INDEX IF NOT EXISTS idx_gis_hazard_zones_tenant_code ON gis_hazard_zones (tenant_id, zone_code);
