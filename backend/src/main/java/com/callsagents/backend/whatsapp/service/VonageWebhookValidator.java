package com.callsagents.backend.whatsapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies Vonage WhatsApp webhook signatures (HMAC-SHA256, hex) so the public
 * {@code /webhooks/vonage} endpoint cannot be spoofed.
 *
 * <p>The {@code X-Vonage-Signature} header carries the hex digest of
 * HMAC-SHA256 over the EXACT raw request body bytes, keyed with the webhook
 * signature secret. Comparison is constant-time ({@link MessageDigest#isEqual}).
 *
 * <p>Verification is FAIL-OPEN when no secret is configured (so dev/prod keep
 * working before the secret is set), and FAIL-CLOSED once the secret is set.
 */
@Component
public class VonageWebhookValidator {

    private static final Logger log = LoggerFactory.getLogger(VonageWebhookValidator.class);

    private final String signatureSecret;

    public VonageWebhookValidator(@Value("${vonage.signature-secret:}") String signatureSecret) {
        this.signatureSecret = signatureSecret;
    }

    /**
     * Verifies that the provided hex signature matches HMAC-SHA256(rawBody, secret).
     * When the secret is not configured, logs a warning and accepts (fail-open).
     */
    public boolean verify(byte[] rawBody, String signatureHeader) {
        if (signatureSecret == null || signatureSecret.isBlank()) {
            log.warn("VONAGE_SIGNATURE_SECRET is not configured — accepting Vonage webhook without "
                + "signature verification (fail open). Set the secret to enable fail-closed verification.");
            return true;
        }
        if (rawBody == null || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        byte[] expected = hmacSha256(signatureSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), rawBody);
        byte[] provided = fromHex(signatureHeader.trim());
        if (provided == null || provided.length != expected.length) {
            return false;
        }
        return MessageDigest.isEqual(expected, provided);
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
        if (hex.length() % 2 != 0) {
            return null;
        }
        try {
            return HexFormat.of().parseHex(hex);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
