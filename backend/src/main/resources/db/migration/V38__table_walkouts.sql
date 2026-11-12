-- Walkouts: a manager closes out a table whose guests left without paying. The unpaid orders are written off
-- (orders.walkout_id) so they stop blocking the table, and the record feeds the walkout report.
CREATE TABLE table_walkouts (
    id UUID PRIMARY KEY,
    table_id UUID REFERENCES restaurant_tables(id) ON DELETE SET NULL,
    table_number VARCHAR(40) NOT NULL,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    branch_name VARCHAR(160),
    amount_owed NUMERIC(12, 2) NOT NULL CHECK (amount_owed >= 0),
    order_count INT NOT NULL CHECK (order_count > 0),
    reason VARCHAR(30) NOT NULL CHECK (reason IN ('WALKED_OUT', 'REFUSED_TO_PAY', 'OTHER')),
    note VARCHAR(500),
    order_taken_by VARCHAR(500),
    guest_phones VARCHAR(200),
    first_order_at TIMESTAMPTZ,
    recorded_by UUID REFERENCES users(id) ON DELETE SET NULL,
    recorded_by_name VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_table_walkouts_created ON table_walkouts (created_at);
CREATE INDEX idx_table_walkouts_branch_created ON table_walkouts (branch_id, created_at);

ALTER TABLE orders ADD COLUMN walkout_id UUID REFERENCES table_walkouts(id) ON DELETE SET NULL;
CREATE INDEX idx_orders_walkout ON orders (walkout_id);

-- When the kitchen marked a dine-in order served (READY -> COMPLETED): drives the "served but unpaid" alert.
ALTER TABLE orders ADD COLUMN served_at TIMESTAMPTZ;
