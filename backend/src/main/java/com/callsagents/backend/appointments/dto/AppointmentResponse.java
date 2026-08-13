package com.callsagents.backend.appointments.dto;

import com.callsagents.backend.appointments.entity.Appointment;
import com.callsagents.backend.appointments.entity.AppointmentStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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
    Instant updatedAt,
    String externalEventId,
    String externalEventUrl
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
            appointment.getUpdatedAt(),
            appointment.getExternalEventId(),
            googleEventUrl(appointment)
        );
    }

    /**
     * One-click deep link to the event in Google Calendar.
     * Format: https://calendar.google.com/calendar/event?eid=<base64url(eventId)>
     * Only meaningful when the appointment was actually synced to Google.
     */
    private static String googleEventUrl(Appointment appointment) {
        String eventId = appointment.getExternalEventId();
        if (eventId == null || eventId.isBlank()) return null;
        if (!"GOOGLE".equalsIgnoreCase(appointment.getExternalProvider())) return null;
        String eid = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(eventId.getBytes(StandardCharsets.UTF_8));
        return "https://calendar.google.com/calendar/event?eid=" + eid;
    }
}
