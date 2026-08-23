package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.whatsapp.config.GroqConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GroqService {

    private static final Logger log = LoggerFactory.getLogger(GroqService.class);

    private final GroqConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GroqService(GroqConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return config.isConfigured();
    }

    /**
     * Send a message to Groq and get an AI response.
     * @param systemPrompt The system prompt defining the chatbot's behavior
     * @param conversationHistory List of previous messages [{role, content}]
     * @param userMessage The current user message
     * @return The AI-generated response, or null if Groq is not configured
     */
    public String chat(String systemPrompt, List<Map<String, String>> conversationHistory, String userMessage) {
        if (!config.isConfigured()) {
            log.warn("Groq not configured — cannot generate AI response");
            return null;
        }

        try {
            // Build messages array
            List<Map<String, String>> messages = new ArrayList<>();

            // System prompt
            messages.add(Map.of("role", "system", "content", systemPrompt));

            // Conversation history
            if (conversationHistory != null) {
                messages.addAll(conversationHistory);
            }

            // Current user message
            messages.add(Map.of("role", "user", "content", userMessage));

            // Build request body
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.getModel());
            body.put("messages", messages);
            body.put("temperature", 0.7);
            body.put("max_tokens", 500);

            // Call Groq API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getApiKey());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                config.getApiUrl(), request, String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Parse response
                Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    log.info("Groq response: {}", content);
                    return content;
                }
            }

            log.error("Groq returned unexpected response: {}", response.getBody());
            return null;

        } catch (Exception e) {
            log.error("Groq API error", e);
            return null;
        }
    }
}
