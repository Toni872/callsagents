package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.business.service.BusinessService;
import com.callsagents.backend.chatbot.ChatTurn;
import com.callsagents.backend.chatbot.ChatbotEngine;
import com.callsagents.backend.chatbot.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Thin WhatsApp adapter over the shared {@link ChatbotEngine}.
 *
 * <p>The engine returns free-text turns; this service delegates directly
 * to Vonage text messaging. No interactive buttons are sent.
 */
@Component
public class WhatsAppAiChatbotService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppAiChatbotService.class);

    private final GroqService groqService;
    private final VonageMessageService vonageMessageService;
    private final BusinessService businessService;
    private final ChatbotEngine engine;

    public WhatsAppAiChatbotService(GroqService groqService,
                                    VonageMessageService vonageMessageService,
                                    BusinessService businessService,
                                    ChatbotEngine engine) {
        this.groqService = groqService;
        this.vonageMessageService = vonageMessageService;
        this.businessService = businessService;
        this.engine = engine;
    }

    /**
     * Process an incoming message with AI. Returns the text response,
     * or null if Groq is not configured.
     */
    public String processMessage(String phone, String message) {
        return processMessage(phone, message, null);
    }

    public boolean isGroqConfigured() {
        return groqService.isConfigured();
    }

    public String processMessage(String phone, String message, UUID businessId) {
        if (!groqService.isConfigured()) {
            return null;
        }

        String text = message == null ? "" : message.trim();
        if (isReset(text)) {
            engine.reset(phone);
            sendInteractiveGreeting(phone, businessId);
            return null;
        }

        ChatTurn turn = engine.process(phone, text, businessId, Channel.WHATSAPP);
        return turn.reply();
    }

    public void sendInteractiveGreeting(String phone) {
        sendInteractiveGreeting(phone, null);
    }

    public void sendInteractiveGreeting(String phone, UUID businessId) {
        ChatTurn turn = engine.greeting(phone, businessId);
        vonageMessageService.sendText(phone, turn.reply());
    }

    private static boolean isReset(String text) {
        String lower = text.toLowerCase();
        return lower.equals("reset") || lower.equals("reiniciar");
    }
}
