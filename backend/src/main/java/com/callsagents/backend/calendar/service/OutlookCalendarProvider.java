package com.callsagents.backend.calendar.service;

import com.callsagents.backend.calendar.domain.CalendarProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Outlook / Microsoft Graph provider — PLACEHOLDER.
 *
 * The Google provider above is the live reference impl. To finish this for
 * Outlook, follow the same shape using Microsoft Graph API:
 *
 *   authorize URL:  https://login.microsoftonline.com/common/oauth2/v2.0/authorize
 *   token URL:      https://login.microsoftonline.com/common/oauth2/v2.0/token
 *   events API:     POST https://graph.microsoft.com/v1.0/me/events
 *   scope:          Calendars.ReadWrite offline_access openid profile
 *
 * Env vars (configure via .env when implementing):
 *   OUTLOOK_CLIENT_ID, OUTLOOK_CLIENT_SECRET, OUTLOOK_REDIRECT_URI
 */
@Component
public class OutlookCalendarProvider implements CalendarProvider {

    private static final Logger log = LoggerFactory.getLogger(OutlookCalendarProvider.class);

    @Value("${app.calendar.outlook.client-id:}")
    private String clientId;

    @Value("${app.calendar.outlook.client-secret:}")
    private String clientSecret;

    @Override
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
            && clientSecret != null && !clientSecret.isBlank();
    }

    @Override
    public CalendarProviderType provider() {
        return CalendarProviderType.OUTLOOK;
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        throw new UnsupportedOperationException(
            "Outlook provider scaffolded but not yet implemented. " +
            "Configure OUTLOOK_CLIENT_ID/SECRET and complete this method.");
    }

    @Override
    public TokenResponse exchangeCode(String code) {
        throw new UnsupportedOperationException("Outlook exchangeCode not implemented");
    }

    @Override
    public TokenResponse refreshAccessToken(String refreshToken) {
        throw new UnsupportedOperationException("Outlook refreshAccessToken not implemented");
    }

    @Override
    public void revokeTokens(String refreshToken) {
        log.warn("Outlook revokeTokens not implemented — local tokens will be dropped anyway");
    }

    @Override
    public String createEvent(String accessToken, String externalCalendarId, EventPayload event) {
        throw new UnsupportedOperationException("Outlook createEvent not implemented");
    }

    @Override
    public String updateEvent(String accessToken, String externalCalendarId, String eventId, EventPayload event) {
        throw new UnsupportedOperationException("Outlook updateEvent not implemented");
    }

    @Override
    public void deleteEvent(String accessToken, String externalCalendarId, String eventId) {
        throw new UnsupportedOperationException("Outlook deleteEvent not implemented");
    }
}
