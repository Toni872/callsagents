-- V17__escalation_orchestrator.sql
--
-- Escalation orchestration: after a WhatsApp chatbot qualifies a lead (the
-- lead confirms a demo through the interactive flow), the system sends a
-- follow-up WhatsApp message, waits a per-business timeout, and if the lead
-- does not reply it escalates automatically to a Retell AI outbound voice
-- call. The voice call is a FALLBACK ONLY -- never a first contact.
--
-- 1) business_profiles: 4 new escalation configuration columns (per-business).
-- 2) escalation_stage enum + escalations table holding the qualified-lead flow.

-- ===========================================================================
-- 1) Per-business escalation configuration
-- ===========================================================================
ALTER TABLE business_profiles ADD COLUMN escalation_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE business_profiles ADD COLUMN reply_timeout_minutes INTEGER NOT NULL DEFAULT 30;
ALTER TABLE business_profiles ADD COLUMN followup_message TEXT;
ALTER TABLE business_profiles ADD COLUMN voice_agent_id VARCHAR(100);

-- ===========================================================================
-- 2) escalation_stage enum (create before the table that uses it)
-- ===========================================================================
CREATE TYPE escalation_stage AS ENUM
 ('QUALIFIED','FOLLOWUP_SENT','WAITING_REPLY','VOICE_CALLED','RESOLVED','ABANDONED','CANCELLED');

-- ===========================================================================
-- 3) escalations table
-- ===========================================================================
CREATE TABLE escalations (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id           UUID NOT NULL REFERENCES leads(id) ON DELETE CASCADE,
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stage             escalation_stage NOT NULL,
    followup_sent_at  TIMESTAMPTZ,
    waiting_until     TIMESTAMPTZ,
    voice_called_at   TIMESTAMPTZ,
    provider_call_id  VARCHAR(255),
    voice_outcome     VARCHAR(50),
    metadata          JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_escalations_lead ON escalations (lead_id);
CREATE INDEX idx_escalations_stage_waiting ON escalations (stage, waiting_until);
CREATE INDEX idx_escalations_user ON escalations (user_id);
