ALTER TABLE cashier_shifts
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE customer_delivery_addresses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label VARCHAR(80) NOT NULL,
    address_line VARCHAR(255) NOT NULL,
    city VARCHAR(120),
    landmark VARCHAR(255),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customer_delivery_addresses_user ON customer_delivery_addresses(user_id);

CREATE TRIGGER customer_delivery_addresses_set_updated_at
BEFORE UPDATE ON customer_delivery_addresses
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
