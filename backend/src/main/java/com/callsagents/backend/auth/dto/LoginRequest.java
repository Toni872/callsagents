package com.callsagents.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credenciales para login")
public record LoginRequest(
    @Schema(description = "Email del usuario", example = "admin@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Email String email,

    @Schema(description = "Contraseña (mínimo 8 caracteres)", example = "********", minLength = 8, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(min = 8, max = 100) String password
) {
}