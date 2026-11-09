-- Leave management: types (configurable approval chains), effective-dated entitlement policies, yearly balances with a
-- ledger, requests and per-step approvals.

CREATE TABLE hr_leave_types (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    paid BOOLEAN NOT NULL,
    deducts_balance BOOLEAN NOT NULL,
    requires_document BOOLEAN NOT NULL DEFAULT FALSE,
    allow_negative BOOLEAN NOT NULL DEFAULT FALSE,
    count_basis VARCHAR(20) NOT NULL CHECK (count_basis IN ('CALENDAR', 'WORKDAYS')),
    -- ordered, comma-separated: REPORTING_MANAGER, BRANCH_MANAGER, HR
    approval_steps VARCHAR(80) NOT NULL,
    max_days_per_request NUMERIC(6, 2) CHECK (max_days_per_request IS NULL OR max_days_per_request > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER hr_leave_types_set_updated_at
BEFORE UPDATE ON hr_leave_types
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Entitlements are deliberately not seeded: they are legal and business decisions configured by HR.
CREATE TABLE hr_leave_policies (
    id UUID PRIMARY KEY,
    leave_type_id UUID NOT NULL REFERENCES hr_leave_types(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE,
    employment_type VARCHAR(20),
    annual_days NUMERIC(6, 2) NOT NULL CHECK (annual_days >= 0),
    accrual VARCHAR(10) NOT NULL CHECK (accrual IN ('UPFRONT', 'MONTHLY')),
    carry_over_max_days NUMERIC(6, 2) NOT NULL DEFAULT 0 CHECK (carry_over_max_days >= 0),
    min_service_days INT NOT NULL DEFAULT 0 CHECK (min_service_days >= 0),
    effective_from DATE NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hr_leave_policies_type ON hr_leave_policies(leave_type_id, effective_from DESC);

CREATE TABLE hr_leave_balances (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE CASCADE,
    leave_type_id UUID NOT NULL REFERENCES hr_leave_types(id) ON DELETE CASCADE,
    leave_year INT NOT NULL,
    entitled_days NUMERIC(6, 2) NOT NULL DEFAULT 0,
    accrual VARCHAR(10) NOT NULL DEFAULT 'UPFRONT',
    accrual_start DATE,
    carried_over_days NUMERIC(6, 2) NOT NULL DEFAULT 0,
    adjustment_days NUMERIC(6, 2) NOT NULL DEFAULT 0,
    used_days NUMERIC(6, 2) NOT NULL DEFAULT 0,
    pending_days NUMERIC(6, 2) NOT NULL DEFAULT 0,
    policy_id UUID REFERENCES hr_leave_policies(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (employee_id, leave_type_id, leave_year),
    CHECK (used_days >= 0 AND pending_days >= 0)
);

CREATE TRIGGER hr_leave_balances_set_updated_at
BEFORE UPDATE ON hr_leave_balances
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hr_leave_requests (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE CASCADE,
    leave_type_id UUID NOT NULL REFERENCES hr_leave_types(id) ON DELETE RESTRICT,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    start_half BOOLEAN NOT NULL DEFAULT FALSE,
    end_half BOOLEAN NOT NULL DEFAULT FALSE,
    days NUMERIC(6, 2) NOT NULL CHECK (days > 0),
    reason VARCHAR(500),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    approval_steps VARCHAR(80) NOT NULL,
    current_step INT NOT NULL DEFAULT 0,
    document_id UUID REFERENCES hr_employee_documents(id) ON DELETE SET NULL,
    requested_by UUID REFERENCES users(id) ON DELETE SET NULL,
    cancel_reason VARCHAR(500),
    decided_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (end_date >= start_date)
);

CREATE INDEX idx_hr_leave_requests_employee ON hr_leave_requests(employee_id, start_date);
CREATE INDEX idx_hr_leave_requests_branch_dates ON hr_leave_requests(branch_id, start_date, end_date);
CREATE INDEX idx_hr_leave_requests_status ON hr_leave_requests(status, created_at);

CREATE TRIGGER hr_leave_requests_set_updated_at
BEFORE UPDATE ON hr_leave_requests
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hr_leave_approvals (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES hr_leave_requests(id) ON DELETE CASCADE,
    step INT NOT NULL,
    step_code VARCHAR(30) NOT NULL,
    decided_by UUID REFERENCES users(id) ON DELETE SET NULL,
    decision VARCHAR(20) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    note VARCHAR(500),
    decided_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (request_id, step)
);

CREATE TABLE hr_leave_transactions (
    id UUID PRIMARY KEY,
    balance_id UUID NOT NULL REFERENCES hr_leave_balances(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('ENTITLEMENT', 'CARRY_OVER', 'ADJUSTMENT', 'PENDING', 'RELEASE', 'USE', 'RESTORE')),
    days NUMERIC(6, 2) NOT NULL,
    request_id UUID REFERENCES hr_leave_requests(id) ON DELETE SET NULL,
    note VARCHAR(300),
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hr_leave_transactions_balance ON hr_leave_transactions(balance_id, created_at);

INSERT INTO hr_leave_types (id, code, name, paid, deducts_balance, requires_document, allow_negative, count_basis, approval_steps, display_order) VALUES
    ('b4000000-0000-0000-0000-000000000001', 'ANNUAL', 'Annual leave', TRUE, TRUE, FALSE, FALSE, 'WORKDAYS', 'BRANCH_MANAGER', 1),
    ('b4000000-0000-0000-0000-000000000002', 'SICK', 'Sick leave', TRUE, TRUE, TRUE, TRUE, 'WORKDAYS', 'BRANCH_MANAGER', 2),
    ('b4000000-0000-0000-0000-000000000003', 'MATERNITY', 'Maternity leave', TRUE, FALSE, TRUE, FALSE, 'CALENDAR', 'BRANCH_MANAGER,HR', 3),
    ('b4000000-0000-0000-0000-000000000004', 'PATERNITY', 'Paternity leave', TRUE, FALSE, FALSE, FALSE, 'WORKDAYS', 'BRANCH_MANAGER,HR', 4),
    ('b4000000-0000-0000-0000-000000000005', 'COMPASSIONATE', 'Compassionate leave', TRUE, FALSE, FALSE, FALSE, 'WORKDAYS', 'BRANCH_MANAGER', 5),
    ('b4000000-0000-0000-0000-000000000006', 'UNPAID', 'Unpaid leave', FALSE, FALSE, FALSE, FALSE, 'WORKDAYS', 'BRANCH_MANAGER,HR', 6),
    ('b4000000-0000-0000-0000-000000000007', 'STUDY', 'Study leave', TRUE, TRUE, FALSE, FALSE, 'WORKDAYS', 'BRANCH_MANAGER,HR', 7);
