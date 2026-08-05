package com.callsagents.backend.auth.dto;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    long accessTokenExpiresInSeconds,
    UserDto user
) {
}
