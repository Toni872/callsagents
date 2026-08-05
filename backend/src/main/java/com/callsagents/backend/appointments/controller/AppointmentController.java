package com.callsagents.backend.appointments.controller;

import com.callsagents.backend.appointments.dto.AppointmentFilter;
import com.callsagents.backend.appointments.dto.AppointmentResponse;
import com.callsagents.backend.appointments.dto.CreateAppointmentRequest;
import com.callsagents.backend.appointments.dto.UpdateAppointmentRequest;
import com.callsagents.backend.appointments.entity.AppointmentStatus;
import com.callsagents.backend.appointments.service.AppointmentService;
import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.common.dto.PageResponse;
import com.callsagents.backend.common.exception.UnauthorizedException;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AppointmentService appointmentService;
    private final UserRepository userRepository;

    public AppointmentController(AppointmentService appointmentService, UserRepository userRepository) {
        this.appointmentService = appointmentService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AppointmentResponse>> findAll(
        @RequestParam(required = false) UUID leadId,
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) AppointmentStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "scheduledAt,asc") String sort
    ) {
        Pageable pageable = buildPageable(page, size, sort);
        AppointmentFilter filter = new AppointmentFilter(leadId, userId, status);
        return ResponseEntity.ok(appointmentService.findAll(filter, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(appointmentService.findById(id));
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(
        @Valid @RequestBody CreateAppointmentRequest req,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        AppointmentResponse created = appointmentService.create(req, userId);
        return ResponseEntity.created(URI.create("/api/appointments/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppointmentResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateAppointmentRequest req,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        return ResponseEntity.ok(appointmentService.update(id, req, userId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails user
    ) {
        UUID userId = resolveUserId(user);
        appointmentService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    private Pageable buildPageable(int page, int size, String sort) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Sort sortSpec = parseSort(sort);
        return PageRequest.of(safePage, safeSize, sortSpec);
    }

    private Sort parseSort(String raw) {
        if (raw == null || raw.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "scheduledAt");
        }
        String[] parts = raw.split(",");
        String field = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc"))
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
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
