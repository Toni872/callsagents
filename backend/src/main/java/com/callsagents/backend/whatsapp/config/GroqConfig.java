package com.callsagents.backend.whatsapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GroqConfig {

    @Value("${groq.api.key:}")
    private String apiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String apiUrl;

    @Value("${groq.model:openai/gpt-oss-20b}")
    private String model;

    @Value("${groq.model.structured:${groq.model:openai/gpt-oss-20b}}")
    private String structuredModel;

    public String getApiKey() { return apiKey; }
    public String getApiUrl() { return apiUrl; }
    public String getModel() { return model; }
    public String getStructuredModel() { return structuredModel; }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
