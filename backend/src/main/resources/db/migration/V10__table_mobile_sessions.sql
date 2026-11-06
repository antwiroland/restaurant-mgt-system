-- Mobile/web QR sessions linked to a dine-in table; closed when the table is settled or reversed.
CREATE TABLE table_mobile_sessions (
    id UUID PRIMARY KEY,
    table_id UUID NOT NULL REFERENCES restaurant_tables(id) ON DELETE CASCADE,
    session_id VARCHAR(120) NOT NULL,
    customer_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ
);

CREATE INDEX idx_table_mobile_sessions_table_open ON table_mobile_sessions(table_id) WHERE closed_at IS NULL;
CREATE UNIQUE INDEX uq_table_mobile_sessions_open_session
    ON table_mobile_sessions(table_id, session_id) WHERE closed_at IS NULL;
