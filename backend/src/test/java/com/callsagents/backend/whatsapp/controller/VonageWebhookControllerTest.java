package com.callsagents.backend.whatsapp.controller;

import com.callsagents.backend.business.service.BusinessService;
import com.callsagents.backend.escalation.service.EscalationService;
import com.callsagents.backend.leads.repository.LeadRepository;
import com.callsagents.backend.whatsapp.service.VonageMessageService;
import com.callsagents.backend.whatsapp.service.VonageWebhookValidator;
import com.callsagents.backend.whatsapp.service.WhatsAppAiChatbotService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.DelegatingServletInputStream;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VonageWebhookControllerTest {

    @Mock WhatsAppAiChatbotService aiChatbotService;
    @Mock VonageMessageService vonageMessageService;
    @Mock BusinessService businessService;
    @Mock EscalationService escalationService;
    @Mock LeadRepository leadRepository;
    @Mock VonageWebhookValidator webhookValidator;
    @Mock HttpServletRequest request;

    private VonageWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new VonageWebhookController(
            aiChatbotService, vonageMessageService, businessService,
            escalationService, leadRepository, webhookValidator);
    }

    private void stubBody(String body) throws Exception {
        when(request.getInputStream())
            .thenReturn(new DelegatingServletInputStream(
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    @DisplayName("audio message -> friendly text-only notice, no AI call, HTTP 200")
    void audioMessage_friendlyNotice_noAi() throws Exception {
        String body = "{\"from\":\"+34687723287\",\"to\":\"+34111111111\",\"channel\":\"whatsapp\","
            + "\"message_type\":\"audio\"}";
        stubBody(body);
        when(webhookValidator.verify(any(), anyString())).thenReturn(true);

        ResponseEntity<Void> response =
            controller.handleInbound("Bearer token", request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(vonageMessageService).sendText(anyString(),
            org.mockito.ArgumentMatchers.contains("solo puedo leer mensajes de texto"));
        verify(aiChatbotService, never()).processMessage(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("image with no caption -> friendly text-only notice, no AI call")
    void imageMessage_noText() throws Exception {
        String body = "{\"from\":\"+34687723287\",\"to\":\"+34111111111\",\"channel\":\"whatsapp\","
            + "\"message_type\":\"image\"}";
        stubBody(body);
        when(webhookValidator.verify(any(), anyString())).thenReturn(true);

        ResponseEntity<Void> response =
            controller.handleInbound("Bearer token", request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(vonageMessageService).sendText(anyString(),
            org.mockito.ArgumentMatchers.contains("solo puedo leer mensajes de texto"));
        verify(aiChatbotService, never()).processMessage(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("text message -> flows to AI chatbot as before")
    void textMessage_flowsToAi() throws Exception {
        String body = "{\"from\":\"+34687723287\",\"to\":\"+34111111111\",\"channel\":\"whatsapp\","
            + "\"message_type\":\"text\",\"text\":\"Hola\"}";
        stubBody(body);
        when(webhookValidator.verify(any(), anyString())).thenReturn(true);
        when(aiChatbotService.processMessage(anyString(), anyString(), any())).thenReturn("¡Hola!");

        ResponseEntity<Void> response =
            controller.handleInbound("Bearer token", request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(aiChatbotService).processMessage("+34687723287", "Hola", null);
    }
}