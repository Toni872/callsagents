-- Store the provider's canonical event link (Google htmlLink) so the app can
-- render a working "Ver en Google Calendar" deep link instead of reconstructing
-- one from the event id (that construction 404s on non-primary calendars).
ALTER TABLE appointments ADD COLUMN external_event_url VARCHAR(1024);