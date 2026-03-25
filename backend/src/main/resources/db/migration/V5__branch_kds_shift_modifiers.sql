CREATE TABLE branches (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE users
    ADD COLUMN branch_id UUID REFERENCES branches(id) ON DELETE SET NULL;

ALTER TABLE restaurant_tables
    ADD COLUMN branch_id UUID REFERENCES branches(id) ON DELETE SET NULL;

ALTER TABLE orders
    ADD COLUMN branch_id UUID REFERENCES branches(id) ON DELETE SET NULL;

CREATE TABLE cashier_shifts (
    id UUID PRIMARY KEY,
    cashier_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL,
    opening_cash NUMERIC(12,2) NOT NULL,
    closing_cash NUMERIC(12,2),
    expected_cash NUMERIC(12,2),
    variance NUMERIC(12,2),
    notes VARCHAR(1000),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cashier_shifts_cashier_status ON cashier_shifts(cashier_user_id, status);

CREATE TABLE menu_modifier_groups (
    id UUID PRIMARY KEY,
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    selection_type VARCHAR(20) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    min_select INT,
    max_select INT,
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_modifier_groups_menu_item ON menu_modifier_groups(menu_item_id);

CREATE TABLE menu_modifier_options (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES menu_modifier_groups(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    price_delta NUMERIC(12,2) NOT NULL DEFAULT 0,
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_modifier_options_group ON menu_modifier_options(group_id);

CREATE TABLE order_item_modifiers (
    id UUID PRIMARY KEY,
    order_item_id UUID NOT NULL REFERENCES order_items(id) ON DELETE CASCADE,
    modifier_option_id UUID REFERENCES menu_modifier_options(id) ON DELETE SET NULL,
    group_name_snapshot VARCHAR(120) NOT NULL,
    option_name_snapshot VARCHAR(120) NOT NULL,
    price_delta_snapshot NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_item_modifiers_item ON order_item_modifiers(order_item_id);

CREATE TRIGGER branches_set_updated_at
BEFORE UPDATE ON branches
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER cashier_shifts_set_updated_at
BEFORE UPDATE ON cashier_shifts
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER menu_modifier_groups_set_updated_at
BEFORE UPDATE ON menu_modifier_groups
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER menu_modifier_options_set_updated_at
BEFORE UPDATE ON menu_modifier_options
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
