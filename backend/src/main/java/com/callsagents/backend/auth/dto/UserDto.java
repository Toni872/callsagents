package com.callsagents.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Perfil público del usuario")
public record UserDto(
    @Schema(description = "ID interno del usuario", example = "11111111-1111-1111-1111-111111111111")
    UUID id,

    @Schema(description = "Email (usado como username)", example = "admin@example.com")
    String email,

    @Schema(description = "Nombre completo", example = "Ada Lovelace")
    String fullName,

    @Schema(description = "Rol del usuario", example = "ADMIN", allowableValues = {"ADMIN", "SUPERVISOR", "AGENT"})
    String role
) {
}