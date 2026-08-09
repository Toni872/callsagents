-- V6__voice_calls.sql
--
-- Tracks outbound voice calls placed through a Voice AI provider
-- (Vapi, Retell). One row per call attempt; updated as the provider
-- sends webhooks about status changes.
--
-- vapi_call_id: provider's identifier, used to query status / retrieve
--   recording / transcript. NULL for manually logged calls (no provider).
-- provider: VAPI / RETELL / null for manual
-- status: lifecycle of the call (RINGING → IN_PROGRESS → ENDED/FAILED etc.)
-- cost_usd: per-call cost in USD, populated by webhook
-- transcript + recording_url: filled when call ends

CREATE TYPE voice_call_status AS ENUM (
    'SCHEDULED',     -- created, not yet placed
    'RINGING',       -- phone is ringing
    'IN_PROGRESS',   -- call connected, conversation ongoing
    'FORWARDING',    -- transferred to a human agent
    'ENDED',         -- call completed normally
    'FAILED',        -- call failed (provider error, no answer, busy)
    'NO_ANSWER'      -- nobody picked up
);

CREATE TABLE voice_calls (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    lead_id UUID,
    appointment_id UUID,
    user_id UUID NOT NULL,                -- agent who initiated / owns the call
    provider VARCHAR(32),                  -- 'VAPI', 'RETELL' (NULL if manual log)
    provider_call_id VARCHAR(255),         -- Vapi call id (or Retell)
    phone_number VARCHAR(32) NOT NULL,    -- number dialed
    status voice_call_status NOT NULL DEFAULT 'SCHEDULED',
    direction VARCHAR(16) NOT NULL DEFAULT 'OUTBOUND',  -- 'OUTBOUND' or 'INBOUND'
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    duration_seconds INT,
    cost_usd NUMERIC(10, 4),
    transcript TEXT,
    recording_url VARCHAR(512),
    error_message TEXT,
    metadata JSONB,                        -- provider-specific extras
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_vc_lead FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE SET NULL,
    CONSTRAINT fk_vc_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE SET NULL,
    CONSTRAINT fk_vc_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_vc_user_status ON voice_calls(user_id, status);
CREATE INDEX idx_vc_provider_call ON voice_calls(provider, provider_call_id);
CREATE INDEX idx_vc_lead ON voice_calls(lead_id);
CREATE INDEX idx_vc_started_at ON voice_calls(started_at);
