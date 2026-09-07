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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end WhatsApp bot flow: [LEAD] tag extraction, FSM advance to
 * confirmation, single-message turns (no buttons + text together), and a
 * guarded repeated-confirm no-op — verifying no double escalation and that the
 * conversation stays alive after a confirmation.
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
        lenient().when(promptComposer.compose(any())).thenReturn("Eres Naiara de Script9.");
        lenient().when(promptComposer.composeDefault()).thenReturn("Eres Naiara de Script9.");
    }

    @Test
    @DisplayName("end-to-end: lead tag -> confirmation -> post-confirmation alive, no double escalation")
    void leadTag_throughConfirmation_noDoubleEscalation() {
        // A lead already exists for this phone; extraction updates it and
        // confirm_yes escalates that lead.
        Lead existingLead = new Lead();
        org.springframework.test.util.ReflectionTestUtils.setField(existingLead, "id", UUID.randomUUID());
        when(leadRepository.findByPhone(anyString())).thenReturn(Optional.of(existingLead));

        // Step 1: user chooses Ventas -> collecting_info
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);

        // Step 2: user shares name+email -> lead saved deterministically.
        // Email detected -> timing buttons sent, NO AI text in the same turn.
        String step2Reply = service.processMessage(PHONE, "Me llamo Juan y mi email es juan@test.com", BUSINESS_ID);

        verify(leadRepository).save(argThat(leadArg ->
            ((Lead) leadArg).getEmail().equals("juan@test.com")));
        assertThat(step2Reply).isNull();

        // Step 3: timing ("now") advances toward confirmation (buttons only)
        service.processMessage(PHONE, "timing_now", BUSINESS_ID);

        // Step 4: confirm_yes -> real confirmation, escalates once
        String confirm = service.processMessage(PHONE, "confirm_yes", BUSINESS_ID);
        assertThat(confirm).contains("demo de 15 minutos");
        verify(escalationService, times(1)).qualify(any(), any());

        // Step 5: another text after confirming keeps the conversation alive
        when(groqService.chat(anyString(), anyList(), anyString()))
            .thenReturn("¡Perfecto! Te envío el correo con el enlace para agendar.");
        String after = service.processMessage(PHONE, "genial, gracias", BUSINESS_ID);
        assertThat(after).isNotNull();
        assertThat(after).doesNotContain("Ya procesé tu respuesta");

        // Step 6: duplicate confirm_yes -> already-handled, NO re-escalation
        String reClick = service.processMessage(PHONE, "confirm_yes", BUSINESS_ID);
        assertThat(reClick).contains("Ya procesé tu respuesta");
        verify(escalationService, times(1)).qualify(any(), any());
    }
}
