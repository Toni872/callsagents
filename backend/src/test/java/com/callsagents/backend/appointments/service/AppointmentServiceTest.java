package com.callsagents.backend.appointments.service;

import com.callsagents.backend.appointments.dto.AppointmentFilter;
import com.callsagents.backend.appointments.dto.AppointmentResponse;
import com.callsagents.backend.appointments.dto.CreateAppointmentRequest;
import com.callsagents.backend.appointments.dto.UpdateAppointmentRequest;
import com.callsagents.backend.appointments.entity.Appointment;
import com.callsagents.backend.appointments.entity.AppointmentStatus;
import com.callsagents.backend.appointments.repository.AppointmentRepository;
import com.callsagents.backend.audit.entity.AuditAction;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.calendar.service.CalendarSyncService;
import com.callsagents.backend.common.audit.AuditService;
import com.callsagents.backend.common.dto.PageResponse;
import com.callsagents.backend.common.exception.BadRequestException;
import com.callsagents.backend.common.exception.ForbiddenException;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.entity.LeadSource;
import com.callsagents.backend.leads.entity.LeadStatus;
import com.callsagents.backend.leads.repository.LeadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private CalendarSyncService calendarSync;
    @Mock
    private LeadRepository leadRepository;

    @InjectMocks
    private AppointmentService appointmentService;

    private final UUID currentUserId = UUID.randomUUID();

    @Test
    void createBuildsAndSavesAppointmentWithPendingDefault() {
        UUID id = UUID.randomUUID();
        when(leadRepository.findById(any())).thenReturn(Optional.of(sampleLead(UUID.randomUUID(), currentUserId)));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment arg = inv.getArgument(0);
            arg.setId(id);
            arg.setCreatedAt(Instant.now());
            arg.setUpdatedAt(Instant.now());
            return arg;
        });

        Instant scheduledAt = Instant.now().plusSeconds(3600);
        CreateAppointmentRequest req = new CreateAppointmentRequest(
            UUID.randomUUID(), UUID.randomUUID(), scheduledAt, 30, null, "Initial consult");

        AppointmentResponse response = appointmentService.create(req, currentUserId, UserRole.AGENT);

        assertEquals(id, response.id());
        assertEquals(AppointmentStatus.PENDING, response.status());
        verify(auditService).log(eq(currentUserId), eq("Appointment"), eq(id), eq(AuditAction.CREATE));
    }

    @Test
    void createAcceptsAnyPositiveDuration() {
        UUID id = UUID.randomUUID();
        when(leadRepository.findById(any())).thenReturn(Optional.of(sampleLead(UUID.randomUUID(), currentUserId)));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment arg = inv.getArgument(0);
            arg.setId(id);
            arg.setCreatedAt(Instant.now());
            arg.setUpdatedAt(Instant.now());
            return arg;
        });

        CreateAppointmentRequest req = new CreateAppointmentRequest(
            UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(3600), 1, null, null);

        AppointmentResponse response = appointmentService.create(req, currentUserId, UserRole.AGENT);

        assertEquals(1, response.durationMinutes());
    }

    @Test
    void createWithExplicitStatus() {
        UUID id = UUID.randomUUID();
        when(leadRepository.findById(any())).thenReturn(Optional.of(sampleLead(UUID.randomUUID(), currentUserId)));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment arg = inv.getArgument(0);
            arg.setId(id);
            arg.setCreatedAt(Instant.now());
            arg.setUpdatedAt(Instant.now());
            return arg;
        });

        CreateAppointmentRequest req = new CreateAppointmentRequest(
            UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(3600),
            60, "CONFIRMED", null);

        AppointmentResponse response = appointmentService.create(req, currentUserId, UserRole.AGENT);

        assertEquals(AppointmentStatus.CONFIRMED, response.status());
    }

    @Test
    void createRejectsInvalidStatus() {
        when(leadRepository.findById(any())).thenReturn(Optional.of(sampleLead(UUID.randomUUID(), currentUserId)));
        CreateAppointmentRequest req = new CreateAppointmentRequest(
            UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(3600),
            30, "INVALID_STATUS", null);
        assertThrows(BadRequestException.class, () -> appointmentService.create(req, currentUserId, UserRole.AGENT));
    }

    @Test
    void createRejectsUnknownLead() {
        UUID leadId = UUID.randomUUID();
        when(leadRepository.findById(leadId)).thenReturn(Optional.empty());
        CreateAppointmentRequest req = new CreateAppointmentRequest(
            leadId, UUID.randomUUID(), Instant.now().plusSeconds(3600), 30, null, null);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> appointmentService.create(req, currentUserId, UserRole.AGENT));
        assertEquals("Lead not found: " + leadId, ex.getMessage());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void createRejectsForeignLead() {
        UUID leadId = UUID.randomUUID();
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(sampleLead(leadId, UUID.randomUUID())));
        CreateAppointmentRequest req = new CreateAppointmentRequest(
            leadId, UUID.randomUUID(), Instant.now().plusSeconds(3600), 30, null, null);
        ForbiddenException ex = assertThrows(ForbiddenException.class,
            () -> appointmentService.create(req, currentUserId, UserRole.AGENT));
        assertEquals("You can only create appointments with your own leads", ex.getMessage());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void createAllowsAdminToUseForeignLead() {
        UUID id = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(sampleLead(leadId, UUID.randomUUID())));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment arg = inv.getArgument(0);
            arg.setId(id);
            return arg;
        });

        CreateAppointmentRequest req = new CreateAppointmentRequest(
            leadId, UUID.randomUUID(), Instant.now().plusSeconds(3600), 30, null, null);

        AppointmentResponse response = appointmentService.create(req, currentUserId, UserRole.ADMIN);

        assertEquals(id, response.id());
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    void updateThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(appointmentRepository.findById(id)).thenReturn(Optional.empty());
        UpdateAppointmentRequest req = new UpdateAppointmentRequest(null, null, "CONFIRMED", null);
        assertThrows(ResourceNotFoundException.class, () -> appointmentService.update(id, req, currentUserId, UserRole.ADMIN));
    }

    @Test
    void updateAppliesProvidedFields() {
        UUID id = UUID.randomUUID();
        Appointment existing = sampleAppointment(id);
        existing.setNotes("old");
        when(appointmentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAppointmentRequest req = new UpdateAppointmentRequest(null, 45, "CONFIRMED", "new notes");

        AppointmentResponse response = appointmentService.update(id, req, currentUserId, UserRole.ADMIN);

        assertEquals(45, response.durationMinutes());
        assertEquals(AppointmentStatus.CONFIRMED, response.status());
        assertEquals("new notes", response.notes());
        verify(auditService).log(eq(currentUserId), eq("Appointment"), eq(id), eq(AuditAction.UPDATE));
    }

    @Test
    void updateThrowsForbiddenWhenSupervisorEditsOtherUserAppointment() {
        UUID id = UUID.randomUUID();
        Appointment existing = sampleAppointment(id);
        when(appointmentRepository.findById(id)).thenReturn(Optional.of(existing));

        UpdateAppointmentRequest req = new UpdateAppointmentRequest(null, null, "CONFIRMED", null);

        assertThrows(ForbiddenException.class, () -> appointmentService.update(id, req, currentUserId, UserRole.SUPERVISOR));
    }

    @Test
    void deleteThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(appointmentRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> appointmentService.delete(id, currentUserId, UserRole.ADMIN));
        verify(appointmentRepository, never()).deleteById(any());
    }

    @Test
    void deleteSucceedsWhenOwnedByCurrentUser() {
        UUID id = UUID.randomUUID();
        Appointment appointment = sampleAppointment(id);
        appointment.setUserId(currentUserId);
        when(appointmentRepository.findById(id)).thenReturn(Optional.of(appointment));

        appointmentService.delete(id, currentUserId, UserRole.AGENT);

        verify(appointmentRepository).deleteById(id);
        verify(auditService).log(eq(currentUserId), eq("Appointment"), eq(id), eq(AuditAction.DELETE));
    }

    @Test
    void deleteRejectsForeignAppointment() {
        UUID id = UUID.randomUUID();
        when(appointmentRepository.findById(id)).thenReturn(Optional.of(sampleAppointment(id)));

        assertThrows(ResourceNotFoundException.class, () -> appointmentService.delete(id, currentUserId, UserRole.AGENT));

        verify(calendarSync, never()).deleteAppointmentEvent(any());
        verify(appointmentRepository, never()).deleteById(any());
    }

    @Test
    void deleteAllowsAdminForForeignAppointment() {
        UUID id = UUID.randomUUID();
        when(appointmentRepository.findById(id)).thenReturn(Optional.of(sampleAppointment(id)));

        appointmentService.delete(id, currentUserId, UserRole.ADMIN);

        verify(appointmentRepository).deleteById(id);
        verify(auditService).log(eq(currentUserId), eq("Appointment"), eq(id), eq(AuditAction.DELETE));
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(appointmentRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> appointmentService.findById(id, currentUserId));
    }

    @Test
    void findAllReturnsPagedResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        Appointment a = sampleAppointment(UUID.randomUUID());
        Page<Appointment> page = new PageImpl<>(List.of(a), pageable, 1);
        when(appointmentRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Appointment>>any(), eq(pageable))).thenReturn(page);

        PageResponse<AppointmentResponse> result = appointmentService.findAll(new AppointmentFilter(null, null, null), pageable, currentUserId);

        assertEquals(1, result.totalElements());
        assertNotNull(result.content().get(0));
    }

    @Test
    void updateRejectsInvalidStatus() {
        UUID id = UUID.randomUUID();
        Appointment existing = sampleAppointment(id);
        when(appointmentRepository.findById(id)).thenReturn(Optional.of(existing));

        UpdateAppointmentRequest req = new UpdateAppointmentRequest(null, null, "INVALID", null);

        assertThrows(BadRequestException.class, () -> appointmentService.update(id, req, currentUserId, UserRole.ADMIN));
    }

    @Test
    void updateThrowsForbiddenWhenAgentEditsOtherUserAppointment() {
        UUID id = UUID.randomUUID();
        Appointment existing = sampleAppointment(id);
        existing.setUserId(UUID.randomUUID());
        when(appointmentRepository.findById(id)).thenReturn(Optional.of(existing));

        UpdateAppointmentRequest req = new UpdateAppointmentRequest(null, null, "CONFIRMED", null);

        assertThrows(ForbiddenException.class, () -> appointmentService.update(id, req, currentUserId, UserRole.AGENT));
    }

    private static Lead sampleLead(UUID id, UUID createdBy) {
        return Lead.builder()
            .id(id)
            .firstName("John")
            .lastName("Doe")
            .status(LeadStatus.NEW)
            .source(LeadSource.MANUAL)
            .createdBy(createdBy)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private static Appointment sampleAppointment(UUID id) {
        return Appointment.builder()
            .id(id)
            .leadId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .scheduledAt(Instant.now().plusSeconds(3600))
            .durationMinutes(30)
            .status(AppointmentStatus.PENDING)
            .notes("test")
            .build();
    }
}