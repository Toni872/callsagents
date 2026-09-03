package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.whatsapp.config.GroqConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GroqService {

    private static final Logger log = LoggerFactory.getLogger(GroqService.class);

    /**
     * Structured extraction result from chatStructured().
     */
    public record LeadExtraction(String response, LeadData lead) {}

    /**
     * Lead data fields extracted by the structured-output model.
     * All fields are nullable — null means the model could not determine the value.
     */
    public record LeadData(String name, String email, String service, String timing) {
        public boolean isEmpty() {
            return isBlank(name) && isBlank(email) && isBlank(service) && isBlank(timing);
        }

        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }
    }

    private final GroqConfig config;
    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GroqService(GroqConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;

        // Configure RestTemplate with timeouts to prevent thread starvation
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);  // 5s connect
        factory.setReadTimeout(15000);    // 15s read
        this.restTemplate = new RestTemplate(factory);
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
                    // Strip thinking tags from response
                    content = stripThinkingTags(content);
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

    /**
     * Send a message to Groq and get a structured JSON response conforming to the lead extraction schema.
     * On HTTP 400 (model does not support structured output), logs an error and falls back to the
     * unstructured chat() method so the caller degrades gracefully instead of failing hard.
     *
     * @param systemPrompt The system prompt defining the chatbot's behavior
     * @param conversationHistory List of previous messages [{role, content}]
     * @param userMessage The current user message
     * @return LeadExtraction with response text and optional lead data, or null if Groq is not configured
     */
    public LeadExtraction chatStructured(String systemPrompt, List<Map<String, String>> conversationHistory, String userMessage) {
        if (!config.isConfigured()) {
            log.warn("Groq not configured — cannot generate structured response");
            return null;
        }

        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            if (conversationHistory != null) {
                messages.addAll(conversationHistory);
            }
            messages.add(Map.of("role", "user", "content", userMessage));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.getStructuredModel());
            body.put("messages", messages);
            body.put("temperature", 0.3);
            body.put("max_tokens", 500);

            // Structured output: json_schema with strict mode
            Map<String, Object> schema = buildLeadExtractionSchema();
            Map<String, Object> jsonSchema = Map.of(
                "name", "lead_extraction",
                "strict", true,
                "schema", schema
            );
            body.put("response_format", Map.of("type", "json_schema", "json_schema", jsonSchema));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getApiKey());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                config.getApiUrl(), request, String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    content = stripThinkingTags(content);
                    log.info("Groq structured response: {}", content);
                    return objectMapper.readValue(content, LeadExtraction.class);
                }
            }

            log.error("Groq returned unexpected structured response: {}", response.getBody());
            return null;

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 400) {
                log.error("chatStructured not supported by model, falling back to chat(): {}", e.getMessage());
                String fallback = chat(systemPrompt, conversationHistory, userMessage);
                // Caller interprets as unstructured — return with response only, no lead
                return fallback != null ? new LeadExtraction(fallback, null) : null;
            }
            log.error("Groq structured API error", e);
            return null;
        } catch (Exception e) {
            log.error("Groq structured API error", e);
            return null;
        }
    }

    /**
     * Build the strict JSON schema for lead extraction.
     * All fields required, no additional properties, nullable fields via type arrays.
     */
    private Map<String, Object> buildLeadExtractionSchema() {
        Map<String, Object> nameField = Map.of("type", new String[]{"string", "null"});
        Map<String, Object> emailField = Map.of("type", new String[]{"string", "null"});
        Map<String, Object> serviceField = Map.of("type", new String[]{"string", "null"});
        Map<String, Object> timingField = Map.of("type", new String[]{"string", "null"});

        Map<String, Object> leadProperties = new LinkedHashMap<>();
        leadProperties.put("name", nameField);
        leadProperties.put("email", emailField);
        leadProperties.put("service", serviceField);
        leadProperties.put("timing", timingField);

        Map<String, Object> leadObject = new LinkedHashMap<>();
        leadObject.put("type", "object");
        leadObject.put("properties", leadProperties);
        leadObject.put("required", List.of("name", "email", "service", "timing"));
        leadObject.put("additionalProperties", false);

        Map<String, Object> rootProperties = new LinkedHashMap<>();
        rootProperties.put("response", Map.of("type", "string"));
        rootProperties.put("lead", Map.of("type", new String[]{"object", "null"}, "properties", leadProperties, "required", List.of("name", "email", "service", "timing"), "additionalProperties", false));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("properties", rootProperties);
        root.put("required", List.of("response", "lead"));
        root.put("additionalProperties", false);

        return root;
    }

    /**
     * Strip thinking/reasoning tags from model responses.
     * Qwen and similar models wrap reasoning in ... tags.
     */
    private String stripThinkingTags(String content) {
        if (content == null) return null;
        // Remove ... blocks
        String result = content.replaceAll("(?s)<think>.*?</think>", "").trim();
        // If nothing left after stripping, return original (might not have had tags)
        return result.isEmpty() ? content.trim() : result;
    }
}
