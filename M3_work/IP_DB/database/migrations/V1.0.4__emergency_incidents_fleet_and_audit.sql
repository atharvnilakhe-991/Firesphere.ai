-- =============================================================================
-- Migration: V1.0.4__emergency_incidents_fleet_and_audit.sql
-- Description: 112/911 emergency incidents, apparatus fleet, inventory, and CDC audit logging
-- Author: Member 3 (Database, Infrastructure & DevOps)
-- =============================================================================

-- 1. Emergency Incidents (Core 112/911 Response Entity)
CREATE TABLE IF NOT EXISTS firesphere.incidents (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    incident_number VARCHAR(100) NOT NULL UNIQUE,
    caller_name VARCHAR(150),
    caller_phone VARCHAR(50),
    incident_type VARCHAR(100) NOT NULL, -- 'STRUCTURAL_FIRE', 'CHEMICAL_HAZMAT', 'VEHICLE_COLLISION', 'RESCUE_OPERATION'
    severity_level VARCHAR(50) NOT NULL DEFAULT 'LEVEL_1', -- 'LEVEL_1', 'LEVEL_2', 'LEVEL_3', 'MAJOR_DISASTER'
    location GEOMETRY(Point, 4326) NOT NULL,
    address_text TEXT NOT NULL,
    assigned_station_id BIGINT REFERENCES firesphere.fire_stations(id) ON DELETE SET NULL,
    building_id BIGINT REFERENCES firesphere.buildings(id) ON DELETE SET NULL,
    incident_status VARCHAR(50) NOT NULL DEFAULT 'REPORTED', -- 'REPORTED', 'DISPATCHED', 'ON_SCENE', 'CONTAINED', 'CLOSED'
    reported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    containment_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_incidents_org ON firesphere.incidents(org_id);
CREATE INDEX idx_incidents_location ON firesphere.incidents USING GIST (location);
CREATE INDEX idx_incidents_status ON firesphere.incidents(incident_status);
CREATE INDEX idx_incidents_station ON firesphere.incidents(assigned_station_id);

-- 2. Incident Dispatches (Section 6.6)
CREATE TABLE IF NOT EXISTS firesphere.incident_dispatches (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    incident_id BIGINT NOT NULL REFERENCES firesphere.incidents(id) ON DELETE CASCADE,
    dispatch_code VARCHAR(100) NOT NULL UNIQUE,
    dispatched_by_user_id BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL,
    dispatch_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_station_id BIGINT REFERENCES firesphere.fire_stations(id) ON DELETE RESTRICT,
    special_instructions TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dispatches_incident ON firesphere.incident_dispatches(incident_id);

-- 3. Apparatus Fleet (Core Fire Vehicle Registry)
CREATE TABLE IF NOT EXISTS firesphere.fire_vehicles (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    station_id BIGINT REFERENCES firesphere.fire_stations(id) ON DELETE SET NULL,
    registration_number VARCHAR(50) NOT NULL UNIQUE,
    call_sign VARCHAR(50) NOT NULL,
    vehicle_type VARCHAR(100) NOT NULL, -- 'WATER_TENDER', 'FOAM_CRASH_TENDER', 'HYDRAULIC_PLATFORM', 'RESCUE_VAN', 'AMBULANCE'
    water_capacity_liters INT DEFAULT 0,
    foam_capacity_liters INT DEFAULT 0,
    pump_capacity_lpm INT DEFAULT 0,
    operational_status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE', -- 'AVAILABLE', 'DISPATCHED', 'MAINTENANCE', 'OUT_OF_SERVICE'
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vehicles_org ON firesphere.fire_vehicles(org_id);
CREATE INDEX idx_vehicles_station ON firesphere.fire_vehicles(station_id);
CREATE INDEX idx_vehicles_status ON firesphere.fire_vehicles(operational_status);

-- 4. Incident Vehicles (Section 6.7)
CREATE TABLE IF NOT EXISTS firesphere.incident_vehicles (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    incident_id BIGINT NOT NULL REFERENCES firesphere.incidents(id) ON DELETE CASCADE,
    vehicle_id BIGINT NOT NULL REFERENCES firesphere.fire_vehicles(id) ON DELETE RESTRICT,
    dispatched_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    arrived_on_scene_at TIMESTAMP WITH TIME ZONE,
    departed_scene_at TIMESTAMP WITH TIME ZONE,
    returned_to_station_at TIMESTAMP WITH TIME ZONE,
    water_used_liters INT DEFAULT 0,
    foam_used_liters INT DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'EN_ROUTE' -- 'EN_ROUTE', 'ON_SCENE', 'RETURNING', 'COMPLETED'
);

CREATE INDEX idx_incident_veh_incident ON firesphere.incident_vehicles(incident_id);
CREATE INDEX idx_incident_veh_vehicle ON firesphere.incident_vehicles(vehicle_id);

-- 5. Incident Crews (Section 6.8)
CREATE TABLE IF NOT EXISTS firesphere.incident_crews (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    incident_id BIGINT NOT NULL REFERENCES firesphere.incidents(id) ON DELETE CASCADE,
    vehicle_id BIGINT REFERENCES firesphere.fire_vehicles(id) ON DELETE SET NULL,
    responder_user_id BIGINT NOT NULL REFERENCES firesphere.users(id) ON DELETE RESTRICT,
    role_in_incident VARCHAR(100) NOT NULL DEFAULT 'FIREFIGHTER', -- 'INCIDENT_COMMANDER', 'DRIVER_OPERATOR', 'FIREFIGHTER', 'PARAMEDIC'
    deployed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    debrief_notes TEXT
);

CREATE INDEX idx_incident_crews_incident ON firesphere.incident_crews(incident_id);
CREATE INDEX idx_incident_crews_responder ON firesphere.incident_crews(responder_user_id);

-- 6. Incident Timeline (Section 6.9 - Chronological Emergency Action Log)
CREATE TABLE IF NOT EXISTS firesphere.incident_timeline (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    incident_id BIGINT NOT NULL REFERENCES firesphere.incidents(id) ON DELETE CASCADE,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_type VARCHAR(100) NOT NULL, -- 'FIRST_UNIT_ARRIVED', 'MAYDAY_CALLED', 'WATER_APPLIED', 'FIRE_UNDER_CONTROL'
    description TEXT NOT NULL,
    recorded_by_user_id BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL
);

CREATE INDEX idx_timeline_incident ON firesphere.incident_timeline(incident_id);
CREATE INDEX idx_timeline_timestamp ON firesphere.incident_timeline(event_timestamp);

-- 7. Incident Casualties (Section 6.10)
CREATE TABLE IF NOT EXISTS firesphere.incident_casualties (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    incident_id BIGINT NOT NULL REFERENCES firesphere.incidents(id) ON DELETE CASCADE,
    person_type VARCHAR(50) NOT NULL DEFAULT 'CIVILIAN', -- 'CIVILIAN', 'FIREFIGHTER', 'POLICE'
    injury_severity VARCHAR(50) NOT NULL, -- 'MINOR', 'MODERATE', 'CRITICAL', 'FATALITY'
    age INT,
    gender VARCHAR(20),
    hospitalized_at VARCHAR(200),
    condition_summary TEXT,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_casualties_incident ON firesphere.incident_casualties(incident_id);

-- 8. Incident Reports (Section 6.11)
CREATE TABLE IF NOT EXISTS firesphere.incident_reports (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    incident_id BIGINT NOT NULL UNIQUE REFERENCES firesphere.incidents(id) ON DELETE CASCADE,
    report_number VARCHAR(100) NOT NULL UNIQUE,
    investigating_officer_id BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL,
    cause_of_fire VARCHAR(200), -- 'ELECTRICAL_SHORT_CIRCUIT', 'LPG_CYLINDER_BLAST', 'ARSON', 'UNDETERMINED'
    estimated_property_loss_inr NUMERIC(14,2),
    estimated_property_saved_inr NUMERIC(14,2),
    detailed_findings TEXT NOT NULL,
    finalized_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 9. Incident Evidence (Raw Media File Store - ADR-008 Option A)
CREATE TABLE IF NOT EXISTS firesphere.incident_evidence (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    incident_id BIGINT NOT NULL REFERENCES firesphere.incidents(id) ON DELETE CASCADE,
    evidence_type VARCHAR(50) NOT NULL, -- 'PHOTO', 'AUDIO_911', 'DRONE_VIDEO', 'CCTV_EXTRACT', 'DOCUMENT'
    file_path TEXT NOT NULL,            -- MinIO S3 Object Key
    file_size_bytes BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_hash_sha256 VARCHAR(64) NOT NULL,
    location GEOMETRY(Point, 4326),
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL
);

CREATE INDEX idx_evidence_incident ON firesphere.incident_evidence(incident_id);
CREATE INDEX idx_evidence_hash ON firesphere.incident_evidence(file_hash_sha256);

-- 10. Vehicle Crew Roster (Section 6.20)
CREATE TABLE IF NOT EXISTS firesphere.vehicle_crew (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    vehicle_id BIGINT NOT NULL REFERENCES firesphere.fire_vehicles(id) ON DELETE CASCADE,
    shift_date DATE NOT NULL,
    shift_name VARCHAR(50) NOT NULL, -- 'MORNING', 'EVENING', 'NIGHT'
    crew_member_user_id BIGINT NOT NULL REFERENCES firesphere.users(id) ON DELETE RESTRICT,
    assigned_role VARCHAR(100) NOT NULL DEFAULT 'FIREFIGHTER',
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_veh_crew_vehicle ON firesphere.vehicle_crew(vehicle_id);

-- 11. Vehicle Fitness & Certifications (Section 6.21)
CREATE TABLE IF NOT EXISTS firesphere.vehicle_fitness (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    vehicle_id BIGINT NOT NULL REFERENCES firesphere.fire_vehicles(id) ON DELETE CASCADE,
    inspection_date DATE NOT NULL,
    pump_test_result VARCHAR(50) NOT NULL, -- 'PASSED', 'FAILED', 'MARGINAL'
    brake_test_result VARCHAR(50) NOT NULL,
    fitness_certificate_expiry DATE NOT NULL,
    inspector_notes TEXT,
    inspected_by_user_id BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_veh_fitness_vehicle ON firesphere.vehicle_fitness(vehicle_id);

-- 12. Vehicle Fuel Logs (Section 6.22)
CREATE TABLE IF NOT EXISTS firesphere.vehicle_fuel_logs (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    vehicle_id BIGINT NOT NULL REFERENCES firesphere.fire_vehicles(id) ON DELETE CASCADE,
    fill_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fuel_liters NUMERIC(8,2) NOT NULL,
    odometer_reading_km NUMERIC(10,2) NOT NULL,
    fuel_pump_station VARCHAR(150),
    total_cost_inr NUMERIC(10,2),
    recorded_by_user_id BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL
);

CREATE INDEX idx_veh_fuel_vehicle ON firesphere.vehicle_fuel_logs(vehicle_id);

-- 13. Vehicle Location History / GPS Telemetry (Section 6.23)
CREATE TABLE IF NOT EXISTS firesphere.vehicle_location_history (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    vehicle_id BIGINT NOT NULL REFERENCES firesphere.fire_vehicles(id) ON DELETE CASCADE,
    location GEOMETRY(Point, 4326) NOT NULL,
    speed_kmh NUMERIC(5,2),
    heading_degrees NUMERIC(5,2),
    telemetry_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_veh_location_vehicle ON firesphere.vehicle_location_history(vehicle_id);
CREATE INDEX idx_veh_location_geom ON firesphere.vehicle_location_history USING GIST (location);
CREATE INDEX idx_veh_location_time ON firesphere.vehicle_location_history(telemetry_timestamp);

-- 14. Warehouses & Supply Depots (Section 6.25)
CREATE TABLE IF NOT EXISTS firesphere.warehouses (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    station_id BIGINT REFERENCES firesphere.fire_stations(id) ON DELETE SET NULL,
    warehouse_code VARCHAR(50) NOT NULL,
    warehouse_name VARCHAR(150) NOT NULL,
    location_address TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_warehouses_org_code UNIQUE (org_id, warehouse_code)
);

CREATE INDEX idx_warehouses_org ON firesphere.warehouses(org_id);

-- 15. Inventory Items (Legacy Verified Table 31)
CREATE TABLE IF NOT EXISTS firesphere.inventory_items (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    item_code VARCHAR(50) NOT NULL,
    item_name VARCHAR(150) NOT NULL,
    unit_of_measure VARCHAR(50) NOT NULL DEFAULT 'PIECES', -- 'PIECES', 'METERS', 'LITERS', 'KILOGRAMS'
    reorder_threshold INT DEFAULT 10,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inventory_org_item_code UNIQUE (org_id, item_code)
);

CREATE INDEX idx_inventory_items_org ON firesphere.inventory_items(org_id);

-- 16. Inventory Stock Balances (Section 6.24)
CREATE TABLE IF NOT EXISTS firesphere.inventory_stock (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT NOT NULL REFERENCES firesphere.organizations(id) ON DELETE RESTRICT,
    warehouse_id BIGINT NOT NULL REFERENCES firesphere.warehouses(id) ON DELETE RESTRICT,
    item_id BIGINT NOT NULL REFERENCES firesphere.inventory_items(id) ON DELETE RESTRICT,
    quantity_on_hand INT NOT NULL DEFAULT 0,
    quantity_allocated INT NOT NULL DEFAULT 0,
    last_audited_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inventory_warehouse_item UNIQUE (warehouse_id, item_id)
);

CREATE INDEX idx_inventory_stock_warehouse ON firesphere.inventory_stock(warehouse_id);

-- 17. Training Courses (Legacy Verified Table 47)
CREATE TABLE IF NOT EXISTS firesphere.training_courses (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT REFERENCES firesphere.organizations(id) ON DELETE CASCADE,
    course_code VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    duration_hours INT NOT NULL DEFAULT 40,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 18. System Change Data Capture (CDC) Audit Logs (Canonical Audit Entity)
CREATE TABLE IF NOT EXISTS firesphere.audit_logs (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    org_id BIGINT REFERENCES firesphere.organizations(id) ON DELETE SET NULL,
    table_name VARCHAR(100) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL, -- 'INSERT', 'UPDATE', 'DELETE'
    old_values JSONB,
    new_values JSONB,
    changed_fields TEXT[],
    user_id BIGINT REFERENCES firesphere.users(id) ON DELETE SET NULL,
    client_ip INET,
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_table_entity ON firesphere.audit_logs(table_name, entity_id);
CREATE INDEX idx_audit_logs_org ON firesphere.audit_logs(org_id);
CREATE INDEX idx_audit_logs_created_at ON firesphere.audit_logs(created_at);

-- 19. Generic Change Data Capture Trigger Function
CREATE OR REPLACE FUNCTION firesphere.firesphere_audit_trigger()
RETURNS TRIGGER AS $$
DECLARE
    v_user_id BIGINT;
    v_org_id BIGINT;
    v_old_values JSONB := NULL;
    v_new_values JSONB := NULL;
    v_entity_id BIGINT;
BEGIN
    -- Extract context safely
    v_org_id := firesphere.current_org_id();
    
    IF TG_OP = 'INSERT' THEN
        v_entity_id := NEW.id;
        v_new_values := to_jsonb(NEW);
    ELSIF TG_OP = 'UPDATE' THEN
        v_entity_id := NEW.id;
        v_old_values := to_jsonb(OLD);
        v_new_values := to_jsonb(NEW);
    ELSIF TG_OP = 'DELETE' THEN
        v_entity_id := OLD.id;
        v_old_values := to_jsonb(OLD);
    END IF;

    INSERT INTO firesphere.audit_logs (
        org_id,
        table_name,
        entity_id,
        action,
        old_values,
        new_values,
        user_id
    ) VALUES (
        COALESCE(v_org_id, CASE WHEN TG_OP = 'DELETE' THEN OLD.org_id ELSE NEW.org_id END),
        TG_TABLE_NAME,
        v_entity_id,
        TG_OP,
        v_old_values,
        v_new_values,
        v_user_id
    );

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Attach audit triggers to critical entities
CREATE TRIGGER trg_audit_buildings AFTER INSERT OR UPDATE OR DELETE ON firesphere.buildings
    FOR EACH ROW EXECUTE FUNCTION firesphere.firesphere_audit_trigger();

CREATE TRIGGER trg_audit_noc_applications AFTER INSERT OR UPDATE OR DELETE ON firesphere.noc_applications
    FOR EACH ROW EXECUTE FUNCTION firesphere.firesphere_audit_trigger();

CREATE TRIGGER trg_audit_inspections AFTER INSERT OR UPDATE OR DELETE ON firesphere.inspections
    FOR EACH ROW EXECUTE FUNCTION firesphere.firesphere_audit_trigger();

CREATE TRIGGER trg_audit_violations AFTER INSERT OR UPDATE OR DELETE ON firesphere.violations
    FOR EACH ROW EXECUTE FUNCTION firesphere.firesphere_audit_trigger();

CREATE TRIGGER trg_audit_incidents AFTER INSERT OR UPDATE OR DELETE ON firesphere.incidents
    FOR EACH ROW EXECUTE FUNCTION firesphere.firesphere_audit_trigger();

-- Attach updated_at triggers
CREATE TRIGGER trg_incidents_updated_at BEFORE UPDATE ON firesphere.incidents
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();

CREATE TRIGGER trg_fire_vehicles_updated_at BEFORE UPDATE ON firesphere.fire_vehicles
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();

CREATE TRIGGER trg_inventory_stock_updated_at BEFORE UPDATE ON firesphere.inventory_stock
    FOR EACH ROW EXECUTE FUNCTION firesphere.update_updated_at_column();
