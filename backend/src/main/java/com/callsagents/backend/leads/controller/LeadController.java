package com.callsagents.backend.leads.controller;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.common.dto.PageResponse;
import com.callsagents.backend.common.exception.UnauthorizedException;
import com.callsagents.backend.common.util.PaginationUtils;
import com.callsagents.backend.leads.dto.CreateLeadRequest;
import com.callsagents.backend.leads.dto.ImportResultDto;
import com.callsagents.backend.leads.dto.LeadFilter;
import com.callsagents.backend.leads.dto.LeadResponse;
import com.callsagents.backend.leads.dto.UpdateLeadRequest;
import com.callsagents.backend.leads.entity.LeadSource;
import com.callsagents.backend.leads.entity.LeadStatus;
import com.callsagents.backend.leads.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.Set;
import java.util.UUID;

@Tag(name = "Leads", description = "Gestión de leads: alta, importación CSV, filtros, asignación")
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

    @Operation(summary = "Listar leads con filtros y paginación")
    @GetMapping
    public ResponseEntity<PageResponse<LeadResponse>> findAll(
        @RequestParam(required = false) LeadStatus status,
        @RequestParam(required = false) LeadSource source,
        @RequestParam(required = false) UUID assignedToId,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        @AuthenticationPrincipal UserDetails user
    ) {
        User current = resolveUser(user);
        Pageable pageable = buildPageable(page, size, sort);
        LeadFilter filter = new LeadFilter(status, source, assignedToId, search);
        return ResponseEntity.ok(leadService.findAll(filter, pageable, current.getId()));
    }

    @Operation(summary = "Obtener lead por ID")
    @GetMapping("/{id}")
    public ResponseEntity<LeadResponse> findById(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails user
    ) {
        User current = resolveUser(user);
        return ResponseEntity.ok(leadService.findById(id, current.getId()));
    }

    @Operation(summary = "Crear lead manualmente")
    @PostMapping
    public ResponseEntity<LeadResponse> create(
        @Valid @RequestBody CreateLeadRequest req,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUser(user).getId();
        LeadResponse created = leadService.create(req, userId);
        return ResponseEntity.created(URI.create("/api/leads/" + created.id())).body(created);
    }

    @Operation(summary = "Actualizar lead existente")
    @PutMapping("/{id}")
    public ResponseEntity<LeadResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateLeadRequest req,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUser(user).getId();
        return ResponseEntity.ok(leadService.update(id, req, userId));
    }

    @Operation(summary = "Eliminar lead (solo ADMIN)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails user
    ) {
        User current = resolveUser(user);
        leadService.delete(id, current.getId(), current.getRole());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Importar leads desde CSV (ADMIN/SUPERVISOR)")
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ImportResultDto> importCsv(
        @RequestParam("file") MultipartFile file,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUser(user).getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(leadService.importCsv(file, userId));
    }

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "createdAt", "updatedAt", "firstName", "lastName", "email", "status", "source", "company"
    );

    private Pageable buildPageable(int page, int size, String sort) {
        return PaginationUtils.buildPageable(page, size, sort, "createdAt", Sort.Direction.DESC, ALLOWED_SORT_FIELDS);
    }

    private User resolveUser(UserDetails user) {
        if (user == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return userRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new UnauthorizedException("Current user not found"));
    }
}
