package com.callsagents.backend.campaigns.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateCampaignRequest(
    @Size(max = 255) String name,
    @Size(max = 4096) String description,
    Instant startAt,
    Instant endAt,
    @Size(max = 65535) String script,
    String status,
    @Size(max = 255) String company,
    @Size(max = 255)
    @Pattern(regexp = VoiceConfigConstraints.WEBSITE_URL_PATTERN, message = VoiceConfigConstraints.WEBSITE_URL_MESSAGE)
    String website,
    @Size(max = 255) String industry,
    @Size(max = VoiceConfigConstraints.SERVICES_MAX_LENGTH) String services,
    @Size(max = 255) String tone
) {
    @AssertTrue(message = "At least one field must be provided for update")
    public boolean isAnyFieldPresent() {
        return name != null
            || description != null
            || startAt != null
            || endAt != null
            || script != null
            || status != null
            || company != null
            || website != null
            || industry != null
            || services != null
            || tone != null;
    }
}
