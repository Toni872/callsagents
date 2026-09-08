package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.business.service.BusinessPromptComposer;
import com.callsagents.backend.business.service.BusinessService;
import com.callsagents.backend.chatbot.ChatbotEngine;
import com.callsagents.backend.escalation.service.EscalationService;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.repository.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppAiChatbotServiceTerminalStateTest {

    @Mock GroqService groqService;
    @Mock LeadRepository leadRepository;
    @Mock VonageMessageService vonageMessageService;
    @Mock BusinessService businessService;
    @Mock BusinessPromptComposer promptComposer;
    @Mock EscalationService escalationService;

    private ChatbotEngine engine;
    private WhatsAppAiChatbotService service;

    private static final String PHONE = "34687723287";
    private static final UUID BUSINESS_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new ChatbotEngine(
            groqService, leadRepository,
            businessService, promptComposer, escalationService
        );
        service = new WhatsAppAiChatbotService(
            groqService, vonageMessageService,
            businessService, engine
        );
        when(groqService.isConfigured()).thenReturn(true);
        lenient().when(promptComposer.compose(any())).thenReturn("Eres Naiara de Script9.");
        lenient().when(promptComposer.composeDefault()).thenReturn("Eres Naiara de Script9.");
        lenient().when(businessService.resolveOwnerUserId(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Perfecto, te ayudo");
    }

    private void stubExistingLead() {
        Lead lead = org.mockito.Mockito.mock(Lead.class);
        lenient().when(lead.getId()).thenReturn(UUID.randomUUID());
        lenient().when(leadRepository.findByPhone(anyString())).thenReturn(Optional.of(lead));
    }

    @Test
    @DisplayName("after email captured + affirmative -> escalation fires once, not twice")
    void emailCapturedAffirmative_escalationOnce() {
        stubExistingLead();
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("¡Genial!");

        service.processMessage(PHONE, "Me llamo Juan y mi email es juan@test.com", BUSINESS_ID);
        service.processMessage(PHONE, "Sí, estoy interesado", BUSINESS_ID);
        service.processMessage(PHONE, "Confirmo", BUSINESS_ID);

        verify(escalationService, times(1)).qualify(any(), any());
    }

    @Test
    @DisplayName("negative response after email -> no escalation")
    void emailCapturedNegative_noEscalation() {
        stubExistingLead();
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("No te preocupes.");

        service.processMessage(PHONE, "Me llamo Juan y mi email es juan@test.com", BUSINESS_ID);
        service.processMessage(PHONE, "No, gracias", BUSINESS_ID);

        verify(escalationService, never()).qualify(any(), any());
    }

    @Test
    @DisplayName("after reset, conversation starts fresh")
    void afterReset_freshConversation() {
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Hola de nuevo");

        service.processMessage(PHONE, "test", BUSINESS_ID);
        service.processMessage(PHONE, "reset", BUSINESS_ID);
        String result = service.processMessage(PHONE, "hola", BUSINESS_ID);

        assertThat(result).isNotNull();
        verify(vonageMessageService).sendText(anyString(), anyString());
    }

    @Test
    @DisplayName("conversation alive after email capture — multiple turns work")
    void conversationAlive_afterEmailCapture() {
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Entendido");

        service.processMessage(PHONE, "Me llamo Antonio y mi email es antonio@test.com", BUSINESS_ID);
        String turn2 = service.processMessage(PHONE, "Quiero saber más", BUSINESS_ID);
        String turn3 = service.processMessage(PHONE, "¿Cuánto cuesta?", BUSINESS_ID);

        assertThat(turn2).isNotNull();
        assertThat(turn3).isNotNull();
    }
}
