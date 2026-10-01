package com.callsagents.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload opcional de compatibilidad para rotar el refresh token.
 *
 * <p>El refresh token se lee PRIMERO de la cookie httpOnly; este body es un
 * fallback para clientes API que no pueden enviar cookies. Por eso su único
 * campo ya no es obligatorio.
 */
@Schema(description = "Payload de compatibilidad para rotar el refresh token (alternativa a la cookie httpOnly)")
public record RefreshRequest(
    @Schema(description = "Refresh token vigente (opcional: se prefiere la cookie httpOnly)", example = "eyJhbGciOi...")
    String refreshToken
) {
}