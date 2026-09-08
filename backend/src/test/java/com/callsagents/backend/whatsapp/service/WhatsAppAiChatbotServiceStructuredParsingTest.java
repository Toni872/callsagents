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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppAiChatbotServiceStructuredParsingTest {

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
    }

    @Test
    @DisplayName("email capture: deterministic save AND Groq reply in same turn")
    void emailCapture_savesLeadAndCallsGroq() {
        when(leadRepository.findByPhoneAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Gracias Juan, te ayudo.");

        String result = service.processMessage(
            PHONE, "Me llamo Juan, juan@test.com", BUSINESS_ID
        );

        assertThat(result).isEqualTo("Gracias Juan, te ayudo.");
        verify(leadRepository).save(argThat(lead -> {
            Lead l = (Lead) lead;
            return "juan@test.com".equals(l.getEmail())
                && "Juan".equals(l.getFirstName());
        }));
        verify(groqService).chat(anyString(), anyList(), anyString());
    }

    @Test
    @DisplayName("no email in message -> no lead saved, AI reply returned")
    void noEmail_noLeadSaved() {
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("¿Cuál es tu correo?");

        String result = service.processMessage(
            PHONE, "Me llamo Juan", BUSINESS_ID
        );

        assertThat(result).isEqualTo("¿Cuál es tu correo?");
        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    @DisplayName("[LEAD] tag from AI -> lead saved, tag stripped from visible text")
    void leadTag_savesLeadAndStripsTag() {
        when(leadRepository.findByPhoneAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
        when(groqService.chat(anyString(), anyList(), anyString()))
            .thenReturn("Perfecto, gracias [LEAD:name=Juan|email=juan@test.com|service=ventas]");

        String result = service.processMessage(
            PHONE, "Me llamo Juan y mi correo es juan@test.com", BUSINESS_ID
        );

        assertThat(result).isEqualTo("Perfecto, gracias");
        assertThat(result).doesNotContain("[LEAD");
        verify(leadRepository).save(any(Lead.class));
    }

    @Test
    @DisplayName("multiple turns after email capture still work")
    void multipleTurns_afterEmailCapture() {
        when(leadRepository.findByPhoneAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Entendido");

        service.processMessage(PHONE, "Me llamo Juan y mi email es juan@test.com", BUSINESS_ID);
        String turn2 = service.processMessage(PHONE, "¿Cuánto cuesta?", BUSINESS_ID);
        String turn3 = service.processMessage(PHONE, "Quiero más info", BUSINESS_ID);

        assertThat(turn2).isNotNull();
        assertThat(turn3).isNotNull();
    }
}
