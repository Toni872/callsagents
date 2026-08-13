package com.callsagents.backend.voice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies voice provider webhook signatures so the public
 * {@code /voice/webhook/**} endpoints cannot be spoofed.
 *
 * <p>Retell: the {@code X-Retell-Signature} header is
 * {@code v={unix_ms_timestamp},d={hex_digest}} where the digest is
 * HMAC-SHA256 of {@code rawBody + timestamp} (string concatenation, no
 * separator) keyed with the Retell API key. The timestamp must be within
 * ~5 minutes to prevent replay attacks. The raw request body must be used,
 * never a re-serialized JSON payload.
 *
 * <p>Vapi: the deterministic, documented mechanism is the
 * {@code X-Vapi-Secret} header carrying the configured webhook secret
 * (the legacy inline {@code secret} field, or a Bearer credential bound to
 * that header). Vapi's HMAC credentials are user-configurable (header names,
 * algorithms, payload format), so no fixed signature format is assumed here;
 * we verify the shared secret with a constant-time comparison.
 *
 * <p>Verification is FAIL-CLOSED: missing configuration (no secret/key set)
 * or any malformed/mismatched signature rejects the webhook.
 */
@Component
public class WebhookSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureValidator.class);

    private static final Pattern RETELL_SIGNATURE =
        Pattern.compile("^v=(\\d+),d=([0-9a-fA-F]{64})$");
    private static final long RETELL_MAX_AGE_MS = 5 * 60 * 1000L;

    private final String vapiWebhookSecret;
    private final String retellApiKey;

    public WebhookSignatureValidator(
            @Value("${app.voice.vapi.webhook-secret:}") String vapiWebhookSecret,
            @Value("${app.voice.retell.api-key:}") String retellApiKey) {
        this.vapiWebhookSecret = vapiWebhookSecret;
        this.retellApiKey = retellApiKey;
    }

    /** Verifies the request for the given provider. Unknown providers are rejected. */
    public boolean verify(String provider, String rawBody, String xVapiSecret, String xRetellSignature) {
        if (provider == null) {
            return false;
        }
        if ("vapi".equalsIgnoreCase(provider)) {
            return verifyVapi(xVapiSecret);
        }
        if ("retell".equalsIgnoreCase(provider)) {
            return verifyRetell(rawBody, xRetellSignature);
        }
        log.warn("Webhook verification: unknown provider '{}' — rejecting", provider);
        return false;
    }

    /** Vapi: constant-time comparison of the shared secret against the header. */
    public boolean verifyVapi(String headerSecret) {
        if (isBlank(vapiWebhookSecret)) {
            log.error("VAPI_WEBHOOK_SECRET is not configured — rejecting Vapi webhook (fail closed)");
            return false;
        }
        if (headerSecret == null) {
            return false;
        }
        return MessageDigest.isEqual(
            vapiWebhookSecret.getBytes(StandardCharsets.UTF_8),
            headerSecret.getBytes(StandardCharsets.UTF_8));
    }

    /** Retell: HMAC-SHA256 over rawBody + timestamp, with a 5-minute replay window. */
    public boolean verifyRetell(String rawBody, String signatureHeader) {
        if (isBlank(retellApiKey)) {
            log.error("RETELL_API_KEY is not configured — rejecting Retell webhook (fail closed)");
            return false;
        }
        if (rawBody == null || signatureHeader == null) {
            return false;
        }
        Matcher m = RETELL_SIGNATURE.matcher(signatureHeader.trim());
        if (!m.matches()) {
            log.warn("Retell webhook rejected: malformed signature header");
            return false;
        }
        String timestamp = m.group(1);
        String digest = m.group(2);

        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            log.warn("Retell webhook rejected: non-numeric timestamp");
            return false;
        }
        if (Math.abs(System.currentTimeMillis() - ts) > RETELL_MAX_AGE_MS) {
            log.warn("Retell webhook rejected: timestamp outside the 5-minute window (replay?)");
            return false;
        }

        byte[] expected = hmacSha256(retellApiKey.getBytes(StandardCharsets.UTF_8),
            (rawBody + timestamp).getBytes(StandardCharsets.UTF_8));
        byte[] provided = fromHex(digest);
        if (provided == null) {
            return false;
        }
        if (!MessageDigest.isEqual(expected, provided)) {
            log.warn("Retell webhook rejected: HMAC digest mismatch");
            return false;
        }
        return true;
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static byte[] fromHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            try {
                out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
