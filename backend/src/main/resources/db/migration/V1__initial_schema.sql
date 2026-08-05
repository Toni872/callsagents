-- =============================================================================
-- Callsagents Backend — Initial schema (V1)
-- Target: PostgreSQL 15+
-- Replicates docs/02-modelo-de-datos.md (Option B: campaign_leads composite PK
-- + calls FK to composite PK, no synthetic campaign_lead_id).
-- =============================================================================

-- Required for gen_random_uuid() (no-op on Postgres 13+ where it's built-in,
-- but harmless and explicit).
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- ENUMs
-- =============================================================================
CREATE TYPE user_role            AS ENUM ('ADMIN', 'SUPERVISOR', 'AGENT');
CREATE TYPE user_status          AS ENUM ('ACTIVE', 'DISABLED');
CREATE TYPE lead_status          AS ENUM ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'QUALIFIED', 'NOT_QUALIFIED', 'CONVERTED', 'DISQUALIFIED');
CREATE TYPE lead_source          AS ENUM ('MANUAL', 'IMPORT', 'API');
CREATE TYPE campaign_status      AS ENUM ('DRAFT', 'SCHEDULED', 'RUNNING', 'PAUSED', 'FINISHED', 'CANCELLED');
CREATE TYPE campaign_lead_status AS ENUM ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED');
CREATE TYPE call_status          AS ENUM ('CONNECTED', 'VOICEMAIL', 'NO_ANSWER', 'BUSY', 'FAILED');
CREATE TYPE call_outcome         AS ENUM ('INTERESTED', 'NOT_INTERESTED', 'CALLBACK', 'APPOINTMENT_SET', 'NOT_REACHED');
CREATE TYPE appointment_status   AS ENUM ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW');
CREATE TYPE audit_action         AS ENUM ('CREATE', 'UPDATE', 'DELETE', 'STATUS_CHANGE');

-- =============================================================================
-- users
-- =============================================================================
CREATE TABLE users (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    role          user_role    NOT NULL,
    status        user_status  NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (email)
);
CREATE INDEX idx_users_role   ON users(role);
CREATE INDEX idx_users_status ON users(status);

