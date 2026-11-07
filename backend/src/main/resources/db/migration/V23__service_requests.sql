-- Guests at a table call a waiter or ask for the bill from the QR page; staff acknowledge and resolve.
CREATE TABLE service_requests (
    id UUID PRIMARY KEY,
    table_id UUID NOT NULL REFERENCES restaurant_tables(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('CALL_WAITER', 'BILL', 'OTHER')),
    note VARCHAR(280),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged_at TIMESTAMPTZ,
    acknowledged_by UUID REFERENCES users(id) ON DELETE SET NULL,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_service_requests_status ON service_requests (status, created_at);
CREATE INDEX idx_service_requests_table ON service_requests (table_id, created_at);
