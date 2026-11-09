-- Timesheets (snapshots of attendance per employee and period), effective-dated overtime rules,
-- overtime pre-authorisations and public holidays.

CREATE TABLE hr_public_holidays (
    id UUID PRIMARY KEY,
    holiday_date DATE NOT NULL,
    name VARCHAR(120) NOT NULL,
    -- NULL = observed at every branch
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_hr_public_holidays_org ON hr_public_holidays(holiday_date) WHERE branch_id IS NULL;
CREATE UNIQUE INDEX uq_hr_public_holidays_branch ON hr_public_holidays(holiday_date, branch_id) WHERE branch_id IS NOT NULL;

-- Overtime rules take effect from a date; the latest rule on or before a work date applies. Multipliers are stored for
-- payroll; they are business configuration, not legal advice.
CREATE TABLE hr_overtime_rules (
    id UUID PRIMARY KEY,
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE,
    effective_from DATE NOT NULL,
    daily_threshold_minutes INT CHECK (daily_threshold_minutes IS NULL OR daily_threshold_minutes > 0),
    weekly_threshold_minutes INT CHECK (weekly_threshold_minutes IS NULL OR weekly_threshold_minutes > 0),
    daily_multiplier NUMERIC(5, 2) NOT NULL CHECK (daily_multiplier >= 1),
    weekly_multiplier NUMERIC(5, 2) NOT NULL CHECK (weekly_multiplier >= 1),
    weekend_days VARCHAR(30),
    weekend_multiplier NUMERIC(5, 2) CHECK (weekend_multiplier IS NULL OR weekend_multiplier >= 1),
    holiday_multiplier NUMERIC(5, 2) CHECK (holiday_multiplier IS NULL OR holiday_multiplier >= 1),
    night_start TIME,
    night_end TIME,
    night_multiplier NUMERIC(5, 2) CHECK (night_multiplier IS NULL OR night_multiplier >= 1),
    requires_approval BOOLEAN NOT NULL,
    note VARCHAR(300),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((night_start IS NULL) = (night_end IS NULL))
);

CREATE UNIQUE INDEX uq_hr_overtime_rules_org ON hr_overtime_rules(effective_from) WHERE branch_id IS NULL;
CREATE UNIQUE INDEX uq_hr_overtime_rules_branch ON hr_overtime_rules(branch_id, effective_from) WHERE branch_id IS NOT NULL;

CREATE TABLE hr_timesheets (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE RESTRICT,
    -- home branch at generation time; governs who reviews it
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'REVIEWED', 'APPROVED', 'LOCKED')),
    scheduled_minutes INT NOT NULL DEFAULT 0,
    worked_minutes INT NOT NULL DEFAULT 0,
    unpaid_break_minutes INT NOT NULL DEFAULT 0,
    paid_minutes INT NOT NULL DEFAULT 0,
    regular_minutes INT NOT NULL DEFAULT 0,
    daily_overtime_minutes INT NOT NULL DEFAULT 0,
    weekly_overtime_minutes INT NOT NULL DEFAULT 0,
    overtime_computed_minutes INT NOT NULL DEFAULT 0,
    overtime_approved_minutes INT,
    overtime_requires_approval BOOLEAN NOT NULL DEFAULT TRUE,
    holiday_minutes INT NOT NULL DEFAULT 0,
    weekend_minutes INT NOT NULL DEFAULT 0,
    night_minutes INT NOT NULL DEFAULT 0,
    late_count INT NOT NULL DEFAULT 0,
    absence_count INT NOT NULL DEFAULT 0,
    -- JSON copy of the rules applied, so an approved timesheet can be explained after rules change
    rule_snapshot TEXT,
    notes VARCHAR(1000),
    generated_at TIMESTAMPTZ NOT NULL,
    submitted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    submitted_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    approved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    approved_at TIMESTAMPTZ,
    locked_by UUID REFERENCES users(id) ON DELETE SET NULL,
    locked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (period_end >= period_start),
    CHECK (overtime_approved_minutes IS NULL OR (overtime_approved_minutes >= 0 AND overtime_approved_minutes <= overtime_computed_minutes))
);

CREATE UNIQUE INDEX uq_hr_timesheets_period ON hr_timesheets(employee_id, period_start, period_end);
CREATE INDEX idx_hr_timesheets_branch_period ON hr_timesheets(branch_id, period_start);
CREATE INDEX idx_hr_timesheets_employee_period ON hr_timesheets(employee_id, period_start, period_end);

CREATE TRIGGER hr_timesheets_set_updated_at
BEFORE UPDATE ON hr_timesheets
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hr_timesheet_lines (
    id UUID PRIMARY KEY,
    timesheet_id UUID NOT NULL REFERENCES hr_timesheets(id) ON DELETE CASCADE,
    work_date DATE NOT NULL,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    time_entry_id UUID REFERENCES time_entries(id) ON DELETE SET NULL,
    schedule_id UUID REFERENCES work_schedules(id) ON DELETE SET NULL,
    shift_start TIMESTAMPTZ,
    shift_end TIMESTAMPTZ,
    clock_in TIMESTAMPTZ,
    clock_out TIMESTAMPTZ,
    scheduled_minutes INT NOT NULL DEFAULT 0,
    worked_minutes INT NOT NULL DEFAULT 0,
    unpaid_break_minutes INT NOT NULL DEFAULT 0,
    paid_minutes INT NOT NULL DEFAULT 0,
    late_minutes INT NOT NULL DEFAULT 0,
    early_leave_minutes INT NOT NULL DEFAULT 0,
    -- comma-separated: ABSENT, UNSCHEDULED, CORRECTED, HOLIDAY, WEEKEND
    flags VARCHAR(120),
    sort_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_hr_timesheet_lines_sheet ON hr_timesheet_lines(timesheet_id, work_date);

CREATE TABLE hr_overtime_requests (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    work_date DATE NOT NULL,
    minutes INT NOT NULL CHECK (minutes > 0 AND minutes <= 960),
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    requested_by UUID REFERENCES users(id) ON DELETE SET NULL,
    decided_by UUID REFERENCES users(id) ON DELETE SET NULL,
    decided_at TIMESTAMPTZ,
    decision_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hr_overtime_requests_employee_date ON hr_overtime_requests(employee_id, work_date);
CREATE INDEX idx_hr_overtime_requests_status ON hr_overtime_requests(status, created_at);
