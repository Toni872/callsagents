-- V20__drop_integration_configs.sql
--
-- Remove the dead `integration_configs` table (from V1): no entity, repository,
-- service, or controller ever referenced it (the "integrations" module has no
-- Java code). Discovered during the 2026-08-29 cleanup (ADR-011 follows Twilio
-- removal; this drops the orphaned table). Drop the index first, then the table.

DROP INDEX IF EXISTS idx_intcfg_provider_enabled;
DROP TABLE IF EXISTS integration_configs;