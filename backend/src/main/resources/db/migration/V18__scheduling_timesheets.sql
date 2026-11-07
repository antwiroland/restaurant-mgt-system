-- Staff rota and time clock.
CREATE TABLE work_schedules (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    position VARCHAR(60),
    notes VARCHAR(500),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (ends_at > starts_at)
);

CREATE INDEX idx_work_schedules_user_start ON work_schedules(user_id, starts_at);
CREATE INDEX idx_work_schedules_start ON work_schedules(starts_at);

CREATE TRIGGER work_schedules_set_updated_at
BEFORE UPDATE ON work_schedules
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE time_entries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    schedule_id UUID REFERENCES work_schedules(id) ON DELETE SET NULL,
    clock_in_at TIMESTAMPTZ NOT NULL,
    clock_out_at TIMESTAMPTZ,
    break_minutes INT NOT NULL DEFAULT 0 CHECK (break_minutes >= 0),
    notes VARCHAR(500),
    edited_by UUID REFERENCES users(id) ON DELETE SET NULL,
    edit_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_time_entries_user_in ON time_entries(user_id, clock_in_at);
CREATE UNIQUE INDEX uq_time_entries_open_per_user ON time_entries(user_id) WHERE clock_out_at IS NULL;

CREATE TRIGGER time_entries_set_updated_at
BEFORE UPDATE ON time_entries
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
