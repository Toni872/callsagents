package com.callsagents.backend.voice.service;

import com.callsagents.backend.voice.domain.CampaignVoiceConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Structural tests for the prompt composition — intentionally NOT golden
 * tests over the seed text. They assert section presence, interpolation,
 * graceful omission of empty sections and brace escaping, so a seed swap
 * never breaks them (design §9).
 */
class PromptComposerTest {

    private final PromptComposer composer = new PromptComposer();

    @Test
    @DisplayName("compose: with all 5 fields set, every section is present with values interpolated")
    void compose_allFieldsSet() {
        var cfg = new CampaignVoiceConfig(
            "Acme", "https://acme.example", "SaaS", "CRM, automatización", "cercano");

        String prompt = composer.compose(cfg);

        assertThat(prompt)
            .contains("Eres el asistente virtual de Acme, una empresa del sector SaaS.")
            .contains("Contactas con personas que han mostrado interés y les ofreces los servicios de Acme")
            .contains("## Contexto de la campaña")
            .contains("- Empresa: Acme")
            .contains("- Web: https://acme.example")
            .contains("- Sector: SaaS")
            .contains("- Servicios: CRM, automatización")
            .contains("- Tono: cercano")
            .contains("- Si mencionan la web, indícala: https://acme.example.")
            .contains("## Confirmación de email (obligatorio)")
            .contains("## Límites")
            .contains("- No prometas nada que Acme no pueda cumplir.")
            .doesNotContain("{{");
    }

    @Test
    @DisplayName("compose: with only company set, empty context blocks are omitted gracefully")
    void compose_partialOnlyCompany() {
        var cfg = new CampaignVoiceConfig("Acme", null, "  ", null, "");

        String prompt = composer.compose(cfg);

        assertThat(prompt)
            .contains("Eres el asistente virtual de Acme.")
            .doesNotContain("una empresa del sector")
            .contains("- Empresa: Acme")
            .doesNotContain("- Web:")
            .doesNotContain("- Sector:")
            .doesNotContain("- Servicios:")
            .doesNotContain("- Tono:")
            .doesNotContain("Si mencionan la web")
            .contains("- No prometas nada que Acme no pueda cumplir.")
            .doesNotContain("{{");
    }

    @Test
    @DisplayName("compose: with only website set, intro is omitted but web line and context item survive")
    void compose_partialOnlyWebsite() {
        var cfg = new CampaignVoiceConfig(null, "https://acme.example", null, null, null);

        String prompt = composer.compose(cfg);

        assertThat(prompt)
            .doesNotContain("Eres el asistente virtual")
            .doesNotContain("## Contexto de la campaña")
            .contains("- Web: https://acme.example")
            .contains("- Si mencionan la web, indícala: https://acme.example.")
            .doesNotContain("- Empresa:")
            .doesNotContain("- No prometas nada que")
            .doesNotContain("{{");
    }

    @Test
    @DisplayName("compose: literal braces in user values are preserved, never re-interpolated")
    void compose_literalBracesNotInterpolated() {
        var cfg = new CampaignVoiceConfig(
            "Acme", null, "SaaS", "Precio {{company}} y {{variable}}", null);

        String prompt = composer.compose(cfg);

        assertThat(prompt)
            .contains("- Servicios: Precio {{company}} y {{variable}}")
            .doesNotContain("Precio Acme y");
    }

    @Test
    @DisplayName("compose: with all fields blank, only the static skeleton remains")
    void compose_allBlank_staticSkeleton() {
        var cfg = new CampaignVoiceConfig(null, "", "  ", null, null);

        String prompt = composer.compose(cfg);

        assertThat(prompt)
            .doesNotContain("Eres el asistente virtual")
            .doesNotContain("## Contexto de la campaña")
            .contains("## Cómo hablar")
            .contains("- Habla en español de España, con tono profesional y cercano.")
            .contains("## Confirmación de email (obligatorio)")
            .contains("1. Pide el email de forma natural")
            .contains("## Límites")
            .contains("- Si la persona no está interesada, despídete con cortesía y sin insistir.")
            .doesNotContain("- No prometas nada que")
            .doesNotContain("{{");
    }

    @Test
    @DisplayName("compose: null config is a programming error")
    void compose_nullConfigThrows() {
        assertThatThrownBy(() -> composer.compose(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("buildVariables: all fields set yields campaign_prompt first plus the 5 individual fields, in stable order")
    void buildVariables_allFieldsSet() {
        var cfg = new CampaignVoiceConfig(
            "  Acme  ", "https://acme.example", "SaaS", "CRM", "cercano");

        Map<String, String> vars = composer.buildVariables(cfg);

        assertThat(vars).hasSize(6);
        assertThat(vars.keySet()).containsExactly(
            "campaign_prompt", "company", "website", "industry", "services", "tone");
        assertThat(vars.get("company")).isEqualTo("Acme");
        assertThat(vars.get("website")).isEqualTo("https://acme.example");
        assertThat(vars.get("industry")).isEqualTo("SaaS");
        assertThat(vars.get("services")).isEqualTo("CRM");
        assertThat(vars.get("tone")).isEqualTo("cercano");
        assertThat(vars.get("campaign_prompt")).isEqualTo(composer.compose(cfg));
    }

    @Test
    @DisplayName("buildVariables: with partial fields only non-blank values are included")
    void buildVariables_partialOnlyNonBlank() {
        var cfg = new CampaignVoiceConfig("Acme", null, null, "CRM", null);

        Map<String, String> vars = composer.buildVariables(cfg);

        assertThat(vars.keySet()).containsExactly("campaign_prompt", "company", "services");
        assertThat(vars.get("company")).isEqualTo("Acme");
        assertThat(vars.get("services")).isEqualTo("CRM");
    }

    @Test
    @DisplayName("buildVariables: empty config yields an empty map (no campaign_prompt)")
    void buildVariables_emptyConfig_emptyMap() {
        var cfg = new CampaignVoiceConfig(null, null, " ", "", null);

        Map<String, String> vars = composer.buildVariables(cfg);

        assertThat(vars).isEmpty();
    }

    @Test
    @DisplayName("buildVariables: null config is a programming error")
    void buildVariables_nullConfigThrows() {
        assertThatThrownBy(() -> composer.buildVariables(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
