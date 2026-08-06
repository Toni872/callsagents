package com.callsagents.backend.calendar.domain;

/**
 * External calendar providers supported by Callsagents.
 *
 * Stored as VARCHAR in calendar_integrations.provider (not a Postgres ENUM)
 * so we can add new providers (e.g. APPLE) without a Flyway migration.
 *
 * The companion CalendarProvider interface (service layer) is named
 * CalendarProvider (no "Type" suffix) — Java doesn't have "import as" so this
 * split keeps the two names distinct.
 */
public enum CalendarProviderType {
    GOOGLE,
    OUTLOOK
}
