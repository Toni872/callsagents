package com.callsagents.backend.chat;

import com.callsagents.backend.chatbot.ChatTurn;
import com.callsagents.backend.chatbot.ChatbotEngine;
import com.callsagents.backend.chatbot.Channel;
import com.callsagents.backend.whatsapp.service.GroqService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock GroqService groqService;
    @Mock ChatbotEngine engine;

    private ChatService service;

    private static final String SESSION = "session-1";
    private static final UUID BUSINESS_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ChatService(groqService, engine);
        when(groqService.isConfigured()).thenReturn(true);
    }

    @Test
    @DisplayName("start() exposes the engine free-text greeting with no buttons")
    void start_exposesGreeting() {
        when(engine.greeting(SESSION, BUSINESS_ID)).thenReturn(
            new ChatTurn("¡Hola! Soy Naiara de Script9. ¿En qué puedo ayudarte?", null, false, false));

        ChatResponse res = service.start(SESSION, BUSINESS_ID);

        assertThat(res.reply()).contains("¡Hola! Soy Naiara de Script9.");
        assertThat(res.buttons()).isNull();
        assertThat(res.leadCaptured()).isFalse();
        assertThat(res.contactForm()).isFalse();
    }

    @Test
    @DisplayName("processMessage delegates to the engine and propagates free-text/leadCaptured")
    void processMessage_delegatesAndPropagates() {
        when(engine.process(SESSION, "mensaje", BUSINESS_ID, Channel.WEB)).thenReturn(
            new ChatTurn("Entendido, ¿en qué te ayudo?", null, true, false));

        ChatResponse res = service.processMessage(SESSION, "mensaje", BUSINESS_ID);

        assertThat(res.reply()).isEqualTo("Entendido, ¿en qué te ayudo?");
        assertThat(res.buttons()).isNull();
        assertThat(res.leadCaptured()).isTrue();
        assertThat(res.contactForm()).isFalse();
    }
}
