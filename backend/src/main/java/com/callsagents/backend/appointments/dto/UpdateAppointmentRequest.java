package com.callsagents.backend.appointments.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

import java.time.Instant;

public record UpdateAppointmentRequest(
    Instant scheduledAt,
    @Min(1) Integer durationMinutes,
    String status,
    String notes
) {
    @AssertTrue(message = "At least one field must be provided for update")
    public boolean isAnyFieldPresent() {
        return scheduledAt != null
            || durationMinutes != null
            || status != null
            || notes != null;
    }
}
