-- =============================================================================
-- Migration: V1.0.2__spatial_infrastructure_and_locations.sql
-- Description: PostGIS spatial tables, boundary polygons, hydrants, stations, and buildings
-- Author: Member 3 (Database, Infrastructure & DevOps)
-- =============================================================================

-- 1. Administrative Risk Zones (Boundary Polygons)
CREATE TABLE IF NOT EXISTS firesphere.zones (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    zone_code VARCHAR(50) NOT NULL,
    zone_name VARCHAR(150) NOT NULL,
    risk_level VARCHAR(50) NOT NULL DEFAULT 'MEDIUM', -- 'LOW', 'MEDIUM', 'HIGH', 'EXTREME'
    boundary_geom GEOMETRY(Polygon, 4326) NOT NULL,
    metadata_json JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_zones_org_code UNIQUE (org_id, zone_code)
);

CREATE INDEX idx_zones_org_id ON firesphere.zones(org_id);
CREATE INDEX idx_zones_boundary_geom ON firesphere.zones USING GIST (boundary_geom);

-- 2. Operational Beats (Sub-Zone Micro-Territories)
CREATE TABLE IF NOT EXISTS firesphere.beats (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    zone_id BIGINT REFERENCES firesphere.zones(id) ON DELETE SET NULL,
    beat_code VARCHAR(50) NOT NULL,
    beat_name VARCHAR(150) NOT NULL,
    boundary_geom GEOMETRY(Polygon, 4326),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_beats_org_code UNIQUE (org_id, beat_code)
);

CREATE INDEX idx_beats_org_id ON firesphere.beats(org_id);
CREATE INDEX idx_beats_zone_id ON firesphere.beats(zone_id);
CREATE INDEX idx_beats_boundary_geom ON firesphere.beats USING GIST (boundary_geom);

-- 3. Fire Stations (Physical Turnout Facilities)
CREATE TABLE IF NOT EXISTS firesphere.fire_stations (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    division_id BIGINT REFERENCES firesphere.divisions(id) ON DELETE SET NULL,
    station_code VARCHAR(50) NOT NULL,
    station_name VARCHAR(150) NOT NULL,
    station_type VARCHAR(50) NOT NULL DEFAULT 'HEADQUARTERS', -- 'HEADQUARTERS', 'SUBSTATION', 'OUTPOST'
    location GEOMETRY(Point, 4326) NOT NULL,
    jurisdiction_geom GEOMETRY(Polygon, 4326),
    address TEXT,
    contact_phone VARCHAR(50),
    officer_in_charge_id BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL,
    is_operational BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_fire_stations_org_code UNIQUE (org_id, station_code)
);

CREATE INDEX idx_fire_stations_org_id ON firesphere.fire_stations(org_id);
CREATE INDEX idx_fire_stations_location ON firesphere.fire_stations USING GIST (location);
CREATE INDEX idx_fire_stations_jurisdiction ON firesphere.fire_stations USING GIST (jurisdiction_geom);

-- 4. Hydrants (Municipal Water Infrastructure)
CREATE TABLE IF NOT EXISTS firesphere.hydrants (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    fire_station_id BIGINT REFERENCES firesphere.fire_stations(id) ON DELETE SET NULL,
    hydrant_code VARCHAR(50) NOT NULL,
    location GEOMETRY(Point, 4326) NOT NULL,
    flow_rate_lpm NUMERIC(10,2),
    pressure_bar NUMERIC(6,2),
    status VARCHAR(50) NOT NULL DEFAULT 'OPERATIONAL', -- 'OPERATIONAL', 'DEFECTIVE', 'UNDER_MAINTENANCE'
    outlet_diameter_mm INT,
    last_inspected_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_hydrants_org_code UNIQUE (org_id, hydrant_code)
);

CREATE INDEX idx_hydrants_org_id ON firesphere.hydrants(org_id);
CREATE INDEX idx_hydrants_station_id ON firesphere.hydrants(fire_station_id);
CREATE INDEX idx_hydrants_location ON firesphere.hydrants USING GIST (location);
CREATE INDEX idx_hydrants_status ON firesphere.hydrants(status);

