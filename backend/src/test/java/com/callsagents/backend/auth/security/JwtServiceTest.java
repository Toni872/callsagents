package com.callsagents.backend.auth.security;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.auth.entity.UserStatus;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties properties;
    private User user;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret("test-secret-test-secret-test-secret-1234");
        properties.setIssuer("callsagents-test");
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(7));

        jwtService = new JwtService(properties);
        jwtService.init();

        user = User.builder()
            .id(UUID.randomUUID())
            .email("agent@example.com")
            .passwordHash("hash")
            .fullName("Test Agent")
            .role(UserRole.AGENT)
            .status(UserStatus.ACTIVE)
            .build();
    }

    @Test
    void generatesAccessTokenParseableAndWithCorrectClaims() throws Exception {
        String token = jwtService.generateAccessToken(user);
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "JWT must have 3 segments");

        Optional<JWTClaimsSet> parsed = jwtService.parseToken(token);
        assertTrue(parsed.isPresent(), "Freshly minted access token must parse");

        JWTClaimsSet claims = parsed.get();
        assertEquals(user.getId().toString(), claims.getSubject());
        assertEquals(user.getEmail(), claims.getStringClaim(JwtService.CLAIM_EMAIL));
        assertEquals(user.getRole().name(), claims.getStringClaim(JwtService.CLAIM_ROLE));
        assertEquals(JwtService.TYPE_ACCESS, claims.getStringClaim(JwtService.CLAIM_TYPE));
        assertEquals(properties.getIssuer(), claims.getIssuer());
        assertNotNull(claims.getJWTID());
        assertNotNull(claims.getExpirationTime());
        assertFalse(jwtService.isRefreshToken(claims));
    }

    @Test
    void generatesRefreshTokenWithTypeClaim() throws Exception {
        String token = jwtService.generateRefreshToken(user);
        Optional<JWTClaimsSet> parsed = jwtService.parseToken(token);
        assertTrue(parsed.isPresent());

        JWTClaimsSet claims = parsed.get();
        assertEquals(JwtService.TYPE_REFRESH, claims.getStringClaim(JwtService.CLAIM_TYPE));
        assertTrue(jwtService.isRefreshToken(claims));
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        String token = jwtService.generateAccessToken(user);

        JwtProperties otherProps = new JwtProperties();
        otherProps.setSecret("another-secret-another-secret-another-12");
        otherProps.setIssuer(properties.getIssuer());
        otherProps.setAccessTokenTtl(Duration.ofMinutes(15));
        otherProps.setRefreshTokenTtl(Duration.ofDays(7));
        JwtService otherService = new JwtService(otherProps);
        otherService.init();

        Optional<JWTClaimsSet> parsed = otherService.parseToken(token);
        assertTrue(parsed.isEmpty(), "Token signed with different secret must be rejected");
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        JwtProperties shortLived = new JwtProperties();
        shortLived.setSecret(properties.getSecret());
        shortLived.setIssuer(properties.getIssuer());
        shortLived.setAccessTokenTtl(Duration.ofMillis(200));
        shortLived.setRefreshTokenTtl(Duration.ofDays(7));
        JwtService shortLivedService = new JwtService(shortLived);
        shortLivedService.init();

        String token = shortLivedService.generateAccessToken(user);

        Thread.sleep(500);

        Optional<JWTClaimsSet> parsed = shortLivedService.parseToken(token);
        assertTrue(parsed.isEmpty(), "Expired token must be rejected");
    }

    @Test
    void rejectsBlankOrNullTokens() {
        assertTrue(jwtService.parseToken(null).isEmpty());
        assertTrue(jwtService.parseToken("").isEmpty());
        assertTrue(jwtService.parseToken("   ").isEmpty());
        assertTrue(jwtService.parseToken("not-a-jwt").isEmpty());
    }

    @Test
    void rejectsTokenWithWrongIssuer() {
        JwtProperties otherIssuer = new JwtProperties();
        otherIssuer.setSecret(properties.getSecret());
        otherIssuer.setIssuer("evil-issuer");
        otherIssuer.setAccessTokenTtl(Duration.ofMinutes(15));
        otherIssuer.setRefreshTokenTtl(Duration.ofDays(7));
        JwtService otherService = new JwtService(otherIssuer);
        otherService.init();

        String token = otherService.generateAccessToken(user);

        Optional<JWTClaimsSet> parsed = jwtService.parseToken(token);
        assertTrue(parsed.isEmpty(), "Token from different issuer must be rejected");
    }

    @Test
    void extractJtiReturnsJtiForValidToken() {
        String token = jwtService.generateAccessToken(user);
        String jti = jwtService.extractJti(token);
        assertNotNull(jti);
        assertFalse(jti.isBlank());
    }

    @Test
    void extractJtiReturnsNullForInvalidToken() {
        assertEquals(null, jwtService.extractJti("garbage"));
    }
}
