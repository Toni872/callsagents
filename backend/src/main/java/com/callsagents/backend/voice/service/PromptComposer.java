package com.callsagents.backend.voice.service;

import com.callsagents.backend.voice.domain.CampaignVoiceConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Composes the agent prompt for a campaign from its voice configuration.
 *
 * <p>Structural by design (design §9): sections are included or omitted based
 * on which values are present, user values are interpolated by plain
 * concatenation (never re-parsed, so literal braces in user input are
 * preserved), and the static skeleton never carries unresolved placeholders.
 */
public class PromptComposer {

    public String compose(CampaignVoiceConfig cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("CampaignVoiceConfig must not be null");
        }

        String company = trimmedOrNull(cfg.company());
        String website = trimmedOrNull(cfg.website());
        String industry = trimmedOrNull(cfg.industry());
        String services = trimmedOrNull(cfg.services());
        String tone = trimmedOrNull(cfg.tone());

        StringBuilder sb = new StringBuilder();

        if (company != null) {
            sb.append("Eres el asistente virtual de ").append(company);
            sb.append(industry != null ? ", una empresa del sector " + industry + "." : ".");
            sb.append("\n\n");
            sb.append("Contactas con personas que han mostrado interés y les ofreces los servicios de ")
                .append(company)
                .append(" de forma natural y cercana, sin parecer un robot ni un teleoperador agresivo.");
            sb.append("\n\n");
        }

        boolean contextoPresent = false;
        if (company != null) {
            sb.append("## Contexto de la campaña\n");
            contextoPresent = true;
        }
        if (company != null) {
            sb.append("- Empresa: ").append(company).append("\n");
            contextoPresent = true;
        }
        if (website != null) {
            sb.append("- Web: ").append(website).append("\n");
            contextoPresent = true;
        }
        if (industry != null) {
            sb.append("- Sector: ").append(industry).append("\n");
            contextoPresent = true;
        }
        if (services != null) {
            sb.append("- Servicios: ").append(services).append("\n");
            contextoPresent = true;
        }
        if (tone != null) {
            sb.append("- Tono: ").append(tone).append("\n");
            contextoPresent = true;
        }
        if (contextoPresent) {
            sb.append("\n");
        }

        sb.append("## Cómo hablar\n");
        sb.append("- Habla en español de España, con tono profesional y cercano.\n");
        sb.append("- Sé natural y directo: saluda, pregunta y escucha con atención.\n");
        sb.append("- Evita leer un guion de forma robótica; adapta las frases a la conversación.\n");
        if (website != null) {
            sb.append("- Si mencionan la web, indícala: ").append(website).append(".\n");
        }
        sb.append("\n");

        sb.append("## Confirmación de email (obligatorio)\n");
        sb.append("1. Pide el email de forma natural\n");
        sb.append("2. Repite el email para confirmarlo y evitar errores\n");
        sb.append("3. Verifica que la persona está de acuerdo en recibir información\n");
        sb.append("4. Despídete con cortesía y resume lo acordado\n");
        sb.append("\n");

        sb.append("## Límites\n");
        if (company != null) {
            sb.append("- No prometas nada que ").append(company).append(" no pueda cumplir.\n");
        }
        sb.append("- Si la persona no está interesada, despídete con cortesía y sin insistir.\n");

        return sb.toString();
    }

    /**
     * Dynamic variables for the Retell agent, in stable insertion order:
     * campaign_prompt first, then the individual fields, skipping blank ones.
     * An empty config yields an empty map (no retell_llm_dynamic_variables).
     */
    public Map<String, String> buildVariables(CampaignVoiceConfig cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("CampaignVoiceConfig must not be null");
        }
        Map<String, String> variables = new LinkedHashMap<>();
        if (cfg.isEmpty()) {
            return variables;
        }
        variables.put("campaign_prompt", compose(cfg));
        putIfNotBlank(variables, "company", cfg.company());
        putIfNotBlank(variables, "website", cfg.website());
        putIfNotBlank(variables, "industry", cfg.industry());
        putIfNotBlank(variables, "services", cfg.services());
        putIfNotBlank(variables, "tone", cfg.tone());
        return variables;
    }

    private static void putIfNotBlank(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }

    private static String trimmedOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
