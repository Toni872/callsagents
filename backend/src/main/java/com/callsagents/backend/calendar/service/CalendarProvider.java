package com.callsagents.backend.calendar.service;

import com.callsagents.backend.calendar.domain.CalendarProviderType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Abstraction over a 3rd-party calendar provider (Google, Outlook).
 *
 * One impl per provider. Impls are Spring beans; CalendarSyncService
 * dispatches by CalendarProviderType enum.
 *
 * MVP scope: only the methods actually called by CalendarSyncService
 * are real. Everything else throws UnsupportedOperationException so we can
 * detect what we haven't built yet.
 */
public interface CalendarProvider {

    /** OAuth: build the URL we redirect users to in order to authorize. */
    String buildAuthorizationUrl(String state);

    /**
     * OAuth: trade the ?code callback param for access + refresh tokens.
     * Returns the encrypted-tokens + expiry in a normalized DTO.
     */
    TokenResponse exchangeCode(String code);

    /**
     * OAuth: best-effort revocation when the user disconnects. Null tokens are OK
     * if the provider doesn't support revocation (we just drop them locally).
     */
    void revokeTokens(String refreshToken);

    /**
     * Create an external calendar event. Returns the provider-assigned event id
     * so we can locate/update/delete it later.
     */
    String createEvent(
        String decryptedAccessToken,
        String externalCalendarId,
        EventPayload event
    );

    /** Provider enum value. Used for routing + storage. */
    CalendarProviderType provider();

    /** True if OAuth credentials for this provider are configured in env. */
    boolean isConfigured();

    /** DTOs exchanged across the boundary. */

    record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") Long expiresInSeconds,
        @JsonProperty("scope") String scope,
        @JsonProperty("token_type") String tokenType
    ) {
        public Instant accessTokenExpiresAt() {
            return Instant.now().plusSeconds(expiresInSeconds != null ? expiresInSeconds : 3600);
        }
    }

    record EventPayload(
        String summary,
        String description,
        Instant start,
        Instant end,
        String timeZone,
        List<UUID> attendeeEmails
    ) {}
}
