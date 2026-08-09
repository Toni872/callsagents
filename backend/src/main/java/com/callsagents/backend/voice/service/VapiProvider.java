package com.callsagents.backend.voice.service;

import com.callsagents.backend.voice.domain.VoiceCallStatus;
import com.callsagents.backend.voice.domain.VoiceProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Vapi provider — https://api.vapi.ai
 *
 * Endpoints used:
 *   POST  /call                — initiate an outbound call
 *   GET   /call/{id}           — fetch call status
 *
 * Env vars:
 *   VAPI_API_KEY         — required to actually place calls
 *   VAPI_ASSISTANT_ID    — default assistant to use
 *   VAPI_PHONE_NUMBER_ID — Vapi phone number id to dial from
 *
 * Without these, startCall returns a "not configured" error.
 */
@Component
public class VapiProvider implements VoiceProvider {

    private static final Logger log = LoggerFactory.getLogger(VapiProvider.class);

    private static final String BASE = "https://api.vapi.ai";

    @Value("${app.voice.vapi.api-key:}")
    private String apiKey;

    @Value("${app.voice.vapi.assistant-id:}")
    private String defaultAssistantId;

    @Value("${app.voice.vapi.phone-number-id:}")
    private String defaultPhoneNumberId;

    @Value("${app.voice.vapi.webhook-url:http://localhost:8080/api/voice/webhook/vapi}")
    private String webhookUrl;

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public VoiceProviderType provider() {
        return VoiceProviderType.VAPI;
    }

    @Override
    public StartCallResult startCall(StartCallRequest req) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                "Vapi is not configured. Set VAPI_API_KEY in .env (see RUNBOOK).");
        }
        try {
            String assistantId = req.assistantId() != null ? req.assistantId() : defaultAssistantId;
            String phoneNumberId = defaultPhoneNumberId;
            if (assistantId == null || assistantId.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
                throw new IllegalStateException(
                    "Vapi requires VAPI_ASSISTANT_ID and VAPI_PHONE_NUMBER_ID env vars.");
            }
            String body = mapper.writeValueAsString(Map.of(
                "assistantId", assistantId,
                "phoneNumberId", phoneNumberId,
                "customer", Map.of("number", req.phoneNumber()),
                "metadata", req.metadata() != null ? req.metadata() : Map.of()
            ));
            HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/call"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Vapi startCall failed: HTTP " + resp.statusCode() + " — " + resp.body());
            }
            JsonNode json = mapper.readTree(resp.body());
            String callId = json.path("id").asText();
            // Initial status from Vapi's response
            String vapiStatus = json.path("status").asText("queued");
            VoiceCallStatus initial = mapVapiStatus(vapiStatus);
            log.info("Vapi call created: id={}, status={}", callId, vapiStatus);
            return new StartCallResult(callId, initial);
        } catch (Exception e) {
            throw new RuntimeException("Vapi startCall error: " + e.getMessage(), e);
        }
    }

    @Override
    public ProviderCallStatus getCall(String providerCallId) {
        if (!isConfigured()) {
            throw new IllegalStateException("Vapi not configured");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/call/" + enc(providerCallId)))
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Vapi getCall failed: HTTP " + resp.statusCode());
            }
            JsonNode json = mapper.readTree(resp.body());
            return new ProviderCallStatus(
                mapVapiStatus(json.path("status").asText("")),
                json.path("duration").isMissingNode() ? null : json.path("duration").asInt(),
                json.path("cost").asText(null),
                json.path("transcript").asText(null),
                json.path("recordingUrl").asText(null),
                json.path("endedReason").asText(null)
            );
        } catch (Exception e) {
            throw new RuntimeException("Vapi getCall error: " + e.getMessage(), e);
        }
    }

    /**
     * Map Vapi's status string to our internal VoiceCallStatus enum.
     * Vapi status values: queued, ringing, in-progress, forwarding, ended, busy, no-answer, failed, canceled
     */
    public static VoiceCallStatus mapVapiStatus(String vapiStatus) {
        if (vapiStatus == null) return VoiceCallStatus.SCHEDULED;
        return switch (vapiStatus.toLowerCase()) {
            case "queued" -> VoiceCallStatus.SCHEDULED;
            case "ringing" -> VoiceCallStatus.RINGING;
            case "in-progress" -> VoiceCallStatus.IN_PROGRESS;
            case "forwarding" -> VoiceCallStatus.FORWARDING;
            case "ended" -> VoiceCallStatus.ENDED;
            case "busy" -> VoiceCallStatus.FAILED;
            case "no-answer" -> VoiceCallStatus.NO_ANSWER;
            case "failed" -> VoiceCallStatus.FAILED;
            case "canceled", "cancelled" -> VoiceCallStatus.FAILED;
            default -> VoiceCallStatus.SCHEDULED;
        };
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
