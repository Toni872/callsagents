package com.callsagents.backend.campaigns.dto;

import com.callsagents.backend.campaigns.entity.CampaignStatus;

import java.util.UUID;

public record CampaignFilter(
    CampaignStatus status,
    UUID createdById,
    Boolean hasVoiceConfig
) {
}
