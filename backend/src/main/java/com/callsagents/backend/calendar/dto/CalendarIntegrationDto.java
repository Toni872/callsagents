package com.callsagents.backend.calendar.dto;

import com.callsagents.backend.calendar.domain.CalendarIntegration;
import com.callsagents.backend.calendar.domain.CalendarProviderType;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for GET /api/calendar/integrations (and similar).
 * NEVER includes decrypted tokens — only metadata safe to expose to the user.
 */
public record CalendarIntegrationDto(
    UUID id,
    UUID userId,
    CalendarProviderType provider,
    String externalAccountEmail,
    String externalCalendarId,
    Instant accessTokenExpiresAt,
    boolean syncEnabled,
    Instant lastSyncAt,
    String lastSyncStatus,
    String lastSyncError,
    Instant createdAt
) {
    public static CalendarIntegrationDto from(CalendarIntegration e) {
        return new CalendarIntegrationDto(
            e.getId(),
            e.getUserId(),
            e.getProvider(),
            e.getExternalAccountEmail(),
            e.getExternalCalendarId(),
            e.getAccessTokenExpiresAt(),
            e.getSyncEnabled() != null && e.getSyncEnabled(),
            e.getLastSyncAt(),
            e.getLastSyncStatus() != null ? e.getLastSyncStatus().name() : null,
            e.getLastSyncError(),
            e.getCreatedAt()
        );
    }
}
