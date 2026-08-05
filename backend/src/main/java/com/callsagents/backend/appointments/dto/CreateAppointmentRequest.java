package com.callsagents.backend.appointments.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateAppointmentRequest(
    @NotNull UUID leadId,
    @NotNull UUID userId,
    @NotNull Instant scheduledAt,
    @NotNull @Min(1) Integer durationMinutes,
    String status,
    String notes
) {
}
