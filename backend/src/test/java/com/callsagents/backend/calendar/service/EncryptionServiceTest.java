package com.callsagents.backend.calendar.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    private EncryptionService service;

    @BeforeEach
    void setUp() {
        service = new EncryptionService("test-master-key-32-bytes-long-xxxx");
    }

    @Test
    @DisplayName("encrypt → decrypt roundtrip returns original")
    void roundtrip() {
        String plaintext = "ya29.a0AfH6SMBxxxx-long-google-access-token";
        String ct = service.encrypt(plaintext);
        String pt = service.decrypt(ct);
        assertThat(pt).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("encrypted output is different each call (random IV)")
    void encryptIsNondeterministic() {
        String plaintext = "same-input-different-output";
        String ct1 = service.encrypt(plaintext);
        String ct2 = service.encrypt(plaintext);
        assertThat(ct1).isNotEqualTo(ct2);
        assertThat(service.decrypt(ct1)).isEqualTo(plaintext);
        assertThat(service.decrypt(ct2)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("with empty/null master key, bean instantiates but encrypt/decrypt throw")
    void missingKeyIsLazy() {
        var service1 = new EncryptionService("");
        var service2 = new EncryptionService(null);
        // Constructor is tolerant — stack boots even without key
        assertThat(service1).isNotNull();
        assertThat(service2).isNotNull();
        // But operations fail clearly
        assertThatThrownBy(() -> service1.encrypt("hello"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ENCRYPTION_KEY");
        assertThatThrownBy(() -> service2.decrypt("anything"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ENCRYPTION_KEY");
    }

    @Test
    @DisplayName("different keys cannot decrypt each other (negative test)")
    void differentKeysDontInteroperate() {
        EncryptionService other = new EncryptionService("different-key-also-32-bytes-xxxx");
        String ct = service.encrypt("secret");
        assertThatThrownBy(() -> other.decrypt(ct))
            .isInstanceOf(IllegalStateException.class);
    }
}
