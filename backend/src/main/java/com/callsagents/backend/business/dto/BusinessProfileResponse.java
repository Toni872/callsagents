package com.callsagents.backend.business.dto;

import com.callsagents.backend.business.entity.BusinessProfile;

import java.time.Instant;
import java.util.UUID;

public record BusinessProfileResponse(
    UUID id,
    UUID userId,
    String companyName,
    String website,
    String industry,
    String services,
    String tone,
    String botName,
    String greeting,
    String chatColor,
    Boolean escalationEnabled,
    Integer replyTimeoutMinutes,
    String followupMessage,
    String voiceAgentId,
    Boolean onboardingComplete,
    Instant createdAt,
    Instant updatedAt
) {
    public static BusinessProfileResponse fromEntity(BusinessProfile profile) {
        return new BusinessProfileResponse(
            profile.getId(),
            profile.getUser().getId(),
            profile.getCompanyName(),
            profile.getWebsite(),
            profile.getIndustry(),
            profile.getServices(),
            profile.getTone(),
            profile.getBotName(),
            profile.getGreeting(),
            profile.getChatColor(),
            profile.getEscalationEnabled(),
            profile.getReplyTimeoutMinutes(),
            profile.getFollowupMessage(),
            profile.getVoiceAgentId(),
            profile.getOnboardingComplete(),
            profile.getCreatedAt(),
            profile.getUpdatedAt()
        );
    }
}
