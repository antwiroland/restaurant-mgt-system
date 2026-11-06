-- One-time passcodes sent by SMS for phone verification (registration) and passwordless customer login.
CREATE TABLE otp_challenges (
    id UUID PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_otp_challenges_phone_created ON otp_challenges(phone, created_at DESC);

ALTER TABLE users ADD COLUMN phone_verified_at TIMESTAMPTZ;
