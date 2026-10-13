-- Flyway Baseline Migration: Member 5 Operational Modules
-- Schema: incidents, dispatches, fleet, inventory, gis

CREATE TABLE IF NOT EXISTS incidents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    incident_number VARCHAR(32) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    incident_type VARCHAR(50) NOT NULL,
    severity VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    address VARCHAR(500) NOT NULL,
    caller_name VARCHAR(100),
    caller_phone VARCHAR(30),
    assigned_station_id UUID,
    reported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    contained_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    version BIGINT
);

CREATE INDEX IF NOT EXISTS idx_incidents_tenant_status ON incidents(tenant_id, status);

CREATE TABLE IF NOT EXISTS dispatch_orders (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    incident_id UUID NOT NULL,
    station_id UUID NOT NULL,
    dispatch_code VARCHAR(32) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    dispatched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    arrived_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    operational_notes VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    version BIGINT
);

CREATE INDEX IF NOT EXISTS idx_dispatch_incident ON dispatch_orders(incident_id);
CREATE INDEX IF NOT EXISTS idx_dispatch_tenant_status ON dispatch_orders(tenant_id, status);

CREATE TABLE IF NOT EXISTS dispatch_assigned_vehicles (
    dispatch_id UUID NOT NULL REFERENCES dispatch_orders(id) ON DELETE CASCADE,
    vehicle_id UUID NOT NULL
);

CREATE TABLE IF NOT EXISTS dispatch_assigned_responders (
    dispatch_id UUID NOT NULL REFERENCES dispatch_orders(id) ON DELETE CASCADE,
    responder_id UUID NOT NULL
);

CREATE TABLE IF NOT EXISTS fleet_vehicles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    registration_number VARCHAR(32) NOT NULL UNIQUE,
    call_sign VARCHAR(50) NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    assigned_station_id UUID NOT NULL,
    water_capacity_liters INTEGER,
    foam_capacity_liters INTEGER,
    pump_capacity_lpm INTEGER,
    current_latitude DOUBLE PRECISION,
    current_longitude DOUBLE PRECISION,
    fuel_percent DOUBLE PRECISION,
    water_level_percent DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    version BIGINT
);

CREATE INDEX IF NOT EXISTS idx_vehicles_tenant_status ON fleet_vehicles(tenant_id, status);

CREATE TABLE IF NOT EXISTS inventory_items (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(60) NOT NULL,
    station_id UUID NOT NULL,
    quantity_on_hand INTEGER NOT NULL,
    minimum_threshold INTEGER NOT NULL,
    unit_of_measure VARCHAR(30) NOT NULL,
    batch_number VARCHAR(60),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    version BIGINT,
    CONSTRAINT uq_inventory_tenant_sku UNIQUE (tenant_id, sku)
);

CREATE INDEX IF NOT EXISTS idx_inventory_station ON inventory_items(tenant_id, station_id);

CREATE TABLE IF NOT EXISTS inventory_stock_movements (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    item_id UUID NOT NULL,
    movement_type VARCHAR(30) NOT NULL,
    quantity INTEGER NOT NULL,
    incident_id UUID,
    destination_or_source VARCHAR(100),
    remarks VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    version BIGINT
);

CREATE INDEX IF NOT EXISTS idx_movements_item ON inventory_stock_movements(item_id);
CREATE INDEX IF NOT EXISTS idx_movements_incident ON inventory_stock_movements(incident_id);

CREATE TABLE IF NOT EXISTS gis_hydrants (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    hydrant_code VARCHAR(32) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    pressure_psi DOUBLE PRECISION,
    flow_rate_lpm DOUBLE PRECISION,
    is_operable BOOLEAN NOT NULL DEFAULT TRUE,
    address VARCHAR(300),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    version BIGINT,
    CONSTRAINT uq_hydrant_tenant_code UNIQUE (tenant_id, hydrant_code)
);

CREATE INDEX IF NOT EXISTS idx_hydrant_coords ON gis_hydrants(latitude, longitude);

CREATE TABLE IF NOT EXISTS gis_hazard_zones (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    hazard_name VARCHAR(150) NOT NULL,
    hazard_type VARCHAR(60) NOT NULL,
    center_lat DOUBLE PRECISION NOT NULL,
    center_lon DOUBLE PRECISION NOT NULL,
    radius_meters DOUBLE PRECISION NOT NULL,
    severity VARCHAR(30) NOT NULL,
    evacuation_protocol VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    version BIGINT
);

CREATE INDEX IF NOT EXISTS idx_hazard_coords ON gis_hazard_zones(center_lat, center_lon);
