-- HR foundation: departments, positions, employee registry, contracts, documents and HR permission grants.
-- Employees reference the existing users table for login identity (optional, one-to-one); users stays the only auth identity.

CREATE TABLE hr_sequences (
    name VARCHAR(40) PRIMARY KEY,
    next_value BIGINT NOT NULL CHECK (next_value > 0)
);

INSERT INTO hr_sequences (name, next_value) VALUES ('EMPLOYEE', 1), ('CONTRACT', 1);

CREATE TABLE hr_departments (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(30) NOT NULL,
    description VARCHAR(1000),
    branch_id UUID REFERENCES branches(id) ON DELETE RESTRICT,
    head_employee_id UUID,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER hr_departments_set_updated_at
BEFORE UPDATE ON hr_departments
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hr_positions (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    title VARCHAR(120) NOT NULL,
    department_id UUID REFERENCES hr_departments(id) ON DELETE RESTRICT,
    category VARCHAR(30) NOT NULL,
    description VARCHAR(2000),
    requirements VARCHAR(2000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hr_positions_department ON hr_positions(department_id);

CREATE TRIGGER hr_positions_set_updated_at
BEFORE UPDATE ON hr_positions
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hr_employees (
    id UUID PRIMARY KEY,
    employee_number VARCHAR(30) NOT NULL UNIQUE,
    user_id UUID UNIQUE REFERENCES users(id) ON DELETE SET NULL,
    first_name VARCHAR(80) NOT NULL,
    middle_name VARCHAR(80),
    last_name VARCHAR(80),
    gender VARCHAR(20),
    date_of_birth DATE,
    email VARCHAR(255),
    phone VARCHAR(20),
    address VARCHAR(500),
    department_id UUID REFERENCES hr_departments(id) ON DELETE RESTRICT,
    position_id UUID REFERENCES hr_positions(id) ON DELETE RESTRICT,
    employment_type VARCHAR(20) NOT NULL,
    employment_status VARCHAR(20) NOT NULL,
    hire_date DATE NOT NULL,
    exit_date DATE,
    exit_reason VARCHAR(500),
    reporting_manager_id UUID REFERENCES hr_employees(id) ON DELETE SET NULL,
    primary_branch_id UUID REFERENCES branches(id) ON DELETE RESTRICT,
    qualifications VARCHAR(2000),
    experience VARCHAR(2000),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (reporting_manager_id IS NULL OR reporting_manager_id <> id),
    CHECK (exit_date IS NULL OR exit_date >= hire_date)
);

CREATE INDEX idx_hr_employees_branch_status ON hr_employees(primary_branch_id, employment_status);
CREATE INDEX idx_hr_employees_department ON hr_employees(department_id);
CREATE INDEX idx_hr_employees_position ON hr_employees(position_id);
CREATE INDEX idx_hr_employees_last_first ON hr_employees(LOWER(last_name), LOWER(first_name));
CREATE UNIQUE INDEX uq_hr_employees_email ON hr_employees(LOWER(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uq_hr_employees_phone ON hr_employees(phone) WHERE phone IS NOT NULL;

CREATE TRIGGER hr_employees_set_updated_at
BEFORE UPDATE ON hr_employees
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE hr_departments
    ADD CONSTRAINT fk_hr_departments_head FOREIGN KEY (head_employee_id) REFERENCES hr_employees(id) ON DELETE SET NULL;

CREATE TABLE hr_emergency_contacts (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    relationship VARCHAR(60),
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(255),
    sort_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_hr_emergency_contacts_employee ON hr_emergency_contacts(employee_id);

CREATE TABLE hr_employee_documents (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE RESTRICT,
    document_type VARCHAR(30) NOT NULL,
    title VARCHAR(160) NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 VARCHAR(64) NOT NULL,
    storage_key VARCHAR(100) NOT NULL UNIQUE,
    expires_on DATE,
    notes VARCHAR(500),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hr_employee_documents_employee ON hr_employee_documents(employee_id, archived);

CREATE TABLE hr_employment_contracts (
    id UUID PRIMARY KEY,
    contract_number VARCHAR(30) NOT NULL UNIQUE,
    employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE RESTRICT,
    contract_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    probation_end_date DATE,
    salary_type VARCHAR(20),
    salary_amount NUMERIC(14, 2) CHECK (salary_amount IS NULL OR salary_amount >= 0),
    currency VARCHAR(3),
    weekly_hours NUMERIC(5, 2) CHECK (weekly_hours IS NULL OR (weekly_hours > 0 AND weekly_hours <= 168)),
    terms TEXT,
    branch_id UUID REFERENCES branches(id) ON DELETE RESTRICT,
    previous_contract_id UUID REFERENCES hr_employment_contracts(id) ON DELETE RESTRICT,
    document_id UUID REFERENCES hr_employee_documents(id) ON DELETE SET NULL,
    end_reason VARCHAR(500),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_hr_contracts_employee ON hr_employment_contracts(employee_id);
CREATE INDEX idx_hr_contracts_status_end ON hr_employment_contracts(status, end_date);
CREATE UNIQUE INDEX uq_hr_contracts_one_active ON hr_employment_contracts(employee_id) WHERE status = 'ACTIVE';

CREATE TRIGGER hr_employment_contracts_set_updated_at
BEFORE UPDATE ON hr_employment_contracts
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hr_user_permissions (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission VARCHAR(50) NOT NULL,
    granted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, permission)
);

-- Suggested restaurant departments and positions. These are ordinary, editable configuration rows.
INSERT INTO hr_departments (id, code, name, category) VALUES
    ('b2000000-0000-0000-0000-000000000001', 'MGMT', 'Management', 'MANAGEMENT'),
    ('b2000000-0000-0000-0000-000000000002', 'FOH', 'Front of House', 'FRONT_OF_HOUSE'),
    ('b2000000-0000-0000-0000-000000000003', 'KITCHEN', 'Kitchen', 'KITCHEN'),
    ('b2000000-0000-0000-0000-000000000004', 'BAR', 'Bar', 'BAR'),
    ('b2000000-0000-0000-0000-000000000005', 'OPS', 'Operations', 'OPERATIONS'),
    ('b2000000-0000-0000-0000-000000000006', 'SUPPORT', 'Support', 'SUPPORT'),
    ('b2000000-0000-0000-0000-000000000007', 'ADMIN', 'Administration', 'ADMINISTRATION');

INSERT INTO hr_positions (id, code, title, department_id, category) VALUES
    ('b3000000-0000-0000-0000-000000000001', 'GM', 'General Manager', 'b2000000-0000-0000-0000-000000000001', 'MANAGEMENT'),
    ('b3000000-0000-0000-0000-000000000002', 'RM', 'Restaurant Manager', 'b2000000-0000-0000-0000-000000000001', 'MANAGEMENT'),
    ('b3000000-0000-0000-0000-000000000003', 'AM', 'Assistant Manager', 'b2000000-0000-0000-0000-000000000001', 'MANAGEMENT'),
    ('b3000000-0000-0000-0000-000000000004', 'SHIFT-SUP', 'Shift Supervisor', 'b2000000-0000-0000-0000-000000000001', 'MANAGEMENT'),
    ('b3000000-0000-0000-0000-000000000005', 'WAITER', 'Waiter', 'b2000000-0000-0000-0000-000000000002', 'FRONT_OF_HOUSE'),
    ('b3000000-0000-0000-0000-000000000006', 'HOST', 'Host', 'b2000000-0000-0000-0000-000000000002', 'FRONT_OF_HOUSE'),
    ('b3000000-0000-0000-0000-000000000007', 'CASHIER', 'Cashier', 'b2000000-0000-0000-0000-000000000002', 'FRONT_OF_HOUSE'),
    ('b3000000-0000-0000-0000-000000000008', 'FLOOR-SUP', 'Floor Supervisor', 'b2000000-0000-0000-0000-000000000002', 'FRONT_OF_HOUSE'),
    ('b3000000-0000-0000-0000-000000000009', 'HEAD-CHEF', 'Head Chef', 'b2000000-0000-0000-0000-000000000003', 'KITCHEN'),
    ('b3000000-0000-0000-0000-000000000010', 'SOUS-CHEF', 'Sous Chef', 'b2000000-0000-0000-0000-000000000003', 'KITCHEN'),
    ('b3000000-0000-0000-0000-000000000011', 'LINE-COOK', 'Line Cook', 'b2000000-0000-0000-0000-000000000003', 'KITCHEN'),
    ('b3000000-0000-0000-0000-000000000012', 'PREP-COOK', 'Prep Cook', 'b2000000-0000-0000-0000-000000000003', 'KITCHEN'),
    ('b3000000-0000-0000-0000-000000000013', 'KITCHEN-ASST', 'Kitchen Assistant', 'b2000000-0000-0000-0000-000000000003', 'KITCHEN'),
    ('b3000000-0000-0000-0000-000000000014', 'BARTENDER', 'Bartender', 'b2000000-0000-0000-0000-000000000004', 'BAR'),
    ('b3000000-0000-0000-0000-000000000015', 'BARISTA', 'Barista', 'b2000000-0000-0000-0000-000000000004', 'BAR'),
    ('b3000000-0000-0000-0000-000000000016', 'BAR-SUP', 'Bar Supervisor', 'b2000000-0000-0000-0000-000000000004', 'BAR'),
    ('b3000000-0000-0000-0000-000000000017', 'OPS-OFFICER', 'Operations Officer', 'b2000000-0000-0000-0000-000000000005', 'OPERATIONS'),
    ('b3000000-0000-0000-0000-000000000018', 'PROCUREMENT', 'Procurement Officer', 'b2000000-0000-0000-0000-000000000005', 'OPERATIONS'),
    ('b3000000-0000-0000-0000-000000000019', 'INVENTORY', 'Inventory Officer', 'b2000000-0000-0000-0000-000000000005', 'OPERATIONS'),
    ('b3000000-0000-0000-0000-000000000020', 'CLEANER', 'Cleaner', 'b2000000-0000-0000-0000-000000000006', 'SUPPORT'),
    ('b3000000-0000-0000-0000-000000000021', 'STEWARD', 'Steward', 'b2000000-0000-0000-0000-000000000006', 'SUPPORT'),
    ('b3000000-0000-0000-0000-000000000022', 'SECURITY', 'Security Officer', 'b2000000-0000-0000-0000-000000000006', 'SUPPORT'),
    ('b3000000-0000-0000-0000-000000000023', 'RIDER', 'Delivery Rider', 'b2000000-0000-0000-0000-000000000006', 'SUPPORT'),
    ('b3000000-0000-0000-0000-000000000024', 'HR-OFFICER', 'HR Officer', 'b2000000-0000-0000-0000-000000000007', 'ADMINISTRATION'),
    ('b3000000-0000-0000-0000-000000000025', 'ACCOUNTANT', 'Accountant', 'b2000000-0000-0000-0000-000000000007', 'ADMINISTRATION'),
    ('b3000000-0000-0000-0000-000000000026', 'ADMIN-OFFICER', 'Administrative Officer', 'b2000000-0000-0000-0000-000000000007', 'ADMINISTRATION');
