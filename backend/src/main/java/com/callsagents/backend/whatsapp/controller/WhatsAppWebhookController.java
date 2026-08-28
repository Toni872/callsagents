package com.callsagents.backend.whatsapp.controller;

import com.callsagents.backend.whatsapp.service.TwilioWebhookValidator;
import com.callsagents.backend.whatsapp.service.WhatsAppService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppService whatsAppService;
    private final TwilioWebhookValidator webhookValidator;

    public WhatsAppWebhookController(WhatsAppService whatsAppService,
                                     TwilioWebhookValidator webhookValidator) {
        this.whatsAppService = whatsAppService;
        this.webhookValidator = webhookValidator;
    }

    /**
     * Twilio verification endpoint. When you configure the webhook in Twilio Console,
     * Twilio sends a GET request with a `challenge` parameter. We echo it back.
     */
    @GetMapping
    public ResponseEntity<String> verify(@RequestParam("challenge") String challenge) {
        log.info("WhatsApp webhook verification: challenge={}", challenge);
        return ResponseEntity.ok(challenge);
    }

    /**
     * Incoming WhatsApp messages from Twilio.
     * Twilio sends form-encoded data: Body, From, To, NumMedia, etc.
     * We respond with TwiML XML.
     * The X-Twilio-Signature header is verified before processing; invalid
     * signatures are rejected with 401 and never processed.
     */
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> handleIncoming(
            @RequestParam("From") String from,
            @RequestParam("Body") String body,
            @RequestParam(value = "NumMedia", defaultValue = "0") String numMedia,
            @RequestHeader(value = "X-Twilio-Signature", required = false) String xTwilioSignature,
            @RequestParam Map<String, String> allParams,
            HttpServletRequest request) {

        if (!webhookValidator.verify(xTwilioSignature, request, allParams)) {
            log.warn("Twilio webhook rejected: invalid X-Twilio-Signature from={}", from);
            return ResponseEntity.status(401).build();
        }

        log.info("WhatsApp incoming: from={} body='{}' media={}", from, body, numMedia);

        String reply = whatsAppService.processMessage(from, body);

        // Return TwiML XML
        String twiml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<Response>\n"
            + "  <Message>" + escapeXml(reply) + "</Message>\n"
            + "</Response>";

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_XML)
            .body(twiml);
    }

    /**
     * Status callback — Twilio POSTs delivery/read status here.
     * For MVP we just log it.
     */
    @PostMapping(value = "/status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> handleStatus(@RequestParam Map<String, String> params) {
        log.info("WhatsApp status callback: {}", params);
        return ResponseEntity.ok().build();
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
