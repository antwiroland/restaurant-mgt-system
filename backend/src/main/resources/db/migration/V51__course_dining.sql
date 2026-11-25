-- Course-based dining: guests order a meal in courses (starter, main, dessert...) and staff release each course to the
-- kitchen when the table is ready for it. Beverages are never held.
--
-- Every batch of dishes sent to the kitchen is still an ordinary row in "orders" (priced, taxed and billed exactly as
-- before, and shown on the KDS as before); a held course is simply orders in status HELD that the KDS does not show
-- until a waiter, cashier or manager releases them.

-- Menu categories say what they are: beverages skip the course hold, and each category is prepared at one station.
ALTER TABLE categories ADD COLUMN is_beverage BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE categories ADD COLUMN kitchen_station VARCHAR(40);

-- A sensible start for existing menus (staff can change both on the menu page).
UPDATE categories
SET is_beverage = TRUE, kitchen_station = 'BAR'
WHERE lower(name) ~ '(drink|beverage|juice|smoothie|cocktail|mocktail|wine|beer|soda|soft|coffee|tea|bar)';

-- The courses a restaurant serves, in order. branch_id NULL: offered at every branch.
CREATE TABLE dining_courses (
    id UUID PRIMARY KEY,
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500),
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    default_station VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dining_courses_branch ON dining_courses (branch_id, active, display_order);

INSERT INTO dining_courses (id, branch_id, name, description, display_order, active) VALUES
    ('c0000000-0000-0000-0000-000000000001', NULL, 'Starter', 'Light dishes to begin the meal', 10, TRUE),
    ('c0000000-0000-0000-0000-000000000002', NULL, 'Soup', NULL, 20, TRUE),
    ('c0000000-0000-0000-0000-000000000003', NULL, 'Main Course', NULL, 30, TRUE),
    ('c0000000-0000-0000-0000-000000000004', NULL, 'Side Dish', 'Served with the main course', 40, TRUE),
    ('c0000000-0000-0000-0000-000000000005', NULL, 'Dessert', NULL, 50, TRUE);

-- One party's meal at a table: the parent of every course and every order of the sitting.
CREATE TABLE dining_orders (
    id UUID PRIMARY KEY,
    table_id UUID NOT NULL REFERENCES restaurant_tables(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dining_orders_branch_status ON dining_orders (branch_id, status);
-- At most one open meal per table.
CREATE UNIQUE INDEX uq_dining_orders_open_table ON dining_orders (table_id) WHERE status = 'OPEN';

-- A course of one meal. The course name and position are copied so renaming a course later never rewrites history.
CREATE TABLE dining_order_courses (
    id UUID PRIMARY KEY,
    dining_order_id UUID NOT NULL REFERENCES dining_orders(id) ON DELETE CASCADE,
    course_id UUID REFERENCES dining_courses(id) ON DELETE SET NULL,
    course_name VARCHAR(80) NOT NULL,
    sequence INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN
        ('PENDING', 'ON_HOLD', 'RELEASED', 'PREPARING', 'READY', 'PARTIALLY_SERVED', 'SERVED', 'CANCELLED')),
    released_at TIMESTAMPTZ,
    released_by UUID REFERENCES users(id) ON DELETE SET NULL,
    released_by_name VARCHAR(120),
    released_by_role VARCHAR(20),
    served_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dining_order_course UNIQUE (dining_order_id, course_id)
);
CREATE INDEX idx_dining_order_courses_order ON dining_order_courses (dining_order_id, sequence);

-- Orders belong to the meal, to a course (NULL: sent straight away, e.g. drinks) and to the station preparing them.
ALTER TABLE orders ADD COLUMN dining_order_id UUID REFERENCES dining_orders(id) ON DELETE SET NULL;
ALTER TABLE orders ADD COLUMN dining_order_course_id UUID REFERENCES dining_order_courses(id) ON DELETE SET NULL;
ALTER TABLE orders ADD COLUMN kitchen_station VARCHAR(40);
ALTER TABLE orders ADD COLUMN released_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN released_by UUID REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_orders_dining_order ON orders (dining_order_id);
CREATE INDEX idx_orders_dining_course ON orders (dining_order_course_id, status);

-- "Bring the next course": one request seen by the waiter and the cashier alike; whoever releases the course closes it.
CREATE TABLE course_release_requests (
    id UUID PRIMARY KEY,
    dining_order_id UUID NOT NULL REFERENCES dining_orders(id) ON DELETE CASCADE,
    dining_order_course_id UUID NOT NULL REFERENCES dining_order_courses(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    table_id UUID NOT NULL REFERENCES restaurant_tables(id) ON DELETE CASCADE,
    session_ref VARCHAR(120),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'CANCELLED', 'EXPIRED')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    handled_by UUID REFERENCES users(id) ON DELETE SET NULL,
    handled_by_role VARCHAR(20),
    handled_at TIMESTAMPTZ
);
CREATE INDEX idx_course_requests_branch_status ON course_release_requests (branch_id, status, requested_at);
-- Never two waiting requests for the same course.
CREATE UNIQUE INDEX uq_course_requests_pending ON course_release_requests (dining_order_course_id) WHERE status = 'PENDING';

-- Every change of a course's status, who made it and why.
CREATE TABLE course_status_history (
    id UUID PRIMARY KEY,
    dining_order_course_id UUID NOT NULL REFERENCES dining_order_courses(id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    changed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    changed_by_role VARCHAR(20),
    reason VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_course_status_history_course ON course_status_history (dining_order_course_id, created_at);
