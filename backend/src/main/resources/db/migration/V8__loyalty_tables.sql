CREATE TABLE loyalty_balances (
    id          UUID PRIMARY KEY,
    customer_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    points      INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_loyalty_balances_customer ON loyalty_balances(customer_id);

CREATE TRIGGER loyalty_balances_set_updated_at
BEFORE UPDATE ON loyalty_balances
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE loyalty_transactions (
    id          UUID PRIMARY KEY,
    customer_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    points      INT         NOT NULL,
    type        VARCHAR(10) NOT NULL,
    order_id    VARCHAR(36),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_loyalty_transactions_customer    ON loyalty_transactions(customer_id);
CREATE INDEX idx_loyalty_transactions_created_at  ON loyalty_transactions(created_at);
