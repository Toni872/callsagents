package com.callsagents.backend.escalation.dto;

import com.callsagents.backend.escalation.entity.Escalation;
import com.callsagents.backend.escalation.entity.EscalationStage;

import java.time.Instant;
import java.util.UUID;

public record EscalationResponse(
    UUID id,
    UUID leadId,
    UUID userId,
    EscalationStage stage,
    Instant followupSentAt,
    Instant waitingUntil,
    Instant voiceCalledAt,
    String providerCallId,
    String voiceOutcome,
    Instant createdAt,
    Instant updatedAt
) {
    public static EscalationResponse from(Escalation e) {
        return new EscalationResponse(
            e.getId(),
            e.getLead() != null ? e.getLead().getId() : null,
            e.getUserId(),
            e.getStage(),
            e.getFollowupSentAt(),
            e.getWaitingUntil(),
            e.getVoiceCalledAt(),
            e.getProviderCallId(),
            e.getVoiceOutcome(),
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }
}
