package com.callsagents.backend.voice.service;

import com.callsagents.backend.voice.domain.VoiceCallStatus;
import com.callsagents.backend.voice.domain.VoiceProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class RetellProvider implements VoiceProvider {

    private static final Logger log = LoggerFactory.getLogger(RetellProvider.class);
    private static final String BASE = "https://api.retellai.com";

    @Value("${app.voice.retell.api-key:}")
    private String apiKey;

    @Value("${app.voice.retell.agent-id:}")
    private String defaultAgentId;

    @Value("${app.voice.retell.from-number:}")
    private String defaultFromNumber;

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
        return VoiceProviderType.RETELL;
    }

    @Override
    public StartCallResult startCall(StartCallRequest req) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                "Retell is not configured. Set RETELL_API_KEY in .env.");
        }
        String agentId = (req.assistantId() != null && !req.assistantId().isBlank())
            ? req.assistantId()
            : defaultAgentId;
        String fromNumber = defaultFromNumber;
        if (agentId == null || agentId.isBlank() || fromNumber == null || fromNumber.isBlank()) {
            throw new IllegalStateException(
                "Retell requires RETELL_AGENT_ID and RETELL_FROM_NUMBER env vars.");
        }
        try {
            Map<String, Object> body = Map.of(
                "from_number", fromNumber,
                "to_number", req.phoneNumber(),
                "override_agent_id", agentId,
                "metadata", req.metadata() != null ? req.metadata() : Map.of()
            );
            String json = mapper.writeValueAsString(body);
            HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/v2/create-phone-call"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Retell create-phone-call failed: HTTP "
                    + resp.statusCode() + " - " + resp.body());
            }
            JsonNode root = mapper.readTree(resp.body());
            String callId = root.path("call_id").asText();
            String retellStatus = root.path("status").asText("queued");
            VoiceCallStatus initial = mapRetellStatus(retellStatus);
            log.info("Retell call created: id={}, status={}", callId, retellStatus);
            return new StartCallResult(callId, initial);
        } catch (Exception e) {
            throw new RuntimeException("Retell startCall error: " + e.getMessage(), e);
        }
    }

    @Override
    public ProviderCallStatus getCall(String providerCallId) {
        if (!isConfigured()) {
            throw new IllegalStateException("Retell not configured");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/v2/get-call/" + enc(providerCallId)))
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Retell getCall failed: HTTP " + resp.statusCode());
            }
            JsonNode json = mapper.readTree(resp.body());
            Integer durationMs = json.path("duration_ms").isMissingNode()
                ? null : json.path("duration_ms").asInt();
            Integer durationSec = durationMs != null ? durationMs / 1000 : null;
            BigDecimal cost = null;
            if (json.has("call_cost") && json.path("call_cost").has("total_cost")) {
                String c = json.path("call_cost").path("total_cost").asText("0");
                try {
                    cost = new BigDecimal(c);
                } catch (NumberFormatException ignored) {
                    cost = null;
                }
            }
            return new ProviderCallStatus(
                mapRetellStatus(json.path("status").asText("")),
                durationSec,
                cost != null ? cost.toPlainString() : null,
                json.path("transcript").asText(null),
                json.path("recording_url").asText(null),
                json.path("end_reason").asText(null)
            );
        } catch (Exception e) {
            throw new RuntimeException("Retell getCall error: " + e.getMessage(), e);
        }
    }

    public static VoiceCallStatus mapRetellStatus(String retellStatus) {
        if (retellStatus == null) {
            return VoiceCallStatus.SCHEDULED;
        }
        return switch (retellStatus.toLowerCase()) {
            case "queued" -> VoiceCallStatus.SCHEDULED;
            case "ringing" -> VoiceCallStatus.RINGING;
            case "in_progress" -> VoiceCallStatus.IN_PROGRESS;
            case "not_connected" -> VoiceCallStatus.NO_ANSWER;
            case "completed", "ended" -> VoiceCallStatus.ENDED;
            case "error", "failed" -> VoiceCallStatus.FAILED;
            default -> VoiceCallStatus.SCHEDULED;
        };
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}