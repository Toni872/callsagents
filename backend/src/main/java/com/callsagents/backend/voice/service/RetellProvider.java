package com.callsagents.backend.voice.service;

import com.callsagents.backend.voice.domain.VoiceCallStatus;
import com.callsagents.backend.voice.domain.VoiceProviderType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Retell AI provider — PLACEHOLDER (same shape as VapiProvider).
 *
 * Retell's API is at https://api.retellai.com. Endpoints:
 *   POST /v2/create-phone-call  — initiate outbound
 *   GET  /v2/get-call/{id}      — fetch status
 *
 * To complete: implement startCall / getCall with Retell's auth and JSON shapes.
 * See https://docs.retellai.com for details.
 */
@Component
public class RetellProvider implements VoiceProvider {

    @Value("${app.voice.retell.api-key:}")
    private String apiKey;

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public VoiceProviderType provider() {
        return VoiceProviderType.RETELL;
    }

    @Override
    public StartCallResult startCall(StartCallRequest req) {
        throw new UnsupportedOperationException(
            "Retell provider is scaffolded but not yet implemented.");
    }

    @Override
    public ProviderCallStatus getCall(String providerCallId) {
        throw new UnsupportedOperationException("Retell getCall not implemented");
    }
}
