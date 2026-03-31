CREATE TABLE promo_codes (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(1000),
    discount_type VARCHAR(20) NOT NULL,
    discount_value NUMERIC(10, 2) NOT NULL,
    min_order_amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    max_discount NUMERIC(10, 2),
    expiry_date TIMESTAMPTZ,
    usage_limit INT,
    usage_count INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_promo_codes_code ON promo_codes(code);
CREATE INDEX idx_promo_codes_active ON promo_codes(active);

INSERT INTO promo_codes (id, code, description, discount_type, discount_value, min_order_amount, max_discount, expiry_date, usage_limit, usage_count, active)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'SAVE10', '10 percent off orders from 20 GHS', 'PERCENTAGE', 10.00, 20.00, NULL, NOW() + INTERVAL '365 days', 1000, 0, TRUE),
    ('22222222-2222-2222-2222-222222222222', 'FLAT5', '5 GHS off orders from 15 GHS', 'FLAT', 5.00, 15.00, NULL, NOW() + INTERVAL '365 days', 1000, 0, TRUE),
    ('33333333-3333-3333-3333-333333333333', 'WELCOME10', '10 percent welcome discount from 20 GHS', 'PERCENTAGE', 10.00, 20.00, NULL, NOW() + INTERVAL '365 days', 1000, 0, TRUE);
