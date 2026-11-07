-- Delivery zones with fees, rider assignment and live rider location.
CREATE TABLE delivery_zones (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    fee NUMERIC(12, 2) NOT NULL DEFAULT 0,
    min_order_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    estimated_minutes INT NOT NULL DEFAULT 45,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_delivery_zones_active ON delivery_zones(active);

CREATE TRIGGER delivery_zones_set_updated_at
BEFORE UPDATE ON delivery_zones
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE orders ADD COLUMN delivery_zone_id UUID REFERENCES delivery_zones(id) ON DELETE SET NULL;
ALTER TABLE orders ADD COLUMN rider_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE orders ADD COLUMN rider_assigned_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN delivered_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN rider_latitude DOUBLE PRECISION;
ALTER TABLE orders ADD COLUMN rider_longitude DOUBLE PRECISION;
ALTER TABLE orders ADD COLUMN rider_location_at TIMESTAMPTZ;

CREATE INDEX idx_orders_rider ON orders(rider_user_id) WHERE rider_user_id IS NOT NULL;
