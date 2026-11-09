-- Multi-branch HR: employee branch assignment history and extra branch scope for HR users.

CREATE TABLE hr_employee_branch_assignments (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE RESTRICT,
    branch_id UUID NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    assignment_type VARCHAR(20) NOT NULL CHECK (assignment_type IN ('PRIMARY', 'SECONDARY', 'TEMPORARY')),
    position_id UUID REFERENCES hr_positions(id) ON DELETE RESTRICT,
    start_date DATE NOT NULL,
    end_date DATE,
    reason VARCHAR(500),
    end_reason VARCHAR(500),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ended_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (end_date IS NULL OR end_date >= start_date),
    CHECK (assignment_type <> 'TEMPORARY' OR end_date IS NOT NULL)
);

CREATE INDEX idx_hr_assignments_employee ON hr_employee_branch_assignments(employee_id, start_date DESC);
CREATE INDEX idx_hr_assignments_branch_dates ON hr_employee_branch_assignments(branch_id, start_date, end_date);
-- At most one open-ended primary assignment per employee, and one open-ended assignment per employee and branch.
CREATE UNIQUE INDEX uq_hr_assignments_open_primary ON hr_employee_branch_assignments(employee_id)
    WHERE assignment_type = 'PRIMARY' AND end_date IS NULL;
CREATE UNIQUE INDEX uq_hr_assignments_open_branch ON hr_employee_branch_assignments(employee_id, branch_id)
    WHERE end_date IS NULL;

CREATE TRIGGER hr_employee_branch_assignments_set_updated_at
BEFORE UPDATE ON hr_employee_branch_assignments
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- History starts from the current primary branch of existing employees (hire date as the start).
INSERT INTO hr_employee_branch_assignments (id, employee_id, branch_id, assignment_type, position_id, start_date, end_date, reason)
SELECT gen_random_uuid(), e.id, e.primary_branch_id, 'PRIMARY', e.position_id, e.hire_date, e.exit_date, 'Recorded from existing employee record'
FROM hr_employees e
WHERE e.primary_branch_id IS NOT NULL;

CREATE TABLE hr_user_branch_access (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    granted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, branch_id)
);
