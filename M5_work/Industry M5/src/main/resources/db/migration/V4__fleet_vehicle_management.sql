-- Flyway Migration V4: Fleet Vehicle Management Enhancements & Status History

-- 1. Add make, model, manufacture_year, and operational_notes to fleet_vehicles
ALTER TABLE fleet_vehicles ADD COLUMN IF NOT EXISTS make VARCHAR(50);
ALTER TABLE fleet_vehicles ADD COLUMN IF NOT EXISTS model VARCHAR(50);
ALTER TABLE fleet_vehicles ADD COLUMN IF NOT EXISTS manufacture_year INTEGER;
ALTER TABLE fleet_vehicles ADD COLUMN IF NOT EXISTS operational_notes VARCHAR(1000);

-- 2. Indexes for tenant, station, and type filtering
CREATE INDEX IF NOT EXISTS idx_vehicles_tenant_station ON fleet_vehicles(tenant_id, assigned_station_id);
CREATE INDEX IF NOT EXISTS idx_vehicles_tenant_type ON fleet_vehicles(tenant_id, vehicle_type);

-- 3. Create vehicle status history table for audit trails
CREATE TABLE IF NOT EXISTS vehicle_status_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    vehicle_id UUID NOT NULL REFERENCES fleet_vehicles(id) ON DELETE CASCADE,
    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    transitioned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    transitioned_by VARCHAR(100) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    version BIGINT
);

CREATE INDEX IF NOT EXISTS idx_vehicle_history_vehicle ON vehicle_status_history(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_vehicle_history_tenant_time ON vehicle_status_history(tenant_id, transitioned_at);
