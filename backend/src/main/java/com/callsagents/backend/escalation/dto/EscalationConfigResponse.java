package com.callsagents.backend.escalation.dto;

import com.callsagents.backend.business.entity.BusinessProfile;

public record EscalationConfigResponse(
    Boolean escalationEnabled,
    Integer replyTimeoutMinutes,
    String followupMessage,
    String voiceAgentId
) {
    public static EscalationConfigResponse from(BusinessProfile profile) {
        return new EscalationConfigResponse(
            profile.getEscalationEnabled(),
            profile.getReplyTimeoutMinutes(),
            profile.getFollowupMessage(),
            profile.getVoiceAgentId()
        );
    }
}
