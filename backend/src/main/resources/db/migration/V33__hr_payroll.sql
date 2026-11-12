-- Payroll: effective-dated deduction/contribution rules (no statutory rates are seeded: they are legal decisions
-- configured by HR), payroll runs per branch and period, payslips with frozen lines, and one-off adjustments.

CREATE TABLE hr_payroll_rules (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(120) NOT NULL,
    kind VARCHAR(30) NOT NULL CHECK (kind IN ('EMPLOYEE_DEDUCTION', 'EMPLOYER_CONTRIBUTION')),
    method VARCHAR(20) NOT NULL CHECK (method IN ('PERCENT', 'FIXED', 'BRACKETS')),
    base VARCHAR(20) NOT NULL CHECK (base IN ('BASIC', 'GROSS', 'TAXABLE')),
    rate_percent NUMERIC(7, 4) CHECK (rate_percent IS NULL OR (rate_percent >= 0 AND rate_percent <= 100)),
    fixed_amount NUMERIC(14, 2) CHECK (fixed_amount IS NULL OR fixed_amount >= 0),
    cap_amount NUMERIC(14, 2) CHECK (cap_amount IS NULL OR cap_amount >= 0),
    -- JSON array of {"upTo": number|null, "ratePercent": number}, ascending; the last band has upTo null
    brackets TEXT,
    pre_tax BOOLEAN NOT NULL DEFAULT FALSE,
    -- comma-separated employment types; null = everyone
    employment_types VARCHAR(200),
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE,
    effective_from DATE NOT NULL,
    -- a row with active = false ends the rule (for this code and scope) from its effective date
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    note VARCHAR(300),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hr_payroll_rules_code ON hr_payroll_rules(code, effective_from DESC);

CREATE TABLE hr_payroll_runs (
    id UUID PRIMARY KEY,
    run_number VARCHAR(30) NOT NULL UNIQUE,
    branch_id UUID NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    pay_date DATE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'CALCULATED', 'APPROVED', 'FINALIZED', 'PAID', 'CANCELLED')),
    currency VARCHAR(3) NOT NULL DEFAULT 'GHS',
    employee_count INT NOT NULL DEFAULT 0,
    gross_total NUMERIC(14, 2) NOT NULL DEFAULT 0,
    deductions_total NUMERIC(14, 2) NOT NULL DEFAULT 0,
    net_total NUMERIC(14, 2) NOT NULL DEFAULT 0,
    employer_total NUMERIC(14, 2) NOT NULL DEFAULT 0,
    notes VARCHAR(500),
    -- JSON array of {employeeId, name, reason} left out of the last calculation
    skipped TEXT,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    calculated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    calculated_at TIMESTAMPTZ,
    approved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    approved_at TIMESTAMPTZ,
    finalized_by UUID REFERENCES users(id) ON DELETE SET NULL,
    finalized_at TIMESTAMPTZ,
    cancel_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (period_end >= period_start)
);

CREATE INDEX idx_hr_payroll_runs_branch ON hr_payroll_runs(branch_id, period_start DESC);

CREATE TRIGGER hr_payroll_runs_set_updated_at
BEFORE UPDATE ON hr_payroll_runs
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hr_payslips (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES hr_payroll_runs(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE RESTRICT,
    contract_id UUID REFERENCES hr_employment_contracts(id) ON DELETE SET NULL,
    salary_type VARCHAR(20),
    rate NUMERIC(14, 2),
    hourly_rate NUMERIC(14, 4),
    paid_minutes INT NOT NULL DEFAULT 0,
    overtime_minutes INT NOT NULL DEFAULT 0,
    worked_days INT NOT NULL DEFAULT 0,
    unpaid_leave_days NUMERIC(6, 2) NOT NULL DEFAULT 0,
    basic NUMERIC(14, 2) NOT NULL DEFAULT 0,
    gross NUMERIC(14, 2) NOT NULL DEFAULT 0,
    taxable NUMERIC(14, 2) NOT NULL DEFAULT 0,
    employee_deductions NUMERIC(14, 2) NOT NULL DEFAULT 0,
    net NUMERIC(14, 2) NOT NULL DEFAULT 0,
    employer_contributions NUMERIC(14, 2) NOT NULL DEFAULT 0,
    -- comma-separated issue codes; ERROR codes block approval
    issues VARCHAR(500),
    payment_method VARCHAR(20) CHECK (payment_method IS NULL OR payment_method IN ('BANK', 'MOBILE_MONEY', 'CASH', 'OTHER')),
    payment_reference VARCHAR(120),
    paid_at TIMESTAMPTZ,
    paid_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (run_id, employee_id)
);

CREATE INDEX idx_hr_payslips_employee ON hr_payslips(employee_id);

CREATE TABLE hr_payslip_lines (
    id UUID PRIMARY KEY,
    payslip_id UUID NOT NULL REFERENCES hr_payslips(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('EARNING', 'DEDUCTION', 'EMPLOYER')),
    code VARCHAR(40) NOT NULL,
    description VARCHAR(200) NOT NULL,
    quantity NUMERIC(12, 2),
    rate NUMERIC(14, 4),
    amount NUMERIC(14, 2) NOT NULL,
    taxable BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_hr_payslip_lines_payslip ON hr_payslip_lines(payslip_id, sort_order);

CREATE TABLE hr_payroll_adjustments (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES hr_payroll_runs(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('EARNING', 'DEDUCTION')),
    taxable BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(200) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hr_payroll_adjustments_run ON hr_payroll_adjustments(run_id, employee_id);

-- A locked timesheet is paid in at most one finalized run.
ALTER TABLE hr_timesheets ADD COLUMN payroll_run_id UUID REFERENCES hr_payroll_runs(id) ON DELETE SET NULL;
