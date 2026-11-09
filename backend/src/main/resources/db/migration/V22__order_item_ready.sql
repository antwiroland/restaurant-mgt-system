-- Kitchen marks individual items ready; the order moves to READY when every item is.
ALTER TABLE order_items ADD COLUMN ready_at TIMESTAMPTZ;
