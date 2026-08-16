package com.callsagents.backend.campaigns.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DTO-level validation for the 5 voice-agent fields (FR-2, NFR-6):
 * length limits, optional URL website, and backward compatibility with
 * legacy payloads that omit the new fields.
 */
class CampaignDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static CreateCampaignRequest create(String company, String website) {
        return new CreateCampaignRequest(
            "Promo Q1", null, null, null, null,
            company, website, "SaaS", "CRM", "cercano");
    }

    @Test
    @DisplayName("create: valid website URL is accepted")
    void create_validWebsiteAccepted() {
        assertThat(validator.validate(create("Acme", "https://acme.com"))).isEmpty();
        assertThat(validator.validate(create("Acme", "acme.com"))).isEmpty();
    }

    @Test
    @DisplayName("create: invalid website URL is rejected")
    void create_invalidWebsiteRejected() {
        Set<ConstraintViolation<CreateCampaignRequest>> violations =
            validator.validate(create("Acme", "not a url"));

        assertThat(violations).isNotEmpty();
        assertThat(violations)
            .anyMatch(v -> v.getPropertyPath().toString().equals("website"));
    }

    @Test
    @DisplayName("create: empty or null website is accepted (field optional)")
    void create_emptyOrNullWebsiteAccepted() {
        assertThat(validator.validate(create("Acme", ""))).isEmpty();
        assertThat(validator.validate(create("Acme", null))).isEmpty();
    }

    @Test
    @DisplayName("create: legacy payload without the 5 voice fields is still valid (NFR-6)")
    void create_legacyPayloadWithoutVoiceFieldsAccepted() {
        CreateCampaignRequest req = new CreateCampaignRequest(
            "Legacy", null, null, null, "script text", null, null, null, null, null);

        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    @DisplayName("create: company longer than 255 chars is rejected")
    void create_companyOver255Rejected() {
        CreateCampaignRequest req = create("A".repeat(256), null);

        assertThat(validator.validate(req))
            .anyMatch(v -> v.getPropertyPath().toString().equals("company"));
    }

    @Test
    @DisplayName("create: services longer than 65535 chars is rejected (TEXT column)")
    void create_servicesOver65535Rejected() {
        CreateCampaignRequest req = new CreateCampaignRequest(
            "Promo", null, null, null, null,
            "Acme", null, "SaaS", "S".repeat(65536), "cercano");

        assertThat(validator.validate(req))
            .anyMatch(v -> v.getPropertyPath().toString().equals("services"));
    }

    @Test
    @DisplayName("update: a voice-only update passes isAnyFieldPresent")
    void update_isAnyFieldPresent_voiceOnlyAccepted() {
        UpdateCampaignRequest req = new UpdateCampaignRequest(
            null, null, null, null, null, null, "Acme", null, null, null, null);

        assertThat(req.isAnyFieldPresent()).isTrue();
    }

    @Test
    @DisplayName("update: all-null update fails isAnyFieldPresent")
    void update_isAnyFieldPresent_allNullRejected() {
        UpdateCampaignRequest req = new UpdateCampaignRequest(
            null, null, null, null, null, null, null, null, null, null, null);

        assertThat(req.isAnyFieldPresent()).isFalse();
    }

    @Test
    @DisplayName("preview request: valid website accepted, invalid rejected")
    void previewRequest_websiteValidation() {
        assertThat(validator.validate(new VoicePromptPreviewRequest(
            "Acme", "https://acme.com", "SaaS", "CRM", "cercano"))).isEmpty();

        Set<ConstraintViolation<VoicePromptPreviewRequest>> violations =
            validator.validate(new VoicePromptPreviewRequest("Acme", "nope", null, null, null));
        assertThat(violations)
            .anyMatch(v -> v.getPropertyPath().toString().equals("website"));
    }
}
