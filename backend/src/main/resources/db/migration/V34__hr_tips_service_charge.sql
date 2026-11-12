-- Tips and service charge distribution: effective-dated sharing rules, pools per branch/source/period built from paid
-- orders, and per-employee allocations that payroll picks up once approved. Nothing is seeded: how tips and service charges
-- are shared (and what the house keeps) is a business and legal decision configured by HR.

CREATE TABLE hr_tip_rules (
    id UUID PRIMARY KEY,
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE,
    source VARCHAR(20) NOT NULL CHECK (source IN ('TIPS', 'SERVICE_CHARGE')),
    method VARCHAR(20) NOT NULL CHECK (method IN ('HOURS', 'POINTS', 'EQUAL')),
    house_percent NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (house_percent >= 0 AND house_percent <= 100),
    taxable BOOLEAN NOT NULL DEFAULT TRUE,
    min_minutes INT NOT NULL DEFAULT 0 CHECK (min_minutes >= 0),
    -- JSON object {positionId: points}; empty = every position takes part with 1 point
    position_points TEXT,
    effective_from DATE NOT NULL,
    note VARCHAR(300),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hr_tip_rules_scope ON hr_tip_rules(source, branch_id, effective_from DESC);

CREATE TABLE hr_tip_pools (
    id UUID PRIMARY KEY,
    pool_number VARCHAR(30) NOT NULL UNIQUE,
    branch_id UUID NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    source VARCHAR(20) NOT NULL CHECK (source IN ('TIPS', 'SERVICE_CHARGE')),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'CALCULATED', 'APPROVED', 'PAID', 'CANCELLED')),
    order_count INT NOT NULL DEFAULT 0,
    collected_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    house_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    distributable_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    -- the sharing rule applied, frozen at calculation
    rule_snapshot TEXT,
    calculated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    calculated_at TIMESTAMPTZ,
    approved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    approved_at TIMESTAMPTZ,
    cancel_reason VARCHAR(500),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (period_end >= period_start)
);

CREATE INDEX idx_hr_tip_pools_branch ON hr_tip_pools(branch_id, source, period_start DESC);

CREATE TRIGGER hr_tip_pools_set_updated_at
BEFORE UPDATE ON hr_tip_pools
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hr_tip_allocations (
    id UUID PRIMARY KEY,
    pool_id UUID NOT NULL REFERENCES hr_tip_pools(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE RESTRICT,
    minutes INT NOT NULL DEFAULT 0,
    points NUMERIC(8, 2) NOT NULL DEFAULT 1,
    amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    -- the payroll run that pays this share (reserved at payroll calculation, released on recalculation/cancel)
    payroll_run_id UUID REFERENCES hr_payroll_runs(id) ON DELETE SET NULL,
    UNIQUE (pool_id, employee_id)
);

CREATE INDEX idx_hr_tip_allocations_employee ON hr_tip_allocations(employee_id);
CREATE INDEX idx_hr_tip_allocations_run ON hr_tip_allocations(payroll_run_id);
