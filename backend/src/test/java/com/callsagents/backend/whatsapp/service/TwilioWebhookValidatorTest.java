package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.whatsapp.config.WhatsAppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class TwilioWebhookValidatorTest {

    private static final String AUTH_TOKEN = "TWILIO_AUTH_TOKEN_123";

    private TwilioWebhookValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TwilioWebhookValidator(new WhatsAppConfig("ACxxx", AUTH_TOKEN, "whatsapp:+1", ""));
    }

    private static String sign(MockHttpServletRequest request, Map<String, String> params) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        String host = request.getServerName();
        String path = request.getRequestURI();
        StringBuilder base = new StringBuilder(scheme).append("://").append(host);
        boolean defaultPort = ("http".equals(scheme) && port == 80)
            || ("https".equals(scheme) && port == 443);
        if (!defaultPort) {
            base.append(':').append(port);
        }
        base.append(path);
        Map<String, String> sorted = new TreeMap<>(params);
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            base.append(e.getKey()).append(e.getValue());
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(AUTH_TOKEN.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(base.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/webhooks/whatsapp");
        req.setScheme("https");
        req.setServerName("app.railway.example");
        req.setServerPort(443);
        return req;
    }

    private Map<String, String> params() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("From", "whatsapp:+14157386102");
        m.put("Body", "Quiero info");
        m.put("NumMedia", "0");
        m.put("To", "whatsapp:+14155551234");
        return m;
    }

    @Test
    @DisplayName("Twilio: accepts a valid signature")
    void acceptsValidSignature() {
        MockHttpServletRequest req = request();
        Map<String, String> p = params();
        assertThat(validator.verify(sign(req, p), req, p)).isTrue();
    }

    @Test
    @DisplayName("Twilio: rejects a tampered body parameter")
    void rejectsTamperedBody() {
        MockHttpServletRequest req = request();
        Map<String, String> p = params();
        Map<String, String> tampered = new LinkedHashMap<>(p);
        tampered.put("Body", "Different message");
        assertThat(validator.verify(sign(req, p), req, tampered)).isFalse();
    }

    @Test
    @DisplayName("Twilio: rejects a signature for a different URL")
    void rejectsDifferentUrl() {
        MockHttpServletRequest req = request();
        Map<String, String> p = params();
        MockHttpServletRequest other = request();
        other.setServerName("evil.example");
        assertThat(validator.verify(sign(other, p), req, p)).isFalse();
    }

    @Test
    @DisplayName("Twilio: rejects missing / empty signature header")
    void rejectsMissingSignature() {
        MockHttpServletRequest req = request();
        Map<String, String> p = params();
        assertThat(validator.verify(null, req, p)).isFalse();
        assertThat(validator.verify("", req, p)).isFalse();
    }

    @Test
    @DisplayName("Twilio: fails open when the auth token is not configured")
    void failsOpenWhenTokenNotConfigured() {
        TwilioWebhookValidator unconfigured = new TwilioWebhookValidator(
            new WhatsAppConfig("ACxxx", "", "whatsapp:+1", ""));
        MockHttpServletRequest req = request();
        Map<String, String> p = params();
        assertThat(unconfigured.verify(null, req, p)).isTrue();
        assertThat(unconfigured.verify("evil", req, p)).isTrue();
    }
}
