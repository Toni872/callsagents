package com.callsagents.backend.calls.dto;

import com.callsagents.backend.calls.entity.Call;
import com.callsagents.backend.calls.entity.CallOutcome;
import com.callsagents.backend.calls.entity.CallStatus;

import java.time.Instant;
import java.util.UUID;

public record CallResponse(
    UUID id,
    UUID campaignId,
    UUID leadId,
    UUID userId,
    Instant startedAt,
    Instant endedAt,
    Integer durationSeconds,
    CallStatus status,
    CallOutcome outcome,
    String recordingUrl,
    String providerCallId,
    String notes,
    Instant createdAt,
    Instant updatedAt
) {
    public static CallResponse fromEntity(Call call) {
        return new CallResponse(
            call.getId(),
            call.getCampaignId(),
            call.getLeadId(),
            call.getUserId(),
            call.getStartedAt(),
            call.getEndedAt(),
            call.getDurationSeconds(),
            call.getStatus(),
            call.getOutcome(),
            call.getRecordingUrl(),
            call.getProviderCallId(),
            call.getNotes(),
            call.getCreatedAt(),
            call.getUpdatedAt()
        );
    }
}
