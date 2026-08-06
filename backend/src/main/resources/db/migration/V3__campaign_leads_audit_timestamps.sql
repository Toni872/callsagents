-- V3__campaign_leads_audit_timestamps.sql
--
-- V1__initial_schema.sql declared `created_at` and `updated_at` columns on
-- campaign_leads as NOT NULL, but the original entity failed to model them,
-- so any INSERT via JPA into campaign_leads violated the constraint.
--
-- This migration makes those columns safely default so existing rows (none
-- in fresh installs, possible N rows in upgraded installs) are valid; the
-- CampaignLead @PrePersist hook keeps them in sync going forward.
--
-- Safe to apply repeatedly: ADD COLUMN IF NOT EXISTS is idempotent.

ALTER TABLE campaign_leads
    ALTER COLUMN created_at SET DEFAULT NOW();

ALTER TABLE campaign_leads
    ALTER COLUMN updated_at SET DEFAULT NOW();
