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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppAiChatbotServiceTerminalStateTest {

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
        // Flow steps use structured output — return a response with no lead
        when(groqService.chatStructured(anyString(), anyList(),
            anyString())).thenReturn(new GroqService.LeadExtraction("Perfecto, te ayudo", null));
    }

    private void stubExistingLead() {
        Lead lead = mock(Lead.class);
        lenient().when(lead.getId()).thenReturn(UUID.randomUUID());
        lenient().when(leadRepository.findByPhone(anyString())).thenReturn(Optional.of(lead));
    }

    /**
     * Drive the conversation to the confirmation step where confirm_yes/confirm_no buttons work.
     */
    private void advanceToConfirmation() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);
        service.processMessage(PHONE, "Juan, juan@test.com", BUSINESS_ID);
        service.processMessage(PHONE, "timing_now", BUSINESS_ID);
    }

    /**
     * Drive the conversation to the voice call decision step where accept/decline buttons work.
     * Requires >= 8 history entries and step NOT in productive steps.
     */
    private void advanceToVoiceDecision() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);
        for (int i = 0; i < 8; i++) {
            service.processMessage(PHONE, "test message " + i, BUSINESS_ID);
        }
        // The 4th+ message triggers voice offer (history >= 8)
    }

    @Test
    @DisplayName("Terminal state: confirmed_yes button re-click returns 'already handled' message")
    void confirmYesReClick_returnsAlreadyHandled() {
        stubExistingLead();
        advanceToConfirmation();

        // Click confirm_yes — this sets terminal state and triggers escalation once
        service.processMessage(PHONE, "confirm_yes", BUSINESS_ID);

        // Re-click confirm_yes — should get "already handled" message, no re-escalation
        String result2 = service.processMessage(PHONE, "confirm_yes", BUSINESS_ID);

        assertThat(result2).isNotNull();
        assertThat(result2).contains("Ya procesé tu respuesta");
        // Escalation should have been triggered exactly once (on first click)
        verify(escalationService, times(1)).qualify(any(), any());
    }

    @Test
    @DisplayName("Terminal state: confirmed_no button re-click returns 'already handled' message")
    void confirmNoReClick_returnsAlreadyHandled() {
        advanceToConfirmation();

        // Click confirm_no
        service.processMessage(PHONE, "confirm_no", BUSINESS_ID);

        // Re-click confirm_no
        String result = service.processMessage(PHONE, "confirm_no", BUSINESS_ID);

        assertThat(result).isNotNull();
        assertThat(result).contains("Ya procesé tu respuesta");
    }

    @Test
    @DisplayName("Terminal state: voice_accepted re-click returns 'already handled' message")
    void voiceAcceptedReClick_returnsAlreadyHandled() {
        stubExistingLead();
        advanceToVoiceDecision();

        // Accept voice call — sets terminal state
        service.processMessage(PHONE, "accept_voice_call", BUSINESS_ID);

        // Re-click accept_voice_call — should be no-op, no re-dispatch
        String result = service.processMessage(PHONE, "accept_voice_call", BUSINESS_ID);

        assertThat(result).isNotNull();
        assertThat(result).contains("Ya procesé tu respuesta");
    }

    @Test
    @DisplayName("Terminal state: voice_declined re-click returns 'already handled' message")
    void voiceDeclinedReClick_returnsAlreadyHandled() {
        stubExistingLead();
        advanceToVoiceDecision();

        // Decline voice call — sets terminal state
        service.processMessage(PHONE, "decline_voice_call", BUSINESS_ID);

        // Re-click decline_voice_call — should be no-op
        String result = service.processMessage(PHONE, "decline_voice_call", BUSINESS_ID);

        assertThat(result).isNotNull();
        assertThat(result).contains("Ya procesé tu respuesta");
    }
}