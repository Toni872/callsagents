package com.callsagents.backend.auth.controller;

import com.callsagents.backend.auth.dto.LoginRequest;
import com.callsagents.backend.auth.dto.LoginResponse;
import com.callsagents.backend.auth.dto.RefreshRequest;
import com.callsagents.backend.auth.dto.RefreshResponse;
import com.callsagents.backend.auth.dto.UserDto;
import com.callsagents.backend.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Autenticación y sesión: login, refresh, logout, perfil actual")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
        summary = "Iniciar sesión",
        description = "Autentica con email + password. Devuelve accessToken (15min) y refreshToken (7d) rotable."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login exitoso"),
        @ApiResponse(responseCode = "401", description = "Credenciales inválidas"),
        @ApiResponse(responseCode = "400", description = "Payload inválido")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @Operation(
        summary = "Refrescar access token",
        description = "Intercambia un refreshToken válido por un nuevo par de tokens (rotación). El refreshToken anterior queda invalidado."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tokens renovados"),
        @ApiResponse(responseCode = "401", description = "Refresh token inválido o expirado")
    })
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Cerrar sesión",
        description = "Revoca el access token actual y, opcionalmente, el refresh token enviado en el body. Requiere Authorization: Bearer."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Sesión cerrada"),
        @ApiResponse(responseCode = "401", description = "Token inválido o ausente")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @RequestHeader("Authorization") String authHeader,
        @RequestBody(required = false) RefreshRequest req
    ) {
        String accessToken = authHeader == null ? null : authHeader.replace("Bearer ", "").trim();
        String refreshToken = req != null ? req.refreshToken() : null;
        authService.logout(accessToken, refreshToken);
        return ResponseEntity.noContent().build();
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Obtener usuario actual",
        description = "Devuelve el perfil del usuario autenticado según el JWT."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Perfil devuelto"),
        @ApiResponse(responseCode = "401", description = "Token inválido o ausente")
    })
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Authentication authentication) {
        // JwtAuthenticationFilter sets the email String as the principal, so we use
        // Authentication#getName() (which returns principal.toString()) instead of
        // @AuthenticationPrincipal UserDetails, which would resolve to null here.
        return ResponseEntity.ok(authService.getCurrentUser(authentication.getName()));
    }
}