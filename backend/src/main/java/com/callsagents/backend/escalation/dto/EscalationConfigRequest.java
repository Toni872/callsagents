package com.callsagents.backend.escalation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record EscalationConfigRequest(
    Boolean escalationEnabled,
    @Min(1) @Max(10080) Integer replyTimeoutMinutes,
    @Size(max = 2000) String followupMessage,
    @Size(max = 100) String voiceAgentId
) {
}
