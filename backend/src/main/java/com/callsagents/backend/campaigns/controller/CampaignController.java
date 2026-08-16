package com.callsagents.backend.campaigns.controller;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.campaigns.dto.CampaignFilter;
import com.callsagents.backend.campaigns.dto.CampaignResponse;
import com.callsagents.backend.campaigns.dto.CreateCampaignRequest;
import com.callsagents.backend.campaigns.dto.UpdateCampaignRequest;
import com.callsagents.backend.campaigns.dto.VoicePromptPreviewRequest;
import com.callsagents.backend.campaigns.dto.VoicePromptPreviewResponse;
import com.callsagents.backend.campaigns.entity.CampaignStatus;
import com.callsagents.backend.campaigns.service.CampaignService;
import com.callsagents.backend.common.dto.PageResponse;
import com.callsagents.backend.common.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Campaigns", description = "Gestión de campañas outbound: alta, edición, lanzamiento y pausa")
@RestController
@RequestMapping("/campaigns")
public class CampaignController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final CampaignService campaignService;
    private final UserRepository userRepository;

    public CampaignController(CampaignService campaignService, UserRepository userRepository) {
        this.campaignService = campaignService;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Listar campañas con filtros y paginación")
    @GetMapping
    public ResponseEntity<PageResponse<CampaignResponse>> findAll(
        @RequestParam(required = false) CampaignStatus status,
        @RequestParam(required = false) UUID createdById,
        @RequestParam(required = false) Boolean hasVoiceConfig,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        Pageable pageable = buildPageable(page, size, sort);
        CampaignFilter filter = new CampaignFilter(status, createdById, hasVoiceConfig);
        return ResponseEntity.ok(campaignService.findAll(filter, pageable));
    }

    @Operation(summary = "Obtener campaña por ID")
    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(campaignService.findById(id));
    }

    @Operation(summary = "Crear campaña (solo ADMIN)")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CampaignResponse> create(
        @Valid @RequestBody CreateCampaignRequest req,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        CampaignResponse created = campaignService.create(req, userId);
        return ResponseEntity.created(URI.create("/api/campaigns/" + created.id())).body(created);
    }

    @Operation(summary = "Actualizar campaña (solo ADMIN)")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CampaignResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateCampaignRequest req,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        return ResponseEntity.ok(campaignService.update(id, req, userId));
    }

    @Operation(summary = "Previsualizar el prompt de voz de la campaña (solo ADMIN)")
    @PostMapping("/voice-prompt/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VoicePromptPreviewResponse> previewVoicePrompt(
        @Valid @RequestBody VoicePromptPreviewRequest req
    ) {
        return ResponseEntity.ok(campaignService.previewVoicePrompt(req));
    }

    @Operation(summary = "Lanzar campaña (solo ADMIN)")
    @PostMapping("/{id}/launch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CampaignResponse> launch(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        return ResponseEntity.ok(campaignService.launch(id, userId));
    }

    @Operation(summary = "Pausar campaña en curso (solo ADMIN)")
    @PostMapping("/{id}/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CampaignResponse> pause(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        return ResponseEntity.ok(campaignService.pause(id, userId));
    }

    private Pageable buildPageable(int page, int size, String sort) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Sort sortSpec = parseSort(sort);
        return PageRequest.of(safePage, safeSize, sortSpec);
    }

    private Sort parseSort(String raw) {
        if (raw == null || raw.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = raw.split(",");
        String field = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc"))
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
        return Sort.by(direction, field);
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
