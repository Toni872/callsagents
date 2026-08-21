package com.callsagents.backend.whatsapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VonageConfig {

    @Value("${vonage.api.key:}")
    private String apiKey;

    @Value("${vonage.api.secret:}")
    private String apiSecret;

    @Value("${vonage.sandbox.number:}")
    private String sandboxNumber;

    @Value("${vonage.sandbox.url:https://messages-sandbox.nexmo.com/v1/messages}")
    private String sandboxUrl;

    public String getApiKey() { return apiKey; }
    public String getApiSecret() { return apiSecret; }
    public String getSandboxNumber() { return sandboxNumber; }
    public String getSandboxUrl() { return sandboxUrl; }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
            && apiSecret != null && !apiSecret.isBlank();
    }
}
