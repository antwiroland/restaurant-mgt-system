-- Ingredient stock, recipes per menu item, stock movements, auto-86 and low-stock alerts.
CREATE TABLE ingredients (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    unit VARCHAR(20) NOT NULL,
    stock_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
    low_stock_threshold NUMERIC(14, 3) NOT NULL DEFAULT 0,
    cost_per_unit NUMERIC(12, 4),
    low_stock_alerted BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER ingredients_set_updated_at
BEFORE UPDATE ON ingredients
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE menu_item_ingredients (
    id UUID PRIMARY KEY,
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    quantity NUMERIC(14, 3) NOT NULL CHECK (quantity > 0),
    UNIQUE (menu_item_id, ingredient_id)
);

CREATE INDEX idx_menu_item_ingredients_ingredient ON menu_item_ingredients(ingredient_id);

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    change_quantity NUMERIC(14, 3) NOT NULL,
    balance_after NUMERIC(14, 3) NOT NULL,
    reason VARCHAR(20) NOT NULL,
    order_id UUID REFERENCES orders(id) ON DELETE SET NULL,
    note VARCHAR(500),
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stock_movements_ingredient_created ON stock_movements(ingredient_id, created_at DESC);

ALTER TABLE menu_items ADD COLUMN auto_86 BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN stock_deducted BOOLEAN NOT NULL DEFAULT FALSE;
