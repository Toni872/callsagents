package com.callsagents.backend.users.controller;

import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.users.dto.CreateUserRequest;
import com.callsagents.backend.users.dto.UserListItem;
import com.callsagents.backend.users.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Admin-only user management endpoints.
 *
 * Designed to grow: future endpoints (PATCH /{id}, DELETE /{id}, etc.) will
 * be added here as the feature expands.
 */
@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Gestión de usuarios (solo ADMIN)")
public class UserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar usuarios (ADMIN)",
        description = "Lista paginada de usuarios. Filtrable opcionalmente por rol.")
    public Page<UserListItem> list(
        @RequestParam(required = false) UserRole role,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // Cap size to prevent DoS via huge page requests
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        return userService.listUsers(role, pageable);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear usuario (ADMIN)",
        description = "Admin crea un usuario nuevo con email + password. El password se hashea con BCrypt antes de persistir.")
    public ResponseEntity<UserListItem> create(@Valid @RequestBody CreateUserRequest req) {
        var created = userService.createUser(req);
        var body = new UserListItem(
            created.getId(),
            created.getEmail(),
            created.getFullName(),
            created.getRole(),
            created.getStatus(),
            created.getLastLoginAt(),
            created.getCreatedAt()
        );
        return ResponseEntity.created(URI.create("/users/" + created.getId())).body(body);
    }
}
