-- Flyway Migration V3: Dispatch Management Enhancements & Auditable History

-- 1. Add priority and operational timestamps to dispatch_orders
ALTER TABLE dispatch_orders ADD COLUMN IF NOT EXISTS priority VARCHAR(30) DEFAULT 'MEDIUM' NOT NULL;
ALTER TABLE dispatch_orders ADD COLUMN IF NOT EXISTS en_route_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE dispatch_orders ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE dispatch_orders ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(500);

-- 2. Index for tenant and priority filtering
CREATE INDEX IF NOT EXISTS idx_dispatch_tenant_priority ON dispatch_orders(tenant_id, priority);

-- 3. Create dispatch status history table for audit trails
CREATE TABLE IF NOT EXISTS dispatch_status_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    dispatch_order_id UUID NOT NULL REFERENCES dispatch_orders(id) ON DELETE CASCADE,
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

CREATE INDEX IF NOT EXISTS idx_dispatch_history_order ON dispatch_status_history(dispatch_order_id);
CREATE INDEX IF NOT EXISTS idx_dispatch_history_tenant_time ON dispatch_status_history(tenant_id, transitioned_at);
