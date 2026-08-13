package com.callsagents.backend.calendar.service;

import com.callsagents.backend.calendar.domain.CalendarProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Google Calendar provider — Google OAuth + calendar.events.insert.
 *
 * Env vars (configured via .env, see RUNBOOK):
 *   GOOGLE_CLIENT_ID          from Google Cloud Console OAuth client
 *   GOOGLE_CLIENT_SECRET      same
 *   GOOGLE_REDIRECT_URI       default: http://localhost:8080/api/calendar/oauth/callback/google
 *
 * Real endpoints used:
 *   authorize:  https://accounts.google.com/o/oauth2/v2/auth
 *   token:      https://oauth2.googleapis.com/token
 *   revoke:     https://oauth2.googleapis.com/revoke
 *   events:     POST https://www.googleapis.com/calendar/v3/calendars/{id}/events
 */
@Component
public class GoogleCalendarProvider implements CalendarProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarProvider.class);

    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    private static final String EVENTS_URL = "https://www.googleapis.com/calendar/v3/calendars/%s/events";
    private static final String EVENT_URL = "https://www.googleapis.com/calendar/v3/calendars/%s/events/%s";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    /** Calendar API scope: read + write events to primary calendar. */
    private static final String SCOPE =
        "https://www.googleapis.com/auth/calendar.events " +
        "https://www.googleapis.com/auth/calendar.readonly " +
        "openid email profile";

    @Value("${app.calendar.google.client-id:}")
    private String clientId;

    @Value("${app.calendar.google.client-secret:}")
    private String clientSecret;

    @Value("${app.calendar.google.redirect-uri:http://localhost:8080/api/calendar/integrations/google/callback}")
    private String redirectUri;

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
            && clientSecret != null && !clientSecret.isBlank();
    }

    @Override
    public CalendarProviderType provider() {
        return CalendarProviderType.GOOGLE;
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                "Google OAuth not configured (missing GOOGLE_CLIENT_ID/SECRET). " +
                "See RUNBOOK.md → Calendar sync setup.");
        }
        return AUTHORIZE_URL
            + "?client_id=" + enc(clientId)
            + "&redirect_uri=" + enc(redirectUri)
            + "&scope=" + enc(SCOPE)
            + "&response_type=code"
            + "&access_type=offline"
            + "&prompt=consent"
            + "&state=" + enc(state == null ? "" : state);
    }

    @Override
    public TokenResponse exchangeCode(String code) {
        if (!isConfigured()) throw new IllegalStateException("Google OAuth not configured");
        try {
            String body = "code=" + enc(code)
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&redirect_uri=" + enc(redirectUri)
                + "&grant_type=authorization_code";

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Google token exchange failed: HTTP "
                    + resp.statusCode() + " — " + resp.body());
            }
            JsonNode json = mapper.readTree(resp.body());
            return new TokenResponse(
                json.path("access_token").asText(null),
                json.path("refresh_token").asText(null),
                json.path("expires_in").asLong(3600),
                json.path("scope").asText(SCOPE),
                json.path("token_type").asText("Bearer")
            );
        } catch (Exception e) {
            throw new RuntimeException("Google token exchange error: " + e.getMessage(), e);
        }
    }

    @Override
    public TokenResponse refreshAccessToken(String refreshToken) {
        if (!isConfigured()) throw new IllegalStateException("Google OAuth not configured");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Cannot refresh without a refresh token");
        }
        try {
            String body = "client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&refresh_token=" + enc(refreshToken)
                + "&grant_type=refresh_token";

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Google token refresh failed: HTTP "
                    + resp.statusCode() + " — " + resp.body());
            }
            JsonNode json = mapper.readTree(resp.body());
            return new TokenResponse(
                json.path("access_token").asText(null),
                json.path("refresh_token").asText(null), // rotated refresh token, if any
                json.path("expires_in").asLong(3600),
                json.path("scope").asText(SCOPE),
                json.path("token_type").asText("Bearer")
            );
        } catch (Exception e) {
            throw new RuntimeException("Google token refresh error: " + e.getMessage(), e);
        }
    }

    @Override
    public void revokeTokens(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(REVOKE_URL + "?token=" + enc(refreshToken)))
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("Google refresh token revoked (best-effort)");
        } catch (Exception e) {
            log.warn("Google revoke failed (non-fatal): {}", e.getMessage());
        }
    }

    @Override
    public String fetchAccountEmail(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) return null;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(USERINFO_URL))
                .header("Authorization", "Bearer " + accessToken)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Google userinfo failed (HTTP {}), account email left empty", resp.statusCode());
                return null;
            }
            JsonNode json = mapper.readTree(resp.body());
            String email = json.path("email").asText(null);
            if (email != null && !email.isBlank()) {
                log.info("Google account email resolved: {}", email);
                return email;
            }
            log.warn("Google userinfo returned no email — account email left empty");
            return null;
        } catch (Exception e) {
            log.warn("Google userinfo error (non-fatal): {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String createEvent(String accessToken, String externalCalendarId, EventPayload event) {
        if (!isConfigured()) throw new IllegalStateException("Google OAuth not configured");
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token is empty — user must re-authenticate");
        }
        String calendarId = externalCalendarId != null ? externalCalendarId : "primary";

        try {
            String json = buildEventJson(event);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(EVENTS_URL.formatted(calendarId)))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 401) {
                throw new RuntimeException("Google access token rejected (401) — user must re-authenticate");
            }
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Google events.insert failed: HTTP "
                    + resp.statusCode() + " — " + resp.body());
            }
            JsonNode created = mapper.readTree(resp.body());
            return created.path("id").asText();
        } catch (Exception e) {
            throw new RuntimeException("Google createEvent failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String updateEvent(String accessToken, String externalCalendarId, String eventId, EventPayload event) {
        if (!isConfigured()) throw new IllegalStateException("Google OAuth not configured");
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token is empty — user must re-authenticate");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Cannot update a Google event without an event id");
        }
        String calendarId = externalCalendarId != null ? externalCalendarId : "primary";

        try {
            String json = buildEventJson(event);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(EVENT_URL.formatted(calendarId, eventId)))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 401) {
                throw new RuntimeException("Google access token rejected (401) — user must re-authenticate");
            }
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Google events.update failed: HTTP "
                    + resp.statusCode() + " — " + resp.body());
            }
            JsonNode updated = mapper.readTree(resp.body());
            return updated.path("id").asText();
        } catch (Exception e) {
            throw new RuntimeException("Google updateEvent failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteEvent(String accessToken, String externalCalendarId, String eventId) {
        if (!isConfigured()) throw new IllegalStateException("Google OAuth not configured");
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token is empty — user must re-authenticate");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Cannot delete a Google event without an event id");
        }
        String calendarId = externalCalendarId != null ? externalCalendarId : "primary";

        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(EVENT_URL.formatted(calendarId, eventId)))
                .header("Authorization", "Bearer " + accessToken)
                .timeout(Duration.ofSeconds(15))
                .DELETE()
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 401) {
                throw new RuntimeException("Google access token rejected (401) — user must re-authenticate");
            }
            // 404 = already gone; treat as success (idempotent delete).
            if (resp.statusCode() / 100 != 2 && resp.statusCode() != 404) {
                throw new RuntimeException("Google events.delete failed: HTTP "
                    + resp.statusCode() + " — " + resp.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Google deleteEvent failed: " + e.getMessage(), e);
        }
    }

    /** Event body shared by insert and update. */
    private String buildEventJson(EventPayload event) throws Exception {
        return """
            {
              "summary": %s,
              "description": %s,
              "start": {"dateTime": "%s", "timeZone": "%s"},
              "end":   {"dateTime": "%s", "timeZone": "%s"}
            }
            """.formatted(
                mapper.writeValueAsString(nullToEmpty(event.summary())),
                mapper.writeValueAsString(nullToEmpty(event.description())),
                event.start().toString(),
                nullToEmpty(event.timeZone(), "UTC"),
                event.end().toString(),
                nullToEmpty(event.timeZone(), "UTC")
            );
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static String nullToEmpty(String s, String def) { return s == null ? def : s; }
}
