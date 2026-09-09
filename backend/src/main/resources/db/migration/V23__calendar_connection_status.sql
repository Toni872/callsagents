-- V23__calendar_connection_status.sql
--
-- Adds connection health tracking to calendar_integrations and a
-- conflict-calendar list for FEATURE 2 (calendar conflict selection).

ALTER TABLE calendar_integrations
    ADD COLUMN connection_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN conflict_calendar_ids JSONB;
