package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.whatsapp.config.VonageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class VonageMessageService {

    private static final Logger log = LoggerFactory.getLogger(VonageMessageService.class);

    private final VonageConfig config;
    private final RestTemplate restTemplate;

    public VonageMessageService(VonageConfig config) {
        this.config = config;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Send a WhatsApp message via Vonage Messages API Sandbox.
     * Returns true if sent successfully.
     */
    public boolean sendText(String to, String text) {
        if (!config.isConfigured()) {
            log.warn("Vonage not configured — cannot send message to {}", to);
            return false;
        }

        try {
            String authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (config.getApiKey() + ":" + config.getApiSecret()).getBytes(StandardCharsets.UTF_8)
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authHeader);

            Map<String, Object> body = Map.of(
                "from", config.getSandboxNumber(),
                "to", to,
                "channel", "whatsapp",
                "message_type", "text",
                "text", text
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                config.getSandboxUrl(), request, String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Vonage message sent: to={} status={}", to, response.getStatusCode());
                return true;
            } else {
                log.error("Vonage message failed: to={} status={} body={}", to, response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("Vonage message error: to={}", to, e);
            return false;
        }
    }
}
