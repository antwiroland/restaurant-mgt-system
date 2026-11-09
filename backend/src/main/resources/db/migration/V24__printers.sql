-- Network thermal printers (ESC/POS over TCP, usually port 9100) per branch.
CREATE TABLE printers (
    id UUID PRIMARY KEY,
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('RECEIPT', 'KITCHEN')),
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL DEFAULT 9100 CHECK (port BETWEEN 1 AND 65535),
    paper_width_mm INT NOT NULL DEFAULT 80 CHECK (paper_width_mm IN (58, 80)),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
