-- Restaurant identity shown on customer pages, receipts and in the apps
-- (previously only configurable through environment variables).
CREATE TABLE restaurant_profile (
    id INT PRIMARY KEY CHECK (id = 1),
    name VARCHAR(120) NOT NULL,
    tagline VARCHAR(160),
    about TEXT,
    address VARCHAR(255),
    phone VARCHAR(40),
    email VARCHAR(160),
    website_url VARCHAR(255),
    hero_image_url VARCHAR(1000),
    currency VARCHAR(8) NOT NULL DEFAULT 'GHS',
    -- one line per entry, e.g. "Mon–Fri 11:00–22:00"
    opening_hours TEXT,
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    receipt_footer VARCHAR(500),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL
);

INSERT INTO restaurant_profile (id, name) VALUES (1, 'Restaurant');
