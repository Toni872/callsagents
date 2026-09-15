package com.callsagents.backend.voice.controller;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.voice.domain.VoiceCall;
import com.callsagents.backend.voice.domain.VoiceCallStatus;
import com.callsagents.backend.voice.domain.VoiceProviderType;
import com.callsagents.backend.voice.service.RetellProvider;
import com.callsagents.backend.voice.service.VapiProvider;
import com.callsagents.backend.voice.service.VoiceCallService;
import com.callsagents.backend.voice.service.VoiceProvider;
import com.callsagents.backend.voice.service.WebhookSignatureValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Voice call management.
 *
 * Authenticated endpoints (admin/supervisor/agent):
 *   GET  /api/voice/calls                  — list my calls
 *   GET  /api/voice/calls/{id}             — get one
 *   POST /api/voice/calls/start           — initiate a call (requires provider configured)
 *   POST /api/voice/calls/log             — manually log a call
 *
 * Webhook endpoints (no auth at the security layer; signatures are verified
 * in the handler — Retell HMAC-SHA256, Vapi shared secret):
 *   POST /api/voice/webhook/{provider}    — receive status updates
 */
@RestController
@RequestMapping("/voice")
@Tag(name = "Voice", description = "Llamadas de voz via Vapi/Retell")
public class VoiceController {

    private static final Logger log = LoggerFactory.getLogger(VoiceController.class);

    private final VoiceCallService service;
    private final WebhookSignatureValidator signatureValidator;
    private final RetellProvider retellProvider;
    private final com.callsagents.backend.auth.repository.UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public VoiceController(
        VoiceCallService service,
        WebhookSignatureValidator signatureValidator,
        RetellProvider retellProvider,
        com.callsagents.backend.auth.repository.UserRepository userRepository
    ) {
        this.service = service;
        this.signatureValidator = signatureValidator;
        this.retellProvider = retellProvider;
        this.userRepository = userRepository;
    }

    @GetMapping("/calls")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','AGENT')")
    public List<VoiceCallDto> list(Authentication auth) {
        UUID userId = resolveUserId(auth.getName());
        if (userId == null) return List.of();
        return service.listForUser(userId).stream().map(VoiceCallDto::from).toList();
    }

