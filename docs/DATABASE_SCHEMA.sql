-- ============================================================
-- Restaurant Manager — Database Schema
-- Migration V1: Full initial schema
-- Database: PostgreSQL 15+
-- ============================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- ENUMS
-- ============================================================

CREATE TYPE user_role AS ENUM ('ADMIN', 'MANAGER', 'CASHIER', 'CUSTOMER');

CREATE TYPE table_status AS ENUM ('AVAILABLE', 'OCCUPIED', 'RESERVED', 'MAINTENANCE');

CREATE TYPE reservation_status AS ENUM ('PENDING', 'CONFIRMED', 'SEATED', 'CANCELLED', 'NO_SHOW');

CREATE TYPE order_type AS ENUM ('PICKUP', 'DELIVERY', 'DINE_IN');

CREATE TYPE order_status AS ENUM (
    'PENDING',
    'CONFIRMED',
    'PREPARING',
    'READY',
    'OUT_FOR_DELIVERY',
    'COMPLETED',
    'CANCELLED',
    'VOIDED'
);

CREATE TYPE group_session_status AS ENUM ('OPEN', 'LOCKED', 'COMPLETED', 'CANCELLED');

CREATE TYPE payment_method AS ENUM ('MOBILE_MONEY', 'CARD', 'CASH', 'LOYALTY_POINTS');

CREATE TYPE payment_status AS ENUM (
    'INITIATED',
    'PENDING',
    'SUCCESS',
    'FAILED',
    'REFUNDED',
    'VOIDED',
    'PARTIALLY_REFUNDED'
);

CREATE TYPE discount_type AS ENUM ('FLAT', 'PERCENTAGE');

CREATE TYPE financial_event_type AS ENUM ('DISCOUNT', 'VOID', 'REFUND', 'PARTIAL_REFUND');

CREATE TYPE loyalty_tx_type AS ENUM ('EARN', 'REDEEM', 'EXPIRE', 'ADJUST');

CREATE TYPE audit_action AS ENUM (
    'USER_LOGIN',
    'USER_LOGOUT',
    'USER_CREATED',
    'USER_DEACTIVATED',
    'ROLE_ASSIGNED',
    'PIN_VERIFIED',
    'PIN_FAILED',
    'PIN_LOCKED',
    'ORDER_CANCELLED',
    'ORDER_VOIDED',
    'PAYMENT_REFUNDED',
    'DISCOUNT_APPLIED',
    'RECONCILIATION_SIGNED'
);

-- ============================================================
-- USERS
-- ============================================================

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(120)        NOT NULL,
    email           VARCHAR(255)        UNIQUE,
    phone           VARCHAR(20)         UNIQUE,
    password_hash   TEXT,                                       -- NULL for guest sessions
    role            user_role           NOT NULL DEFAULT 'CUSTOMER',
    pin_hash        TEXT,                                       -- Managers only
    pin_fail_count  SMALLINT            NOT NULL DEFAULT 0,
    pin_locked_until TIMESTAMPTZ,
    active          BOOLEAN             NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email  ON users (email);
CREATE INDEX idx_users_phone  ON users (phone);
CREATE INDEX idx_users_role   ON users (role);

-- ============================================================
-- REFRESH TOKENS
-- ============================================================

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- ============================================================
-- MENU CATEGORIES
-- ============================================================

