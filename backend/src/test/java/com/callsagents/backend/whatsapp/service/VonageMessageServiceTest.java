package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.whatsapp.config.VonageConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

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

    @Test
    void sendButtons_whenConfigured_sendsInteractiveMessage() {
        when(vonageConfig.isConfigured()).thenReturn(true);
        when(vonageConfig.getApiKey()).thenReturn("test-key");
        when(vonageConfig.getApiSecret()).thenReturn("test-secret");
        when(vonageConfig.getSandboxNumber()).thenReturn("14157386102");
        when(vonageConfig.getSandboxUrl()).thenReturn("https://messages-sandbox.nexmo.com/v1/messages");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        List<String[]> buttons = List.of(
            new String[]{"btn_1", "Option A"},
            new String[]{"btn_2", "Option B"}
        );

        boolean result = service.sendButtons("447700900000", "Choose an option", buttons);

        assertTrue(result);

        // Verify the body contains interactive message type
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        Map<String, Object> body = captor.getValue().getBody();
        assertNotNull(body);
        assertEquals("custom", body.get("message_type"));
    }

    @Test
    void sendList_whenConfigured_sendsListMessage() {
        when(vonageConfig.isConfigured()).thenReturn(true);
        when(vonageConfig.getApiKey()).thenReturn("test-key");
        when(vonageConfig.getApiSecret()).thenReturn("test-secret");
        when(vonageConfig.getSandboxNumber()).thenReturn("14157386102");
        when(vonageConfig.getSandboxUrl()).thenReturn("https://messages-sandbox.nexmo.com/v1/messages");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        List<Map<String, List<String[]>>> sections = List.of(
            Map.of("Services", List.of(
                new String[]{"svc_1", "Ventas", "Automatizar ventas"},
                new String[]{"svc_2", "Soporte", "Atención al cliente"}
            ))
        );

        boolean result = service.sendList("447700900000", "Services", "Choose a service", "Ver opciones", sections);

        assertTrue(result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        Map<String, Object> body = captor.getValue().getBody();
        assertNotNull(body);
        assertEquals("custom", body.get("message_type"));
    }

    @Test
    void sendButtons_whenNotConfigured_returnsFalse() {
        when(vonageConfig.isConfigured()).thenReturn(false);

        boolean result = service.sendButtons("447700900000", "Choose", List.<String[]>of(new String[]{"a", "A"}));

        assertFalse(result);
        verifyNoInteractions(restTemplate);
    }
}
