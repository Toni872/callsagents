package com.callsagents.backend.auth.security;

import com.callsagents.backend.auth.entity.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    private byte[] secretBytes;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        String configured = properties.getSecret();
        if (configured == null || configured.isBlank()) {
            byte[] generated = new byte[MIN_SECRET_BYTES];
            secureRandom.nextBytes(generated);
            this.secretBytes = generated;
            log.warn("=========================================================");
            log.warn("JWT_SECRET is NOT configured. Generated an EPHEMERAL random secret.");
            log.warn("This is acceptable ONLY for local dev. All tokens become");
            log.warn("invalid on restart. Set JWT_SECRET env var (>=32 bytes) for any");
            log.warn("non-dev environment.");
            log.warn("=========================================================");
        } else {
            byte[] bytes = configured.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes for HS256; got " + bytes.length);
            }
            this.secretBytes = bytes;
            log.info("JWT secret initialized from configuration ({} bytes)", bytes.length);
        }
    }

    public String generateAccessToken(User user) {
        return generateToken(user, TYPE_ACCESS, properties.getAccessTokenTtl());
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, TYPE_REFRESH, properties.getRefreshTokenTtl());
    }

    private String generateToken(User user, String type, java.time.Duration ttl) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plus(ttl);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.getId().toString())
                .issuer(properties.getIssuer())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .jwtID(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);

            JWSSigner signer = new MACSigner(secretBytes);
            synchronized (signer) {
                signedJWT.sign(signer);
            }
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    public Optional<JWTClaimsSet> parseToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            JWSVerifier verifier = new MACVerifier(secretBytes);
            boolean valid;
            synchronized (verifier) {
                valid = signedJWT.verify(verifier);
            }
            if (!valid) {
                return Optional.empty();
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            String issuer = claims.getIssuer();
            if (issuer == null || !issuer.equals(properties.getIssuer())) {
                return Optional.empty();
            }

            Date exp = claims.getExpirationTime();
            if (exp == null || exp.toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }

            return Optional.of(claims);
        } catch (ParseException | JOSEException e) {
            return Optional.empty();
        }
    }

    public boolean isRefreshToken(JWTClaimsSet claims) {
        if (claims == null) {
            return false;
        }
        Object type = claims.getClaim(CLAIM_TYPE);
        return TYPE_REFRESH.equals(type);
    }

    public String extractJti(String token) {
        return parseToken(token)
            .map(JWTClaimsSet::getJWTID)
            .orElse(null);
    }
}
