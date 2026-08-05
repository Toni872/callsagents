package com.callsagents.backend.appointments.dto;

import com.callsagents.backend.appointments.entity.Appointment;
import com.callsagents.backend.appointments.entity.AppointmentStatus;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
    UUID id,
    UUID leadId,
    UUID userId,
    Instant scheduledAt,
    Integer durationMinutes,
    AppointmentStatus status,
    String notes,
    Instant createdAt,
    Instant updatedAt
) {
    public static AppointmentResponse fromEntity(Appointment appointment) {
        return new AppointmentResponse(
            appointment.getId(),
            appointment.getLeadId(),
            appointment.getUserId(),
            appointment.getScheduledAt(),
            appointment.getDurationMinutes(),
            appointment.getStatus(),
            appointment.getNotes(),
            appointment.getCreatedAt(),
            appointment.getUpdatedAt()
        );
    }
}
