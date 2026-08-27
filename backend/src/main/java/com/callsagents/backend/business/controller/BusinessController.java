package com.callsagents.backend.business.controller;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.business.dto.BusinessProfileRequest;
import com.callsagents.backend.business.dto.BusinessProfileResponse;
import com.callsagents.backend.business.dto.WidgetConfigResponse;
import com.callsagents.backend.business.service.BusinessService;
import com.callsagents.backend.common.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Business Profile", description = "Gestion del perfil de negocio y configuracion del chatbot")
@RestController
@RequestMapping("/business/profile")
public class BusinessController {

    private final BusinessService businessService;
    private final UserRepository userRepository;

    public BusinessController(BusinessService businessService, UserRepository userRepository) {
        this.businessService = businessService;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Crear perfil de negocio")
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        BusinessProfileResponse profile = businessService.create(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(
            Map.of("success", true, "data", profile)
        );
    }

    @Operation(summary = "Obtener perfil de negocio del usuario actual")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        BusinessProfileResponse profile = businessService.getByUserId(userId);
        return ResponseEntity.ok(Map.of("success", true, "data", profile));
    }

    @Operation(summary = "Actualizar perfil de negocio")
    @PutMapping
    public ResponseEntity<Map<String, Object>> update(
        @Valid @RequestBody BusinessProfileRequest request,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        BusinessProfileResponse profile = businessService.update(userId, request);
        return ResponseEntity.ok(Map.of("success", true, "data", profile));
    }

    @Operation(summary = "Obtener configuracion del widget (branding)")
    @GetMapping("/widget-config")
    public ResponseEntity<Map<String, Object>> getWidgetConfig(
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        WidgetConfigResponse config = businessService.getWidgetConfig(userId);
        return ResponseEntity.ok(Map.of("success", true, "data", config));
    }

    @Operation(summary = "Obtener configuracion del widget publica (sin auth)")
    @GetMapping("/widget-config/{businessId}")
    public ResponseEntity<Map<String, Object>> getPublicWidgetConfig(
        @org.springframework.web.bind.annotation.PathVariable UUID businessId
    ) {
        WidgetConfigResponse config = businessService.getWidgetConfig(businessId);
        return ResponseEntity.ok(Map.of("success", true, "data", config));
    }

    private UUID resolveUserId(UserDetails user) {
        if (user == null) {
            throw new UnauthorizedException("Authentication required");
        }
        User u = userRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new UnauthorizedException("Current user not found"));
        return u.getId();
    }
}
