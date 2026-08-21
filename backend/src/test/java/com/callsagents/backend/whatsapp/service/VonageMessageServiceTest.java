package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.whatsapp.config.VonageConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VonageMessageServiceTest {

    @Mock
    private VonageConfig vonageConfig;

    @Mock
    private RestTemplate restTemplate;

    private VonageMessageService service;

    @BeforeEach
    void setUp() {
        service = new VonageMessageService(vonageConfig);
        // Inject mock RestTemplate via reflection (it's created in constructor)
        try {
            var field = VonageMessageService.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(service, restTemplate);
        } catch (Exception e) {
            fail("Could not inject mock RestTemplate: " + e.getMessage());
        }
    }

    @Test
    void sendText_whenConfigured_sendsSuccessfully() {
        when(vonageConfig.isConfigured()).thenReturn(true);
        when(vonageConfig.getApiKey()).thenReturn("test-key");
        when(vonageConfig.getApiSecret()).thenReturn("test-secret");
        when(vonageConfig.getSandboxNumber()).thenReturn("14157386102");
        when(vonageConfig.getSandboxUrl()).thenReturn("https://messages-sandbox.nexmo.com/v1/messages");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        boolean result = service.sendText("447700900000", "Hello!");

        assertTrue(result);
        verify(restTemplate).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    void sendText_whenNotConfigured_returnsFalse() {
        when(vonageConfig.isConfigured()).thenReturn(false);

        boolean result = service.sendText("447700900000", "Hello!");

        assertFalse(result);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendText_whenApiThrows_returnsFalse() {
        when(vonageConfig.isConfigured()).thenReturn(true);
        when(vonageConfig.getApiKey()).thenReturn("test-key");
        when(vonageConfig.getApiSecret()).thenReturn("test-secret");
        when(vonageConfig.getSandboxNumber()).thenReturn("14157386102");
        when(vonageConfig.getSandboxUrl()).thenReturn("https://messages-sandbox.nexmo.com/v1/messages");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenThrow(new RuntimeException("Connection refused"));

        boolean result = service.sendText("447700900000", "Hello!");

        assertFalse(result);
    }
}
