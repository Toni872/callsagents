-- V4__calendar_integrations.sql
--
-- Stores the OAuth link between a Callsagents user and an external calendar
-- provider (Google Calendar, Outlook). One row per (user, provider).
--
-- Tokens (access, refresh) are stored AES-GCM encrypted; the symmetric key is
-- derived from ENCRYPTION_KEY (set in env / .env via RUNBOOK).
--
-- last_sync_at / last_sync_status / last_sync_error give operators visibility
-- into failed sync attempts without exposing the user.

CREATE TYPE calendar_sync_status AS ENUM ('PENDING', 'SYNCED', 'FAILED');

CREATE TABLE calendar_integrations (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    external_calendar_id VARCHAR(255),
    external_account_email VARCHAR(255),
    access_token_encrypted VARCHAR(2048) NOT NULL,
    refresh_token_encrypted VARCHAR(2048),
    access_token_expires_at TIMESTAMPTZ,
    scopes VARCHAR(1024),
    sync_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_sync_at TIMESTAMPTZ,
    last_sync_status calendar_sync_status,
    last_sync_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_cal_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_cal_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX idx_cal_user ON calendar_integrations(user_id);
CREATE INDEX idx_cal_provider_enabled ON calendar_integrations(provider, sync_enabled);
