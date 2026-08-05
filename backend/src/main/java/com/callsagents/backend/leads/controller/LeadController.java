package com.callsagents.backend.leads.controller;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.common.dto.PageResponse;
import com.callsagents.backend.common.exception.UnauthorizedException;
import com.callsagents.backend.leads.dto.CreateLeadRequest;
import com.callsagents.backend.leads.dto.ImportResultDto;
import com.callsagents.backend.leads.dto.LeadFilter;
import com.callsagents.backend.leads.dto.LeadResponse;
import com.callsagents.backend.leads.dto.UpdateLeadRequest;
import com.callsagents.backend.leads.entity.LeadSource;
import com.callsagents.backend.leads.entity.LeadStatus;
import com.callsagents.backend.leads.service.LeadService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/leads")
public class LeadController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final LeadService leadService;
    private final UserRepository userRepository;

    public LeadController(LeadService leadService, UserRepository userRepository) {
        this.leadService = leadService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<PageResponse<LeadResponse>> findAll(
        @RequestParam(required = false) LeadStatus status,
        @RequestParam(required = false) LeadSource source,
        @RequestParam(required = false) UUID assignedToId,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        Pageable pageable = buildPageable(page, size, sort);
        LeadFilter filter = new LeadFilter(status, source, assignedToId, search);
        return ResponseEntity.ok(leadService.findAll(filter, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeadResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(leadService.findById(id));
    }

    @PostMapping
    public ResponseEntity<LeadResponse> create(
        @Valid @RequestBody CreateLeadRequest req,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        LeadResponse created = leadService.create(req, userId);
        return ResponseEntity.created(URI.create("/api/leads/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LeadResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateLeadRequest req,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        return ResponseEntity.ok(leadService.update(id, req, userId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        leadService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ImportResultDto> importCsv(
        @RequestParam("file") MultipartFile file,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(leadService.importCsv(file, userId));
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
