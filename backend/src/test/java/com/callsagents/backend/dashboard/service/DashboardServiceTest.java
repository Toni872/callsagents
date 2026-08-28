package com.callsagents.backend.dashboard.service;

import com.callsagents.backend.appointments.entity.AppointmentStatus;
import com.callsagents.backend.appointments.repository.AppointmentRepository;
import com.callsagents.backend.calls.entity.CallStatus;
import com.callsagents.backend.calls.repository.CallRepository;
import com.callsagents.backend.campaigns.entity.CampaignStatus;
import com.callsagents.backend.campaigns.repository.CampaignRepository;
import com.callsagents.backend.dashboard.dto.DashboardSummary;
import com.callsagents.backend.leads.repository.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private LeadRepository leadRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private CallRepository callRepository;
    @Mock private AppointmentRepository appointmentRepository;

    private DashboardService service;

    private final UUID currentUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DashboardService(
            leadRepository, campaignRepository, callRepository, appointmentRepository);
    }

    @Test
    @DisplayName("summary: aggregates per-user repository counts and computes connection rate")
    void summary_happyPath() {
        when(leadRepository.countByCreatedBy(currentUserId)).thenReturn(120L);
        when(leadRepository.countByCreatedByAndAssignedToIsNotNull(currentUserId)).thenReturn(80L);
        when(campaignRepository.countByStatusAndCreatedBy(CampaignStatus.RUNNING, currentUserId)).thenReturn(3L);

        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = startOfDay.plusSeconds(86_400);
        when(callRepository.countByCreatedAtBetweenAndUserId(startOfDay, endOfDay, currentUserId)).thenReturn(45L);
        when(callRepository.countByCreatedAtBetweenAndStatusAndUserId(
            startOfDay, endOfDay, CallStatus.CONNECTED, currentUserId)).thenReturn(18L);

        when(appointmentRepository.countByScheduledAtGreaterThanEqualAndStatusInAndUserId(
            any(), eq(List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED)), eq(currentUserId)))
            .thenReturn(7L);

        DashboardSummary s = service.getSummary(currentUserId);

        assertThat(s.totalLeads()).isEqualTo(120L);
        assertThat(s.assignedLeads()).isEqualTo(80L);
        assertThat(s.activeCampaigns()).isEqualTo(3L);
        assertThat(s.callsToday()).isEqualTo(45L);
        assertThat(s.callsTodayConnected()).isEqualTo(18L);
        // 18 / 45 = 0.4 exactly (40%)
        assertThat(s.connectionRateToday()).isEqualTo(0.4);
        assertThat(s.upcomingAppointments()).isEqualTo(7L);
        assertThat(s.generatedAt()).isNotNull();
    }

    @Test
    @DisplayName("summary: 100% connection rate when everything today connected")
    void summary_fullConnectionRate() {
        when(leadRepository.countByCreatedBy(currentUserId)).thenReturn(10L);
        when(leadRepository.countByCreatedByAndAssignedToIsNotNull(currentUserId)).thenReturn(5L);
        when(campaignRepository.countByStatusAndCreatedBy(CampaignStatus.RUNNING, currentUserId)).thenReturn(0L);

        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = startOfDay.plusSeconds(86_400);
        when(callRepository.countByCreatedAtBetweenAndUserId(startOfDay, endOfDay, currentUserId)).thenReturn(7L);
        when(callRepository.countByCreatedAtBetweenAndStatusAndUserId(
            startOfDay, endOfDay, CallStatus.CONNECTED, currentUserId)).thenReturn(7L);
        when(appointmentRepository.countByScheduledAtGreaterThanEqualAndStatusInAndUserId(
            any(), any(), eq(currentUserId))).thenReturn(0L);

        DashboardSummary s = service.getSummary(currentUserId);

        assertThat(s.connectionRateToday()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("summary: connection rate is 0 when no calls today (not divide by zero)")
    void summary_zeroCallsRate() {
        when(leadRepository.countByCreatedBy(currentUserId)).thenReturn(0L);
        when(leadRepository.countByCreatedByAndAssignedToIsNotNull(currentUserId)).thenReturn(0L);
        when(campaignRepository.countByStatusAndCreatedBy(CampaignStatus.RUNNING, currentUserId)).thenReturn(0L);

        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = startOfDay.plusSeconds(86_400);
        when(callRepository.countByCreatedAtBetweenAndUserId(startOfDay, endOfDay, currentUserId)).thenReturn(0L);
        when(callRepository.countByCreatedAtBetweenAndStatusAndUserId(
            startOfDay, endOfDay, CallStatus.CONNECTED, currentUserId)).thenReturn(0L);
        when(appointmentRepository.countByScheduledAtGreaterThanEqualAndStatusInAndUserId(
            any(), any(), eq(currentUserId))).thenReturn(0L);

        DashboardSummary s = service.getSummary(currentUserId);

        assertThat(s.connectionRateToday()).isEqualTo(0.0);
        assertThat(s.totalLeads()).isZero();
        assertThat(s.callsToday()).isZero();
    }
}
