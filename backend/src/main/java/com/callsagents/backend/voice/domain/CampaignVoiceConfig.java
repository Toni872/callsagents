package com.callsagents.backend.voice.domain;

/**
 * Voice-agent configuration values attached to a campaign (or typed in the
 * preview form). Mirrors the 5 voice columns of the campaigns table.
 */
public record CampaignVoiceConfig(
    String company,
    String website,
    String industry,
    String services,
    String tone
) {
    public boolean isEmpty() {
        return isBlank(company)
            && isBlank(website)
            && isBlank(industry)
            && isBlank(services)
            && isBlank(tone);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
