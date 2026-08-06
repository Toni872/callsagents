package com.callsagents.backend.calendar.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for sensitive strings (OAuth tokens).
 *
 * Format of ciphertext (after Base64 decoding):
 *   [12 bytes IV][ciphertext][16 bytes GCM tag]
 *
 * - IV: random per encryption (SecureRandom), 96 bits (GCM-recommended).
 * - Tag: 128 bits (GCM-recommended). Authenticates ciphertext + IV.
 * - Key: 32 bytes derived from the master secret (ENCRYPTION_KEY) via SHA-256.
 *
 * Master secret is supplied via the environment variable ENCRYPTION_KEY
 * (configured in .env via RUNBOOK). If missing, the bean still instantiates
 * (key=null, ready=false) so the rest of the app boots — encrypt/decrypt
 * throw IllegalStateException at call time. Calendar endpoints depend on
 * this service; without a key they fail with a clear runtime error.
 */
@Service
public class EncryptionService {

    private static final int IV_LEN = 12;        // 96 bits
    private static final int TAG_LEN_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    private final SecretKey key;
    private final boolean ready;

    public EncryptionService(@Value("${app.encryption.key:}") String masterSecret) {
        if (masterSecret == null || masterSecret.isBlank()) {
            this.key = null;
            this.ready = false;
            return;
        }
        try {
            byte[] raw = masterSecret.getBytes(StandardCharsets.UTF_8);
            byte[] hashed = MessageDigest.getInstance("SHA-256").digest(raw);
            this.key = new SecretKeySpec(hashed, "AES");
            this.ready = true;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive encryption key", e);
        }
    }

    public String encrypt(String plaintext) {
        if (!ready) {
            throw new IllegalStateException(
                "EncryptionService is not configured. Set ENCRYPTION_KEY in .env (see RUNBOOK.md).");
        }
        try {
            byte[] iv = new byte[IV_LEN];
            RNG.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
            buf.put(iv).put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String ciphertextB64) {
        if (!ready) {
            throw new IllegalStateException(
                "EncryptionService is not configured. Set ENCRYPTION_KEY in .env (see RUNBOOK.md).");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(ciphertextB64);
            if (raw.length <= IV_LEN) throw new IllegalArgumentException("Ciphertext too short");
            ByteBuffer buf = ByteBuffer.wrap(raw);
            byte[] iv = new byte[IV_LEN];
            buf.get(iv);
            byte[] ct = new byte[buf.remaining()];
            buf.get(ct);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
