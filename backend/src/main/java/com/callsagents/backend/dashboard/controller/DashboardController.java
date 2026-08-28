package com.callsagents.backend.dashboard.controller;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.common.exception.UnauthorizedException;
import com.callsagents.backend.dashboard.dto.DashboardSummary;
import com.callsagents.backend.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard endpoints.
 *
 * Authentication is required (handled by SecurityConfig's anyRequest().authenticated()).
 * Metrics are scoped to the authenticated user (per-user KPIs).
 */
@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "Métricas ejecutivas del sistema")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserRepository userRepository;

    public DashboardController(DashboardService dashboardService, UserRepository userRepository) {
        this.dashboardService = dashboardService;
        this.userRepository = userRepository;
    }

    @GetMapping("/summary")
    @Operation(summary = "Resumen ejecutivo",
        description = "Devuelve métricas por usuario autenticado: total leads, asignados, " +
            "campañas activas, llamadas hoy, tasa de conexión, citas próximas.")
    public DashboardSummary summary(@AuthenticationPrincipal UserDetails user) {
        User current = resolveUser(user);
        return dashboardService.getSummary(current.getId());
    }

    private User resolveUser(UserDetails user) {
        if (user == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return userRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new UnauthorizedException("Current user not found"));
    }
}
