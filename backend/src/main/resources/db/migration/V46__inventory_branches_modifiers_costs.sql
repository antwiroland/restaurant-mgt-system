-- Inventory per branch, modifier recipes, purchase costs and food cost.

-- 1. Stock per location. branch_id NULL is the restaurant's own stock when it has no branches (or for orders that
--    have no branch). A branch with no row for an ingredient does not track it: its orders use none of it.
CREATE TABLE ingredient_stock (
    id UUID PRIMARY KEY,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE,
    stock_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
    low_stock_alerted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX ux_ingredient_stock_location
    ON ingredient_stock(ingredient_id, COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid));

-- Dishes sold out at a location because an ingredient ran out there (replaces the global auto-86 flag).
CREATE TABLE menu_item_stockouts (
    id UUID PRIMARY KEY,
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX ux_menu_item_stockouts_location
    ON menu_item_stockouts(menu_item_id, COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid));

ALTER TABLE stock_movements ADD COLUMN branch_id UUID REFERENCES branches(id) ON DELETE SET NULL;
-- Cost per unit at the time of the movement (the purchase price for a restock), for food cost.
ALTER TABLE stock_movements ADD COLUMN unit_cost NUMERIC(12, 4);
ALTER TABLE stock_movements ADD COLUMN supplier VARCHAR(120);
CREATE INDEX idx_stock_movements_branch_created ON stock_movements(branch_id, created_at);

-- 2. Ingredients used by a modifier option (e.g. "Large" uses 0.1 kg more rice), on top of the dish's recipe.
CREATE TABLE modifier_option_ingredients (
    id UUID PRIMARY KEY,
    modifier_option_id UUID NOT NULL REFERENCES menu_modifier_options(id) ON DELETE CASCADE,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    quantity NUMERIC(14, 3) NOT NULL CHECK (quantity > 0),
    UNIQUE (modifier_option_id, ingredient_id)
);

-- Existing single stock pool -> the first branch (or the restaurant itself when there are no branches).
INSERT INTO ingredient_stock (id, ingredient_id, branch_id, stock_quantity, low_stock_alerted)
SELECT gen_random_uuid(), i.id,
       (SELECT b.id FROM branches b WHERE b.active ORDER BY b.created_at, b.id LIMIT 1),
       i.stock_quantity, i.low_stock_alerted
FROM ingredients i;

UPDATE stock_movements
SET branch_id = (SELECT b.id FROM branches b WHERE b.active ORDER BY b.created_at, b.id LIMIT 1),
    unit_cost = (SELECT i.cost_per_unit FROM ingredients i WHERE i.id = stock_movements.ingredient_id);

-- Dishes the old global auto-86 took off the menu: sold out at that same location, available elsewhere.
INSERT INTO menu_item_stockouts (id, menu_item_id, branch_id)
SELECT gen_random_uuid(), m.id, (SELECT b.id FROM branches b WHERE b.active ORDER BY b.created_at, b.id LIMIT 1)
FROM menu_items m WHERE m.auto_86;
UPDATE menu_items SET available = TRUE, auto_86 = FALSE WHERE auto_86;

-- ingredients.stock_quantity is kept as the total across locations; ingredients.low_stock_alerted is no longer used.
