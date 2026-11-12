-- Name and phone a dine-in guest gives on the table QR page (no account needed).
-- Kept off the order notes so the kitchen ticket and the public table tracking never show the phone number.
ALTER TABLE orders ADD COLUMN guest_name VARCHAR(80);
ALTER TABLE orders ADD COLUMN guest_phone VARCHAR(20);
