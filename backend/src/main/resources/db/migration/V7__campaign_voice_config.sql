-- V7__campaign_voice_config.sql
--
-- Voice-agent configuration per campaign (Retell). Each campaign defines the
-- company it represents, its website, industry, services and tone; the backend
-- composes the system prompt from these fields (PromptComposer) and injects it
-- per call via retell_llm_dynamic_variables over the generic agent
-- (RETELL_AGENT_ID).
--
-- Nullable on purpose: no default, no backfill. A campaign without these
-- fields keeps the current behavior (generic agent, no dynamic variables).
-- No ENUMs and no ALTER TYPE → avoids the NAMED_ENUM gotcha (docs/00-handoff.md).
-- Rollback: V8 DROP COLUMN (never edit this file once applied).

ALTER TABLE campaigns
    ADD COLUMN company  VARCHAR(255),
    ADD COLUMN website  VARCHAR(255),
    ADD COLUMN industry VARCHAR(255),
    ADD COLUMN services TEXT,
    ADD COLUMN tone     VARCHAR(255);
