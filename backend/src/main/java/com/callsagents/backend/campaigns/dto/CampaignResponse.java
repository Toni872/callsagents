package com.callsagents.backend.campaigns.dto;

import com.callsagents.backend.auth.dto.UserDto;
import com.callsagents.backend.campaigns.entity.Campaign;
import com.callsagents.backend.campaigns.entity.CampaignStatus;

import java.time.Instant;
import java.util.UUID;

public record CampaignResponse(
    UUID id,
    String name,
    String description,
    CampaignStatus status,
    Instant startAt,
    Instant endAt,
    String script,
    UserDto createdBy,
    Instant createdAt,
    Instant updatedAt
) {
    public static CampaignResponse fromEntity(Campaign campaign, UserDto creator) {
        return new CampaignResponse(
            campaign.getId(),
            campaign.getName(),
            campaign.getDescription(),
            campaign.getStatus(),
            campaign.getStartAt(),
            campaign.getEndAt(),
            campaign.getScript(),
            creator,
            campaign.getCreatedAt(),
            campaign.getUpdatedAt()
        );
    }
}
