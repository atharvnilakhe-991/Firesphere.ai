-- Flyway Migration V2: Incident Lifecycle Timestamps, Assigned Resources & Auditable History

ALTER TABLE incidents ADD COLUMN IF NOT EXISTS verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS dispatched_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS en_route_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS on_scene_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS under_control_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS closure_remarks VARCHAR(1000);

CREATE TABLE IF NOT EXISTS incident_assigned_resources (
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    resource_reference VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS incident_status_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
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

CREATE INDEX IF NOT EXISTS idx_history_incident ON incident_status_history(incident_id);
CREATE INDEX IF NOT EXISTS idx_history_tenant_time ON incident_status_history(tenant_id, transitioned_at);
