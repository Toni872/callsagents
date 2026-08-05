package com.callsagents.backend.calls.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateCallRequest(
    @NotNull java.util.UUID campaignId,
    @NotNull java.util.UUID leadId,
    @NotNull java.util.UUID userId,
    Instant startedAt,
    Instant endedAt,
    Integer durationSeconds,
    String status,
    String outcome,
    String recordingUrl,
    String providerCallId,
    String notes
) {
}
