-- Add authentication fields to users table

ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(60) NOT NULL AFTER email,
    ADD COLUMN account_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN last_login_at DATETIME NULL,
    ADD COLUMN password_changed_at DATETIME NULL,
    ADD COLUMN updated_at DATETIME NULL;

CREATE INDEX idx_email ON users(email);
CREATE INDEX idx_account_status ON users(account_enabled, account_locked);