-- =============================================================================
-- leads
-- =============================================================================
CREATE TABLE leads (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    email               VARCHAR(255),
    phone               VARCHAR(32),
    company             VARCHAR(255),
    status              lead_status  NOT NULL DEFAULT 'NEW',
    source              lead_source  NOT NULL,
    assigned_to         UUID,
    notes               TEXT,
    custom_fields       JSONB,
    consent_at          TIMESTAMPTZ,
    do_not_call         BOOLEAN      NOT NULL DEFAULT FALSE,
    data_retention_until DATE,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_leads_contact      CHECK (email IS NOT NULL OR phone IS NOT NULL),
    CONSTRAINT fk_leads_assigned_to   FOREIGN KEY (assigned_to) REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_leads_email        ON leads(email);
CREATE INDEX idx_leads_phone        ON leads(phone);
CREATE INDEX idx_leads_status       ON leads(status);
CREATE INDEX idx_leads_assigned_to  ON leads(assigned_to);
CREATE INDEX idx_leads_company      ON leads(company);
CREATE INDEX idx_leads_do_not_call  ON leads(do_not_call);

-- =============================================================================
-- campaigns
-- =============================================================================
CREATE TABLE campaigns (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    status      campaign_status NOT NULL DEFAULT 'DRAFT',
    start_at    TIMESTAMPTZ,
    end_at      TIMESTAMPTZ,
    script      TEXT,
    created_by  UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_campaigns_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
);
CREATE INDEX idx_campaigns_status     ON campaigns(status);
CREATE INDEX idx_campaigns_start_at   ON campaigns(start_at);
CREATE INDEX idx_campaigns_end_at     ON campaigns(end_at);
CREATE INDEX idx_campaigns_created_by ON campaigns(created_by);

-- =============================================================================
-- campaign_leads (COMPOSITE PK: (campaign_id, lead_id))
-- =============================================================================
CREATE TABLE campaign_leads (
    campaign_id      UUID                NOT NULL,
    lead_id          UUID                NOT NULL,
    status           campaign_lead_status NOT NULL DEFAULT 'PENDING',
    attempts         INT                 NOT NULL DEFAULT 0,
    last_attempt_at  TIMESTAMPTZ,
    assigned_to      UUID,
    next_attempt_at  TIMESTAMPTZ,
    created_at       TIMESTAMPTZ         NOT NULL,
    updated_at       TIMESTAMPTZ         NOT NULL,
    PRIMARY KEY (campaign_id, lead_id),
    CONSTRAINT fk_cl_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE,
    CONSTRAINT fk_cl_lead     FOREIGN KEY (lead_id)     REFERENCES leads(id)     ON DELETE CASCADE,
    CONSTRAINT fk_cl_assigned FOREIGN KEY (assigned_to) REFERENCES users(id)     ON DELETE SET NULL
);
CREATE INDEX idx_cl_campaign_status ON campaign_leads(campaign_id, status);
CREATE INDEX idx_cl_lead            ON campaign_leads(lead_id);
CREATE INDEX idx_cl_assigned        ON campaign_leads(assigned_to);

-- =============================================================================
-- calls (FK COMPUESTA a campaign_leads)
-- =============================================================================
CREATE TABLE calls (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    campaign_id      UUID         NOT NULL,
    lead_id          UUID         NOT NULL,
    user_id          UUID         NOT NULL,
    started_at       TIMESTAMPTZ,
    ended_at         TIMESTAMPTZ,
    duration_seconds INT,
    status           call_status,
    outcome          call_outcome,
    recording_url    VARCHAR(512),
    provider_call_id VARCHAR(255),
    notes            TEXT,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_calls_cl   FOREIGN KEY (campaign_id, lead_id) REFERENCES campaign_leads(campaign_id, lead_id) ON DELETE CASCADE,
    CONSTRAINT fk_calls_user FOREIGN KEY (user_id)              REFERENCES users(id) ON DELETE RESTRICT
);
CREATE INDEX idx_calls_cl          ON calls(campaign_id, lead_id);
CREATE INDEX idx_calls_user        ON calls(user_id);
CREATE INDEX idx_calls_status      ON calls(status);
CREATE INDEX idx_calls_outcome     ON calls(outcome);
CREATE INDEX idx_calls_created_at  ON calls(created_at);

-- =============================================================================
-- appointments
-- =============================================================================
CREATE TABLE appointments (
    id               UUID                NOT NULL DEFAULT gen_random_uuid(),
    lead_id          UUID                NOT NULL,
    user_id          UUID                NOT NULL,
    scheduled_at     TIMESTAMPTZ         NOT NULL,
    duration_minutes INT                 NOT NULL DEFAULT 30,
    status           appointment_status  NOT NULL DEFAULT 'PENDING',
    notes            TEXT,
    created_at       TIMESTAMPTZ         NOT NULL,
    updated_at       TIMESTAMPTZ         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_appt_lead FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE,
    CONSTRAINT fk_appt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);
CREATE INDEX idx_appt_lead         ON appointments(lead_id);
CREATE INDEX idx_appt_user         ON appointments(user_id);
CREATE INDEX idx_appt_scheduled_at ON appointments(scheduled_at);
CREATE INDEX idx_appt_status       ON appointments(status);

-- =============================================================================
-- integration_configs
-- =============================================================================
CREATE TABLE integration_configs (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    provider          VARCHAR(50)  NOT NULL,
    api_key_encrypted VARCHAR(512) NOT NULL,
    webhook_secret    VARCHAR(255) NOT NULL,
    config_json       JSONB,
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_intcfg_provider_enabled ON integration_configs(provider, enabled);

-- =============================================================================
-- audit_logs
-- =============================================================================
CREATE TABLE audit_logs (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id       UUID,
    entity_type   VARCHAR(100) NOT NULL,
    entity_id     UUID         NOT NULL,
    action        audit_action NOT NULL,
    changes_json  JSONB,
    created_at    TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_audit_entity          ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_user            ON audit_logs(user_id);
CREATE INDEX idx_audit_created_at      ON audit_logs(created_at);
CREATE INDEX idx_audit_changes_gin     ON audit_logs USING GIN (changes_json);