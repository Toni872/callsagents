package com.callsagents.backend.whatsapp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class VonageWebhookValidatorTest {

    private static final String SECRET = "vonage-signature-secret";
    private static final byte[] RAW_BODY =
        "{\"from\":\"447700900000\",\"message_type\":\"text\",\"text\":\"Hola\"}"
            .getBytes(StandardCharsets.UTF_8);

    private static String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private VonageWebhookValidator validator() {
        return new VonageWebhookValidator(SECRET);
    }

    @Test
    @DisplayName("Vonage: accepts a valid HMAC-SHA256 signature")
    void acceptsValidSignature() {
        assertThat(validator().verify(RAW_BODY, sign(RAW_BODY))).isTrue();
    }

    @Test
    @DisplayName("Vonage: accepts uppercase hex digest")
    void acceptsUppercaseHex() {
        assertThat(validator().verify(RAW_BODY, sign(RAW_BODY).toUpperCase())).isTrue();
    }

    @Test
    @DisplayName("Vonage: rejects a tampered body")
    void rejectsTamperedBody() {
        byte[] tampered = (new String(RAW_BODY, StandardCharsets.UTF_8) + "x")
            .getBytes(StandardCharsets.UTF_8);
        assertThat(validator().verify(tampered, sign(RAW_BODY))).isFalse();
    }

    @Test
    @DisplayName("Vonage: rejects a signature computed with the wrong secret")
    void rejectsWrongSecret() {
        VonageWebhookValidator wrong = new VonageWebhookValidator("different-secret");
        assertThat(wrong.verify(RAW_BODY, sign(RAW_BODY))).isFalse();
    }

    @Test
    @DisplayName("Vonage: rejects an empty signature header")
    void rejectsEmptySignature() {
        assertThat(validator().verify(RAW_BODY, "")).isFalse();
        assertThat(validator().verify(RAW_BODY, null)).isFalse();
    }

    @Test
    @DisplayName("Vonage: rejects malformed (non-hex) signature")
    void rejectsMalformedSignature() {
        assertThat(validator().verify(RAW_BODY, "not-hex-data")).isFalse();
        assertThat(validator().verify(RAW_BODY, "abcd")).isFalse();
    }

    @Test
    @DisplayName("Vonage: fails open when the secret is not configured")
    void failsOpenWhenSecretNotConfigured() {
        VonageWebhookValidator unconfigured = new VonageWebhookValidator("");
        assertThat(unconfigured.verify(RAW_BODY, null)).isTrue();
        assertThat(unconfigured.verify(RAW_BODY, "garbage")).isTrue();
    }
}
