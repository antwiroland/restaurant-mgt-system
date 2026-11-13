-- Guests scanning a table's QR only see orders, bills and requests from the current sitting.
-- Set when a sitting ends (bill settled and food served, table closed/reversed, walkout) or a new one starts.
ALTER TABLE restaurant_tables ADD COLUMN guest_history_from TIMESTAMPTZ;
