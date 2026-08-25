package com.callsagents.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Solicitud de login/registro con Google OAuth2")
public record GoogleAuthRequest(
    @Schema(description = "Google ID token (credential) obtenido del botón de Google", example = "eyJhbGciOiJSUzI1NiIs...")
    @NotBlank
    String credential
) {}
