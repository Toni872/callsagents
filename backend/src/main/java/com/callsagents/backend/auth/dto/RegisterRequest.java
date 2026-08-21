package com.callsagents.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for POST /auth/register — public self-registration.
 *
 * No role field: public registration ALWAYS creates an AGENT account with a
 * 14-day trial. The email is normalized to lowercase before persisting.
 */
@Schema(description = "Solicitud de registro público (crea cuenta AGENT con trial de 14 días)")
public record RegisterRequest(
    @Schema(description = "Email (se normaliza a minúsculas)", example = "agent@example.com")
    @NotBlank
    @Email
    @Size(max = 255)
    String email,

    @Schema(description = "Contraseña (mínimo 8 caracteres)", example = "secret123")
    @NotBlank
    @Size(min = 8, max = 100)
    String password,

    @Schema(description = "Nombre completo", example = "Ada Lovelace")
    @NotBlank
    @Size(max = 255)
    String fullName
) {}
