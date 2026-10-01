package com.callsagents.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Nuevo access token emitido tras refresh")
public record RefreshResponse(
    @Schema(description = "Nuevo access token JWT", example = "eyJhbGciOi...")
    String accessToken,

    @Schema(description = "Segundos hasta que el access token expire", example = "900")
    long accessTokenExpiresInSeconds
) {
}