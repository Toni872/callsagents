package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.business.entity.BusinessProfile;
import com.callsagents.backend.business.service.BusinessService;
import com.callsagents.backend.chatbot.ChatButton;
import com.callsagents.backend.chatbot.ChatTurn;
import com.callsagents.backend.chatbot.ChatbotEngine;
import com.callsagents.backend.chatbot.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Thin WhatsApp adapter over the shared {@link ChatbotEngine}.
 *
 * <p>This service no longer contains the FSM; it only translates engine turns
 * into Vonage interactive messages (buttons/text) and exposes the same public
 * API it always has, so callers (Webhooks, etc.) are unaffected.
 */
@Component
public class WhatsAppAiChatbotService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppAiChatbotService.class);

    private final GroqService groqService;
    private final VonageMessageService vonageMessageService;
    private final BusinessService businessService;
    private final ChatbotEngine engine;

    // Button turns produced by the engine that carry no reply text carry the
    // timing prompt, so the Vonage message body falls back to this.
    private static final String DEFAULT_BUTTON_BODY = "¿Cuándo te gustaría empezar?";

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
     * Process an incoming message with AI + interactive messages.
     * Returns the text response, or null if Groq is not configured or buttons were sent.
     */
    public String processMessage(String phone, String message) {
        return processMessage(phone, message, null);
    }

    /**
     * Whether the Groq AI backend is configured. Lets callers distinguish
     * "chatbot unavailable (fall back to the basic state machine)" from
     * "chatbot handled the message and already sent buttons/greeting"
     * (processMessage returns null in both cases).
     */
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

        // Interactive buttons were produced — send them via Vonage and return null
        // so no plain AI text is sent on top (single-message rule).
        if (turn.buttons() != null && !turn.buttons().isEmpty()) {
            String body = turn.reply() != null ? turn.reply() : DEFAULT_BUTTON_BODY;
            vonageMessageService.sendButtons(phone, body, toVonageButtons(turn.buttons()));
            return null;
        }

        if (turn.reply() != null) {
            return turn.reply();
        }

        return null;
    }

    /**
     * Send the initial greeting with interactive buttons.
     */
    public void sendInteractiveGreeting(String phone) {
        sendInteractiveGreeting(phone, null);
    }

    public void sendInteractiveGreeting(String phone, UUID businessId) {
        ChatTurn turn = engine.greeting(phone, businessId);
        boolean sent = vonageMessageService.sendButtons(
            phone, turn.reply(), toVonageButtons(turn.buttons())
        );
        if (!sent) {
            // Fallback to text
            vonageMessageService.sendText(phone, buildGreetingFallback(phone, businessId));
        }
    }

    private String buildGreetingFallback(String phone, UUID businessId) {
        String botName = "Naiara";
        String companyName = "Script9";
        if (businessId != null) {
            BusinessProfile profile = businessService.getProfileEntityByUserId(businessId);
            if (profile != null) {
                if (profile.getBotName() != null && !profile.getBotName().isBlank()) {
                    botName = profile.getBotName();
                }
                if (profile.getCompanyName() != null && !profile.getCompanyName().isBlank()) {
                    companyName = profile.getCompanyName();
                }
            }
        }
        return String.format("¡Hola! Soy %s de %s. ¿En qué puedo ayudarte?", botName, companyName);
    }

    private static boolean isReset(String text) {
        String lower = text.toLowerCase();
        return lower.equals("hola") || lower.equals("inicio") || lower.equals("reset") || lower.equals("reiniciar");
    }

    private static List<String[]> toVonageButtons(List<ChatButton> buttons) {
        List<String[]> out = new ArrayList<>();
        for (ChatButton b : buttons) {
            out.add(new String[]{b.id(), b.label()});
        }
        return out;
    }
}
