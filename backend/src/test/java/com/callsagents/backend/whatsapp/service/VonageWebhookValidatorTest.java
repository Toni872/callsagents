package com.callsagents.backend.whatsapp.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VonageWebhookValidatorTest {

    private static final String SECRET = "vonage-test-signature-secret-0123456789abcdef"; // >= 32 bytes for HS256
    private static final byte[] RAW_BODY =
        "{\"from\":\"447700900000\",\"message_type\":\"text\",\"text\":\"Hola\"}"
            .getBytes(StandardCharsets.UTF_8);

    private VonageWebhookValidator validator() {
        return new VonageWebhookValidator(SECRET);
    }

    private static String sign(String headerJson, String claimsJson, String secret) throws Exception {
        // Build a compact JWT directly: base64url(header).base64url(payload).base64url(HMAC-SHA256)
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        byte[] sig = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String buildClaims(String issuer, String payloadHash, Instant iat) {
        return "{"
            + "\"iat\":" + iat.getEpochSecond() + ","
            + "\"jti\":\"" + UUID.randomUUID() + "\","
            + "\"iss\":\"" + issuer + "\","
            + "\"payload_hash\":\"" + payloadHash + "\","
            + "\"api_key\":\"75800ef5\""
            + "}";
    }

    private static String validToken() throws Exception {
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String claims = buildClaims("Vonage", sha256Hex(RAW_BODY), Instant.now());
        return sign(header, claims, SECRET);
    }

    @Test
    @DisplayName("Vonage: accepts a valid JWT with matching payload_hash")
    void acceptsValidJwt() throws Exception {
        assertThat(validator().verify(RAW_BODY, "Bearer " + validToken())).isTrue();
    }

    @Test
    @DisplayName("Vonage: accepts a valid JWT without the Bearer prefix but with whitespace")
    void acceptsValidJwtWithWhitespace() throws Exception {
        assertThat(validator().verify(RAW_BODY, "  Bearer  " + validToken() + "  ")).isTrue();
    }

    @Test
    @DisplayName("Vonage: rejects a tampered body (payload_hash mismatch)")
    void rejectsTamperedBody() throws Exception {
        byte[] tampered = (new String(RAW_BODY, StandardCharsets.UTF_8) + "x")
            .getBytes(StandardCharsets.UTF_8);
        assertThat(validator().verify(tampered, "Bearer " + validToken())).isFalse();
    }

    @Test
    @DisplayName("Vonage: rejects a JWT signed with the wrong secret")
    void rejectsWrongSecret() throws Exception {
        VonageWebhookValidator wrong = new VonageWebhookValidator("different-secret");
        assertThat(wrong.verify(RAW_BODY, "Bearer " + validToken())).isFalse();
    }

    @Test
    @DisplayName("Vonage: rejects a JWT from a different issuer")
    void rejectsWrongIssuer() throws Exception {
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String claims = buildClaims("NotVonage", sha256Hex(RAW_BODY), Instant.now());
        assertThat(validator().verify(RAW_BODY, "Bearer " + sign(header, claims, SECRET))).isFalse();
    }

    @Test
    @DisplayName("Vonage: rejects a JWT with an impossible future iat")
    void rejectsFutureIat() throws Exception {
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String claims = buildClaims("Vonage", sha256Hex(RAW_BODY), Instant.now().plusSeconds(3600));
        assertThat(validator().verify(RAW_BODY, "Bearer " + sign(header, claims, SECRET))).isFalse();
    }

    @Test
    @DisplayName("Vonage: rejects a JWT without a payload_hash claim")
    void rejectsMissingPayloadHash() throws Exception {
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String claims = "{"
            + "\"iat\":" + Instant.now().getEpochSecond() + ","
            + "\"jti\":\"" + UUID.randomUUID() + "\","
            + "\"iss\":\"Vonage\""
            + "}";
        assertThat(validator().verify(RAW_BODY, "Bearer " + sign(header, claims, SECRET))).isFalse();
    }

    @Test
    @DisplayName("Vonage: rejects a malformed JWT")
    void rejectsMalformedJwt() throws Exception {
        assertThat(validator().verify(RAW_BODY, "Bearer not-a-jwt")).isFalse();
        assertThat(validator().verify(RAW_BODY, "Bearer eyJ.eyJ.sig")).isFalse();
    }

    @Test
    @DisplayName("Vonage: rejects a non-Bearer Authorization header")
    void rejectsNonBearerHeader() throws Exception {
        assertThat(validator().verify(RAW_BODY, "Basic abc123")).isFalse();
    }

    @Test
    @DisplayName("Vonage: rejects an empty or missing Authorization header")
    void rejectsEmptyAuthorization() {
        assertThat(validator().verify(RAW_BODY, "")).isFalse();
        assertThat(validator().verify(RAW_BODY, null)).isFalse();
    }

    @Test
    @DisplayName("Vonage: fails open when the secret is not configured")
    void failsOpenWhenSecretNotConfigured() {
        VonageWebhookValidator unconfigured = new VonageWebhookValidator("");
        assertThat(unconfigured.verify(RAW_BODY, null)).isTrue();
        assertThat(unconfigured.verify(RAW_BODY, "Bearer garbage")).isTrue();
    }
}