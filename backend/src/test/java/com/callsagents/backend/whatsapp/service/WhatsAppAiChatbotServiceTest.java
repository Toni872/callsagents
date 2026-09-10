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
class WhatsAppAiChatbotServiceTest {

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

    private void expectNoExistingLead() {
        lenient().when(leadRepository.findByPhoneAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("email in message -> deterministic lead capture AND AI reply (no timing buttons)")
    void emailInMessage_savesLeadAndReturnsAiReply() {
        expectNoExistingLead();
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Gracias Antonio, te ayudo.");

        String result = service.processMessage(PHONE, "Me llamo Antonio, antonio@test.com", BUSINESS_ID);

        assertThat(result).isEqualTo("Gracias Antonio, te ayudo.");
        verify(leadRepository).save(argThat(lead -> {
            Lead l = (Lead) lead;
            return "antonio@test.com".equals(l.getEmail())
                && "Antonio".equals(l.getFirstName())
                && BUSINESS_ID.equals(l.getCreatedBy())
                && l.getPhone().equals("+" + PHONE);
        }));
    }

    @Test
    @DisplayName("email without name -> lead with Desconocido, AI reply returned")
    void emailOnly_leadWithUnknownName() {
        expectNoExistingLead();
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Entendido.");

        String result = service.processMessage(PHONE, "antonio@test.com", BUSINESS_ID);

        assertThat(result).isEqualTo("Entendido.");
        verify(leadRepository).save(argThat(lead ->
            ((Lead) lead).getEmail().equals("antonio@test.com")
                && "Desconocido".equals(((Lead) lead).getFirstName())
                && BUSINESS_ID.equals(((Lead) lead).getCreatedBy())));
    }

    @Test
    @DisplayName("name + email -> lead saved with clean parsed name")
    void nameAndEmail_savesCleanParsedName() {
        expectNoExistingLead();
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Gracias Juan.");

        String result = service.processMessage(PHONE, "Me llamo Juan y mi email es juan@test.com", BUSINESS_ID);

        assertThat(result).isEqualTo("Gracias Juan.");
        verify(leadRepository).save(argThat(lead ->
            ((Lead) lead).getEmail().equals("juan@test.com")
                && "Juan".equals(((Lead) lead).getFirstName())
                && BUSINESS_ID.equals(((Lead) lead).getCreatedBy())));
    }

    @Test
    @DisplayName("no email in message -> no lead saved, AI reply returned")
    void noEmail_noLeadSaved() {
        expectNoExistingLead();
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("¿Cuál es tu correo?");

        String result = service.processMessage(PHONE, "Me llamo Antonio", BUSINESS_ID);

        assertThat(result).isEqualTo("¿Cuál es tu correo?");
        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    @DisplayName("model emits [LEAD] tag -> tag stripped from visible text, lead saved")
    void leadTag_strippedAndLeadSaved() {
        expectNoExistingLead();
        when(groqService.chat(anyString(), anyList(), anyString()))
            .thenReturn("Claro, aquí tienes la información [LEAD:name=Antonio|email=x@test.com|service=soporte]");

        String result = service.processMessage(PHONE, "¿Cómo funciona?", BUSINESS_ID);

        assertThat(result).isEqualTo("Claro, aquí tienes la información");
        assertThat(result).doesNotContain("[LEAD");
        verify(leadRepository).save(any(Lead.class));
    }

    @Test
    @DisplayName("null Groq response -> friendly retry message, no mention of technical problem")
    void nullGroqResponse_technicalMessage() {
        expectNoExistingLead();
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn(null);

        String result = service.processMessage(PHONE, "Me llamo Antonio", BUSINESS_ID);

        assertThat(result).contains("no he podido procesar tu mensaje");
        assertThat(result).doesNotContain("problema técnico");
        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    @DisplayName("without businessId -> no lead saved")
    void withoutBusinessId_noLeadSaved() {
        expectNoExistingLead();
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Hola");

        service.processMessage(PHONE, "Me llamo Antonio, antonio@test.com");

        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    @DisplayName("reset via 'reset' sends greeting text via Vonage")
    void reset_sendsGreeting() {
        service.processMessage(PHONE, "test", BUSINESS_ID);

        service.processMessage(PHONE, "reset", BUSINESS_ID);

        verify(vonageMessageService).sendText(anyString(), anyString());
    }

    @Test
    @DisplayName("'hola' is NOT a reset — flows to AI")
    void hola_isNotReset() {
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("¡Hola! ¿Cómo estás?");

        String result = service.processMessage(PHONE, "hola", BUSINESS_ID);

        assertThat(result).isEqualTo("¡Hola! ¿Cómo estás?");
        verify(vonageMessageService, never()).sendText(anyString(), anyString());
    }

    @Test
    @DisplayName("real transcript: email capture + affirmative -> escalation fires once")
    void realTranscript_emailAndAffirmative_escalationFiresOnce() {
        Lead existingLead = new Lead();
        org.springframework.test.util.ReflectionTestUtils.setField(existingLead, "id", UUID.randomUUID());
        when(leadRepository.findByPhoneAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(existingLead));
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("¡Genial!");

        service.processMessage(PHONE, "Me llamo Antonio y mi correo es antohachi@gmail.com", BUSINESS_ID);
        service.processMessage(PHONE, "Sí, estoy interesado", BUSINESS_ID);
        service.processMessage(PHONE, "Sí, confirmo", BUSINESS_ID);

        verify(escalationService, times(1)).qualify(any(), any());
    }
}
