package com.callsagents.backend.whatsapp.controller;

import com.callsagents.backend.whatsapp.service.VonageMessageService;
import com.callsagents.backend.whatsapp.service.WhatsAppAiChatbotService;
import com.callsagents.backend.whatsapp.service.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhooks/vonage")
public class VonageWebhookController {

    private static final Logger log = LoggerFactory.getLogger(VonageWebhookController.class);

    private final WhatsAppService whatsAppService;
    private final WhatsAppAiChatbotService aiChatbotService;
    private final VonageMessageService vonageMessageService;

    public VonageWebhookController(WhatsAppService whatsAppService,
                                    WhatsAppAiChatbotService aiChatbotService,
                                    VonageMessageService vonageMessageService) {
        this.whatsAppService = whatsAppService;
        this.aiChatbotService = aiChatbotService;
        this.vonageMessageService = vonageMessageService;
    }

    /**
     * Vonage inbound webhook. Receives JSON:
     * { "from": "447700900000", "to": "14157386102", "channel": "whatsapp",
     *   "message_type": "text", "text": "Hello!" }
     *
     * We try AI first (Groq), fall back to basic state machine if Groq is not configured.
     */
    @PostMapping
    public ResponseEntity<Void> handleInbound(@RequestBody Map<String, Object> payload) {
        String from = (String) payload.getOrDefault("from", "");
        String text = (String) payload.getOrDefault("text", "");
        String channel = (String) payload.getOrDefault("channel", "");

        log.info("Vonage inbound: from={} channel={} text='{}'", from, channel, text);

        if (!"whatsapp".equals(channel) || from.isBlank()) {
            log.warn("Vonage inbound ignored: channel={} from={}", channel, from);
            return ResponseEntity.ok().build();
        }

        // Try AI chatbot first
        String reply = aiChatbotService.processMessage(from, text);

        // Fall back to basic state machine if AI is not configured
        if (reply == null) {
            reply = whatsAppService.processMessage(from, text);
        }

        // Send reply via Vonage API
        boolean sent = vonageMessageService.sendText(from, reply);
        if (!sent) {
            log.error("Failed to send Vonage reply to {}", from);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Vonage status webhook — delivery/read receipts.
     * For MVP we just log it.
     */
    @PostMapping("/status")
    public ResponseEntity<Void> handleStatus(@RequestBody Map<String, Object> payload) {
        log.info("Vonage status: {}", payload);
        return ResponseEntity.ok().build();
    }
}
