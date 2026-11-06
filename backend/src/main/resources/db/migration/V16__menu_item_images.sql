-- Storage reference for uploaded menu item images (Cloudinary public_id or local file key).
ALTER TABLE menu_items ADD COLUMN image_public_id VARCHAR(255);
