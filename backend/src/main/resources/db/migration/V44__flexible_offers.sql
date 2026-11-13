-- Offers beyond "buy X get X free": buy one dish get another free, percentage off a dish, and fixed-price combos;
-- a daily time window (happy hour) and days of the week; and an optional branch.

ALTER TABLE menu_offers ADD COLUMN offer_type VARCHAR(30) NOT NULL DEFAULT 'BUY_X_GET_Y';

-- Combos have no single dish; percentage and combo offers have no buy/free quantities.
ALTER TABLE menu_offers ALTER COLUMN menu_item_id DROP NOT NULL;
ALTER TABLE menu_offers ALTER COLUMN buy_quantity DROP NOT NULL;
ALTER TABLE menu_offers ALTER COLUMN free_quantity DROP NOT NULL;

-- BUY_X_GET_OTHER: buy X of menu_item_id, get free_quantity of free_menu_item_id.
ALTER TABLE menu_offers ADD COLUMN free_menu_item_id UUID REFERENCES menu_items(id) ON DELETE CASCADE;
-- PERCENT_OFF: percentage off every unit of menu_item_id.
ALTER TABLE menu_offers ADD COLUMN percent_off NUMERIC(5,2) CHECK (percent_off IS NULL OR (percent_off > 0 AND percent_off <= 100));
-- COMBO: the dishes in menu_offer_combo_items together for combo_price.
ALTER TABLE menu_offers ADD COLUMN combo_price NUMERIC(12,2) CHECK (combo_price IS NULL OR combo_price >= 0);

-- Happy hour: comma-separated days (MON..SUN, empty = every day) and a daily window in the venue's time zone.
-- An end before the start runs past midnight.
ALTER TABLE menu_offers ADD COLUMN days_of_week VARCHAR(40);
ALTER TABLE menu_offers ADD COLUMN daily_start_time TIME;
ALTER TABLE menu_offers ADD COLUMN daily_end_time TIME;

-- NULL = every branch.
ALTER TABLE menu_offers ADD COLUMN branch_id UUID REFERENCES branches(id) ON DELETE CASCADE;

CREATE TABLE menu_offer_combo_items (
    offer_id UUID NOT NULL REFERENCES menu_offers(id) ON DELETE CASCADE,
    menu_item_id UUID NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    quantity INT NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (offer_id, menu_item_id)
);

CREATE INDEX idx_menu_offers_active ON menu_offers(active);
