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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
        lenient().when(promptComposer.compose(any())).thenReturn("Eres Naiara de Script9.");
        lenient().when(promptComposer.composeDefault()).thenReturn("Eres Naiara de Script9.");
    }

    private void expectNoExistingLead() {
        lenient().when(leadRepository.findByPhone(anyString())).thenReturn(Optional.empty());
    }

    private void stubStructured(String responseText, String name, String email) {
        GroqService.LeadData leadData = name == null && email == null ? null
            : new GroqService.LeadData(name, email, null, null);
        when(groqService.chatStructured(anyString(), anyList(), anyString()))
            .thenReturn(new GroqService.LeadExtraction(responseText, leadData));
    }

    @Test
    void structuredLead_savedFromStructuredResponse() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);

        expectNoExistingLead();
        stubStructured("Perfecto, te ayudo", "Antonio", "antonio@test.com");

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
    void structuredLead_noEmail_savedWithUnknownName() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);
        expectNoExistingLead();
        stubStructured("Perfecto, ¿algo más?", null, "antonio@test.com");

        service.processMessage(PHONE, "antonio@test.com", BUSINESS_ID);

        verify(leadRepository).save(argThat(lead ->
            ((Lead) lead).getEmail().equals("antonio@test.com")
                && "Desconocido".equals(((Lead) lead).getFirstName())
                && BUSINESS_ID.equals(((Lead) lead).getCreatedBy())));
    }

    @Test
    void structuredLead_withConnectorName_usesCleanStructuredName() {
        // Name/email hygiene now comes from the structured model output, so the
        // message can contain connector words without polluting the saved name.
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);
        expectNoExistingLead();
        stubStructured("Perfecto, te ayudo con eso", "Juan", "juan@test.com");

        service.processMessage(PHONE, "Me llamo Juan y mi email es juan@test.com", BUSINESS_ID);

        verify(leadRepository).save(argThat(lead ->
            ((Lead) lead).getEmail().equals("juan@test.com")
                && "Juan".equals(((Lead) lead).getFirstName())
                && BUSINESS_ID.equals(((Lead) lead).getCreatedBy())));
    }

    @Test
    void nullExtraction_noLeadSaved() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);
        expectNoExistingLead();
        stubStructured("¿Cuál es tu email?", null, null);

        service.processMessage(PHONE, "Me llamo Antonio", BUSINESS_ID);

        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    void withoutBusinessId_noLeadSaved() {
        service.processMessage(PHONE, "intent_ventas");
        stubStructured("Hola Antonio", "Antonio", "antonio@test.com");

        service.processMessage(PHONE, "Me llamo Antonio, antonio@test.com");

        verify(leadRepository, never()).save(any(Lead.class));
    }
}