-- Order pricing breakdown: total = subtotal + delivery_fee - promo - offer - loyalty - manual discount
ALTER TABLE orders ADD COLUMN promo_code VARCHAR(30);
ALTER TABLE orders ADD COLUMN promo_discount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN offer_discount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN loyalty_points_redeemed INT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN loyalty_discount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN loyalty_points_awarded INT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN manual_discount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN manual_discount_reason VARCHAR(500);
ALTER TABLE orders ADD COLUMN delivery_fee NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN voided_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN voided_by UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_orders_promo_code ON orders(promo_code);

-- Refund tracking on payments
ALTER TABLE payments ADD COLUMN refunded_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN refunded_at TIMESTAMPTZ;

CREATE TABLE payment_refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    amount NUMERIC(12, 2) NOT NULL,
    provider_reference VARCHAR(120),
    provider_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    requested_by UUID REFERENCES users(id) ON DELETE SET NULL,
    approved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_refunds_payment ON payment_refunds(payment_id);
CREATE INDEX idx_payment_refunds_created_at ON payment_refunds(created_at);

-- Daily reconciliation sign-off (one per business date per branch; NULL branch = all branches)
CREATE TABLE reconciliation_signoffs (
    id UUID PRIMARY KEY,
    business_date DATE NOT NULL,
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE,
    gross_sales NUMERIC(12, 2) NOT NULL,
    total_refunds NUMERIC(12, 2) NOT NULL,
    total_voids NUMERIC(12, 2) NOT NULL,
    total_discounts NUMERIC(12, 2) NOT NULL,
    net_sales NUMERIC(12, 2) NOT NULL,
    expected_cash NUMERIC(12, 2) NOT NULL,
    counted_cash NUMERIC(12, 2),
    cash_variance NUMERIC(12, 2),
    notes VARCHAR(1000),
    signed_by UUID NOT NULL REFERENCES users(id),
    signed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_reconciliation_signoff_date_branch
    ON reconciliation_signoffs(business_date, COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid));

-- Buy-X-Get-Y offers
CREATE TABLE menu_offers (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    buy_quantity INT NOT NULL CHECK (buy_quantity > 0),
    free_quantity INT NOT NULL CHECK (free_quantity > 0),
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_menu_offers_item_active ON menu_offers(menu_item_id, active);

CREATE TRIGGER menu_offers_set_updated_at
BEFORE UPDATE ON menu_offers
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
