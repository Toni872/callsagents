package com.callsagents.backend.voice.service;

import com.callsagents.backend.voice.domain.VoiceCallStatus;
import com.callsagents.backend.voice.domain.VoiceProviderType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * Startup gate: surface missing voice configuration as WARNs instead of
     * failing hard. Backward-compatible — the provider still reports whether it
     * is configured, and startCall keeps its own explicit guards.
     */
    @PostConstruct
    void validateConfig() {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("RETELL_API_KEY", apiKey);
        vars.put("RETELL_AGENT_ID", defaultAgentId);
        vars.put("RETELL_FROM_NUMBER", defaultFromNumber);
        vars.forEach((varName, value) -> {
            if (value == null || value.isBlank()) {
                log.warn("Voice gate: {} is not configured", varName);
            }
        });
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Whether the host/from phone number for outbound calls is set. Used by the
     * voice health indicator to distinguish API-key from number gaps.
     */
    public boolean isFromNumberConfigured() {
        return defaultFromNumber != null && !defaultFromNumber.isBlank();
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
        String agentId = resolveAgentId(req);
        String fromNumber = defaultFromNumber;
        if (agentId == null || agentId.isBlank() || fromNumber == null || fromNumber.isBlank()) {
            List<String> missing = new ArrayList<>();
            if (agentId == null || agentId.isBlank()) {
                missing.add("RETELL_AGENT_ID");
            }
            if (fromNumber == null || fromNumber.isBlank()) {
                missing.add("RETELL_FROM_NUMBER");
            }
            throw new IllegalStateException(
                "Retell not fully configured. Missing env var(s): " + String.join(", ", missing));
        }
        try {
            String json = buildBodyJson(req);
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

    private String resolveAgentId(StartCallRequest req) {
        return (req.assistantId() != null && !req.assistantId().isBlank())
            ? req.assistantId()
            : defaultAgentId;
    }

    /**
     * Request body for create-phone-call with a stable key order:
     * from_number, to_number, override_agent_id, metadata and, only when
     * non-empty, retell_llm_dynamic_variables (FR-2).
     */
    Map<String, Object> buildBody(StartCallRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from_number", defaultFromNumber);
        body.put("to_number", req.phoneNumber());
        body.put("override_agent_id", resolveAgentId(req));
        body.put("metadata", req.metadata() != null ? req.metadata() : Map.of());
        if (req.dynamicVariables() != null && !req.dynamicVariables().isEmpty()) {
            body.put("retell_llm_dynamic_variables", req.dynamicVariables());
        }
        return body;
    }

    String buildBodyJson(StartCallRequest req) throws JsonProcessingException {
        return mapper.writeValueAsString(buildBody(req));
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

    /**
     * Create a browser-based web call (WebRTC). Returns a short-lived access_token
     * that the frontend uses to start the call via the Retell Web SDK.
     * No phone number required, no telephony cost.
     */
    public String createWebCall(String agentId) {
        if (!isConfigured()) {
            throw new IllegalStateException("Retell is not configured. Set RETELL_API_KEY in .env.");
        }
        String resolvedAgentId = (agentId != null && !agentId.isBlank()) ? agentId : defaultAgentId;
        if (resolvedAgentId == null || resolvedAgentId.isBlank()) {
            throw new IllegalStateException("Retell not configured. Missing RETELL_AGENT_ID.");
        }
        try {
            Map<String, Object> body = Map.of("agent_id", resolvedAgentId);
            String json = mapper.writeValueAsString(body);
            HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/v2/create-web-call"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Retell create-web-call failed: HTTP "
                    + resp.statusCode() + " - " + resp.body());
            }
            JsonNode root = mapper.readTree(resp.body());
            String accessToken = root.path("access_token").asText();
            log.info("Retell web call created, agent={}", resolvedAgentId);
            return accessToken;
        } catch (Exception e) {
            throw new RuntimeException("Retell createWebCall error: " + e.getMessage(), e);
        }
    }
}