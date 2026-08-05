package com.callsagents.backend.campaigns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateCampaignRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 4096) String description,
    Instant startAt,
    Instant endAt,
    @Size(max = 65535) String script
) {
}
