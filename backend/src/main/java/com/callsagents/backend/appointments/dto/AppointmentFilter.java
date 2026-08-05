package com.callsagents.backend.appointments.dto;

import com.callsagents.backend.appointments.entity.AppointmentStatus;

import java.util.UUID;

public record AppointmentFilter(
    UUID leadId,
    UUID userId,
    AppointmentStatus status
) {
}
