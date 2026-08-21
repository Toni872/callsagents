-- 14-day trial for publicly self-registered accounts (AGENT role).
-- NULL means the account is on the full plan (admin-created users).
ALTER TABLE users ADD COLUMN trial_ends_at TIMESTAMPTZ NULL;
