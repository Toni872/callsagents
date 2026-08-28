package com.callsagents.backend.calls.controller;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.calls.dto.CallFilter;
import com.callsagents.backend.calls.dto.CallResponse;
import com.callsagents.backend.calls.dto.CreateCallRequest;
import com.callsagents.backend.calls.dto.UpdateCallRequest;
import com.callsagents.backend.calls.entity.CallOutcome;
import com.callsagents.backend.calls.entity.CallStatus;
import com.callsagents.backend.calls.service.CallService;
import com.callsagents.backend.common.dto.PageResponse;
import com.callsagents.backend.common.exception.ForbiddenException;
import com.callsagents.backend.common.exception.UnauthorizedException;
import com.callsagents.backend.common.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
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
import java.util.Set;
import java.util.UUID;

@Tag(name = "Calls", description = "Registro y consulta de llamadas (outbound)")
@RestController
@RequestMapping("/calls")
public class CallController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final CallService callService;
    private final UserRepository userRepository;

    public CallController(CallService callService, UserRepository userRepository) {
        this.callService = callService;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Listar llamadas con filtros y paginación")
    @GetMapping
    public ResponseEntity<PageResponse<CallResponse>> findAll(
        @RequestParam(required = false) UUID campaignId,
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) UUID leadId,
        @RequestParam(required = false) CallStatus status,
        @RequestParam(required = false) CallOutcome outcome,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        @AuthenticationPrincipal UserDetails user
    ) {
        User current = resolveUser(user);
        Pageable pageable = buildPageable(page, size, sort);
        CallFilter filter = new CallFilter(campaignId, userId, leadId, status, outcome);
        return ResponseEntity.ok(callService.findAll(filter, pageable, current.getId()));
    }

    @Operation(summary = "Obtener llamada por ID")
    @GetMapping("/{id}")
    public ResponseEntity<CallResponse> findById(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails user
    ) {
        User current = resolveUser(user);
        return ResponseEntity.ok(callService.findById(id, current.getId()));
    }

    @Operation(summary = "Registrar nueva llamada")
    @PostMapping
    public ResponseEntity<CallResponse> create(
        @Valid @RequestBody CreateCallRequest req,
        @AuthenticationPrincipal UserDetails user
    ) {
        User current = resolveUser(user);
        UUID requested = req.userId();
        if (requested != null && !requested.equals(current.getId())
            && current.getRole() != UserRole.ADMIN && current.getRole() != UserRole.SUPERVISOR) {
            throw new ForbiddenException("You can only assign calls to yourself");
        }
        CallResponse created = callService.create(req, current.getId());
        return ResponseEntity.created(URI.create("/api/calls/" + created.id())).body(created);
    }

    @Operation(summary = "Actualizar resultado/notas de una llamada")
    @PutMapping("/{id}")
    public ResponseEntity<CallResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateCallRequest req,
        @AuthenticationPrincipal UserDetails user
    ) {
        User current = resolveUser(user);
        return ResponseEntity.ok(callService.update(id, req, current.getId(), current.getRole()));
    }

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "createdAt", "startedAt", "endedAt", "status", "outcome", "durationSeconds"
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
