package com.callsagents.backend.chat;

import com.callsagents.backend.chatbot.ChatTurn;
import com.callsagents.backend.chatbot.ChatbotEngine;
import com.callsagents.backend.chatbot.Channel;
import com.callsagents.backend.whatsapp.service.GroqService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Thin web-widget adapter over the shared {@link ChatbotEngine}.
 *
 * <p>This service no longer runs its own free-text-only flow with Groq: it
 * delegates entirely to the shared engine (same FSM and buttons as WhatsApp),
 * returning the turn's reply + buttons to the frontend.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final GroqService groqService;
    private final ChatbotEngine engine;

    public ChatService(GroqService groqService, ChatbotEngine engine) {
        this.groqService = groqService;
        this.engine = engine;
    }

    public ChatResponse processMessage(String sessionId, String message) {
        return processMessage(sessionId, message, null);
    }

    public ChatResponse processMessage(String sessionId, String message, UUID businessId) {
        if (!groqService.isConfigured()) {
            return new ChatResponse(sessionId,
                "El chat no está disponible ahora mismo. Inténtalo más tarde.", false, null, false);
        }

        ChatTurn turn = engine.process(sessionId, message, businessId, Channel.WEB);
        log.info("Chat [{}]: leadCaptured={} buttons={}",
            sessionId, turn.leadCaptured(), turn.buttons() == null ? 0 : turn.buttons().size());
        return new ChatResponse(sessionId, turn.reply(), turn.leadCaptured(), turn.buttons(), turn.contactForm());
    }

    /**
     * Start a conversation for the widget: returns the engine greeting turn
     * (reply + intent buttons) or a graceful fallback when the backend is off.
     */
    public ChatResponse start(String sessionId, UUID businessId) {
        if (!groqService.isConfigured()) {
            return new ChatResponse(sessionId,
                "El chat no está disponible ahora mismo. Inténtalo más tarde.", false, null, false);
        }
        ChatTurn turn = engine.greeting(sessionId, businessId);
        return new ChatResponse(sessionId, turn.reply(), false, turn.buttons(), false);
    }
}