CREATE TABLE menu_categories (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(80)     NOT NULL,
    description     TEXT,
    display_order   SMALLINT        NOT NULL DEFAULT 0,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ============================================================
-- MENU ITEMS
-- ============================================================

CREATE TABLE menu_items (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    category_id     UUID            REFERENCES menu_categories(id) ON DELETE SET NULL,
    name            VARCHAR(120)    NOT NULL,
    description     TEXT,
    price           NUMERIC(10, 2)  NOT NULL CHECK (price >= 0),
    image_url       TEXT,
    available       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_menu_items_category ON menu_items (category_id);
CREATE INDEX idx_menu_items_available ON menu_items (available);

-- ============================================================
-- RESTAURANT TABLES
-- ============================================================

CREATE TABLE restaurant_tables (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    number      VARCHAR(10)     NOT NULL UNIQUE,
    capacity    SMALLINT        NOT NULL CHECK (capacity > 0),
    zone        VARCHAR(50),                                    -- e.g. 'Ground Floor', 'Rooftop'
    status      table_status    NOT NULL DEFAULT 'AVAILABLE',
    qr_token    TEXT            UNIQUE,                        -- unique token encoded in QR
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tables_status ON restaurant_tables (status);

-- ============================================================
-- RESERVATIONS
-- ============================================================

CREATE TABLE reservations (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    table_id        UUID            REFERENCES restaurant_tables(id) ON DELETE SET NULL,
    customer_id     UUID            REFERENCES users(id) ON DELETE SET NULL,
    customer_name   VARCHAR(120)    NOT NULL,
    customer_phone  VARCHAR(20)     NOT NULL,
    party_size      SMALLINT        NOT NULL CHECK (party_size > 0),
    reserved_at     TIMESTAMPTZ     NOT NULL,
    duration_mins   SMALLINT        NOT NULL DEFAULT 90,
    status          reservation_status NOT NULL DEFAULT 'PENDING',
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reservations_table      ON reservations (table_id);
CREATE INDEX idx_reservations_customer   ON reservations (customer_id);
CREATE INDEX idx_reservations_date       ON reservations (reserved_at);
CREATE INDEX idx_reservations_status     ON reservations (status);

-- ============================================================
-- PROMO CODES
-- ============================================================

CREATE TABLE promo_codes (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code            VARCHAR(30)     NOT NULL UNIQUE,
    description     TEXT,
    discount_type   discount_type   NOT NULL,
    discount_value  NUMERIC(10, 2)  NOT NULL CHECK (discount_value > 0),
    min_order_amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    max_discount    NUMERIC(10, 2),                            -- cap for percentage discounts
    expiry_date     TIMESTAMPTZ,
    usage_limit     INT,                                       -- NULL = unlimited
    usage_count     INT             NOT NULL DEFAULT 0,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_promo_codes_code   ON promo_codes (code);
CREATE INDEX idx_promo_codes_active ON promo_codes (active);

-- ============================================================
-- GROUP ORDER SESSIONS
-- ============================================================

CREATE TABLE group_order_sessions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_code    VARCHAR(10)         NOT NULL UNIQUE,
    host_user_id    UUID                REFERENCES users(id) ON DELETE SET NULL,
    status          group_session_status NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_group_sessions_code   ON group_order_sessions (session_code);
CREATE INDEX idx_group_sessions_status ON group_order_sessions (status);

CREATE TABLE group_session_participants (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id      UUID        NOT NULL REFERENCES group_order_sessions(id) ON DELETE CASCADE,
    user_id         UUID        REFERENCES users(id) ON DELETE SET NULL,
    display_name    VARCHAR(60),                               -- for guests
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_group_participants_session ON group_session_participants (session_id);

-- ============================================================
-- ORDERS
-- ============================================================

CREATE TABLE orders (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id         UUID            REFERENCES users(id) ON DELETE SET NULL,
    created_by          UUID            REFERENCES users(id) ON DELETE SET NULL,  -- staff who entered it
    type                order_type      NOT NULL,
    status              order_status    NOT NULL DEFAULT 'PENDING',
    table_id            UUID            REFERENCES restaurant_tables(id) ON DELETE SET NULL,
    delivery_address    TEXT,
    pickup_code         VARCHAR(8)      UNIQUE,
    group_session_id    UUID            REFERENCES group_order_sessions(id) ON DELETE SET NULL,
    promo_code_id       UUID            REFERENCES promo_codes(id) ON DELETE SET NULL,
    subtotal            NUMERIC(10, 2)  NOT NULL DEFAULT 0,
    discount_amount     NUMERIC(10, 2)  NOT NULL DEFAULT 0,
    loyalty_redeemed    NUMERIC(10, 2)  NOT NULL DEFAULT 0,
    total               NUMERIC(10, 2)  NOT NULL DEFAULT 0,
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_customer     ON orders (customer_id);
CREATE INDEX idx_orders_status       ON orders (status);
CREATE INDEX idx_orders_type         ON orders (type);
CREATE INDEX idx_orders_table        ON orders (table_id);
CREATE INDEX idx_orders_created_at   ON orders (created_at);
CREATE INDEX idx_orders_group        ON orders (group_session_id);

-- ============================================================
-- ORDER ITEMS
-- ============================================================

CREATE TABLE order_items (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id        UUID            NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    participant_id  UUID            REFERENCES group_session_participants(id) ON DELETE SET NULL,
    menu_item_id    UUID            REFERENCES menu_items(id) ON DELETE SET NULL,
    name_snapshot   VARCHAR(120)    NOT NULL,                  -- denormalised at order time
    price_snapshot  NUMERIC(10, 2)  NOT NULL,                  -- price at time of order
    quantity        SMALLINT        NOT NULL CHECK (quantity > 0),
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_items_order ON order_items (order_id);

-- ============================================================
-- PAYMENTS
-- ============================================================

CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id            UUID            NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    amount              NUMERIC(10, 2)  NOT NULL CHECK (amount > 0),
    currency            CHAR(3)         NOT NULL DEFAULT 'GHS',
    method              payment_method  NOT NULL DEFAULT 'MOBILE_MONEY',
    status              payment_status  NOT NULL DEFAULT 'INITIATED',
    paystack_reference  TEXT            UNIQUE,
    idempotency_key     TEXT            NOT NULL UNIQUE,
    momo_phone          VARCHAR(20),
    refunded_amount     NUMERIC(10, 2)  NOT NULL DEFAULT 0,
    failure_reason      TEXT,
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_order       ON payments (order_id);
CREATE INDEX idx_payments_status      ON payments (status);
CREATE INDEX idx_payments_reference   ON payments (paystack_reference);

-- ============================================================
-- LOYALTY
-- ============================================================

CREATE TABLE loyalty_balances (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    points      INT         NOT NULL DEFAULT 0 CHECK (points >= 0),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE loyalty_transactions (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    order_id    UUID            REFERENCES orders(id) ON DELETE SET NULL,
    points      INT             NOT NULL,                      -- positive = earn, negative = redeem
    type        loyalty_tx_type NOT NULL,
    note        TEXT,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_loyalty_tx_customer ON loyalty_transactions (customer_id);
CREATE INDEX idx_loyalty_tx_order    ON loyalty_transactions (order_id);

-- ============================================================
-- FINANCIAL EVENTS (Discounts, Voids, Refunds)
-- ============================================================

CREATE TABLE financial_events (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id        UUID                NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    payment_id      UUID                REFERENCES payments(id) ON DELETE SET NULL,
    actor_id        UUID                NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    event_type      financial_event_type NOT NULL,
    amount          NUMERIC(10, 2)      NOT NULL DEFAULT 0,
    reason          TEXT                NOT NULL,
    pin_verified    BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fin_events_order ON financial_events (order_id);
CREATE INDEX idx_fin_events_actor ON financial_events (actor_id);
CREATE INDEX idx_fin_events_type  ON financial_events (event_type);

-- ============================================================
-- RECONCILIATION
-- ============================================================

CREATE TABLE daily_reconciliations (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    date                DATE            NOT NULL UNIQUE,
    total_sales         NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    total_momo          NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    total_card          NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    total_cash          NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    total_refunds       NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    total_voids         NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    total_discounts     NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    order_count         INT             NOT NULL DEFAULT 0,
    signed_by           UUID            REFERENCES users(id) ON DELETE SET NULL,
    signed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ============================================================
-- AUDIT LOG
-- ============================================================

CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    actor_id        UUID            REFERENCES users(id) ON DELETE SET NULL,
    action          audit_action    NOT NULL,
    entity_type     VARCHAR(50),
    entity_id       UUID,
    metadata        JSONB,
    ip_address      INET,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_actor      ON audit_logs (actor_id);
CREATE INDEX idx_audit_action     ON audit_logs (action);
CREATE INDEX idx_audit_entity     ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_created    ON audit_logs (created_at);

-- ============================================================
-- UPDATED_AT TRIGGER FUNCTION
-- ============================================================

CREATE OR REPLACE FUNCTION trigger_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply updated_at trigger to all relevant tables
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'users', 'menu_categories', 'menu_items', 'restaurant_tables',
        'reservations', 'group_order_sessions', 'orders', 'payments', 'loyalty_balances'
    ] LOOP
        EXECUTE format('
            CREATE TRIGGER set_updated_at
            BEFORE UPDATE ON %I
            FOR EACH ROW EXECUTE FUNCTION trigger_set_updated_at();
        ', t);
    END LOOP;
END;
$$;
