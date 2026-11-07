-- Configurable taxes / levies, service charge and tips.
CREATE TABLE tax_rates (
    id UUID PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(60) NOT NULL,
    rate NUMERIC(6, 3) NOT NULL CHECK (rate >= 0),
    -- compound taxes are charged on the base plus all non-compound levies (Ghana VAT on top of NHIL/GETFund)
    compound BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER tax_rates_set_updated_at
BEFORE UPDATE ON tax_rates
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO tax_rates (id, code, name, rate, compound, display_order, active) VALUES
    ('a1000000-0000-0000-0000-000000000001', 'NHIL', 'NHIL', 2.500, FALSE, 1, TRUE),
    ('a1000000-0000-0000-0000-000000000002', 'GETFUND', 'GETFund Levy', 2.500, FALSE, 2, TRUE),
    ('a1000000-0000-0000-0000-000000000003', 'VAT', 'VAT', 15.000, TRUE, 3, TRUE);

CREATE TABLE billing_settings (
    id INT PRIMARY KEY CHECK (id = 1),
    service_charge_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
    service_charge_dine_in_only BOOLEAN NOT NULL DEFAULT TRUE,
    prices_include_tax BOOLEAN NOT NULL DEFAULT FALSE,
    tax_delivery_fee BOOLEAN NOT NULL DEFAULT TRUE,
    tips_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL
);

INSERT INTO billing_settings (id) VALUES (1);

ALTER TABLE orders ADD COLUMN service_charge NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN tax_total NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN tax_inclusive BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN tip_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

CREATE TABLE order_tax_lines (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(60) NOT NULL,
    rate NUMERIC(6, 3) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    display_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_order_tax_lines_order ON order_tax_lines(order_id);
