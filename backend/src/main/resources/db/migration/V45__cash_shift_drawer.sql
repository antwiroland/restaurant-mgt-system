-- Cash shifts count the drawer: each cash payment and cash refund belongs to the open shift of the staff member who
-- took or handed back the money, and pay-ins/pay-outs (petty cash, float top-ups) are recorded against the shift.

ALTER TABLE payments ADD COLUMN shift_id UUID REFERENCES cashier_shifts(id) ON DELETE SET NULL;
CREATE INDEX idx_payments_shift ON payments(shift_id);

ALTER TABLE payment_refunds ADD COLUMN shift_id UUID REFERENCES cashier_shifts(id) ON DELETE SET NULL;
CREATE INDEX idx_payment_refunds_shift ON payment_refunds(shift_id);

CREATE TABLE cash_movements (
    id UUID PRIMARY KEY,
    shift_id UUID NOT NULL REFERENCES cashier_shifts(id) ON DELETE CASCADE,
    movement_type VARCHAR(10) NOT NULL CHECK (movement_type IN ('PAY_IN', 'PAY_OUT')),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    reason VARCHAR(255) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cash_movements_shift ON cash_movements(shift_id);

CREATE INDEX idx_cashier_shifts_opened ON cashier_shifts(opened_at);

-- Existing cash payments and refunds: attach them to the shift the same person had open at the time.
UPDATE payments p SET shift_id = s.id
FROM cashier_shifts s
WHERE p.method = 'CASH' AND p.shift_id IS NULL AND p.paid_at IS NOT NULL
  AND p.received_by = s.cashier_user_id
  AND p.paid_at >= s.opened_at AND (s.closed_at IS NULL OR p.paid_at <= s.closed_at);

UPDATE payment_refunds r SET shift_id = s.id
FROM cashier_shifts s, payments p
WHERE r.payment_id = p.id AND p.method = 'CASH' AND r.shift_id IS NULL
  AND r.requested_by = s.cashier_user_id
  AND r.created_at >= s.opened_at AND (s.closed_at IS NULL OR r.created_at <= s.closed_at);
