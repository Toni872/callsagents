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
     * Vonage inbound webhook. Handles text messages, button replies, and list replies.
     */
    @SuppressWarnings("unchecked")
    @PostMapping
    public ResponseEntity<Void> handleInbound(@RequestBody Map<String, Object> payload) {
        String from = (String) payload.getOrDefault("from", "");
        String channel = (String) payload.getOrDefault("channel", "");
        String messageType = (String) payload.getOrDefault("message_type", "");

        log.info("Vonage inbound: from={} channel={} type={}", from, channel, messageType);
        log.debug("Vonage raw payload: {}", payload);

        if (!"whatsapp".equals(channel) || from.isBlank()) {
            log.warn("Vonage inbound ignored: channel={} from={}", channel, from);
            return ResponseEntity.ok().build();
        }

        // Extract text based on message type
        String text = extractText(payload, messageType);
        log.info("Vonage extracted text: '{}'", text);

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
     * Extract text content from various message types.
     */
    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> payload, String messageType) {
        if ("text".equals(messageType)) {
            return (String) payload.getOrDefault("text", "");
        }

        // Vonage sends button/list replies as message_type="reply" with a "reply" object
        if ("reply".equals(messageType)) {
            Map<String, Object> reply = (Map<String, Object>) payload.get("reply");
            if (reply != null) {
                String id = (String) reply.get("id");
                String title = (String) reply.get("title");
                log.info("Reply received: id={} title={}", id, title);
                return id != null ? id : (title != null ? title : "");
            }
        }

        // Fallback: check interactive block (some Vonage versions)
        if ("interactive".equals(messageType)) {
            Map<String, Object> interactive = (Map<String, Object>) payload.get("interactive");
            if (interactive == null) return "";

            String type = (String) interactive.get("type");

            if ("button_reply".equals(type)) {
                Map<String, Object> buttonReply = (Map<String, Object>) interactive.get("button_reply");
                if (buttonReply != null) {
                    String id = (String) buttonReply.get("id");
                    log.info("Button reply (interactive): id={}", id);
                    return id;
                }
            }

            if ("list_reply".equals(type)) {
                Map<String, Object> listReply = (Map<String, Object>) interactive.get("list_reply");
                if (listReply != null) {
                    String id = (String) listReply.get("id");
                    log.info("List reply (interactive): id={}", id);
                    return id;
                }
            }
        }

        return (String) payload.getOrDefault("text", "");
    }

    /**
     * Vonage status webhook — delivery/read receipts.
     */
    @PostMapping("/status")
    public ResponseEntity<Void> handleStatus(@RequestBody Map<String, Object> payload) {
        log.info("Vonage status: {}", payload);
        return ResponseEntity.ok().build();
    }
}
