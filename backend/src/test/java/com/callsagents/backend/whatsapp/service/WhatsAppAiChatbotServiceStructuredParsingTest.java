package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.business.service.BusinessPromptComposer;
import com.callsagents.backend.business.service.BusinessService;
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

    private WhatsAppAiChatbotService service;

    private static final String PHONE = "34687723287";
    private static final UUID BUSINESS_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WhatsAppAiChatbotService(
            groqService, leadRepository, vonageMessageService,
            businessService, promptComposer, escalationService, voiceCallService
        );
        when(groqService.isConfigured()).thenReturn(true);
        // System prompt resolution needs a non-null prompt
        lenient().when(promptComposer.compose(any())).thenReturn("Eres Naiara de Script9.");
        lenient().when(promptComposer.composeDefault()).thenReturn("Eres Naiara de Script9.");
    }

    @Test
    @DisplayName("processMessage: structured lead extraction populates leadData and saves lead")
    void processMessage_savesLeadFromStructuredResponse() {
        // Advance to collecting_info step
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);

        // Neither a lead exists yet
        when(leadRepository.findByPhone(anyString())).thenReturn(Optional.empty());

        // Mock chatStructured to return a structured lead
        GroqService.LeadData leadData = new GroqService.LeadData(
            "Juan", "juan@test.com", "taxi", "now"
        );
        GroqService.LeadExtraction extraction = new GroqService.LeadExtraction(
            "Perfecto!", leadData
        );
        when(groqService.chatStructured(anyString(), anyList(),
            anyString())).thenReturn(extraction);

        String result = service.processMessage(
            PHONE, "Me llamo Juan, juan@test.com", BUSINESS_ID
        );

        // Lead saved
        verify(leadRepository).save(any(Lead.class));
        verify(leadRepository).save(argThat(leadArg -> {
            Lead lead = (Lead) leadArg;
            return "juan@test.com".equals(lead.getEmail())
                && "Juan".equals(lead.getFirstName());
        }));
        // Bot reply is the structured response
        assertThat(result).isEqualTo("Perfecto!");
    }

    @Test
    @DisplayName("processMessage: null lead from structured response does not save lead")
    void processMessage_nullLead_doesNotSave() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);

        GroqService.LeadExtraction extraction = new GroqService.LeadExtraction(
            "¿Cuál es tu email?", null
        );
        when(groqService.chatStructured(anyString(), anyList(),
            anyString())).thenReturn(extraction);

        String result = service.processMessage(
            PHONE, "Me llamo Juan", BUSINESS_ID
        );

        verify(leadRepository, never()).save(any(Lead.class));
        assertThat(result).isEqualTo("¿Cuál es tu email?");
    }
}