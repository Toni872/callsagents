package com.callsagents.backend.campaigns.dto;

/**
 * Shared validation constraints for the campaign voice-agent fields.
 * The same rules apply to Create/UpdateCampaignRequest and
 * VoicePromptPreviewRequest, so they live in a single place.
 */
public final class VoiceConfigConstraints {

    /**
     * Optional website URL. The whole pattern is wrapped in an optional group so
     * an empty string is accepted: the field is optional and the service
     * normalizes blank values to null (spec: "una URL válida o un campo vacío
     * se aceptan").
     */
    public static final String WEBSITE_URL_PATTERN =
        "^((https?://)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(/\\S*)?)?$";

    public static final String WEBSITE_URL_MESSAGE =
        "website debe ser una URL válida (p.ej. https://acme.com)";

    public static final int FIELD_MAX_LENGTH = 255;

    public static final int SERVICES_MAX_LENGTH = 65535;

    private VoiceConfigConstraints() {
    }
}
