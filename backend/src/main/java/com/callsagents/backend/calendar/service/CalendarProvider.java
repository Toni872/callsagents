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
     * OAuth: exchange a stored refresh token for a fresh access token.
     * Access tokens are short-lived (Google: ~1h); called when expired or on 401.
     * A returned refresh_token (rotation) replaces the stored one when present.
     */
    TokenResponse refreshAccessToken(String refreshToken);

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

    /**
     * Update an existing external calendar event (idempotent PUT).
     * Only meaningful when eventId is known; callers fall back to create
     * when the event was never persisted.
     */
    String updateEvent(
        String decryptedAccessToken,
        String externalCalendarId,
        String eventId,
        EventPayload event
    );

    /**
     * Delete an external calendar event. Callers skip this when eventId is null;
     * a 404 from the provider is treated as success (nothing to delete).
     */
    void deleteEvent(
        String decryptedAccessToken,
        String externalCalendarId,
        String eventId
    );

    /** Provider enum value. Used for routing + storage. */
    CalendarProviderType provider();

    /**
     * Best-effort: the email of the external account that just authorized
     * (e.g. Google userinfo). Used for display only ("Conectado como X").
     * Returns null when the provider doesn't support it or the call fails —
     * the integration still saves without it.
     */
    default String fetchAccountEmail(String accessToken) {
        return null;
    }

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
