-- Cash is taken at the counter and recorded immediately (no payment provider).
ALTER TABLE payments ADD COLUMN amount_tendered NUMERIC(12, 2);
ALTER TABLE payments ADD COLUMN change_due NUMERIC(12, 2);
ALTER TABLE payments ADD COLUMN received_by UUID REFERENCES users(id) ON DELETE SET NULL;
-- Cash has no payer phone.
ALTER TABLE payments ALTER COLUMN momo_phone DROP NOT NULL;
