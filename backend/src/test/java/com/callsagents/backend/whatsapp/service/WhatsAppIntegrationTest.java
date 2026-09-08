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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppIntegrationTest {

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
    @DisplayName("end-to-end: email capture + affirmative -> escalation fires once, conversation continues")
    void emailCaptureAndAffirmative_escalationOnce() {
        Lead existingLead = new Lead();
        org.springframework.test.util.ReflectionTestUtils.setField(existingLead, "id", UUID.randomUUID());
        when(leadRepository.findByPhoneAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(existingLead));
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("¡Genial!");

        // Step 1: email captured deterministically + AI reply
        String step1 = service.processMessage(PHONE, "Me llamo Juan y mi email es juan@test.com", BUSINESS_ID);
        assertThat(step1).isNotNull();
        verify(leadRepository).save(argThat(lead ->
            ((Lead) lead).getEmail().equals("juan@test.com")));

        // Step 2: affirmative -> escalation fires once
        String step2 = service.processMessage(PHONE, "Sí, estoy interesado", BUSINESS_ID);
        assertThat(step2).isNotNull();
        verify(escalationService, times(1)).qualify(any(), any());

        // Step 3: more conversation — still alive
        String step3 = service.processMessage(PHONE, "Cuéntame más", BUSINESS_ID);
        assertThat(step3).isNotNull();

        // Step 4: another affirmative — no re-escalation
        String step4 = service.processMessage(PHONE, "Perfecto, confirmo", BUSINESS_ID);
        assertThat(step4).isNotNull();
        verify(escalationService, times(1)).qualify(any(), any());
    }

    @Test
    @DisplayName("end-to-end: email capture + negative -> no escalation")
    void emailCaptureAndNegative_noEscalation() {
        Lead existingLead = new Lead();
        org.springframework.test.util.ReflectionTestUtils.setField(existingLead, "id", UUID.randomUUID());
        when(leadRepository.findByPhoneAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(existingLead));
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("No te preocupes.");

        service.processMessage(PHONE, "Me llamo Juan y mi email es juan@test.com", BUSINESS_ID);
        service.processMessage(PHONE, "No, gracias", BUSINESS_ID);

        verify(escalationService, never()).qualify(any(), any());
    }

    @Test
    @DisplayName("end-to-end: reset after email capture clears state")
    void resetAfterEmailCapture() {
        when(leadRepository.findByPhoneAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Hola de nuevo");

        service.processMessage(PHONE, "Me llamo Juan y mi email es juan@test.com", BUSINESS_ID);
        service.processMessage(PHONE, "reset", BUSINESS_ID);
        String after = service.processMessage(PHONE, "hola", BUSINESS_ID);

        assertThat(after).isNotNull();
    }
}
