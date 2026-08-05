package com.callsagents.backend.calls.dto;

import com.callsagents.backend.calls.entity.CallOutcome;
import com.callsagents.backend.calls.entity.CallStatus;

import java.util.UUID;

public record CallFilter(
    UUID campaignId,
    UUID userId,
    UUID leadId,
    CallStatus status,
    CallOutcome outcome
) {
}
