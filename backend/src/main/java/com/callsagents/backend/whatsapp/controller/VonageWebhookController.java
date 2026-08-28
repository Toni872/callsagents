package com.callsagents.backend.whatsapp.controller;

import com.callsagents.backend.business.entity.BusinessProfile;
import com.callsagents.backend.business.service.BusinessService;
import com.callsagents.backend.escalation.service.EscalationService;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.repository.LeadRepository;
import com.callsagents.backend.whatsapp.service.VonageMessageService;
import com.callsagents.backend.whatsapp.service.VonageWebhookValidator;
import com.callsagents.backend.whatsapp.service.WhatsAppAiChatbotService;
import com.callsagents.backend.whatsapp.service.WhatsAppService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/webhooks/vonage")
public class VonageWebhookController {

    private static final Logger log = LoggerFactory.getLogger(VonageWebhookController.class);

    private final WhatsAppService whatsAppService;
    private final WhatsAppAiChatbotService aiChatbotService;
    private final VonageMessageService vonageMessageService;
    private final BusinessService businessService;
    private final EscalationService escalationService;
    private final LeadRepository leadRepository;
    private final VonageWebhookValidator webhookValidator;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public VonageWebhookController(WhatsAppService whatsAppService,
                                    WhatsAppAiChatbotService aiChatbotService,
                                    VonageMessageService vonageMessageService,
                                    BusinessService businessService,
                                    EscalationService escalationService,
                                    LeadRepository leadRepository,
                                    VonageWebhookValidator webhookValidator) {
        this.whatsAppService = whatsAppService;
        this.aiChatbotService = aiChatbotService;
        this.vonageMessageService = vonageMessageService;
        this.businessService = businessService;
        this.escalationService = escalationService;
        this.leadRepository = leadRepository;
        this.webhookValidator = webhookValidator;
    }

    /**
     * Vonage inbound webhook. Handles text messages, button replies, and list replies.
     * The raw body is verified against X-Vonage-Signature (HMAC-SHA256) before
     * processing. Invalid signatures are rejected with 401 and never processed.
     */
    @SuppressWarnings("unchecked")
    @PostMapping
    public ResponseEntity<Void> handleInbound(
            @RequestHeader(value = "X-Vonage-Signature", required = false) String xVonageSignature,
            HttpServletRequest request) throws java.io.IOException {
        byte[] rawBody = request.getInputStream().readAllBytes();

        if (!webhookValidator.verify(rawBody, xVonageSignature)) {
            log.warn("Vonage webhook rejected: invalid X-Vonage-Signature");
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> payload = mapper.readValue(rawBody, Map.class);
        String from = (String) payload.getOrDefault("from", "");
        String to = (String) payload.getOrDefault("to", "");
        String channel = (String) payload.getOrDefault("channel", "");
        String messageType = (String) payload.getOrDefault("message_type", "");

        log.info("Vonage inbound: from={} to={} channel={} type={}", from, to, channel, messageType);
        log.debug("Vonage raw payload: {}", payload);

        if (!"whatsapp".equals(channel) || from.isBlank()) {
            log.warn("Vonage inbound ignored: channel={} from={}", channel, from);
            return ResponseEntity.ok().build();
        }

        // A reply from an existing lead stops any active escalation (no voice call).
        // lookup by E.164 (with "+") to match how leads are stored.
        String fromE164 = from.startsWith("+") ? from : "+" + from;
        findLeadByPhone(fromE164).ifPresent(lead -> escalationService.handleReply(lead.getId()));

        // Extract text based on message type
        String text = extractText(payload, messageType);
        log.info("Vonage extracted text: '{}'", text);

        // Try AI chatbot first — resolve business profile from "to" number
        UUID businessId = null;
        if (!to.isBlank()) {
            BusinessProfile profile = businessService.getProfileEntityByWhatsappNumber(to);
            if (profile != null) {
                businessId = profile.getUser().getId();
            }
        }
        String reply = aiChatbotService.processMessage(from, text, businessId);

        // Only fall back to the basic state machine when Groq is genuinely
        // unavailable. When the chatbot IS configured but returns null it has
        // already sent interactive buttons/greeting — falling through here
        // caused a duplicate (and failing) second send. Do NOT re-send.
        if (reply == null && !aiChatbotService.isGroqConfigured()) {
            reply = whatsAppService.processMessage(from, text);
        }

        // Send reply via Vonage API (only when there is actually text to send)
        if (reply != null) {
            boolean sent = vonageMessageService.sendText(from, reply);
            if (!sent) {
                log.error("Failed to send Vonage reply to {}", from);
            }
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

    /**
     * Find a lead by phone, trying E.164 ("+") first then the raw value as a
     * fallback for Vonage's occasionally inconsistent number formatting.
     */
    private Optional<Lead> findLeadByPhone(String phoneE164) {
        Optional<Lead> lead = leadRepository.findByPhone(phoneE164);
        if (lead.isPresent()) {
            return lead;
        }
        String raw = phoneE164.startsWith("+") ? phoneE164.substring(1) : phoneE164;
        return leadRepository.findByPhone(raw);
    }
}
