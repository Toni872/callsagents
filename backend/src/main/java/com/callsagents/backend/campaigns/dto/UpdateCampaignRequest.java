package com.callsagents.backend.campaigns.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateCampaignRequest(
    @Size(max = 255) String name,
    @Size(max = 4096) String description,
    Instant startAt,
    Instant endAt,
    @Size(max = 65535) String script,
    String status
) {
    @AssertTrue(message = "At least one field must be provided for update")
    public boolean isAnyFieldPresent() {
        return name != null
            || description != null
            || startAt != null
            || endAt != null
            || script != null
            || status != null;
    }
}
