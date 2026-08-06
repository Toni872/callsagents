-- V5__appointment_external_sync.sql
--
-- Tracks whether (and to which external event) an Appointment has been
-- synced to a third-party calendar.
--
-- external_provider: NULLABLE. When set, identifies the provider
--   (GOOGLE, OUTLOOK). Paired with external_event_id, this lets us locate and
--   update the event later.
-- external_event_id: NULLABLE. The provider-specific identifier of the event.
-- external_synced_at: NULLABLE. Timestamp of the last successful sync.

ALTER TABLE appointments
    ADD COLUMN external_provider VARCHAR(32),
    ADD COLUMN external_event_id VARCHAR(255),
    ADD COLUMN external_synced_at TIMESTAMPTZ;
