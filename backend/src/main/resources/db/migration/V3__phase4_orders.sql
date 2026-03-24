CREATE TABLE group_order_sessions (
    id UUID PRIMARY KEY,
    session_code VARCHAR(20) NOT NULL UNIQUE,
    host_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE group_session_participants (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES group_order_sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_group_session_participant_unique
ON group_session_participants(session_id, user_id);

CREATE TABLE group_session_items (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES group_order_sessions(id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES group_session_participants(id) ON DELETE CASCADE,
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE RESTRICT,
    quantity INT NOT NULL,
    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    table_id UUID REFERENCES restaurant_tables(id) ON DELETE SET NULL,
    delivery_address VARCHAR(1000),
    pickup_time TIMESTAMPTZ,
    pickup_code VARCHAR(20) UNIQUE,
    estimated_delivery_time TIMESTAMPTZ,
    group_session_id UUID REFERENCES group_order_sessions(id) ON DELETE SET NULL,
    cancel_reason VARCHAR(1000),
    notes VARCHAR(1000),
    subtotal NUMERIC(12,2) NOT NULL,
    total NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_customer_user_id ON orders(customer_user_id);
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_pickup_code ON orders(pickup_code);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE RESTRICT,
    participant_id UUID REFERENCES group_session_participants(id) ON DELETE SET NULL,
    name_snapshot VARCHAR(150) NOT NULL,
    price_snapshot NUMERIC(12,2) NOT NULL,
    quantity INT NOT NULL,
    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);

CREATE TRIGGER group_order_sessions_set_updated_at
BEFORE UPDATE ON group_order_sessions
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER group_session_items_set_updated_at
BEFORE UPDATE ON group_session_items
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER orders_set_updated_at
BEFORE UPDATE ON orders
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
