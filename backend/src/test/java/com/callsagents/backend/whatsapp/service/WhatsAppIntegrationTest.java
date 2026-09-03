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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end WhatsApp bot flow: structured lead extraction, FSM advance to
 * confirmation, and terminal state on button reply — verifying no double
 * escalation on a duplicate terminal-state click.
 */
@ExtendWith(MockitoExtension.class)
class WhatsAppIntegrationTest {

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
        lenient().when(promptComposer.compose(any())).thenReturn("Eres Naiara de Script9.");
        lenient().when(promptComposer.composeDefault()).thenReturn("Eres Naiara de Script9.");
    }

    @Test
    @DisplayName("end-to-end: structured lead -> confirmation -> terminal state, no double escalation")
    void structuredLead_throughConfirmation_noDoubleEscalation() {
        // A lead already exists for this phone; structured extraction will update
        // it, and the confirm_yes handler escalates that lead.
        Lead existingLead = new Lead();
        org.springframework.test.util.ReflectionTestUtils.setField(existingLead, "id", UUID.randomUUID());
        when(leadRepository.findByPhone(anyString())).thenReturn(Optional.of(existingLead));

        // Step 1: user chooses Ventas -> collecting_info
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);

        // Step 2: user shares name+email -> model returns structured lead
        when(groqService.chatStructured(anyString(), anyList(), anyString()))
            .thenReturn(new GroqService.LeadExtraction(
                "Perfecto, ya tengo tus datos",
                new GroqService.LeadData("Juan", "juan@test.com", "ventas", null)));

        String step2Reply = service.processMessage(PHONE, "Me llamo Juan y mi email es juan@test.com", BUSINESS_ID);

        // Lead updated with the structured value
        verify(leadRepository).save(argThat(leadArg ->
            ((Lead) leadArg).getEmail().equals("juan@test.com")));
        assertThat(step2Reply).isEqualTo("Perfecto, ya tengo tus datos");

        // Step 3: timing ("now") advances toward confirmation
        lenient().when(groqService.chatStructured(anyString(), anyList(), anyString()))
            .thenReturn(new GroqService.LeadExtraction("¿Te confirma un demo ahora?", null));
        service.processMessage(PHONE, "timing_now", BUSINESS_ID);

        // Step 4: confirm_yes -> terminal state confirmed_yes, escalates once
        lenient().when(groqService.chatStructured(anyString(), anyList(), anyString()))
            .thenReturn(new GroqService.LeadExtraction("Listo, te confirmo", null));
        service.processMessage(PHONE, "confirm_yes", BUSINESS_ID);

        // Step 5: duplicate confirm_yes -> already-handled, NO re-escalation
        String reClick = service.processMessage(PHONE, "confirm_yes", BUSINESS_ID);

        assertThat(reClick).contains("Ya procesé tu respuesta");
        verify(escalationService, times(1)).qualify(any(), any());
    }
}