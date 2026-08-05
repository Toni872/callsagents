package com.callsagents.backend.auth.service;

import com.callsagents.backend.auth.dto.LoginRequest;
import com.callsagents.backend.auth.dto.LoginResponse;
import com.callsagents.backend.auth.dto.RefreshRequest;
import com.callsagents.backend.auth.dto.RefreshResponse;
import com.callsagents.backend.auth.dto.UserDto;
import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.auth.security.JwtProperties;
import com.callsagents.backend.auth.security.JwtService;
import com.callsagents.backend.auth.security.RefreshTokenService;
import com.callsagents.backend.common.exception.UnauthorizedException;
import com.nimbusds.jwt.JWTClaimsSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public LoginResponse login(LoginRequest req) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );

        User user = userRepository.findByEmail(req.email())
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        String refreshJti = extractJti(refreshToken);
        refreshTokenService.storeRefreshToken(
            user.getId(), refreshJti, jwtProperties.getRefreshTokenTtl());

        return new LoginResponse(
            accessToken,
            refreshToken,
            jwtProperties.getAccessTokenTtl().toSeconds(),
            toDto(user)
        );
    }

    @Transactional
    public RefreshResponse refresh(RefreshRequest req) {
        JWTClaimsSet claims = jwtService.parseToken(req.refreshToken())
            .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        if (!jwtService.isRefreshToken(claims)) {
            throw new UnauthorizedException("Provided token is not a refresh token");
        }

        UUID userId = parseUserId(claims);
        String jti = claims.getJWTID();

        if (!refreshTokenService.isRefreshTokenValid(userId, jti)) {
            log.warn("Refresh token reuse detected for user {}; revoking entire session", userId);
            refreshTokenService.revokeAllRefreshTokens(userId);
            throw new UnauthorizedException("Refresh token reuse detected; session terminated");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));

        String newAccess = jwtService.generateAccessToken(user);
        String newRefresh = jwtService.generateRefreshToken(user);

        String newRefreshJti = extractJti(newRefresh);
        refreshTokenService.storeRefreshToken(
            user.getId(), newRefreshJti, jwtProperties.getRefreshTokenTtl());

        refreshTokenService.revokeRefreshToken(userId, jti);

        return new RefreshResponse(
            newAccess,
            newRefresh,
            jwtProperties.getAccessTokenTtl().toSeconds()
        );
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            jwtService.parseToken(accessToken).ifPresent(claims -> {
                if (jwtService.isRefreshToken(claims)) {
                    return;
                }
                String jti = claims.getJWTID();
                Date exp = claims.getExpirationTime();
                if (jti != null && exp != null) {
                    Duration remaining = Duration.between(Instant.now(), exp.toInstant());
                    refreshTokenService.revokeAccessToken(jti, remaining);
                }
            });
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            jwtService.parseToken(refreshToken).ifPresent(claims -> {
                if (!jwtService.isRefreshToken(claims)) {
                    return;
                }
                try {
                    UUID userId = parseUserId(claims);
                    refreshTokenService.revokeRefreshToken(userId, claims.getJWTID());
                } catch (UnauthorizedException ignored) {
                }
            });
        }
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UnauthorizedException("User not found"));
        return toDto(user);
    }

    private static UserDto toDto(User user) {
        return new UserDto(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole().name()
        );
    }

    private static UUID parseUserId(JWTClaimsSet claims) {
        try {
            String sub = claims.getSubject();
            if (sub == null) {
                throw new UnauthorizedException("Token missing subject");
            }
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid subject in token");
        }
    }

    private String extractJti(String token) {
        return jwtService.parseToken(token)
            .map(JWTClaimsSet::getJWTID)
            .orElseThrow(() -> new UnauthorizedException("Failed to extract jti from freshly generated token"));
    }
}
