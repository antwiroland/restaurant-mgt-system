-- Biometric attendance devices (Hikvision first; ZKTeco and others later) reached through a local attendance gateway.
-- The gateway is a separate program on the restaurant's network. It authenticates with its own revocable key and
-- forwards every device event here; each event is stored as received (raw) and then turned into a clock action
-- (hr_attendance_events / time_entries) by the existing attendance service.

CREATE TABLE hr_attendance_gateways (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    -- Public identifier the gateway reports (e.g. GATEWAY-001); the secret part of its key is only stored hashed.
    gateway_identifier VARCHAR(60) NOT NULL UNIQUE,
    key_hash VARCHAR(128) NOT NULL,
    key_prefix VARCHAR(20) NOT NULL,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    revoked_at TIMESTAMPTZ,
    last_heartbeat_at TIMESTAMPTZ,
    last_sync_at TIMESTAMPTZ,
    pending_events INT NOT NULL DEFAULT 0,
    failed_sync_attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    software_version VARCHAR(40),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE hr_attendance_devices (
    id UUID PRIMARY KEY,
    gateway_id UUID REFERENCES hr_attendance_gateways(id) ON DELETE SET NULL,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    name VARCHAR(120) NOT NULL,
    vendor VARCHAR(20) NOT NULL CHECK (vendor IN ('HIKVISION', 'ZKTECO', 'OTHER')),
    model VARCHAR(80),
    serial_number VARCHAR(80) NOT NULL UNIQUE,
    ip_address VARCHAR(80),
    port INT CHECK (port IS NULL OR (port BETWEEN 1 AND 65535)),
    use_https BOOLEAN NOT NULL DEFAULT FALSE,
    -- How to tell clock-in from clock-out when the device does not say: DEVICE_REPORTED = only trust the device's own
    -- attendance status (anything else goes to review); the others fix one action for every scan on this terminal.
    direction_mode VARCHAR(20) NOT NULL CHECK (direction_mode IN ('DEVICE_REPORTED', 'CLOCK_IN', 'CLOCK_OUT', 'BREAK_OUT', 'BREAK_IN')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    connection_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' CHECK (connection_status IN ('UNKNOWN', 'ONLINE', 'OFFLINE', 'ERROR')),
    last_connected_at TIMESTAMPTZ,
    last_event_at TIMESTAMPTZ,
    clock_offset_seconds INT,
    status_message VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hr_attendance_devices_gateway ON hr_attendance_devices(gateway_id);

-- Which person on the device is which employee. device_id NULL: the same device user id on every device.
CREATE TABLE hr_employee_device_ids (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES hr_employees(id) ON DELETE CASCADE,
    device_id UUID REFERENCES hr_attendance_devices(id) ON DELETE CASCADE,
    device_user_id VARCHAR(40) NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_hr_device_ids_device ON hr_employee_device_ids(device_id, device_user_id) WHERE device_id IS NOT NULL;
CREATE UNIQUE INDEX uq_hr_device_ids_all ON hr_employee_device_ids(device_user_id) WHERE device_id IS NULL;
CREATE INDEX idx_hr_device_ids_employee ON hr_employee_device_ids(employee_id);

-- Every event a gateway delivered, exactly as received. (device serial, device event id) is unique, so a repeated
-- delivery is answered with the first outcome and never creates a second clock action.
CREATE TABLE hr_device_events (
    id UUID PRIMARY KEY,
    gateway_id UUID REFERENCES hr_attendance_gateways(id) ON DELETE SET NULL,
    device_id UUID REFERENCES hr_attendance_devices(id) ON DELETE SET NULL,
    device_serial_number VARCHAR(80) NOT NULL,
    device_event_id VARCHAR(80) NOT NULL,
    device_user_id VARCHAR(40) NOT NULL,
    employee_id UUID REFERENCES hr_employees(id) ON DELETE SET NULL,
    -- What the device said (CLOCK_IN, BREAK_OUT, BREAK_IN, CLOCK_OUT or UNKNOWN) and what was applied.
    reported_type VARCHAR(20) NOT NULL,
    applied_action VARCHAR(20) CHECK (applied_action IS NULL OR applied_action IN ('CLOCK_IN', 'CLOCK_OUT', 'BREAK_START', 'BREAK_END')),
    authentication_method VARCHAR(20),
    device_timestamp TIMESTAMPTZ NOT NULL,
    gateway_received_at TIMESTAMPTZ,
    backend_received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    raw_event TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACCEPTED', 'REPEAT_SCAN', 'NEEDS_REVIEW', 'UNMATCHED', 'REJECTED', 'RESOLVED', 'DISMISSED')),
    message VARCHAR(300),
    attendance_event_id UUID REFERENCES hr_attendance_events(id) ON DELETE SET NULL,
    resolved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    resolved_at TIMESTAMPTZ,
    resolution_note VARCHAR(300)
);

CREATE UNIQUE INDEX uq_hr_device_events_identity ON hr_device_events(device_serial_number, device_event_id);
CREATE INDEX idx_hr_device_events_status ON hr_device_events(status, device_timestamp);
CREATE INDEX idx_hr_device_events_employee ON hr_device_events(employee_id, device_timestamp);
