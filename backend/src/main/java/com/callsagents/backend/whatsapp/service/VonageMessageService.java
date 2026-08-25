package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.whatsapp.config.VonageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class VonageMessageService {

    private static final Logger log = LoggerFactory.getLogger(VonageMessageService.class);

    private final VonageConfig config;
    private final RestTemplate restTemplate;

    public VonageMessageService(VonageConfig config) {
        this.config = config;

        // Configure RestTemplate with timeouts to prevent thread starvation
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);  // 5s connect
        factory.setReadTimeout(10000);    // 10s read
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Send a plain text WhatsApp message via Vonage.
     */
    public boolean sendText(String to, String text) {
        Map<String, Object> body = Map.of(
            "from", config.getSandboxNumber(),
            "to", to,
            "channel", "whatsapp",
            "message_type", "text",
            "text", text
        );
        return send(body, to);
    }

    /**
     * Send reply buttons (up to 3 tappable buttons).
     * @param bodyText The message body
     * @param buttons List of [id, title] pairs. Max 3. Title max 20 chars.
     */
    public boolean sendButtons(String to, String bodyText, List<String[]> buttons) {
        List<Map<String, Object>> buttonObjects = new ArrayList<>();
        for (String[] btn : buttons) {
            buttonObjects.add(Map.of(
                "type", "reply",
                "reply", Map.of("id", btn[0], "title", btn[1])
            ));
        }

        Map<String, Object> body = Map.of(
            "from", config.getSandboxNumber(),
            "to", to,
            "channel", "whatsapp",
            "message_type", "custom",
            "custom", Map.of(
                "type", "interactive",
                "interactive", Map.of(
                    "type", "button",
                    "body", Map.of("text", bodyText),
                    "action", Map.of("buttons", buttonObjects)
                )
            )
        );
        return send(body, to);
    }

    /**
     * Send a list message (up to 10 options in sections).
     * @param headerText Optional header (max 60 chars)
     * @param bodyText The message body (max 1024 chars)
     * @param buttonText Button text to open the list (max 20 chars)
     * @param sections List of sections, each with title and rows [id, title, description]
     */
    public boolean sendList(String to, String headerText, String bodyText, String buttonText,
                            List<Map<String, List<String[]>>> sections) {
        List<Map<String, Object>> sectionObjects = new ArrayList<>();
        for (Map<String, List<String[]>> section : sections) {
            String title = section.keySet().iterator().next();
            List<String[]> rows = section.get(title);
            List<Map<String, String>> rowObjects = new ArrayList<>();
            for (String[] row : rows) {
                Map<String, String> rowMap = new LinkedHashMap<>();
                rowMap.put("id", row[0]);
                rowMap.put("title", row[1]);
                if (row.length > 2) rowMap.put("description", row[2]);
                rowObjects.add(rowMap);
            }
            sectionObjects.add(Map.of("title", title, "rows", rowObjects));
        }

        Map<String, Object> interactive = new LinkedHashMap<>();
        interactive.put("type", "list");
        if (headerText != null) {
            interactive.put("header", Map.of("type", "text", "text", headerText));
        }
        interactive.put("body", Map.of("text", bodyText));
        interactive.put("action", Map.of("button", buttonText, "sections", sectionObjects));

        Map<String, Object> body = Map.of(
            "from", config.getSandboxNumber(),
            "to", to,
            "channel", "whatsapp",
            "message_type", "custom",
            "custom", Map.of(
                "type", "interactive",
                "interactive", interactive
            )
        );
        return send(body, to);
    }

    private boolean send(Map<String, Object> body, String to) {
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
