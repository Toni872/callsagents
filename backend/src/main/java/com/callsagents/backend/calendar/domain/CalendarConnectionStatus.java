package com.callsagents.backend.calendar.domain;

/**
 * Health status of the OAuth connection between a Callsagents user and an external
 * calendar provider. Stored as VARCHAR in calendar_integrations.connection_status.
 */
public enum CalendarConnectionStatus {
    /** Token is valid and sync is operational. */
    ACTIVE,
    /** Access/refresh token rejected (401) — user must re-authorize. */
    NEEDS_REAUTH,
    /** Provider returned 403 — granted scopes are insufficient. */
    INSUFFICIENT_PERMISSIONS
}
