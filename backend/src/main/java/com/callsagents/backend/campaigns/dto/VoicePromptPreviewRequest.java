package com.callsagents.backend.campaigns.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VoicePromptPreviewRequest(
    @Size(max = 255) String company,
    @Size(max = 255)
    @Pattern(regexp = VoiceConfigConstraints.WEBSITE_URL_PATTERN, message = VoiceConfigConstraints.WEBSITE_URL_MESSAGE)
    String website,
    @Size(max = 255) String industry,
    @Size(max = VoiceConfigConstraints.SERVICES_MAX_LENGTH) String services,
    @Size(max = 255) String tone
) {
}
