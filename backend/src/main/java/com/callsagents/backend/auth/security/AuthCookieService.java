package com.callsagents.backend.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Manages the refresh token as an HttpOnly cookie.
 *
 * <p>Storing the refresh token in a cookie (HttpOnly, SameSite=Lax) keeps it
 * out of JavaScript reach, so an XSS in the SPA can steal the short-lived
 * access token but NOT the 7-day session. The access token remains in
 * localStorage for the SPA to attach as a Bearer header.
 *
 * <p>CSRF: SameSite=Lax prevents the cookie from being sent on cross-site
 * POSTs, so refresh/logout cannot be triggered from another origin. The
 * cookie path is restricted to /api/auth, where only refresh/logout accept
 * it; authenticated data endpoints rely on the Bearer header instead.
 */
@Service
public class AuthCookieService {

    public static final String REFRESH_COOKIE_NAME = "callsagents_refresh";

    private final boolean secure;

    public AuthCookieService(@Value("${app.auth.cookie-secure:true}") boolean secure) {
        this.secure = secure;
    }

    /** Set (or rotate) the refresh cookie on the response. */
    public void setRefreshCookie(HttpServletResponse response, String refreshToken, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/api/auth")
            .maxAge(maxAge)
            .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /** Read the refresh token from the request cookie, or null when absent. */
    public String getRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** Expire the cookie immediately (logout). */
    public void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/api/auth")
            .maxAge(Duration.ZERO)
            .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}