-- 5. Emergency Water Sources (Reservoirs, Lakes, Sump Tanks)
CREATE TABLE IF NOT EXISTS firesphere.water_sources (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    source_name VARCHAR(150) NOT NULL,
    source_type VARCHAR(50) NOT NULL, -- 'OPEN_WELL', 'LAKE', 'CANAL', 'UNDERGROUND_SUMP', 'OVERHEAD_TANK'
    location GEOMETRY(Point, 4326) NOT NULL,
    capacity_liters NUMERIC(14,2),
    is_perennial BOOLEAN NOT NULL DEFAULT TRUE,
    access_suitability VARCHAR(50) DEFAULT 'ALL_WEATHER',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_water_sources_org_id ON firesphere.water_sources(org_id);
CREATE INDEX idx_water_sources_location ON firesphere.water_sources USING GIST (location);

-- 6. Buildings (Inspected Physical Infrastructure)
CREATE TABLE IF NOT EXISTS firesphere.buildings (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    zone_id BIGINT REFERENCES firesphere.zones(id) ON DELETE SET NULL,
    beat_id BIGINT REFERENCES firesphere.beats(id) ON DELETE SET NULL,
    assigned_fire_station_id BIGINT REFERENCES firesphere.fire_stations(id) ON DELETE SET NULL,
    building_name VARCHAR(200) NOT NULL,
    property_tax_number VARCHAR(100),
    occupancy_type VARCHAR(100) NOT NULL, -- NBC 2016 Part 4: 'RESIDENTIAL', 'COMMERCIAL', 'INSTITUTIONAL', 'HAZARDOUS', etc.
    height_meters NUMERIC(6,2),
    floors_count INT,
    basements_count INT DEFAULT 0,
    built_up_area_sqm NUMERIC(12,2),
    location GEOMETRY(Point, 4326) NOT NULL,
    building_footprint GEOMETRY(Polygon, 4326),
    address TEXT NOT NULL,
    risk_category VARCHAR(50) DEFAULT 'MODERATE', -- 'LOW', 'MODERATE', 'HIGH', 'SEVERE'
    is_noc_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_buildings_org_id ON firesphere.buildings(org_id);
CREATE INDEX idx_buildings_location ON firesphere.buildings USING GIST (location);
CREATE INDEX idx_buildings_footprint ON firesphere.buildings USING GIST (building_footprint);
CREATE INDEX idx_buildings_occupancy ON firesphere.buildings(occupancy_type);
CREATE INDEX idx_buildings_station_id ON firesphere.buildings(assigned_fire_station_id);

-- 7. High-Performance Spatial Helper: Sub-5ms KNN Hydrant Locator
CREATE OR REPLACE FUNCTION firesphere.find_nearest_operational_hydrants(
    p_org_id BIGINT,
    p_lng DOUBLE PRECISION,
    p_lat DOUBLE PRECISION,
    p_limit INT DEFAULT 5
)
RETURNS TABLE (
    hydrant_id BIGINT,
    hydrant_code VARCHAR(50),
    distance_meters DOUBLE PRECISION,
    flow_rate_lpm NUMERIC(10,2),
    pressure_bar NUMERIC(6,2),
    geom GEOMETRY
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        h.id,
        h.hydrant_code,
        ST_Distance(h.location::geography, ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326)::geography) AS distance_meters,
        h.flow_rate_lpm,
        h.pressure_bar,
        h.location
    FROM firesphere.hydrants h
    WHERE h.org_id = p_org_id
      AND h.status = 'OPERATIONAL'
    ORDER BY h.location <-> ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326)
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql STABLE;

-- Attach updated_at triggers
CREATE TRIGGER trg_zones_updated_at BEFORE UPDATE ON firesphere.zones
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();

CREATE TRIGGER trg_beats_updated_at BEFORE UPDATE ON firesphere.beats
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();

CREATE TRIGGER trg_fire_stations_updated_at BEFORE UPDATE ON firesphere.fire_stations
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();

CREATE TRIGGER trg_hydrants_updated_at BEFORE UPDATE ON firesphere.hydrants
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();

CREATE TRIGGER trg_water_sources_updated_at BEFORE UPDATE ON firesphere.water_sources
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();

CREATE TRIGGER trg_buildings_updated_at BEFORE UPDATE ON firesphere.buildings
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();
