package com.callsagents.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta de login con access token JWT")
public record LoginResponse(
    @Schema(description = "Access token JWT (TTL 15min)", example = "eyJhbGciOi...")
    String accessToken,

    @Schema(description = "Segundos hasta que el access token expire", example = "900")
    long accessTokenExpiresInSeconds,

    @Schema(description = "Perfil del usuario autenticado")
    UserDto user
) {
}