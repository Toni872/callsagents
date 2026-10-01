package com.callsagents.backend.auth.controller;

import com.callsagents.backend.auth.dto.LoginRequest;
import com.callsagents.backend.auth.dto.LoginResponse;
import com.callsagents.backend.auth.dto.RefreshRequest;
import com.callsagents.backend.auth.dto.RefreshResponse;
import com.callsagents.backend.auth.dto.RegisterRequest;
import com.callsagents.backend.auth.dto.UserDto;
import com.callsagents.backend.auth.dto.GoogleAuthRequest;
import com.callsagents.backend.auth.security.AuthCookieService;
import com.callsagents.backend.auth.security.JwtProperties;
import com.callsagents.backend.auth.service.AuthService;
import com.callsagents.backend.auth.service.AuthTokens;
import com.callsagents.backend.common.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
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
    private final AuthCookieService authCookieService;
    private final JwtProperties jwtProperties;
    private final Environment environment;

    public AuthController(AuthService authService,
                          AuthCookieService authCookieService,
                          JwtProperties jwtProperties,
                          Environment environment) {
        this.authService = authService;
        this.authCookieService = authCookieService;
        this.jwtProperties = jwtProperties;
        this.environment = environment;
    }

    @Operation(
        summary = "Iniciar sesión",
        description = "Autentica con email + password. Devuelve accessToken (15min) y setea el refreshToken (7d) como cookie httpOnly rotable."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login exitoso"),
        @ApiResponse(responseCode = "401", description = "Credenciales inválidas"),
        @ApiResponse(responseCode = "400", description = "Payload inválido")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletResponse response) {
        AuthTokens tokens = authService.login(req);
        authCookieService.setRefreshCookie(response, tokens.refreshToken(), jwtProperties.getRefreshTokenTtl());
        return ResponseEntity.ok(toLoginResponse(tokens));
    }

    @Operation(
        summary = "Registro público",
        description = "Crea una cuenta AGENT con trial de 7 días y hace login automático: devuelve los mismos tokens que POST /auth/login."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cuenta creada y sesión iniciada"),
        @ApiResponse(responseCode = "400", description = "Payload inválido o email ya registrado")
    })
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest req,
                                                  HttpServletResponse response) {
        AuthTokens tokens = authService.register(req);
        authCookieService.setRefreshCookie(response, tokens.refreshToken(), jwtProperties.getRefreshTokenTtl());
        return ResponseEntity.status(HttpStatus.CREATED).body(toLoginResponse(tokens));
    }

    @Operation(
        summary = "Login/registro con Google",
        description = "Acepta un Google ID token. Si el email no existe, crea la cuenta automáticamente (registro + login)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login exitoso"),
        @ApiResponse(responseCode = "400", description = "Token de Google inválido")
    })
    @PostMapping("/google")
    public ResponseEntity<LoginResponse> googleLogin(@Valid @RequestBody GoogleAuthRequest req,
                                                     HttpServletResponse response) {
        String clientId = environment.getProperty("app.google.client-id", "");
        AuthTokens tokens = authService.googleLogin(req.credential(), clientId);
        authCookieService.setRefreshCookie(response, tokens.refreshToken(), jwtProperties.getRefreshTokenTtl());
        return ResponseEntity.ok(toLoginResponse(tokens));
    }

    @Operation(
        summary = "Refrescar access token",
        description = "Rota el refresh token de la cookie httpOnly (o del body como fallback). El refreshToken anterior queda invalidado."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Access token renovado"),
        @ApiResponse(responseCode = "401", description = "Refresh token inválido o expirado")
    })
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(HttpServletRequest request,
                                                   HttpServletResponse response,
                                                   @RequestBody(required = false) RefreshRequest req) {
        String refreshToken = authCookieService.getRefreshCookie(request);
        if (refreshToken == null && req != null) {
            refreshToken = req.refreshToken();
        }
        if (refreshToken == null) {
            throw new UnauthorizedException("Missing refresh token");
        }
        AuthTokens tokens = authService.refresh(refreshToken);
        // Rotación: la cookie vieja queda invalidada; emitimos la nueva.
        authCookieService.setRefreshCookie(response, tokens.refreshToken(), jwtProperties.getRefreshTokenTtl());
        return ResponseEntity.ok(new RefreshResponse(
            tokens.accessToken(),
            tokens.accessTokenExpiresInSeconds()
        ));
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Cerrar sesión",
        description = "Revoca el access token actual y el refresh token (cookie httpOnly o body). Requiere Authorization: Bearer."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Sesión cerrada"),
        @ApiResponse(responseCode = "401", description = "Token inválido o ausente")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestHeader("Authorization") String authHeader,
        @RequestBody(required = false) RefreshRequest req
    ) {
        String accessToken = authHeader == null ? null : authHeader.replace("Bearer ", "").trim();
        String refreshToken = authCookieService.getRefreshCookie(request);
        if (refreshToken == null && req != null) {
            refreshToken = req.refreshToken();
        }
        authService.logout(accessToken, refreshToken);
        authCookieService.clearRefreshCookie(response);
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

    private static LoginResponse toLoginResponse(AuthTokens tokens) {
        return new LoginResponse(
            tokens.accessToken(),
            tokens.accessTokenExpiresInSeconds(),
            tokens.user()
        );
    }
}