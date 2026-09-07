package com.callsagents.backend.chatbot;

import com.callsagents.backend.business.service.BusinessPromptComposer;
import com.callsagents.backend.business.service.BusinessService;
import com.callsagents.backend.escalation.service.EscalationService;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.repository.LeadRepository;
import com.callsagents.backend.voice.service.VoiceCallService;
import com.callsagents.backend.whatsapp.service.GroqService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @Mock VoiceCallService voiceCallService;

    private ChatbotEngine engine;

    private static final String KEY = "34687723287";
    private static final UUID BUSINESS_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new ChatbotEngine(
            groqService, leadRepository,
            businessService, promptComposer, escalationService, voiceCallService
        );
        lenient().when(groqService.isConfigured()).thenReturn(true);
        lenient().when(promptComposer.compose(any())).thenReturn("Eres Naiara de Script9.");
        lenient().when(promptComposer.composeDefault()).thenReturn("Eres Naiara de Script9.");
        // By default the engine treats the identifier as a plain user id (identity mapping).
        lenient().when(businessService.resolveOwnerUserId(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private String stepOf(String key) {
        com.github.benmanes.caffeine.cache.Cache<String, String> stepCache =
            (com.github.benmanes.caffeine.cache.Cache<String, String>)
                ReflectionTestUtils.getField(engine, "conversationStep");
        return stepCache.getIfPresent(key);
    }

    @Test
    @DisplayName("greeting returns reply + 3 intent buttons and sets step=awaiting_intent")
    void greeting_replyAndIntentButtons_awaitingIntent() {
        ChatTurn turn = engine.greeting(KEY, BUSINESS_ID);

        assertThat(turn.reply()).contains("Hola, soy Naiara de Script9.");
        assertThat(turn.buttons()).hasSize(3);
        assertThat(turn.buttons().get(0).id()).isEqualTo("intent_ventas");
        assertThat(turn.buttons().get(1).id()).isEqualTo("intent_soporte");
        assertThat(turn.buttons().get(2).id()).isEqualTo("intent_demo");
        assertThat(stepOf(KEY)).isEqualTo("awaiting_intent");
        assertThat(turn.leadCaptured()).isFalse();
        assertThat(turn.contactForm()).isFalse();
    }

    @Test
    @DisplayName("contactForm=true for intent_ventas and intent_demo, false for intent_soporte and greeting")
    void contactForm_flagPerIntent() {
        ChatTurn greeting = engine.greeting(KEY, BUSINESS_ID);
        assertThat(greeting.contactForm()).isFalse();

        ChatTurn ventas = engine.process(KEY, "intent_ventas", BUSINESS_ID, Channel.WEB);
        assertThat(ventas.contactForm()).isTrue();

        String key2 = "session-contact-2";
        ChatTurn demo = engine.process(key2, "intent_demo", BUSINESS_ID, Channel.WEB);
        assertThat(demo.contactForm()).isTrue();

        String key3 = "session-contact-3";
        ChatTurn soporte = engine.process(key3, "intent_soporte", BUSINESS_ID, Channel.WEB);
        assertThat(soporte.contactForm()).isFalse();
    }

    @Test
    @DisplayName("WHATSAPP full sales flow: email -> timing buttons, timing -> confirmation, confirm_yes escalates once")
    void whatsapp_fullSalesFlow_escalatesOnce() {
        Lead existingLead = new Lead();
        org.springframework.test.util.ReflectionTestUtils.setField(existingLead, "id", UUID.randomUUID());
        when(leadRepository.findByPhone(anyString()))
            .thenReturn(Optional.empty(), Optional.of(existingLead));

        ChatTurn step1 = engine.process(KEY, "intent_ventas", BUSINESS_ID, Channel.WHATSAPP);
        assertThat(step1.reply()).contains("¿Cómo te llamas");
        assertThat(step1.buttons()).isNull();
        assertThat(step1.contactForm()).isTrue();
        assertThat(stepOf(KEY)).isEqualTo("collecting_info");

        ChatTurn step2 = engine.process(KEY, "Antonio y mi email es antohachi@gmail.com", BUSINESS_ID, Channel.WHATSAPP);
        verify(groqService, never()).chat(anyString(), anyList(), anyString());
        assertThat(step2.reply()).isNull();
        assertThat(step2.buttons()).hasSize(3);
        assertThat(step2.buttons().stream().map(ChatButton::id))
            .containsExactly("timing_now", "timing_month", "timing_later");
        assertThat(step2.leadCaptured()).isTrue();
        verify(leadRepository).save(argThat(leadArg -> {
            Lead lead = (Lead) leadArg;
            return "antohachi@gmail.com".equals(lead.getEmail())
                && "Antonio".equals(lead.getFirstName());
        }));

        ChatTurn step3 = engine.process(KEY, "timing_now", BUSINESS_ID, Channel.WHATSAPP);
        assertThat(step3.reply()).contains("¿Confirmas los datos?");
        assertThat(step3.buttons().stream().map(ChatButton::id))
            .containsExactly("confirm_yes", "confirm_no");

        ChatTurn step4 = engine.process(KEY, "confirm_yes", BUSINESS_ID, Channel.WHATSAPP);
        assertThat(step4.reply()).contains("demo de 15 minutos");
        verify(escalationService, times(1)).qualify(any(), any());
    }

    @Test
    @DisplayName("WEB confirm_yes does NOT trigger escalation")
    void web_confirmYes_noEscalation() {
        when(leadRepository.countByCreatedBy(BUSINESS_ID)).thenReturn(0L);

        engine.process(KEY, "intent_ventas", BUSINESS_ID, Channel.WEB);
        engine.process(KEY, "Antonio antohachi@gmail.com", BUSINESS_ID, Channel.WEB);
        engine.process(KEY, "timing_now", BUSINESS_ID, Channel.WEB);

        ChatTurn confirm = engine.process(KEY, "confirm_yes", BUSINESS_ID, Channel.WEB);
        assertThat(confirm.reply()).contains("demo de 15 minutos");
        assertThat(confirm.buttons()).isNull();
        verify(escalationService, never()).qualify(any(), any());
    }

    @Test
    @DisplayName("Bug1: email in collecting_info -> skips Groq, saves lead deterministically, returns timing buttons")
    void emailInCollectingInfo_skipsGroq_savesLead_returnsTimingButtons() {
        when(leadRepository.findByPhone(anyString())).thenReturn(Optional.empty());

        engine.process(KEY, "intent_ventas", BUSINESS_ID, Channel.WHATSAPP);

        ChatTurn turn = engine.process(KEY, "Me llamo Mariana y mi correo es mariana@test.com",
            BUSINESS_ID, Channel.WHATSAPP);

        verify(groqService, never()).chat(anyString(), anyList(), anyString());
        assertThat(turn.reply()).isNull();
        assertThat(turn.buttons()).hasSize(3);
        assertThat(turn.buttons().stream().map(ChatButton::id))
            .containsExactly("timing_now", "timing_month", "timing_later");
        assertThat(turn.leadCaptured()).isTrue();
        assertThat(stepOf(KEY)).isEqualTo("awaiting_timing");
        verify(leadRepository).save(argThat(leadArg -> {
            Lead lead = (Lead) leadArg;
            return "mariana@test.com".equals(lead.getEmail())
                && "Mariana".equals(lead.getFirstName());
        }));
    }

    @Test
    @DisplayName("Bug2: widget sends BusinessProfile.id -> lead is saved with the real owner userId")
    void widgetProfileId_resolvesUserId_savesLeadWithOwner() {
        UUID profileId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        when(businessService.resolveOwnerUserId(profileId)).thenReturn(ownerUserId);
        when(leadRepository.countByCreatedBy(ownerUserId)).thenReturn(0L);

        engine.process("session-widget", "intent_ventas", profileId, Channel.WEB);
        ChatTurn turn = engine.process("session-widget", "Me llamo Laura y mi email es laura@test.com",
            profileId, Channel.WEB);

        assertThat(turn.leadCaptured()).isTrue();
        verify(leadRepository).save(argThat(leadArg -> {
            Lead lead = (Lead) leadArg;
            return ownerUserId.equals(lead.getCreatedBy());
        }));
    }

    @Test
    @DisplayName("Bug2: unknown/unresolvable id -> lead skipped, no FK violation, no crash")
    void unresolvableId_skipsLead_noCrash() {
        UUID unknownId = UUID.randomUUID();
        when(businessService.resolveOwnerUserId(unknownId)).thenReturn(null);

        engine.process("session-unknown", "intent_ventas", unknownId, Channel.WEB);
        ChatTurn turn = engine.process("session-unknown", "Me llamo Pedro y mi email es pedro@test.com",
            unknownId, Channel.WEB);

        assertThat(turn.leadCaptured()).isFalse();
        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("voice offer happens on WHATSAPP but never on WEB")
    void voiceOffer_whatsappOnly_neverOnWeb() {
        engine.process(KEY, "intent_ventas", BUSINESS_ID, Channel.WHATSAPP);
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Perfecto, te ayudo");
        List<ChatButton> offer = null;
        for (int i = 0; i < 8 && offer == null; i++) {
            offer = extractVoiceButtons(engine.process(KEY, "test message " + i, BUSINESS_ID, Channel.WHATSAPP));
        }
        assertThat(offer).isNotNull();
        assertThat(offer.stream().map(ChatButton::id))
            .containsExactly("accept_voice_call", "decline_voice_call");

        String webKey = "session-web-1";
        engine.process(webKey, "intent_ventas", BUSINESS_ID, Channel.WEB);
        for (int i = 0; i < 8; i++) {
            ChatTurn web = engine.process(webKey, "test message " + i, BUSINESS_ID, Channel.WEB);
            assertThat(extractVoiceButtons(web)).isNull();
        }
    }

    @Test
    @DisplayName("Bug2: button turns are recorded in history visible to Groq")
    void buttonTurns_recordedInHistory_visibleToGroq() {
        when(leadRepository.findByPhone(anyString())).thenReturn(Optional.empty());
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("Entendido.");

        engine.process(KEY, "intent_ventas", BUSINESS_ID, Channel.WHATSAPP);
        engine.process(KEY, "Antonio antohachi@gmail.com", BUSINESS_ID, Channel.WHATSAPP);
        engine.process(KEY, "timing_now", BUSINESS_ID, Channel.WHATSAPP);
        engine.process(KEY, "confirm_yes", BUSINESS_ID, Channel.WHATSAPP);

        ChatTurn turn = engine.process(KEY, "¿Me podés enviar los detalles?", BUSINESS_ID, Channel.WHATSAPP);
        assertThat(turn).isNotNull();

        ArgumentCaptor<List<Map<String, String>>> historyCaptor =
            ArgumentCaptor.forClass((Class) List.class);
        verify(groqService).chat(anyString(), historyCaptor.capture(), anyString());
        List<Map<String, String>> history = historyCaptor.getValue();

        // The user's timing choice appears as a "user" turn
        assertThat(history).anyMatch(e -> "user".equals(e.get("role"))
            && "timing_now".equals(e.get("content")));
        // The confirmation summary body (shown to the user) appears as the assistant reply
        assertThat(history).anyMatch(e -> "assistant".equals(e.get("role"))
            && e.get("content").contains("¿Confirmas los datos?"));
        // The post-confirmation reply (shown to the user) appears as the assistant reply
        assertThat(history).anyMatch(e -> "assistant".equals(e.get("role"))
            && e.get("content").contains("demo de 15 minutos"));
    }

    @Test
    @DisplayName("Bug4: empty Groq response -> friendly repeat message, not empty bubble")
    void emptyGroqResponse_friendlyRepeatMessage() {
        engine.process(KEY, "intent_ventas", BUSINESS_ID, Channel.WEB);
        when(groqService.chat(anyString(), anyList(), anyString())).thenReturn("");
        ChatTurn turn = engine.process(KEY, "hola necesito ayuda", BUSINESS_ID, Channel.WEB);

        assertThat(turn.reply()).isNotBlank();
        assertThat(turn.reply()).contains("repetirme");
        assertThat(turn.reply()).doesNotContain("problema técnico");
    }

    @Test
    @DisplayName("Bug5: 429 sentinel -> rate limit message, not generic technical error")
    void rateLimitSentinel_rateLimitMessage() {
        engine.process(KEY, "intent_ventas", BUSINESS_ID, Channel.WEB);
        when(groqService.chat(anyString(), anyList(), anyString()))
            .thenReturn(GroqService.RATE_LIMITED_SENTINEL);
        ChatTurn turn = engine.process(KEY, "hola necesito ayuda", BUSINESS_ID, Channel.WEB);

        assertThat(turn.reply()).contains("muchas peticiones");
        assertThat(turn.reply()).doesNotContain("problema técnico");
    }

    private static List<ChatButton> extractVoiceButtons(ChatTurn turn) {
        if (turn == null || turn.buttons() == null) return null;
        return turn.buttons().stream()
            .filter(b -> "accept_voice_call".equals(b.id()) || "decline_voice_call".equals(b.id()))
            .toList();
    }
}
