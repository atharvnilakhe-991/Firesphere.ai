-- Flyway Migration V5: Fire Store & Inventory Enhancements

-- 1. Add description, location_in_store, and is_active to inventory_items
ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS description VARCHAR(500);
ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS location_in_store VARCHAR(100);

-- 2. Add resulting_quantity to inventory_stock_movements
ALTER TABLE inventory_stock_movements ADD COLUMN IF NOT EXISTS resulting_quantity INTEGER;

-- 3. Composite indexes for tenant-scoped querying, station lookups, and auditing
CREATE INDEX IF NOT EXISTS idx_inventory_tenant_cat ON inventory_items(tenant_id, category);
CREATE INDEX IF NOT EXISTS idx_inventory_tenant_active ON inventory_items(tenant_id, is_active);
CREATE INDEX IF NOT EXISTS idx_movements_tenant_item ON inventory_stock_movements(tenant_id, item_id);
CREATE INDEX IF NOT EXISTS idx_movements_tenant_time ON inventory_stock_movements(tenant_id, created_at);
