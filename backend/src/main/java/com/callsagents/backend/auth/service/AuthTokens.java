package com.callsagents.backend.auth.service;

import com.callsagents.backend.auth.dto.UserDto;

/**
 * Internal token pair produced by {@link AuthService}.
 *
 * <p>The refresh token is delivered to the browser exclusively via an HttpOnly
 * cookie (never in a JSON body), so this record keeps both halves together for
 * the controller to split: access token → response body, refresh token → cookie.
 */
public record AuthTokens(
    String accessToken,
    String refreshToken,
    long accessTokenExpiresInSeconds,
    UserDto user
) {
}