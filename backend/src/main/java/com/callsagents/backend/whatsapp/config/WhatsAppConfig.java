package com.callsagents.backend.whatsapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppConfig {

    private final String accountSid;
    private final String authToken;
    private final String whatsappFrom;
    private final String whatsappTo;

    public WhatsAppConfig(
            @Value("${app.twilio.account-sid:}") String accountSid,
            @Value("${app.twilio.auth-token:}") String authToken,
            @Value("${app.twilio.whatsapp.from:}") String whatsappFrom,
            @Value("${app.twilio.whatsapp.to:}") String whatsappTo) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.whatsappFrom = whatsappFrom;
        this.whatsappTo = whatsappTo;
    }

    public boolean isConfigured() {
        return accountSid != null && !accountSid.isBlank()
            && authToken != null && !authToken.isBlank();
    }

    public String accountSid() { return accountSid; }
    public String authToken() { return authToken; }
    public String whatsappFrom() { return whatsappFrom; }
    public String whatsappTo() { return whatsappTo; }
}
