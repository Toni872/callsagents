package com.callsagents.backend.appointments.service;

import com.callsagents.backend.appointments.dto.AppointmentFilter;
import com.callsagents.backend.appointments.dto.AppointmentResponse;
import com.callsagents.backend.appointments.dto.CreateAppointmentRequest;
import com.callsagents.backend.appointments.dto.UpdateAppointmentRequest;
import com.callsagents.backend.appointments.entity.Appointment;
import com.callsagents.backend.appointments.entity.AppointmentStatus;
import com.callsagents.backend.appointments.repository.AppointmentRepository;
import com.callsagents.backend.audit.entity.AuditAction;
import com.callsagents.backend.common.audit.AuditService;
import com.callsagents.backend.common.dto.PageResponse;
import com.callsagents.backend.common.exception.BadRequestException;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
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

    @InjectMocks
    private AppointmentService appointmentService;

    private final UUID currentUserId = UUID.randomUUID();

    @Test
    void createBuildsAndSavesAppointmentWithPendingDefault() {
        UUID id = UUID.randomUUID();
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

        AppointmentResponse response = appointmentService.create(req, currentUserId);

        assertEquals(id, response.id());
        assertEquals(AppointmentStatus.PENDING, response.status());
        verify(auditService).log(eq(currentUserId), eq("Appointment"), eq(id), eq(AuditAction.CREATE));
    }

    @Test
    void createAcceptsAnyPositiveDuration() {
        UUID id = UUID.randomUUID();
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment arg = inv.getArgument(0);
            arg.setId(id);
            arg.setCreatedAt(Instant.now());
            arg.setUpdatedAt(Instant.now());
            return arg;
        });

        CreateAppointmentRequest req = new CreateAppointmentRequest(
            UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(3600), 1, null, null);

        AppointmentResponse response = appointmentService.create(req, currentUserId);

        assertEquals(1, response.durationMinutes());
    }

    @Test
    void createWithExplicitStatus() {
        UUID id = UUID.randomUUID();
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

        AppointmentResponse response = appointmentService.create(req, currentUserId);

        assertEquals(AppointmentStatus.CONFIRMED, response.status());
    }

    @Test
    void createRejectsInvalidStatus() {
        CreateAppointmentRequest req = new CreateAppointmentRequest(
            UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(3600),
            30, "INVALID_STATUS", null);
        assertThrows(BadRequestException.class, () -> appointmentService.create(req, currentUserId));
    }

    @Test
    void updateThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(appointmentRepository.findById(id)).thenReturn(Optional.empty());
        UpdateAppointmentRequest req = new UpdateAppointmentRequest(null, null, "CONFIRMED", null);
        assertThrows(ResourceNotFoundException.class, () -> appointmentService.update(id, req, currentUserId));
    }

    @Test
    void updateAppliesProvidedFields() {
        UUID id = UUID.randomUUID();
        Appointment existing = sampleAppointment(id);
        existing.setNotes("old");
        when(appointmentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAppointmentRequest req = new UpdateAppointmentRequest(null, 45, "CONFIRMED", "new notes");

        AppointmentResponse response = appointmentService.update(id, req, currentUserId);

        assertEquals(45, response.durationMinutes());
        assertEquals(AppointmentStatus.CONFIRMED, response.status());
        assertEquals("new notes", response.notes());
        verify(auditService).log(eq(currentUserId), eq("Appointment"), eq(id), eq(AuditAction.UPDATE));
    }

    @Test
    void deleteThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(appointmentRepository.existsById(id)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> appointmentService.delete(id, currentUserId));
        verify(appointmentRepository, never()).deleteById(any());
    }

    @Test
    void deleteSucceedsAndAudits() {
        UUID id = UUID.randomUUID();
        when(appointmentRepository.existsById(id)).thenReturn(true);

        appointmentService.delete(id, currentUserId);

        verify(appointmentRepository).deleteById(id);
        verify(auditService).log(eq(currentUserId), eq("Appointment"), eq(id), eq(AuditAction.DELETE));
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(appointmentRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> appointmentService.findById(id));
    }

    @Test
    void findAllReturnsPagedResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        Appointment a = sampleAppointment(UUID.randomUUID());
        Page<Appointment> page = new PageImpl<>(List.of(a), pageable, 1);
        when(appointmentRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PageResponse<AppointmentResponse> result = appointmentService.findAll(new AppointmentFilter(null, null, null), pageable);

        assertEquals(1, result.totalElements());
        assertNotNull(result.content().get(0));
    }

    @Test
    void updateRejectsInvalidStatus() {
        UUID id = UUID.randomUUID();
        Appointment existing = sampleAppointment(id);
        when(appointmentRepository.findById(id)).thenReturn(Optional.of(existing));

        UpdateAppointmentRequest req = new UpdateAppointmentRequest(null, null, "INVALID", null);

        assertThrows(BadRequestException.class, () -> appointmentService.update(id, req, currentUserId));
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
