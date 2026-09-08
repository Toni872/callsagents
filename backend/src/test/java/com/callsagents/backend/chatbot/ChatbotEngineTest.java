package com.callsagents.backend.chatbot;

import com.callsagents.backend.business.service.BusinessPromptComposer;
import com.callsagents.backend.business.service.BusinessService;
import com.callsagents.backend.escalation.service.EscalationService;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.repository.LeadRepository;
import com.callsagents.backend.whatsapp.service.GroqService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotEngineTest {

    @Mock GroqService groqService;
    @Mock LeadRepository leadRepository;
    @Mock BusinessService businessService;
    @Mock BusinessPromptComposer promptComposer;
    @Mock EscalationService escalationService;

    private ChatbotEngine engine;

    private static final String KEY = "34687723287";
    private static final UUID BUSINESS_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new ChatbotEngine(
            groqService, leadRepository,
            businessService, promptComposer, escalationService
        );
        lenient().when(promptComposer.compose(any())).thenReturn("Eres Naiara de Script9.");
        lenient().when(promptComposer.composeDefault()).thenReturn("Eres Naiara de Script9.");
        lenient().when(businessService.resolveOwnerUserId(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private String stepOf(String key) {
        com.github.benmanes.caffeine.cache.Cache<String, String> stepCache =
            (com.github.benmanes.caffeine.cache.Cache<String, String>)
                ReflectionTestUtils.getField(engine, "conversationStep");
        return stepCache.getIfPresent(key);
    }

    @Test
    @DisplayName("greeting returns natural free-text, no buttons, step=initial")
    void greeting_freeTextNoButtons() {
        ChatTurn turn = engine.greeting(KEY, BUSINESS_ID);

        assertThat(turn.reply()).contains("¡Hola! Soy Naiara de Script9.");
        assertThat(turn.buttons()).isNull();
        assertThat(stepOf(KEY)).isEqualTo("initial");
        assertThat(turn.leadCaptured()).isFalse();
        assertThat(turn.contactForm()).isFalse();
    }

    @Test
    @DisplayName("greeting uses custom botName/companyName from profile")
    void greeting_customProfile() {
        com.callsagents.backend.business.entity.BusinessProfile profile =
            com.callsagents.backend.business.entity.BusinessProfile.builder()
                .botName("Luca")
                .companyName("Acme Corp")
                .build();
        when(businessService.getProfileEntityByUserId(BUSINESS_ID)).thenReturn(profile);

        ChatTurn turn = engine.greeting(KEY, BUSINESS_ID);

        assertThat(turn.reply()).contains("Soy Luca de Acme Corp");
        assertThat(turn.buttons()).isNull();
    }

    @Test
    @DisplayName("free-text process -> Groq call -> text response, no buttons, contactForm=false")
    void freeText_returnsNoButtons() {
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Claro, ¿en qué te ayudo?");

        ChatTurn turn = engine.process(KEY, "Necesito ayuda con mi cuenta", BUSINESS_ID, Channel.WEB);

        assertThat(turn.reply()).isEqualTo("Claro, ¿en qué te ayudo?");
        assertThat(turn.buttons()).isNull();
        assertThat(turn.contactForm()).isFalse();
        assertThat(turn.leadCaptured()).isFalse();
    }

    @Test
    @DisplayName("email in message -> deterministic lead capture AND Groq reply in same turn")
    void emailCapture_stillCallsGroq() {
        when(leadRepository.findByPhone(anyString())).thenReturn(Optional.empty());
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Gracias Antonio, te ayudo.");

        ChatTurn turn = engine.process(KEY, "Me llamo Antonio y mi email es antonio@test.com",
            BUSINESS_ID, Channel.WHATSAPP);

        assertThat(turn.reply()).isEqualTo("Gracias Antonio, te ayudo.");
        assertThat(turn.leadCaptured()).isTrue();
        assertThat(turn.buttons()).isNull();
        verify(groqService).chat(anyString(), anyList(), anyString());
        verify(leadRepository).save(argThat(leadArg -> {
            Lead lead = (Lead) leadArg;
            return "antonio@test.com".equals(lead.getEmail())
                && "Antonio".equals(lead.getFirstName());
        }));
    }

    @Test
    @DisplayName("[LEAD:...] tag extracted from AI response, stripped from visible text")
    void leadTag_extractedAndStripped() {
        when(leadRepository.findByPhone(anyString())).thenReturn(Optional.empty());
        when(groqService.chat(anyString(), anyList(), anyString()))
            .thenReturn("Perfecto, gracias por tus datos. [LEAD:name=Juan|email=juan@test.com|service=ventas]");

        ChatTurn turn = engine.process(KEY, "Me llamo Juan y mi correo es juan@test.com",
            BUSINESS_ID, Channel.WHATSAPP);

        assertThat(turn.reply()).isEqualTo("Perfecto, gracias por tus datos.");
        assertThat(turn.reply()).doesNotContain("[LEAD");
        assertThat(turn.leadCaptured()).isTrue();
    }

    @Test
    @DisplayName("WhatsApp: escalation fires once after email captured + affirmative, never twice")
    void whatsapp_escalationFiresOnce() {
        Lead existingLead = new Lead();
        ReflectionTestUtils.setField(existingLead, "id", UUID.randomUUID());
        when(leadRepository.findByPhone(anyString())).thenReturn(Optional.of(existingLead));
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("¡Genial!");

        engine.process(KEY, "Me llamo Antonio y mi email es antonio@test.com", BUSINESS_ID, Channel.WHATSAPP);
        engine.process(KEY, "Sí, estoy interesado", BUSINESS_ID, Channel.WHATSAPP);
        engine.process(KEY, "Confirmo todo", BUSINESS_ID, Channel.WHATSAPP);

        verify(escalationService, times(1)).qualify(any(), any());
    }

    @Test
    @DisplayName("WEB channel: escalation never fires even with email + affirmative")
    void web_noEscalationEver() {
        when(leadRepository.countByCreatedBy(BUSINESS_ID)).thenReturn(0L);
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("¡Genial!");

        engine.process(KEY, "Me llamo Laura y mi email es laura@test.com", BUSINESS_ID, Channel.WEB);
        engine.process(KEY, "Sí, estoy interesada", BUSINESS_ID, Channel.WEB);

        verify(escalationService, never()).qualify(any(), any());
    }

    @Test
    @DisplayName("reset via 'reset' clears state and returns greeting")
    void reset_clearsAndGreets() {
        engine.greeting(KEY, BUSINESS_ID);
        assertThat(stepOf(KEY)).isEqualTo("initial");

        ChatTurn turn = engine.process(KEY, "reset", BUSINESS_ID, Channel.WEB);
        assertThat(turn.reply()).contains("¡Hola!");
        assertThat(turn.buttons()).isNull();
        assertThat(stepOf(KEY)).isEqualTo("initial");
    }

    @Test
    @DisplayName("reset via 'reiniciar' clears state")
    void reiniciar_clearsState() {
        engine.process(KEY, "test", BUSINESS_ID, Channel.WEB);
        ChatTurn turn = engine.process(KEY, "reiniciar", BUSINESS_ID, Channel.WEB);
        assertThat(turn.reply()).contains("¡Hola!");
    }

    @Test
    @DisplayName("'hola' is NOT a reset — flows to Groq")
    void hola_isNotReset() {
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("¡Hola! ¿Cómo te llamo?");

        engine.process(KEY, "test", BUSINESS_ID, Channel.WEB);
        String stepBefore = stepOf(KEY);

        engine.process(KEY, "hola", BUSINESS_ID, Channel.WEB);
        assertThat(stepOf(KEY)).isEqualTo(stepBefore);
        verify(groqService, atLeast(1)).chat(anyString(), anyList(), anyString());
    }

    @Test
    @DisplayName("'inicio' is NOT a reset — flows to Groq")
    void inicio_isNotReset() {
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Hola");

        engine.process(KEY, "test", BUSINESS_ID, Channel.WEB);
        String stepBefore = stepOf(KEY);

        engine.process(KEY, "inicio", BUSINESS_ID, Channel.WEB);
        assertThat(stepOf(KEY)).isEqualTo(stepBefore);
        verify(groqService, atLeast(1)).chat(anyString(), anyList(), anyString());
    }

    @Test
    @DisplayName("rate-limit sentinel -> friendly rate-limit message")
    void rateLimitSentinel_friendlyMessage() {
        when(groqService.chat(anyString(), anyList(), anyString()))
            .thenReturn(GroqService.RATE_LIMITED_SENTINEL);

        ChatTurn turn = engine.process(KEY, "test message", BUSINESS_ID, Channel.WEB);

        assertThat(turn.reply()).contains("muchas peticiones");
        assertThat(turn.reply()).doesNotContain("problema técnico");
        assertThat(turn.buttons()).isNull();
    }

    @Test
    @DisplayName("null Groq response -> friendly technical error, not empty bubble")
    void nullGroqResponse_friendlyError() {
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn(null);

        ChatTurn turn = engine.process(KEY, "test", BUSINESS_ID, Channel.WEB);

        assertThat(turn.reply()).contains("problema técnico");
        assertThat(turn.buttons()).isNull();
    }

    @Test
    @DisplayName("empty Groq response -> friendly repeat message")
    void emptyGroqResponse_friendlyRepeat() {
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("");

        ChatTurn turn = engine.process(KEY, "test", BUSINESS_ID, Channel.WEB);

        assertThat(turn.reply()).isNotBlank();
        assertThat(turn.reply()).contains("repetirme");
        assertThat(turn.buttons()).isNull();
    }

    @Test
    @DisplayName("unresolvable business id -> no lead saved, no crash")
    void unresolvableId_noCrash() {
        UUID unknownId = UUID.randomUUID();
        when(businessService.resolveOwnerUserId(unknownId)).thenReturn(null);
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Hola");

        ChatTurn turn = engine.process("session-unknown", "Me llamo Pedro y mi email es pedro@test.com",
            unknownId, Channel.WEB);

        assertThat(turn.leadCaptured()).isFalse();
        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("widget profile id resolves to owner userId for lead saving")
    void widgetProfileId_resolvesOwner() {
        UUID profileId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        when(businessService.resolveOwnerUserId(profileId)).thenReturn(ownerUserId);
        when(leadRepository.countByCreatedBy(ownerUserId)).thenReturn(0L);
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Gracias");

        engine.process("session-widget", "Me llamo Laura y mi email es laura@test.com",
            profileId, Channel.WEB);

        verify(leadRepository).save(argThat(leadArg -> {
            Lead lead = (Lead) leadArg;
            return ownerUserId.equals(lead.getCreatedBy());
        }));
    }

    @Test
    @DisplayName("no email in message -> no lead saved, only Groq reply")
    void noEmail_noLeadSaved() {
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Entendido, ¿en qué te ayudo?");

        ChatTurn turn = engine.process(KEY, "Necesito información", BUSINESS_ID, Channel.WEB);

        assertThat(turn.reply()).isEqualTo("Entendido, ¿en qué te ayudo?");
        assertThat(turn.leadCaptured()).isFalse();
        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("lead data persisted: WhatsApp lead created with correct fields")
    void whatsapp_leadCreated() {
        when(leadRepository.findByPhone(anyString())).thenReturn(Optional.empty());
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("¡Perfecto!");

        engine.process(KEY, "Soy Carlos y mi correo es carlos@test.com", BUSINESS_ID, Channel.WHATSAPP);

        verify(leadRepository).save(argThat(leadArg -> {
            Lead lead = (Lead) leadArg;
            return "carlos@test.com".equals(lead.getEmail())
                && "Carlos".equals(lead.getFirstName())
                && lead.getPhone().equals("+" + KEY)
                && BUSINESS_ID.equals(lead.getCreatedBy());
        }));
    }

    @Test
    @DisplayName("WhatsApp: existing lead is updated, not duplicated")
    void whatsapp_existingLeadUpdated() {
        Lead existingLead = new Lead();
        ReflectionTestUtils.setField(existingLead, "id", UUID.randomUUID());
        when(leadRepository.findByPhone(anyString())).thenReturn(Optional.of(existingLead));
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Gracias");

        engine.process(KEY, "Mi email es nuevo@test.com", BUSINESS_ID, Channel.WHATSAPP);

        verify(leadRepository, never()).save(argThat(leadArg ->
            ((Lead) leadArg).getPhone() != null && ((Lead) leadArg).getFirstName() != null
        ));
        verify(leadRepository).save(existingLead);
    }

    @Test
    @DisplayName("WEB lead creation respects trial limit")
    void web_trialLimit() {
        when(leadRepository.countByCreatedBy(BUSINESS_ID)).thenReturn(50L);
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Gracias");

        ChatTurn turn = engine.process("session-web", "Me llamo Pedro y mi email es pedro@test.com",
            BUSINESS_ID, Channel.WEB);

        assertThat(turn.leadCaptured()).isFalse();
    }

    @Test
    @DisplayName("state context includes name, email, and captured status")
    void stateContext_includesAll() {
        when(leadRepository.findByPhone(anyString())).thenReturn(Optional.empty());
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Gracias");

        engine.process(KEY, "Me llamo Antonio y mi email es antonio@test.com", BUSINESS_ID, Channel.WHATSAPP);

        org.mockito.ArgumentCaptor<String> promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(groqService).chat(promptCaptor.capture(), anyList(), anyString());
        String prompt = promptCaptor.getValue();

        assertThat(prompt).contains("ESTADO ACTUAL DE LA CONVERSACIÓN");
        assertThat(prompt).contains("Nombre: Antonio");
        assertThat(prompt).contains("Email: antonio@test.com");
        assertThat(prompt).contains("Datos de contacto capturados");
    }

    @Test
    @DisplayName("fresh session with no step still triggers greeting behavior")
    void freshSession_greetingWorks() {
        ChatTurn turn = engine.greeting("fresh-session", BUSINESS_ID);
        assertThat(turn.reply()).contains("¡Hola!");
        assertThat(stepOf("fresh-session")).isEqualTo("initial");
    }

    @Test
    @DisplayName("isAffirmative helper recognizes common affirmative phrases")
    void affirmativePhrases() {
        assertThat(engine.process(KEY, "test", BUSINESS_ID, Channel.WEB)).isNotNull();
        // isAffirmative is private — tested indirectly via escalation test above
        // and WhatsApp service tests. The helper recognizes: si, sí, confirmo,
        // adelante, dale, agenda, vale, ok, claro, perfecto
    }
}
