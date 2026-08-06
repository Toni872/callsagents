package com.callsagents.backend.dashboard.service;

import com.callsagents.backend.appointments.entity.AppointmentStatus;
import com.callsagents.backend.appointments.repository.AppointmentRepository;
import com.callsagents.backend.calls.repository.CallRepository;
import com.callsagents.backend.calls.entity.CallStatus;
import com.callsagents.backend.campaigns.entity.CampaignStatus;
import com.callsagents.backend.campaigns.repository.CampaignRepository;
import com.callsagents.backend.dashboard.dto.DashboardSummary;
import com.callsagents.backend.leads.repository.LeadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Aggregate metrics for the executive dashboard.
 *
 * All counts are computed live against PostgreSQL with single-statement
 * COUNT queries (Spring Data JPA derived methods → SELECT COUNT(*)).
 * Cheap enough to be called on every dashboard mount.
 */
@Service
public class DashboardService {

    private final LeadRepository leadRepository;
    private final CampaignRepository campaignRepository;
    private final CallRepository callRepository;
    private final AppointmentRepository appointmentRepository;

    public DashboardService(LeadRepository leadRepository,
                            CampaignRepository campaignRepository,
                            CallRepository callRepository,
                            AppointmentRepository appointmentRepository) {
        this.leadRepository = leadRepository;
        this.campaignRepository = campaignRepository;
        this.callRepository = callRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummary getSummary() {
        // Day window in UTC (start of day → start of next day)
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = startOfDay.plusSeconds(86_400);

        long totalLeads = leadRepository.count();
        long assignedLeads = leadRepository.countByAssignedToIsNotNull();
        long activeCampaigns = campaignRepository.countByStatus(CampaignStatus.RUNNING);
        long callsToday = callRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        long callsTodayConnected = callRepository.countByCreatedAtBetweenAndStatus(
            startOfDay, endOfDay, CallStatus.CONNECTED);
        double connectionRate = callsToday == 0
            ? 0.0
            : (double) callsTodayConnected / (double) callsToday;

        long upcomingAppointments = appointmentRepository
            .countByScheduledAtGreaterThanEqualAndStatusIn(
                Instant.now(),
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED));

        return new DashboardSummary(
            totalLeads,
            assignedLeads,
            activeCampaigns,
            callsToday,
            callsTodayConnected,
            connectionRate,
            upcomingAppointments,
            Instant.now()
        );
    }
}
