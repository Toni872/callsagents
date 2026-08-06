package com.callsagents.backend.dashboard.controller;

import com.callsagents.backend.dashboard.dto.DashboardSummary;
import com.callsagents.backend.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard endpoints.
 *
 * Authentication is required (handled by SecurityConfig's anyRequest().authenticated()).
 * All authenticated users see the same metrics — these are team-wide KPIs,
 * not per-user.
 */
@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "Métricas ejecutivas del sistema")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Resumen ejecutivo",
        description = "Devuelve métricas agregadas del sistema: total leads, asignados, " +
            "campañas activas, llamadas hoy, tasa de conexión, citas próximas.")
    public DashboardSummary summary() {
        return dashboardService.getSummary();
    }
}
