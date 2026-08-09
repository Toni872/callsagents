package com.callsagents.backend.voice.service;

import com.callsagents.backend.voice.domain.VoiceCallStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Abstraction over a Voice AI provider (Vapi, Retell).
 *
 * One impl per provider. Impls are Spring beans; VoiceCallService
 * dispatches by VoiceProviderType.
 *
 * MVP scope: startCall + getCall are real. updateFromWebhook and
 * normalizeStatus are real. Everything else is optional / placeholder.
 */
public interface VoiceProvider {

    /**
     * Initiate an outbound call. Returns the provider's call id.
     * @param req phone number, optional assistant id / prompt override
     */
    StartCallResult startCall(StartCallRequest req);

    /**
     * Fetch current call state from the provider. Used to reconcile
     * missed webhooks.
     */
    ProviderCallStatus getCall(String providerCallId);

    /** Provider enum value. */
    com.callsagents.backend.voice.domain.VoiceProviderType provider();

    /** True if API key for this provider is configured in env. */
    boolean isConfigured();

    record StartCallRequest(
        String phoneNumber,
        String assistantId,
        Map<String, Object> metadata
    ) {}

    record StartCallResult(
        @JsonProperty("provider_call_id") String providerCallId,
        VoiceCallStatus initialStatus
    ) {}

    record ProviderCallStatus(
        @JsonProperty("status") VoiceCallStatus status,
        @JsonProperty("duration_seconds") Integer durationSeconds,
        @JsonProperty("cost_usd") String costUsd,
        @JsonProperty("transcript") String transcript,
        @JsonProperty("recording_url") String recordingUrl,
        @JsonProperty("error_message") String errorMessage
    ) {}
}
