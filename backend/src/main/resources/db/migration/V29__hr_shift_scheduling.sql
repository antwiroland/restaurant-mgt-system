-- HR shift scheduling: templates, scheduling policies, availability, swaps, and draft/published shifts.
-- Extends the existing work_schedules rota instead of adding a parallel shifts table.

CREATE TABLE hr_shift_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    shift_type VARCHAR(20) NOT NULL CHECK (shift_type IN ('MORNING', 'AFTERNOON', 'EVENING', 'NIGHT', 'SPLIT', 'CUSTOM')),
    branch_id UUID REFERENCES branches(id) ON DELETE RESTRICT,
    department_id UUID REFERENCES hr_departments(id) ON DELETE RESTRICT,
    position_id UUID REFERENCES hr_positions(id) ON DELETE RESTRICT,
    start_time TIME NOT NULL,
    -- an end time at or before the start time means the shift ends the next day
    end_time TIME NOT NULL,
    break_minutes INT NOT NULL DEFAULT 0 CHECK (break_minutes >= 0),
    days_of_week VARCHAR(30) NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI,SAT,SUN',
    min_staff INT NOT NULL DEFAULT 0 CHECK (min_staff >= 0),
    max_staff INT CHECK (max_staff IS NULL OR max_staff >= min_staff),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hr_shift_templates_branch ON hr_shift_templates(branch_id, active);

CREATE TRIGGER hr_shift_templates_set_updated_at
BEFORE UPDATE ON hr_shift_templates
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Scheduling limits. The row with branch_id NULL is the organisation default; a branch row overrides it.
-- Values are business configuration, not legal advice: set them to the applicable labour rules.
CREATE TABLE hr_scheduling_policies (
    id UUID PRIMARY KEY,
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE,
    min_rest_minutes INT NOT NULL CHECK (min_rest_minutes >= 0),
    max_shift_minutes INT NOT NULL CHECK (max_shift_minutes > 0),
    max_weekly_minutes INT NOT NULL CHECK (max_weekly_minutes > 0),
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_hr_scheduling_policies_branch ON hr_scheduling_policies(branch_id) WHERE branch_id IS NOT NULL;
CREATE UNIQUE INDEX uq_hr_scheduling_policies_default ON hr_scheduling_policies((branch_id IS NULL)) WHERE branch_id IS NULL;

CREATE TRIGGER hr_scheduling_policies_set_updated_at
BEFORE UPDATE ON hr_scheduling_policies
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE work_schedules ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE work_schedules ADD COLUMN employee_id UUID REFERENCES hr_employees(id) ON DELETE SET NULL;
ALTER TABLE work_schedules ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED'));
ALTER TABLE work_schedules ADD COLUMN template_id UUID REFERENCES hr_shift_templates(id) ON DELETE SET NULL;
ALTER TABLE work_schedules ADD COLUMN department_id UUID REFERENCES hr_departments(id) ON DELETE SET NULL;
ALTER TABLE work_schedules ADD COLUMN position_id UUID REFERENCES hr_positions(id) ON DELETE SET NULL;
ALTER TABLE work_schedules ADD COLUMN break_minutes INT NOT NULL DEFAULT 0 CHECK (break_minutes >= 0);
ALTER TABLE work_schedules ADD COLUMN published_at TIMESTAMPTZ;
ALTER TABLE work_schedules ADD COLUMN published_by UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE work_schedules ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE work_schedules ADD COLUMN cancel_reason VARCHAR(500);
ALTER TABLE work_schedules ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Existing rota entries are live shifts: mark them published and attach the employee record of their login.
UPDATE work_schedules ws SET employee_id = e.id FROM hr_employees e WHERE e.user_id = ws.user_id;
UPDATE work_schedules SET published_at = created_at WHERE status = 'PUBLISHED';

CREATE INDEX idx_work_schedules_employee_start ON work_schedules(employee_id, starts_at);
CREATE INDEX idx_work_schedules_branch_start ON work_schedules(branch_id, starts_at);

CREATE TABLE hr_employee_availability (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('AVAILABLE', 'UNAVAILABLE', 'PREFERRED')),
    -- either a weekly rule (day_of_week, optionally bounded by effective dates) or a single date
    day_of_week VARCHAR(3) CHECK (day_of_week IN ('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN')),
    specific_date DATE,
    start_time TIME,
    end_time TIME,
    effective_from DATE,
    effective_to DATE,
    note VARCHAR(300),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((day_of_week IS NULL) <> (specific_date IS NULL)),
    CHECK ((start_time IS NULL) = (end_time IS NULL)),
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)
);

CREATE INDEX idx_hr_availability_employee ON hr_employee_availability(employee_id);

CREATE TABLE hr_shift_swap_requests (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES work_schedules(id) ON DELETE CASCADE,
    requester_employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE CASCADE,
    colleague_employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE CASCADE,
    -- the colleague's shift taken in exchange; NULL means the colleague simply takes over the shift
    colleague_schedule_id UUID REFERENCES work_schedules(id) ON DELETE CASCADE,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    decided_by UUID REFERENCES users(id) ON DELETE SET NULL,
    decided_at TIMESTAMPTZ,
    decision_note VARCHAR(500),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (requester_employee_id <> colleague_employee_id)
);

CREATE INDEX idx_hr_swaps_status ON hr_shift_swap_requests(status, created_at);
CREATE UNIQUE INDEX uq_hr_swaps_pending_shift ON hr_shift_swap_requests(schedule_id) WHERE status = 'PENDING';
