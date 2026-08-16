package com.callsagents.backend.voice.controller;

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
    private final com.callsagents.backend.auth.repository.UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public VoiceController(
        VoiceCallService service,
        WebhookSignatureValidator signatureValidator,
        com.callsagents.backend.auth.repository.UserRepository userRepository
    ) {
        this.service = service;
        this.signatureValidator = signatureValidator;
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
        VoiceCall call = service.placeCall(provider, req, userId, campaignId);
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
            String status = json.path("status").asText();
            boolean isRetell = provider.equalsIgnoreCase("retell");

            // Provider-specific field names. Vapi uses 'id', Retell uses 'call_id'.
            String callId = isRetell
                ? json.path("call_id").asText()
                : json.path("id").asText();
            // Vapi uses 'duration' (seconds), Retell uses 'duration_ms' (milliseconds).
            Integer duration = null;
            if (isRetell) {
                Integer ms = json.path("duration_ms").isMissingNode() ? null : json.path("duration_ms").asInt();
                duration = ms != null ? ms / 1000 : null;
            } else {
                duration = json.path("duration").isMissingNode() ? null : json.path("duration").asInt();
            }
            // Retell uses 'recording_url' and 'end_reason'; Vapi uses 'recordingUrl' and 'endedReason'.
            String transcript = json.path("transcript").asText(null);
            String recordingUrl = isRetell
                ? json.path("recording_url").asText(null)
                : json.path("recordingUrl").asText(null);
            String errorMessage = isRetell
                ? json.path("end_reason").asText(null)
                : json.path("endedReason").asText(null);
            // Retell nests cost under call_cost.total_cost; Vapi uses 'cost'.
            BigDecimal cost = null;
            String costStr = isRetell
                ? json.path("call_cost").path("total_cost").asText(null)
                : json.path("cost").asText(null);
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

    private UUID resolveUserId(String email) {
        return userRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
    }
}
