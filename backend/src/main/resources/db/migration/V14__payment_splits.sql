-- Split bills: each participant pays their own share with their own payment.
CREATE TABLE payment_splits (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    participant_id UUID REFERENCES group_session_participants(id) ON DELETE SET NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    display_name VARCHAR(120) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    status VARCHAR(20) NOT NULL,
    payment_id UUID REFERENCES payments(id) ON DELETE SET NULL,
    paid_at TIMESTAMPTZ,
    position INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_splits_order ON payment_splits(order_id);
CREATE INDEX idx_payment_splits_user ON payment_splits(user_id);

ALTER TABLE payments ADD COLUMN split_id UUID REFERENCES payment_splits(id) ON DELETE SET NULL;
CREATE INDEX idx_payments_split ON payments(split_id);
