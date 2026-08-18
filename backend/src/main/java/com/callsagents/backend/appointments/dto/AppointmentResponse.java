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
            externalEventUrl(appointment)
        );
    }

    /**
     * Deep link to the event in Google Calendar. Prefers the provider's own
     * canonical link (Google htmlLink, stored at sync time — always works).
     * Falls back to the legacy base64(eid) construction for events synced
     * before the URL was stored; that format only works on the primary
     * calendar, so a stale event may still 404 until re-synced.
     */
    private static String externalEventUrl(Appointment appointment) {
        String eventId = appointment.getExternalEventId();
        if (eventId == null || eventId.isBlank()) return null;
        if (!"GOOGLE".equalsIgnoreCase(appointment.getExternalProvider())) return null;
        if (appointment.getExternalEventUrl() != null && !appointment.getExternalEventUrl().isBlank()) {
            return appointment.getExternalEventUrl();
        }
        String eid = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(eventId.getBytes(StandardCharsets.UTF_8));
        return "https://calendar.google.com/calendar/event?eid=" + eid;
    }
}
