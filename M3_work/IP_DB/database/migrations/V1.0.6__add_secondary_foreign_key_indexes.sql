-- =============================================================================
-- Migration: V1.0.6__add_secondary_foreign_key_indexes.sql
-- Description: P1 Performance Optimization — Supplementary B-tree indexes on 
--              un-indexed foreign key and tenant filtering columns to accelerate
--              relational joins, cascading validations, and tenant filtering.
-- Author: Member 3 (Database, Infrastructure & DevOps)
-- =============================================================================

-- 1. Index on incident_dispatches(dispatched_station_id)
-- Speeds up turnouts/dispatches filtered or joined by fire station
CREATE INDEX IF NOT EXISTS idx_dispatches_station_id 
    ON firesphere.incident_dispatches(dispatched_station_id);

-- 2. Index on incident_dispatches(dispatched_by_user_id)
-- Speeds up dispatcher lookup and auditing of dispatch actions by user
CREATE INDEX IF NOT EXISTS idx_dispatches_user_id 
    ON firesphere.incident_dispatches(dispatched_by_user_id);

-- 3. Index on vehicle_fitness(inspected_by_user_id)
-- Speeds up fitness certification history by inspecting officer
CREATE INDEX IF NOT EXISTS idx_veh_fitness_user_id 
    ON firesphere.vehicle_fitness(inspected_by_user_id);

-- 4. Index on vehicle_fuel_logs(recorded_by_user_id)
-- Speeds up fuel log lookups and auditing by logging officer
CREATE INDEX IF NOT EXISTS idx_veh_fuel_user_id 
    ON firesphere.vehicle_fuel_logs(recorded_by_user_id);

-- 5. Index on training_courses(org_id)
-- Essential for multi-tenant course catalog queries and tenant RLS filtering
CREATE INDEX IF NOT EXISTS idx_training_courses_org_id 
    ON firesphere.training_courses(org_id);

-- 6. Index on building_fire_equipment(org_id)
-- Accelerates direct organization-wide life safety equipment audits and RLS checks
CREATE INDEX IF NOT EXISTS idx_building_equipment_org_id 
    ON firesphere.building_fire_equipment(org_id);

-- 7. Index on building_risk_profiles(org_id)
-- Accelerates tenant-wide building risk priority queries and RLS filtering
CREATE INDEX IF NOT EXISTS idx_building_risk_org_id 
    ON firesphere.building_risk_profiles(org_id);