    @GetMapping("/calls/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','AGENT')")
    public ResponseEntity<VoiceCallDto> getOne(@PathVariable UUID id, Authentication auth) {
        return service.findById(id)
            .filter(c -> {
                UUID uid = resolveUserId(auth.getName());
                return uid != null && c.getUserId().equals(uid);
            })
            .map(c -> ResponseEntity.ok(VoiceCallDto.from(c)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/calls/start")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','AGENT')")
    public ResponseEntity<VoiceCallDto> startCall(
        @RequestParam VoiceProviderType provider,
        @RequestParam String phoneNumber,
        @RequestParam(required = false) UUID campaignId,
        Authentication auth
    ) {
        UUID userId = resolveUserId(auth.getName());
        if (userId == null) return ResponseEntity.status(403).build();

        var req = new VoiceProvider.StartCallRequest(phoneNumber, null, Map.of(), null);
        VoiceCall call = service.placeCall(provider, req, userId, campaignId, resolveRole(auth.getName()));
        return ResponseEntity.ok(VoiceCallDto.from(call));
    }

    @PostMapping("/calls/log")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','AGENT')")
    public VoiceCallDto logCall(@RequestBody VoiceCall call, Authentication auth) {
        UUID userId = resolveUserId(auth.getName());
        if (userId == null) throw new IllegalStateException("User not found");
        call.setUserId(userId);
        return VoiceCallDto.from(service.logManualCall(call));
    }

    /**
     * Webhook from the voice provider. Signature is verified before any state
     * is touched: Retell via X-Retell-Signature (HMAC-SHA256), Vapi via
     * X-Vapi-Secret. Verification is fail-closed — 401 unless proven authentic.
     */
    @PostMapping("/webhook/{provider}")
    public ResponseEntity<Void> webhook(
        @PathVariable String provider,
        @RequestBody String rawBody,
        @RequestHeader(value = "X-Vapi-Secret", required = false) String xVapiSecret,
        @RequestHeader(value = "X-Retell-Signature", required = false) String xRetellSignature
    ) {
        if (!signatureValidator.verify(provider, rawBody, xVapiSecret, xRetellSignature)) {
            log.warn("Webhook rejected: invalid signature for provider '{}'", provider);
            return ResponseEntity.status(401).build();
        }
        try {
            JsonNode json = mapper.readTree(rawBody);
            boolean isRetell = provider.equalsIgnoreCase("retell");

            // Retell nests the call payload under a top-level "call" node (current
            // webhook shape). Older Retell payloads and Vapi keep every field at
            // the root, so fall back to the whole body when no nested node exists.
            JsonNode node = json;
            if (isRetell && json.get("call") != null && json.get("call").isObject()) {
                node = json.get("call");
            }

            // Provider-specific field names. Vapi uses 'status'/'id', Retell uses
            // 'call_status' (legacy 'status') and 'call_id'.
            String status = isRetell
                ? firstText(node, "call_status", "status")
                : firstText(node, "status");
            String callId = isRetell
                ? node.path("call_id").asText()
                : node.path("id").asText();
            // Vapi uses 'duration' (seconds), Retell uses 'duration_ms' (milliseconds).
            Integer duration = null;
            if (isRetell) {
                Integer ms = node.path("duration_ms").isMissingNode() ? null : node.path("duration_ms").asInt();
                duration = ms != null ? ms / 1000 : null;
            } else {
                duration = node.path("duration").isMissingNode() ? null : node.path("duration").asInt();
            }
            // Retell uses 'recording_url' and 'disconnection_reason' (legacy
            // 'end_reason'); Vapi uses 'recordingUrl' and 'endedReason'.
            String transcript = node.path("transcript").asText(null);
            String recordingUrl = isRetell
                ? node.path("recording_url").asText(null)
                : node.path("recordingUrl").asText(null);
            String errorMessage = isRetell
                ? firstText(node, "disconnection_reason", "end_reason")
                : node.path("endedReason").asText(null);
            // Retell nests cost under call_cost.combined_cost (legacy
            // call_cost.total_cost); Vapi uses 'cost'.
            BigDecimal cost = null;
            String costStr = isRetell
                ? firstText(node.path("call_cost"), "combined_cost", "total_cost")
                : node.path("cost").asText(null);
            if (costStr != null) {
                try { cost = new BigDecimal(costStr); } catch (NumberFormatException ignored) {}
            }

            VoiceCallStatus mappedStatus;
            if (isRetell) {
                mappedStatus = RetellProvider.mapRetellStatus(status);
            } else if (provider.equalsIgnoreCase("vapi")) {
                mappedStatus = VapiProvider.mapVapiStatus(status);
            } else {
                log.warn("Webhook from unknown provider '{}'; using ENDED fallback", provider);
                mappedStatus = VoiceCallStatus.ENDED;
            }

            service.applyWebhook(provider, callId, mappedStatus, duration, cost, transcript,
                recordingUrl, errorMessage, null);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("Webhook parse error: {}", e.getMessage(), e);
            return ResponseEntity.ok().build(); // 200 to prevent provider retry storms
        }
    }

    // -------- helpers --------

    /**
     * Create a browser-based web call (WebRTC). No auth required — used by the
     * public demo page. Returns a short-lived access_token for the Retell Web SDK.
     * The created call is persisted so provider webhooks can update it; when no
     * business profile exists to attribute it, the token is still returned.
     */
    @PostMapping("/web-call")
    public ResponseEntity<Map<String, String>> createWebCall(
        @RequestBody(required = false) Map<String, String> body
    ) {
        String agentId = body != null ? body.getOrDefault("agent_id", null) : null;
        String businessId = body != null ? body.get("business_id") : null;
        try {
            RetellProvider.WebCallResult result = retellProvider.createWebCall(agentId);
            Map<String, Object> metadata = (agentId != null && !agentId.isBlank())
                ? Map.of("agentId", agentId)
                : Map.of();
            Map<String, String> response = new LinkedHashMap<>();
            response.put("access_token", result.accessToken());
            response.put("call_id", result.callId());
            service.recordWebCall(businessId, result.callId(), metadata)
                .ifPresent(call -> response.put("voice_call_id", call.getId().toString()));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Web call creation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static String firstText(JsonNode node, String primary, String fallback) {
        String value = node.path(primary).asText();
        if (!value.isEmpty()) {
            return value;
        }
        return fallback != null ? node.path(fallback).asText() : "";
    }

    private static String firstText(JsonNode node, String field) {
        return firstText(node, field, null);
    }

    private UUID resolveUserId(String email) {
        return userRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
    }

    private UserRole resolveRole(String email) {
        return userRepository.findByEmail(email).map(User::getRole).orElse(null);
    }
}
