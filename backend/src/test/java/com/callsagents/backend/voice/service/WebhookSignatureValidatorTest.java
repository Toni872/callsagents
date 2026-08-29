package com.callsagents.backend.voice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureValidatorTest {

    private static final String VAPI_SECRET = "vapi-shared-secret";
    private static final String RETELL_KEY = "retell-api-key-123";
    private static final String RAW_BODY = "{\"event\":\"call_ended\",\"call_id\":\"abc-123\"}";

    private WebhookSignatureValidator validator() {
        return new WebhookSignatureValidator(VAPI_SECRET, RETELL_KEY);
    }

    private static String retellHeader(long timestampMs) {
        return "v=" + timestampMs + ",d=" + hmacHex((RAW_BODY + timestampMs).getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacHex(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(RETELL_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ----- Vapi -----

    @Test
    @DisplayName("Vapi: accepts the correct X-Vapi-Secret")
    void vapi_acceptsMatchingSecret() {
        assertThat(validator().verifyVapi(VAPI_SECRET)).isTrue();
    }

    @Test
    @DisplayName("Vapi: rejects a wrong secret")
    void vapi_rejectsWrongSecret() {
        assertThat(validator().verifyVapi("wrong")).isFalse();
    }

    @Test
    @DisplayName("Vapi: rejects a missing header")
    void vapi_rejectsMissingHeader() {
        assertThat(validator().verifyVapi(null)).isFalse();
    }

    @Test
    @DisplayName("Vapi: fails closed when the secret is not configured")
    void vapi_failsClosedWhenSecretNotConfigured() {
        WebhookSignatureValidator unconfigured = new WebhookSignatureValidator("", RETELL_KEY);
        assertThat(unconfigured.verifyVapi("anything")).isFalse();
    }

    // ----- Retell -----

    @Test
    @DisplayName("Retell: accepts a valid HMAC signature")
    void retell_acceptsValidSignature() {
        assertThat(validator().verifyRetell(RAW_BODY, retellHeader(System.currentTimeMillis()))).isTrue();
    }

    @Test
    @DisplayName("Retell: rejects a tampered body")
    void retell_rejectsTamperedBody() {
        String header = retellHeader(System.currentTimeMillis());
        assertThat(validator().verifyRetell(RAW_BODY + "x", header)).isFalse();
    }

    @Test
    @DisplayName("Retell: rejects a stale timestamp (replay protection)")
    void retell_rejectsStaleTimestamp() {
        long stale = System.currentTimeMillis() - 6 * 60 * 1000L; // 6 minutes ago
        assertThat(validator().verifyRetell(RAW_BODY, retellHeader(stale))).isFalse();
    }

    @Test
    @DisplayName("Retell: rejects a malformed header")
    void retell_rejectsMalformedHeader() {
        assertThat(validator().verifyRetell(RAW_BODY, "garbage")).isFalse();
        assertThat(validator().verifyRetell(RAW_BODY, "t=123,d=abc")).isFalse();
        assertThat(validator().verifyRetell(RAW_BODY, null)).isFalse();
    }

    @Test
    @DisplayName("Retell: fails closed when the API key is not configured")
    void retell_failsClosedWhenKeyNotConfigured() {
        WebhookSignatureValidator unconfigured = new WebhookSignatureValidator(VAPI_SECRET, "");
        assertThat(unconfigured.verifyRetell(RAW_BODY, retellHeader(System.currentTimeMillis()))).isFalse();
    }

    // ----- Orchestration -----

    @Test
    @DisplayName("verify: routes vapi and retell correctly")
    void verify_routesByProvider() {
        WebhookSignatureValidator v = validator();
        assertThat(v.verify("vapi", RAW_BODY, VAPI_SECRET, null)).isTrue();
        assertThat(v.verify("retell", RAW_BODY, null, retellHeader(System.currentTimeMillis()))).isTrue();
    }

    @Test
    @DisplayName("verify: rejects unknown providers and null provider")
    void verify_rejectsUnknownProvider() {
        WebhookSignatureValidator v = validator();
        assertThat(v.verify("unknown-provider", RAW_BODY, null, null)).isFalse();
        assertThat(v.verify(null, RAW_BODY, null, null)).isFalse();
    }
}
