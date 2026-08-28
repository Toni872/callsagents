package com.callsagents.backend.campaigns.controller;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.campaigns.dto.AddLeadRequest;
import com.callsagents.backend.campaigns.dto.CampaignLeadResponse;
import com.callsagents.backend.campaigns.service.CampaignLeadService;
import com.callsagents.backend.common.dto.PageResponse;
import com.callsagents.backend.common.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Campaign Leads", description = "Asignación de leads a campañas")
@RestController
@RequestMapping("/campaigns/{campaignId}/leads")
public class CampaignLeadController {

    private final CampaignLeadService campaignLeadService;
    private final UserRepository userRepository;

    public CampaignLeadController(
        CampaignLeadService campaignLeadService,
        UserRepository userRepository
    ) {
        this.campaignLeadService = campaignLeadService;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Listar leads asignados a una campaña (paginado)")
    @GetMapping
    public ResponseEntity<PageResponse<CampaignLeadResponse>> list(
        @PathVariable UUID campaignId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal UserDetails user
    ) {
        User current = resolveUser(user);
        return ResponseEntity.ok(
            campaignLeadService.listLeads(campaignId, page, size, current.getId(), current.getRole())
        );
    }

    @Operation(summary = "Añadir un lead a una campaña (ADMIN)")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CampaignLeadResponse> add(
        @PathVariable UUID campaignId,
        @Valid @RequestBody AddLeadRequest req,
        @AuthenticationPrincipal UserDetails user
    ) {
        User current = resolveUser(user);
        CampaignLeadResponse created = campaignLeadService.addLead(campaignId, req, current.getId(), current.getRole());
        return ResponseEntity
            .created(URI.create("/api/campaigns/" + campaignId + "/leads/" + created.leadId()))
            .body(created);
    }

    @Operation(summary = "Quitar un lead de una campaña (ADMIN)")
    @DeleteMapping("/{leadId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> remove(
        @PathVariable UUID campaignId,
        @PathVariable UUID leadId,
        @AuthenticationPrincipal UserDetails user
    ) {
        User current = resolveUser(user);
        campaignLeadService.removeLead(campaignId, leadId, current.getId(), current.getRole());
        return ResponseEntity.noContent().build();
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