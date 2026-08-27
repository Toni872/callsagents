package com.callsagents.backend.business.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessProfileRequest(
    @NotBlank @Size(max = 255) String companyName,

    @Size(max = 500) String website,

    @Size(max = 100) String industry,

    String services,

    @Size(max = 20) String tone,

    @Size(max = 100) String botName,

    String greeting,

    @Size(max = 7) String chatColor,

    Boolean escalationEnabled,

    @Min(1) @Max(10080) Integer replyTimeoutMinutes,

    @Size(max = 2000) String followupMessage,

    @Size(max = 100) String voiceAgentId
) {
}
