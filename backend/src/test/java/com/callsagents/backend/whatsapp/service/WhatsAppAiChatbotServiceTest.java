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
        lenient().when(businessService.resolveOwnerUserId(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void expectNoExistingLead() {
        lenient().when(leadRepository.findByPhone(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void chatLead_savedFromLeadTag_andTimingButtonsSent() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);

        expectNoExistingLead();

        String result = service.processMessage(PHONE, "Me llamo Antonio, antonio@test.com", BUSINESS_ID);

        // Email detected -> timing buttons sent, no AI text in the same turn
        assertThat(result).isNull();
        verify(leadRepository).save(argThat(lead -> {
            Lead l = (Lead) lead;
            return "antonio@test.com".equals(l.getEmail())
                && "Antonio".equals(l.getFirstName())
                && BUSINESS_ID.equals(l.getCreatedBy())
                && l.getPhone().equals("+" + PHONE);
        }));
    }

    @Test
    void chatLead_noEmail_savedWithUnknownName() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);
        expectNoExistingLead();

        service.processMessage(PHONE, "antonio@test.com", BUSINESS_ID);

        verify(leadRepository).save(argThat(lead ->
            ((Lead) lead).getEmail().equals("antonio@test.com")
                && "Desconocido".equals(((Lead) lead).getFirstName())
                && BUSINESS_ID.equals(((Lead) lead).getCreatedBy())));
    }

    @Test
    void chatLead_withConnectorName_usesCleanParsedName() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);
        expectNoExistingLead();

        service.processMessage(PHONE, "Me llamo Juan y mi email es juan@test.com", BUSINESS_ID);

        verify(leadRepository).save(argThat(lead ->
            ((Lead) lead).getEmail().equals("juan@test.com")
                && "Juan".equals(((Lead) lead).getFirstName())
                && BUSINESS_ID.equals(((Lead) lead).getCreatedBy())));
    }

    @Test
    void noLeadTag_noLeadSaved() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);
        expectNoExistingLead();
        when(groqService.chat(anyString(), anyList(), anyString()))
            .thenReturn("¿Cuál es tu correo?");

        String result = service.processMessage(PHONE, "Me llamo Antonio", BUSINESS_ID);

        assertThat(result).isEqualTo("¿Cuál es tu correo?");
        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    @DisplayName("fallback: model omits the tag but the user stated an email -> lead captured by regex")
    void noLeadTag_userStatedEmail_leadCapturedByRegex() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);
        expectNoExistingLead();

        String result = service.processMessage(PHONE, "Antonio y mi email es antohachi@gmail.com", BUSINESS_ID);

        // Email detected -> timing buttons sent, no AI text in the same turn (single-message rule)
        assertThat(result).isNull();
        verify(leadRepository).save(argThat(lead -> {
            Lead l = (Lead) lead;
            return "antohachi@gmail.com".equals(l.getEmail())
                && "Antonio".equals(l.getFirstName())
                && BUSINESS_ID.equals(l.getCreatedBy());
        }));
    }

    @Test
    @DisplayName("fallback: email without a name token -> lead with Desconocido")
    void noLeadTag_emailOnly_leadCapturedWithUnknownName() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);
        expectNoExistingLead();

        service.processMessage(PHONE, "escribe a antohachi@gmail.com", BUSINESS_ID);

        verify(leadRepository).save(argThat(lead ->
            ((Lead) lead).getEmail().equals("antohachi@gmail.com")
                && "Desconocido".equals(((Lead) lead).getFirstName())
                && BUSINESS_ID.equals(((Lead) lead).getCreatedBy())));
    }

    @Test
    @DisplayName("fallback never saves when the user message has no email at all")
    void noLeadTag_noEmailInMessage_noLeadSaved() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);
        expectNoExistingLead();
        when(groqService.chat(anyString(), anyList(), anyString()))
            .thenReturn("Entendido, te ayudo con ventas.");

        String result = service.processMessage(PHONE, "Quiero saber más sobre ventas", BUSINESS_ID);

        assertThat(result).isEqualTo("Entendido, te ayudo con ventas.");
        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    void nullChatResponse_noLeadSaved_technicalMessage() {
        service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);
        expectNoExistingLead();
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn(null);

        String result = service.processMessage(PHONE, "Me llamo Antonio", BUSINESS_ID);

        assertThat(result).contains("problema técnico");
        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    void withoutBusinessId_noLeadSaved() {
        service.processMessage(PHONE, "intent_ventas");
        expectNoExistingLead();

        service.processMessage(PHONE, "Me llamo Antonio, antonio@test.com");

        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    @DisplayName("visible text never contains the [LEAD] tag")
    void visibleText_stripsLeadTag() {
        service.processMessage(PHONE, "intent_soporte", BUSINESS_ID);
        expectNoExistingLead();
        when(groqService.chat(anyString(), anyList(), anyString()))
            .thenReturn("Claro, aquí tienes la información [LEAD:name=Antonio|email=x@test.com|service=soporte]");

        String result = service.processMessage(PHONE, "¿Cómo funciona?", BUSINESS_ID);

        assertThat(result).isEqualTo("Claro, aquí tienes la información");
        assertThat(result).doesNotContain("[LEAD");
        assertThat(result).doesNotContain("Lead extracted");
        verify(leadRepository).save(any(Lead.class));
    }

    @Test
    @DisplayName("real transcript: intent -> timing -> confirmation -> post-confirmation alive, single messages, repeated button guarded")
    void realTranscript_singleMessages_postConfirmationAlive() {
        Lead existingLead = new Lead();
        org.springframework.test.util.ReflectionTestUtils.setField(existingLead, "id", UUID.randomUUID());
        when(leadRepository.findByPhone(anyString())).thenReturn(Optional.of(existingLead));

        // 1. user chooses Ventas -> collecting_info (single text reply)
        String step1 = service.processMessage(PHONE, "intent_ventas", BUSINESS_ID);
        assertThat(step1).contains("¿Cómo te llamas");

        // 2. name + email -> ONLY timing buttons (null reply), lead saved deterministically
        String step2 = service.processMessage(PHONE, "Me llamo Antonio y mi correo es antohachi@gmail.com", BUSINESS_ID);
        assertThat(step2).isNull();
        verify(leadRepository).save(argThat(lead ->
            ((Lead) lead).getEmail().equals("antohachi@gmail.com")));

        // 3. "Lo antes posible" free text -> confirmation buttons (null, no AI text)
        String step3 = service.processMessage(PHONE, "Lo antes posible", BUSINESS_ID);
        assertThat(step3).isNull();

        // 4. "Si, agendar" free text -> real confirmation + escalation + demo message
        String step4 = service.processMessage(PHONE, "Si, agendar", BUSINESS_ID);
        assertThat(step4).contains("demo de 50 leads");
        verify(escalationService, times(1)).qualify(any(), any());

        // 5. another "si" after confirming stays alive (NO "ya procesé tu respuesta")
        when(groqService.chat(anyString(), anyList(), anyString()))
            .thenReturn("¡Perfecto! Te envío el correo con el enlace para agendar.");
        String step5 = service.processMessage(PHONE, "si", BUSINESS_ID);
        assertThat(step5).isNotNull();
        assertThat(step5).doesNotContain("Ya procesé tu respuesta");

        // 6. repeating the SAME confirm_yes button -> already handled, no extra escalation
        String step6 = service.processMessage(PHONE, "confirm_yes", BUSINESS_ID);
        assertThat(step6).isNotNull();
        assertThat(step6).contains("Ya procesé tu respuesta");
        verify(escalationService, times(1)).qualify(any(), any());
    }
}
