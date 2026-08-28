package com.callsagents.backend.appointments.service;

import com.callsagents.backend.appointments.dto.AppointmentFilter;
import com.callsagents.backend.appointments.dto.AppointmentResponse;
import com.callsagents.backend.appointments.dto.CreateAppointmentRequest;
import com.callsagents.backend.appointments.dto.UpdateAppointmentRequest;
import com.callsagents.backend.appointments.entity.Appointment;
import com.callsagents.backend.appointments.entity.AppointmentStatus;
import com.callsagents.backend.appointments.repository.AppointmentRepository;
import com.callsagents.backend.appointments.repository.AppointmentSpecifications;
import com.callsagents.backend.audit.entity.AuditAction;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.calendar.service.CalendarSyncService;
import com.callsagents.backend.common.audit.AuditService;
import com.callsagents.backend.common.dto.PageResponse;
import com.callsagents.backend.common.exception.BadRequestException;
import com.callsagents.backend.common.exception.ForbiddenException;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import com.callsagents.backend.leads.repository.LeadRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AuditService auditService;
    private final CalendarSyncService calendarSync;
    private final LeadRepository leadRepository;

    public AppointmentService(
        AppointmentRepository appointmentRepository,
        AuditService auditService,
        CalendarSyncService calendarSync,
        LeadRepository leadRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.auditService = auditService;
        this.calendarSync = calendarSync;
        this.leadRepository = leadRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> findAll(AppointmentFilter filter, Pageable pageable, UUID currentUserId) {
        Specification<Appointment> spec = AppointmentSpecifications.build(filter);
        spec = (spec == null)
            ? AppointmentSpecifications.ownedBy(currentUserId)
            : spec.and(AppointmentSpecifications.ownedBy(currentUserId));
        Page<Appointment> page = appointmentRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(AppointmentResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public AppointmentResponse findById(UUID id, UUID currentUserId) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));
        if (appointment.getUserId() == null || !appointment.getUserId().equals(currentUserId)) {
            throw new ResourceNotFoundException("Appointment not found: " + id);
        }
        return AppointmentResponse.fromEntity(appointment);
    }

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest req, UUID currentUserId) {
        if (req.leadId() != null && !leadRepository.existsById(req.leadId())) {
            throw new BadRequestException("Lead not found: " + req.leadId());
        }

        Appointment appointment = Appointment.builder()
            .leadId(req.leadId())
            .userId(currentUserId)
            .scheduledAt(req.scheduledAt())
            .durationMinutes(req.durationMinutes())
            .status(parseStatusOrDefault(req.status(), AppointmentStatus.PENDING))
            .notes(req.notes())
            .build();

        Appointment saved = appointmentRepository.save(appointment);
        auditService.log(currentUserId, "Appointment", saved.getId(), AuditAction.CREATE);
        // Push to external calendar (Google/Outlook) if user has an integration.
        // syncAppointment uses its own transaction (REQUIRES_NEW) so a sync failure
        // doesn't roll back the appointment creation.
        calendarSync.syncAppointment(saved);
        return AppointmentResponse.fromEntity(saved);
    }

    @Transactional
    public AppointmentResponse update(UUID id, UpdateAppointmentRequest req, UUID currentUserId, UserRole role) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));

        if (appointment.getUserId() != null && !appointment.getUserId().equals(currentUserId)
            && role != UserRole.ADMIN && role != UserRole.SUPERVISOR) {
            throw new ForbiddenException("You can only update your own appointments");
        }

        if (req.scheduledAt() != null) appointment.setScheduledAt(req.scheduledAt());
        if (req.durationMinutes() != null) appointment.setDurationMinutes(req.durationMinutes());
        if (req.status() != null) appointment.setStatus(parseStatus(req.status()));
        if (req.notes() != null) appointment.setNotes(req.notes());

        Appointment saved = appointmentRepository.save(appointment);
        auditService.log(currentUserId, "Appointment", saved.getId(), AuditAction.UPDATE);

        if (saved.getStatus() == AppointmentStatus.CANCELLED) {
            // Cancel semantics: remove the Google event and forget the linkage.
            // (An uncancel later re-creates the event via updateAppointment fallback.)
            calendarSync.deleteAppointmentEvent(saved);
            saved.setExternalEventId(null);
            saved.setExternalProvider(null);
            saved.setExternalSyncedAt(null);
            appointmentRepository.save(saved);
        } else {
            // Push edits to Google (creates the event if it was never synced).
            calendarSync.updateAppointment(saved);
        }
        return AppointmentResponse.fromEntity(saved);
    }

    @Transactional
    public void delete(UUID id, UUID currentUserId) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));
        // Best-effort: remove the Google event before the row is gone.
        calendarSync.deleteAppointmentEvent(appointment);
        appointmentRepository.deleteById(id);
        auditService.log(currentUserId, "Appointment", id, AuditAction.DELETE);
    }

    private static AppointmentStatus parseStatus(String raw) {
        try {
            return AppointmentStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid appointment status: " + raw);
        }
    }

    private static AppointmentStatus parseStatusOrDefault(String raw, AppointmentStatus fallback) {
        return (raw == null || raw.isBlank()) ? fallback : parseStatus(raw);
    }
}
