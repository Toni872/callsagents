package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.business.service.BusinessPromptComposer;
import com.callsagents.backend.business.service.BusinessService;
import com.callsagents.backend.chatbot.ChatbotEngine;
import com.callsagents.backend.escalation.service.EscalationService;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.repository.LeadRepository;
import com.callsagents.backend.voice.service.VoiceCallService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppAiChatbotServiceStructuredParsingTest {

    @Mock GroqService groqService;
    @Mock LeadRepository leadRepository;
    @Mock VonageMessageService vonageMessageService;
    @Mock BusinessService businessService;
    @Mock BusinessPromptComposer promptComposer;
    @Mock EscalationService escalationService;
    @Mock VoiceCallService voiceCallService;

    private ChatbotEngine engine;
    private WhatsAppAiChatbotService service;

    private static final String PHONE = "34687723287";
    private static final UUID BUSINESS_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new ChatbotEngine(
            groqService, leadRepository,
            businessService, promptComposer, escalationService, voiceCallService
        );
        service = new WhatsAppAiChatbotService(
            groqService, vonageMessageService,
            businessService, engine
        );
        when(groqService.isConfigured()).thenReturn(true);
        // System prompt resolution needs a non-null prompt
        lenient().when(promptComposer.compose(any())).thenReturn("Eres Naiara de Script9.");
        lenient().when(promptComposer.composeDefault()).thenReturn("Eres Naiara de Script9.");
    }

    @Test
    @DisplayName("processMessage: [LEAD] tag extraction populates leadData and saves lead, tag removed from visible text")
    void processMessage_savesLeadFromLeadTag() {
        // Advance to collecting_info step
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);

        // No lead exists yet
        when(leadRepository.findByPhone(anyString())).thenReturn(Optional.empty());

        String result = service.processMessage(
            PHONE, "Me llamo Juan, juan@test.com", BUSINESS_ID
        );

        // Lead saved with parsed values
        verify(leadRepository).save(any(Lead.class));
        verify(leadRepository).save(argThat(leadArg -> {
            Lead lead = (Lead) leadArg;
            return "juan@test.com".equals(lead.getEmail())
                && "Juan".equals(lead.getFirstName());
        }));
        // The tag is stripped: only the natural text is shown (email detected ->
        // timing buttons are sent, so in this collecting_info flow the reply is null)
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("processMessage: response without a [LEAD] tag does not save a lead")
    void processMessage_noLeadTag_doesNotSave() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);

        when(groqService.chat(anyString(), anyList(),
            anyString())).thenReturn("¿Cuál es tu correo?");

        String result = service.processMessage(
            PHONE, "Me llamo Juan", BUSINESS_ID
        );

        verify(leadRepository, never()).save(any(Lead.class));
        assertThat(result).isEqualTo("¿Cuál es tu correo?");
    }

    @Test
    @DisplayName("free text timing answer advances to confirmation with buttons only")
    void freeTextTiming_advancesToConfirmation() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);
        when(leadRepository.findByPhone(anyString())).thenReturn(Optional.empty());

        // name + email advance to awaiting_timing (buttons only)
        service.processMessage(PHONE, "Juan, juan@test.com", BUSINESS_ID);

        // Free text timing -> confirmation buttons sent, no AI text
        String result = service.processMessage(PHONE, "Lo antes posible", BUSINESS_ID);
        assertThat(result).isNull();

        // Confirm button flows after free-text timing
        String confirm = service.processMessage(PHONE, "Sí, agendar", BUSINESS_ID);
        assertThat(confirm).contains("demo de 15 minutos");
    }
}