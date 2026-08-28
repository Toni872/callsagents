package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.business.service.BusinessPromptComposer;
import com.callsagents.backend.business.service.BusinessService;
import com.callsagents.backend.escalation.service.EscalationService;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.repository.LeadRepository;
import com.callsagents.backend.voice.service.VoiceCallService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppAiChatbotServiceTest {

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
    }

    private void expectNoExistingLead() {
        when(leadRepository.findByPhone(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void modelOmitsLeadTag_leadStillSavedFromMessage() {
        // Step 1: user taps "Ventas" -> step becomes collecting_info
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);

        // Step 2: model replies conversationally WITHOUT the [LEAD:...] tag
        expectNoExistingLead();
        when(groqService.chat(any(), any(), any()))
            .thenReturn("Hola Antonio, soy Naiara de Script9. ¿En qué te ayudo?");

        service.processMessage(PHONE, "Me llamo Antonio, antonio@test.com", BUSINESS_ID);

        verify(leadRepository).save(any(Lead.class));
        verify(leadRepository).save(argThat(lead -> {
            Lead l = (Lead) lead;
            return "antonio@test.com".equals(l.getEmail())
                && "Antonio".equals(l.getFirstName())
                && BUSINESS_ID.equals(l.getCreatedBy())
                && l.getPhone().equals("+" + PHONE);
        }));
    }

    @Test
    void modelEmitsLeadTag_leadSavedOnce() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);

        // Model complies: tag present at the end of the reply
        expectNoExistingLead();
        when(groqService.chat(any(), any(), any()))
            .thenReturn("Listo, te ayudo con eso [LEAD:name=Antonio|email=antonio@test.com|service=Automatizacion]");

        service.processMessage(PHONE, "Me llamo Antonio, antonio@test.com", BUSINESS_ID);

        verify(leadRepository, times(1)).save(any(Lead.class));
        verify(leadRepository).save(argThat(lead ->
            ((Lead) lead).getEmail().equals("antonio@test.com")
                && BUSINESS_ID.equals(((Lead) lead).getCreatedBy())));
    }

    @Test
    void withoutBusinessId_noLeadSaved() {
        // Same flow but no business profile resolved -> created_by NOT NULL guard
        service.processMessage(PHONE, "intent_ventas");
        when(groqService.chat(any(), any(), any()))
            .thenReturn("Hola Antonio, soy Naiara de Script9.");

        service.processMessage(PHONE, "Me llamo Antonio, antonio@test.com");

        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    void emailOutsideCollectingInfo_noLeadSaved() {
        // Bare email in the initial step should not trigger the fallback
        when(groqService.chat(any(), any(), any()))
            .thenReturn("Hola, ¿en qué puedo ayudarte?");
        service.processMessage(PHONE, "Mi email es antonio@test.com", BUSINESS_ID);

        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    void onlyEmailNoName_leadSavedWithUnknownName() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);
        expectNoExistingLead();
        when(groqService.chat(any(), any(), any()))
            .thenReturn("Perfecto, ¿algo más?");

        service.processMessage(PHONE, "antonio@test.com", BUSINESS_ID);

        verify(leadRepository).save(argThat(lead ->
            ((Lead) lead).getEmail().equals("antonio@test.com")
                && "Desconocido".equals(((Lead) lead).getFirstName())
                && BUSINESS_ID.equals(((Lead) lead).getCreatedBy())));
    }
}