package com.callsagents.backend.escalation.controller;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.business.dto.BusinessProfileRequest;
import com.callsagents.backend.business.dto.BusinessProfileResponse;
import com.callsagents.backend.business.service.BusinessService;
import com.callsagents.backend.common.exception.UnauthorizedException;
import com.callsagents.backend.escalation.dto.EscalationConfigRequest;
import com.callsagents.backend.escalation.dto.EscalationConfigResponse;
import com.callsagents.backend.escalation.dto.EscalationResponse;
import com.callsagents.backend.escalation.entity.Escalation;
import com.callsagents.backend.escalation.service.EscalationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Escalation", description = "Orquestacion de escalamiento de leads a llamada de voz (follow-up)")
@RestController
@RequestMapping("/escalation")
public class EscalationController {

    private final EscalationService escalationService;
    private final BusinessService businessService;
    private final UserRepository userRepository;

    public EscalationController(EscalationService escalationService,
                                BusinessService businessService,
                                UserRepository userRepository) {
        this.escalationService = escalationService;
        this.businessService = businessService;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Obtener estado de escalamiento de un lead")
    @GetMapping("/leads/{leadId}")
    public ResponseEntity<Map<String, Object>> getForLead(
        @PathVariable UUID leadId,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID currentUserId = resolveUserId(user);
        return escalationService.getForLead(leadId, currentUserId)
            .map(e -> ResponseEntity.ok(Map.of("success", true, "data", EscalationResponse.from(e))))
            .orElseGet(() -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", true);
                body.put("data", null);
                return ResponseEntity.ok(body);
            });
    }

    @Operation(summary = "Cancelar un escalamiento activo")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<Map<String, Object>> cancel(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails user
    ) {
        User current = resolveUser(user);
        Escalation escalation = escalationService.cancel(id, current.getId(), current.getRole());
        return ResponseEntity.ok(Map.of("success", true, "data", EscalationResponse.from(escalation)));
    }

    @Operation(summary = "Actualizar configuracion de escalamiento del negocio actual")
    @PutMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(
        @Valid @RequestBody EscalationConfigRequest request,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        BusinessProfileRequest profileRequest = new BusinessProfileRequest(
            null, null, null, null, null, null, null, null,
            request.escalationEnabled(),
            request.replyTimeoutMinutes(),
            request.followupMessage(),
            request.voiceAgentId()
        );
        BusinessProfileResponse profile = businessService.update(userId, profileRequest);
        // Map.of rejects null values; build a null-safe response object instead.
        return ResponseEntity.ok(Map.of("success", true, "data", new EscalationConfigResponse(
            profile.escalationEnabled(),
            profile.replyTimeoutMinutes(),
            profile.followupMessage(),
            profile.voiceAgentId()
        )));
    }

    @Operation(summary = "Obtener configuracion de escalamiento del negocio actual")
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig(
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        var profile = businessService.getProfileEntityByUserId(userId);
        EscalationConfigResponse config = profile != null
            ? EscalationConfigResponse.from(profile)
            : new EscalationConfigResponse(true, 30, null, null);
        return ResponseEntity.ok(Map.of("success", true, "data", config));
    }

    private UUID resolveUserId(UserDetails user) {
        return resolveUser(user).getId();
    }

    private User resolveUser(UserDetails user) {
        if (user == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return userRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new UnauthorizedException("Current user not found"));
    }
}
