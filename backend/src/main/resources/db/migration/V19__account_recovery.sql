-- One-time tokens for password reset and email verification (stored hashed).
CREATE TABLE account_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(30) NOT NULL,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    email VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_account_tokens_user_purpose ON account_tokens(user_id, purpose);

ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
