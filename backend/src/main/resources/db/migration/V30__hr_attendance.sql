-- HR attendance: idempotent clock events, breaks, corrections with approval, attendance policies and employee clock PINs.
-- Extends the existing time_entries time clock instead of replacing it.

ALTER TABLE time_entries ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE time_entries ADD COLUMN employee_id UUID REFERENCES hr_employees(id) ON DELETE SET NULL;
ALTER TABLE time_entries ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'SELF_SERVICE'
    CHECK (source IN ('POS', 'SELF_SERVICE', 'MANAGER', 'DEVICE', 'CORRECTION'));
ALTER TABLE time_entries ADD COLUMN clock_out_source VARCHAR(20)
    CHECK (clock_out_source IS NULL OR clock_out_source IN ('POS', 'SELF_SERVICE', 'MANAGER', 'DEVICE', 'CORRECTION'));
ALTER TABLE time_entries ADD COLUMN terminal_id VARCHAR(80);
ALTER TABLE time_entries ADD COLUMN unscheduled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE time_entries ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE time_entries te SET employee_id = e.id FROM hr_employees e WHERE e.user_id = te.user_id;

CREATE UNIQUE INDEX uq_time_entries_open_per_employee ON time_entries(employee_id) WHERE clock_out_at IS NULL AND employee_id IS NOT NULL;
CREATE INDEX idx_time_entries_employee_in ON time_entries(employee_id, clock_in_at);
CREATE INDEX idx_time_entries_branch_in ON time_entries(branch_id, clock_in_at);

CREATE TABLE hr_attendance_breaks (
    id UUID PRIMARY KEY,
    time_entry_id UUID NOT NULL REFERENCES time_entries(id) ON DELETE CASCADE,
    break_type VARCHAR(20) NOT NULL CHECK (break_type IN ('MEAL', 'REST', 'OTHER')),
    paid BOOLEAN NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    source VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE INDEX idx_hr_breaks_entry ON hr_attendance_breaks(time_entry_id);
CREATE UNIQUE INDEX uq_hr_breaks_open_per_entry ON hr_attendance_breaks(time_entry_id) WHERE ended_at IS NULL;

-- Append-only log of every clock action as received. A repeated idempotency key returns the first outcome.
CREATE TABLE hr_attendance_events (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE CASCADE,
    event_type VARCHAR(20) NOT NULL CHECK (event_type IN ('CLOCK_IN', 'CLOCK_OUT', 'BREAK_START', 'BREAK_END')),
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source VARCHAR(20) NOT NULL,
    terminal_id VARCHAR(80),
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    time_entry_id UUID REFERENCES time_entries(id) ON DELETE SET NULL,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('ACCEPTED', 'REJECTED')),
    message VARCHAR(300),
    recorded_by UUID REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_hr_attendance_events_employee ON hr_attendance_events(employee_id, received_at DESC);

CREATE TABLE hr_attendance_corrections (
    id UUID PRIMARY KEY,
    time_entry_id UUID REFERENCES time_entries(id) ON DELETE SET NULL,
    employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    original_clock_in TIMESTAMPTZ,
    original_clock_out TIMESTAMPTZ,
    original_break_minutes INT,
    corrected_clock_in TIMESTAMPTZ NOT NULL,
    corrected_clock_out TIMESTAMPTZ,
    corrected_break_minutes INT NOT NULL CHECK (corrected_break_minutes >= 0),
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED')),
    requested_by UUID REFERENCES users(id) ON DELETE SET NULL,
    decided_by UUID REFERENCES users(id) ON DELETE SET NULL,
    decided_at TIMESTAMPTZ,
    decision_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (corrected_clock_out IS NULL OR corrected_clock_out > corrected_clock_in)
);

CREATE INDEX idx_hr_corrections_status ON hr_attendance_corrections(status, created_at);
CREATE INDEX idx_hr_corrections_entry ON hr_attendance_corrections(time_entry_id);

-- Attendance rules. The row with branch_id NULL is the organisation default; a branch row overrides it.
CREATE TABLE hr_attendance_policies (
    id UUID PRIMARY KEY,
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE,
    late_grace_minutes INT NOT NULL CHECK (late_grace_minutes >= 0),
    early_leave_grace_minutes INT NOT NULL CHECK (early_leave_grace_minutes >= 0),
    early_clock_in_minutes INT NOT NULL CHECK (early_clock_in_minutes >= 0),
    unscheduled_clock_in VARCHAR(10) NOT NULL CHECK (unscheduled_clock_in IN ('ALLOW', 'BLOCK')),
    require_correction_approval BOOLEAN NOT NULL,
    meal_break_paid BOOLEAN NOT NULL,
    rest_break_paid BOOLEAN NOT NULL,
    allow_self_clock BOOLEAN NOT NULL,
    missed_clock_out_minutes INT NOT NULL CHECK (missed_clock_out_minutes > 0),
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_hr_attendance_policies_branch ON hr_attendance_policies(branch_id) WHERE branch_id IS NOT NULL;
CREATE UNIQUE INDEX uq_hr_attendance_policies_default ON hr_attendance_policies((branch_id IS NULL)) WHERE branch_id IS NULL;

CREATE TRIGGER hr_attendance_policies_set_updated_at
BEFORE UPDATE ON hr_attendance_policies
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Employee clock PIN: separate from the manager override PIN on users and from any POS login.
ALTER TABLE hr_employees ADD COLUMN clock_pin_hash TEXT;
ALTER TABLE hr_employees ADD COLUMN clock_pin_fail_count SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE hr_employees ADD COLUMN clock_pin_locked_until TIMESTAMPTZ;
ALTER TABLE hr_employees ADD COLUMN clock_pin_set_at TIMESTAMPTZ;
