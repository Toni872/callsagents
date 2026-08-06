package com.callsagents.backend.auth.service;

import com.callsagents.backend.auth.dto.LoginRequest;
import com.callsagents.backend.auth.dto.LoginResponse;
import com.callsagents.backend.auth.dto.RefreshRequest;
import com.callsagents.backend.auth.dto.RefreshResponse;
import com.callsagents.backend.auth.dto.UserDto;
import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.auth.entity.UserStatus;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.auth.security.JwtProperties;
import com.callsagents.backend.auth.security.JwtService;
import com.callsagents.backend.auth.security.RefreshTokenService;
import com.callsagents.backend.common.exception.UnauthorizedException;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AuthService — pure Mockito, no Spring context.
 * Covers: login, refresh (rotation + reuse detection), logout (access + refresh),
 * getCurrentUser.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;
    @Mock private JwtProperties jwtProperties;
    @Mock private RefreshTokenService refreshTokenService;

    private AuthService authService;

    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String EMAIL = "admin@callsagents.local";
    private static final String ACCESS = "fake.access.token";
    private static final String REFRESH = "fake.refresh.token";
    private static final String NEW_ACCESS = "new.access.token";
    private static final String NEW_REFRESH = "new.refresh.token";
    private static final String REFRESH_JTI = "refresh-jti-001";
    private static final String NEW_REFRESH_JTI = "new-refresh-jti-002";
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            authenticationManager,
            userRepository,
            jwtService,
            jwtProperties,
            refreshTokenService
        );
    }

    private User buildUser() {
        User u = new User();
        u.setId(USER_ID);
        u.setEmail(EMAIL);
        u.setPasswordHash("$2b$10$abc");
        u.setFullName("Admin");
        u.setRole(UserRole.ADMIN);
        u.setStatus(UserStatus.ACTIVE);
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        return u;
    }

    // ---------------------- login ----------------------

    @Test
    @DisplayName("login: returns tokens + updates lastLoginAt + stores refresh in Redis")
    void login_success() {
        LoginRequest req = new LoginRequest(EMAIL, "admin123");
        User user = buildUser();
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtProperties.getAccessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        when(jwtProperties.getRefreshTokenTtl()).thenReturn(REFRESH_TTL);
        when(jwtService.generateAccessToken(user)).thenReturn(ACCESS);
        when(jwtService.generateRefreshToken(user)).thenReturn(REFRESH);
        when(jwtService.parseToken(REFRESH)).thenReturn(Optional.of(claimsWithJti(REFRESH_JTI, USER_ID, false)));

        LoginResponse response = authService.login(req);

        // Authenticate called with creds
        ArgumentCaptor<UsernamePasswordAuthenticationToken> authCaptor =
            ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(authCaptor.capture());
        assertThat(authCaptor.getValue().getPrincipal()).isEqualTo(EMAIL);
        assertThat(authCaptor.getValue().getCredentials()).isEqualTo("admin123");

        // Last login updated + saved
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getLastLoginAt()).isNotNull();

        // Refresh stored with TTL
        verify(refreshTokenService).storeRefreshToken(USER_ID, REFRESH_JTI, REFRESH_TTL);

        // Response shape
        assertThat(response.accessToken()).isEqualTo(ACCESS);
        assertThat(response.refreshToken()).isEqualTo(REFRESH);
        assertThat(response.accessTokenExpiresInSeconds()).isEqualTo(900L);
        assertThat(response.user().email()).isEqualTo(EMAIL);
        assertThat(response.user().role()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("login: invalid credentials propagate BadCredentialsException")
    void login_invalidCredentials() {
        LoginRequest req = new LoginRequest(EMAIL, "wrong");
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByEmail(anyString());
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("login: user deleted between auth and lookup throws BadCredentials")
    void login_userNotFoundAfterAuth() {
        LoginRequest req = new LoginRequest(EMAIL, "admin123");
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessageContaining("Invalid credentials");

        verify(jwtService, never()).generateAccessToken(any());
    }

    // ---------------------- refresh ----------------------

    @Test
    @DisplayName("refresh: successful rotation stores new + revokes old")
    void refresh_success() {
        RefreshRequest req = new RefreshRequest(REFRESH);
        User user = buildUser();
        when(jwtProperties.getAccessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        when(jwtProperties.getRefreshTokenTtl()).thenReturn(REFRESH_TTL);
        when(jwtService.parseToken(REFRESH))
            .thenReturn(Optional.of(claimsWithJti(REFRESH_JTI, USER_ID, true)));
        when(jwtService.isRefreshToken(any())).thenReturn(true);
        when(refreshTokenService.isRefreshTokenValid(USER_ID, REFRESH_JTI)).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn(NEW_ACCESS);
        when(jwtService.generateRefreshToken(user)).thenReturn(NEW_REFRESH);
        when(jwtService.parseToken(NEW_REFRESH))
            .thenReturn(Optional.of(claimsWithJti(NEW_REFRESH_JTI, USER_ID, true)));

        RefreshResponse response = authService.refresh(req);

        // Old refresh revoked
        verify(refreshTokenService).revokeRefreshToken(USER_ID, REFRESH_JTI);
        // New refresh stored
        verify(refreshTokenService).storeRefreshToken(USER_ID, NEW_REFRESH_JTI, REFRESH_TTL);

        assertThat(response.accessToken()).isEqualTo(NEW_ACCESS);
        assertThat(response.refreshToken()).isEqualTo(NEW_REFRESH);
        assertThat(response.accessTokenExpiresInSeconds()).isEqualTo(900L);
    }

    @Test
    @DisplayName("refresh: reuse detection revokes all user tokens and throws 401")
    void refresh_reuseDetection() {
        RefreshRequest req = new RefreshRequest(REFRESH);
        when(jwtService.parseToken(REFRESH))
            .thenReturn(Optional.of(claimsWithJti(REFRESH_JTI, USER_ID, true)));
        when(jwtService.isRefreshToken(any())).thenReturn(true);
        when(refreshTokenService.isRefreshTokenValid(USER_ID, REFRESH_JTI)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(req))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("reuse");

        // All tokens revoked
        verify(refreshTokenService).revokeAllRefreshTokens(USER_ID);
        // No new tokens generated
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("refresh: invalid token throws UnauthorizedException")
    void refresh_invalidToken() {
        RefreshRequest req = new RefreshRequest(REFRESH);
        when(jwtService.parseToken(REFRESH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(req))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Invalid or expired");

        verify(refreshTokenService, never()).isRefreshTokenValid(any(), anyString());
    }

    @Test
    @DisplayName("refresh: token signed but not a refresh type throws UnauthorizedException")
    void refresh_accessTokenUsedAsRefresh() {
        RefreshRequest req = new RefreshRequest(REFRESH);
        when(jwtService.parseToken(REFRESH))
            .thenReturn(Optional.of(claimsWithJti(REFRESH_JTI, USER_ID, false)));
        when(jwtService.isRefreshToken(any())).thenReturn(false); // access token, not refresh

        assertThatThrownBy(() -> authService.refresh(req))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("not a refresh");

        verify(refreshTokenService, never()).isRefreshTokenValid(any(), anyString());
    }

    // ---------------------- logout ----------------------

    @Test
    @DisplayName("logout: revokes access token jti with remaining TTL")
    void logout_revokesAccess() {
        JWTClaimsSet accessClaims = claimsWithJti("access-jti-100", USER_ID, false);
        when(jwtService.parseToken(ACCESS)).thenReturn(Optional.of(accessClaims));
        when(jwtService.isRefreshToken(accessClaims)).thenReturn(false);

        authService.logout(ACCESS, null);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(refreshTokenService).revokeAccessToken(eq("access-jti-100"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isPositive();
        verify(refreshTokenService, never()).revokeRefreshToken(any(), anyString());
    }

    @Test
    @DisplayName("logout: revokes refresh token when provided")
    void logout_revokesRefresh() {
        JWTClaimsSet refreshClaims = claimsWithJti(REFRESH_JTI, USER_ID, true);
        when(jwtService.parseToken(REFRESH)).thenReturn(Optional.of(refreshClaims));
        when(jwtService.isRefreshToken(refreshClaims)).thenReturn(true);

        authService.logout(null, REFRESH);

        verify(refreshTokenService).revokeRefreshToken(USER_ID, REFRESH_JTI);
    }

    @Test
    @DisplayName("logout: refresh token provided as access is ignored (not revoked)")
    void logout_refreshPassedAsAccessIgnored() {
        JWTClaimsSet refreshClaims = claimsWithJti(REFRESH_JTI, USER_ID, true);
        when(jwtService.parseToken(ACCESS)).thenReturn(Optional.of(refreshClaims));
        when(jwtService.isRefreshToken(refreshClaims)).thenReturn(true); // IS a refresh token

        authService.logout(ACCESS, null);

        // Access was actually a refresh — should NOT be revoked as access
        verify(refreshTokenService, never()).revokeAccessToken(anyString(), any());
    }

    @Test
    @DisplayName("logout: access token provided as refresh is ignored")
    void logout_accessPassedAsRefreshIgnored() {
        JWTClaimsSet accessClaims = claimsWithJti("access-jti-200", USER_ID, false);
        when(jwtService.parseToken(REFRESH)).thenReturn(Optional.of(accessClaims));
        when(jwtService.isRefreshToken(accessClaims)).thenReturn(false); // IS an access token

        authService.logout(null, REFRESH);

        // Refresh was actually an access — should NOT be revoked as refresh
        verify(refreshTokenService, never()).revokeRefreshToken(any(), anyString());
    }

    @Test
    @DisplayName("logout: null access token does nothing for access side")
    void logout_nullAccess() {
        authService.logout(null, null);

        verify(jwtService, never()).parseToken(anyString());
        verify(refreshTokenService, never()).revokeAccessToken(anyString(), any());
    }

    @Test
    @DisplayName("logout: blank access token does nothing for access side")
    void logout_blankAccess() {
        authService.logout("", null);

        verify(jwtService, never()).parseToken(anyString());
        verify(refreshTokenService, never()).revokeAccessToken(anyString(), any());
    }

    @Test
    @DisplayName("logout: invalid access token (parse returns empty) does nothing")
    void logout_invalidAccess() {
        when(jwtService.parseToken(ACCESS)).thenReturn(Optional.empty());

        authService.logout(ACCESS, null);

        verify(refreshTokenService, never()).revokeAccessToken(anyString(), any());
    }

    @Test
    @DisplayName("logout: invalid refresh token (parse returns empty) does nothing")
    void logout_invalidRefresh() {
        when(jwtService.parseToken(REFRESH)).thenReturn(Optional.empty());

        authService.logout(null, REFRESH);

        verify(refreshTokenService, never()).revokeRefreshToken(any(), anyString());
    }

    // ---------------------- getCurrentUser ----------------------

    @Test
    @DisplayName("getCurrentUser: returns UserDto when user found")
    void getCurrentUser_success() {
        User user = buildUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        UserDto dto = authService.getCurrentUser(EMAIL);

        assertThat(dto.id()).isEqualTo(USER_ID);
        assertThat(dto.email()).isEqualTo(EMAIL);
        assertThat(dto.fullName()).isEqualTo("Admin");
        assertThat(dto.role()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("getCurrentUser: throws Unauthorized when user not found")
    void getCurrentUser_notFound() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser(EMAIL))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("not found");
    }

    // ---------------------- helper ----------------------

    private JWTClaimsSet claimsWithJti(String jti, UUID userId, boolean isRefresh) {
        // Construct minimal JWTClaimsSet via builder
        Instant now = Instant.now();
        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
            .subject(userId.toString())
            .issuer("callsagents-backend")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(900)))
            .jwtID(jti);
        if (isRefresh) {
            b.claim(JwtService.CLAIM_TYPE, "refresh");
        } else {
            b.claim(JwtService.CLAIM_TYPE, "access");
        }
        return b.build();
    }
}
