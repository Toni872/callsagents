package com.callsagents.backend.whatsapp.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

/**
 * Verifies Vonage WhatsApp webhook signatures so the public
 * {@code /webhooks/vonage} endpoint cannot be spoofed.
 *
 * <p>Vonage signs Messages API webhooks with a <b>JWT in the
 * {@code Authorization: Bearer <token>} header</b> (HS256, keyed with the
 * account signature secret from the Dashboard). The JWT carries an
 * {@code iss} claim of {@code "Vonage"} and a {@code payload_hash} claim that
 * is the SHA-256 of the EXACT raw request body — comparing it proves both
 * authenticity and that the payload was not tampered with during transit.
 *
 * <p>Verification steps:
 * <ol>
 *   <li>extract the JWT from the {@code Authorization} header</li>
 *   <li>verify the HS256 signature with the configured signature secret</li>
 *   <li>reject any JWT whose issuer is not {@code Vonage}</li>
 *   <li>reject an {@code iat} claim far in the future (clock-skew tolerance)</li>
 *   <li>compare {@code payload_hash} to SHA-256 of the raw body</li>
 * </ol>
 *
 * <p>Verification is FAIL-OPEN when no secret is configured (so dev/prod keep
 * working before the secret is set), and FAIL-CLOSED once the secret is set.
 */
@Component
public class VonageWebhookValidator {

    private static final Logger log = LoggerFactory.getLogger(VonageWebhookValidator.class);

    private static final String ISSUER_VONAGE = "Vonage";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final long MAX_IAT_SKEW_MS = 5 * 60 * 1000L;

    private final String signatureSecret;

    public VonageWebhookValidator(@Value("${vonage.signature-secret:}") String signatureSecret) {
        this.signatureSecret = signatureSecret;
    }

    /**
     * Verifies the {@code Authorization: Bearer <JWT>} header against the raw
     * request body. When the secret is not configured, logs a warning and
     * accepts (fail-open).
     */
    public boolean verify(byte[] rawBody, String authorizationHeader) {
        if (signatureSecret == null || signatureSecret.isBlank()) {
            log.warn("VONAGE_SIGNATURE_SECRET is not configured — accepting Vonage webhook without "
                + "signature verification (fail open). Set the secret to enable fail-closed verification.");
            return true;
        }
        if (rawBody == null || authorizationHeader == null || authorizationHeader.isBlank()) {
            return false;
        }

        String token = extractBearerToken(authorizationHeader);
        if (token == null) {
            log.warn("Vonage webhook rejected: Authorization header is not 'Bearer <JWT>'");
            return false;
        }

        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(signatureSecret.getBytes(StandardCharsets.UTF_8));

            boolean valid;
            synchronized (verifier) {
                valid = signedJWT.verify(verifier);
            }
            if (!valid) {
                log.warn("Vonage webhook rejected: JWT signature does not verify with the configured secret");
                return false;
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            if (!ISSUER_VONAGE.equals(claims.getIssuer())) {
                log.warn("Vonage webhook rejected: JWT issuer is '{}', expected 'Vonage'", claims.getIssuer());
                return false;
            }

            Date iat = claims.getIssueTime();
            if (iat == null || iat.toInstant().isAfter(Instant.now().plusMillis(MAX_IAT_SKEW_MS))) {
                log.warn("Vonage webhook rejected: missing or impossible 'iat' claim");
                return false;
            }

            String payloadHash = claims.getStringClaim("payload_hash");
            if (payloadHash == null) {
                log.warn("Vonage webhook rejected: JWT has no 'payload_hash' claim");
                return false;
            }
            if (!constantTimeEquals(payloadHash, sha256Hex(rawBody))) {
                log.warn("Vonage webhook rejected: payload_hash does not match the raw body (tampered or replayed)");
                return false;
            }

            return true;
        } catch (ParseException e) {
            log.warn("Vonage webhook rejected: malformed JWT ({})", e.getMessage());
            return false;
        } catch (com.nimbusds.jose.JOSEException e) {
            log.warn("Vonage webhook rejected: JWT verification failed ({})", e.getMessage());
            return false;
        }
    }

    /** Extracts the JWT from {@code "Bearer <token>"} (case-insensitive scheme). */
    private static String extractBearerToken(String authorizationHeader) {
        String header = authorizationHeader.trim();
        if (header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
    }
}