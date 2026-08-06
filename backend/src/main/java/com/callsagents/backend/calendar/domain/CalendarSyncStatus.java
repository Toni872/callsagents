package com.callsagents.backend.calendar.domain;

/**
 * Status of the last external-sync attempt for a calendar integration.
 * Stored as Postgres ENUM (calendar_sync_status) — see V4__calendar_integrations.sql.
 */
public enum CalendarSyncStatus {
    /** Sync not yet attempted (e.g. just connected, never made an appointment) */
    PENDING,
    /** Last sync succeeded */
    SYNCED,
    /** Last sync failed; see calendar_integrations.last_sync_error for details */
    FAILED
}
