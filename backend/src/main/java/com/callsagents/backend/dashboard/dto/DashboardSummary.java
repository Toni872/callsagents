package com.callsagents.backend.dashboard.dto;

import java.time.Instant;

/**
 * Aggregate metrics for the executive dashboard.
 *
 * Returned by GET /api/dashboard/summary in a single call so the frontend
 * doesn't have to fire 6+ separate requests on page load.
 */
public record DashboardSummary(
    long totalLeads,
    long assignedLeads,
    long activeCampaigns,
    long callsToday,
    long callsTodayConnected,
    /** 0.0 to 1.0 — converted to percentage at the view layer */
    double connectionRateToday,
    long upcomingAppointments,
    Instant generatedAt
) {}
