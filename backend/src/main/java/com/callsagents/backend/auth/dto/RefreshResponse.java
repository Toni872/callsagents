package com.callsagents.backend.auth.dto;

public record RefreshResponse(
    String accessToken,
    String refreshToken,
    long accessTokenExpiresInSeconds
) {
}
