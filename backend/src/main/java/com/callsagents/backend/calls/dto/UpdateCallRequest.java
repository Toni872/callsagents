package com.callsagents.backend.calls.dto;

import jakarta.validation.constraints.AssertTrue;

import java.time.Instant;

public record UpdateCallRequest(
    Instant startedAt,
    Instant endedAt,
    Integer durationSeconds,
    String status,
    String outcome,
    String recordingUrl,
    String providerCallId,
    String notes
) {
    @AssertTrue(message = "At least one field must be provided for update")
    public boolean isAnyFieldPresent() {
        return startedAt != null
            || endedAt != null
            || durationSeconds != null
            || status != null
            || outcome != null
            || recordingUrl != null
            || providerCallId != null
            || notes != null;
    }
}
