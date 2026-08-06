package com.callsagents.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload para rotar el refresh token")
public record RefreshRequest(
    @Schema(description = "Refresh token vigente", example = "eyJhbGciOi...", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank String refreshToken
) {
}