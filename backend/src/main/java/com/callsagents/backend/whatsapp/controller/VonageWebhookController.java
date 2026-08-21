package com.callsagents.backend.whatsapp.controller;

import com.callsagents.backend.whatsapp.service.VonageMessageService;
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
    private final VonageMessageService vonageMessageService;

    public VonageWebhookController(WhatsAppService whatsAppService,
                                    VonageMessageService vonageMessageService) {
        this.whatsAppService = whatsAppService;
        this.vonageMessageService = vonageMessageService;
    }

    /**
     * Vonage inbound webhook. Receives JSON:
     * { "from": "447700900000", "to": "14157386102", "channel": "whatsapp",
     *   "message_type": "text", "text": "Hello!" }
     *
     * We process the message and send the reply via Vonage API (async).
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

        // Process conversation (same logic as Twilio)
        String reply = whatsAppService.processMessage(from, text);

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
