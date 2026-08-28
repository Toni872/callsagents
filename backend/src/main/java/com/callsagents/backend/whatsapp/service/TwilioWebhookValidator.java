package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.whatsapp.config.WhatsAppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * Verifies Twilio WhatsApp webhook signatures so the public
 * {@code /webhooks/whatsapp} endpoint cannot be spoofed.
 *
 * <p>Twilio signs the request by computing the <b>canonical URL</b>
 * ({@code https://{host}{path}}) plus all form parameters sorted by name, then
 * HMAC-SHA1 with the account auth token as the key, Base64-encoded. The
 * {@code X-Twilio-Signature} header must match. Comparison is constant-time.
 *
 * <p>Verification is FAIL-OPEN when no auth token is configured (so dev/prod
 * keep working before the token is set), and FAIL-CLOSED once the token is set.
 */
@Component
public class TwilioWebhookValidator {

    private static final Logger log = LoggerFactory.getLogger(TwilioWebhookValidator.class);

    private final WhatsAppConfig whatsAppConfig;

    public TwilioWebhookValidator(WhatsAppConfig whatsAppConfig) {
        this.whatsAppConfig = whatsAppConfig;
    }

    /**
     * Verifies the {@code X-Twilio-Signature} header for the given request and
     * its form parameters. Returns true only if the signature matches.
     */
    public boolean verify(String signatureHeader, HttpServletRequest request, Map<String, String> params) {
        String authToken = whatsAppConfig.authToken();
        if (authToken == null || authToken.isBlank()) {
            log.warn("TWILIO_AUTH_TOKEN is not configured — accepting Twilio webhook without "
                + "signature verification (fail open). Set the token to enable fail-closed verification.");
            return true;
        }
        if (signatureHeader == null || signatureHeader.isBlank() || request == null) {
            return false;
        }

        StringBuilder base = new StringBuilder(canonicalUrl(request));
        // Append all params sorted by name: name=value with no separators.
        Map<String, String> sorted = new TreeMap<>(params);
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            base.append(e.getKey()).append(e.getValue());
        }

        byte[] expected = hmacSha1(authToken.getBytes(StandardCharsets.UTF_8),
            base.toString().getBytes(StandardCharsets.UTF_8));
        String expectedB64 = Base64.getEncoder().encodeToString(expected);

        return MessageDigest.isEqual(
            expectedB64.getBytes(StandardCharsets.UTF_8),
            signatureHeader.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static String canonicalUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        int serverPort = request.getServerPort();
        String host = request.getServerName();
        String path = request.getRequestURI();
        StringBuilder url = new StringBuilder(scheme).append("://").append(host);
        boolean defaultPort = ("http".equals(scheme) && serverPort == 80)
            || ("https".equals(scheme) && serverPort == 443);
        if (!defaultPort) {
            url.append(':').append(serverPort);
        }
        url.append(path);
        return url.toString();
    }

    private static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA1 unavailable", e);
        }
    }
}
