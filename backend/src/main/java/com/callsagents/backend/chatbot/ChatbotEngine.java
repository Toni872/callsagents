package com.callsagents.backend.chatbot;

import com.callsagents.backend.business.entity.BusinessProfile;
import com.callsagents.backend.business.service.BusinessPromptComposer;
import com.callsagents.backend.business.service.BusinessService;
import com.callsagents.backend.escalation.service.EscalationService;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.entity.LeadSource;
import com.callsagents.backend.leads.entity.LeadStatus;
import com.callsagents.backend.leads.repository.LeadRepository;
import com.callsagents.backend.whatsapp.service.GroqService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Channel-agnostic free-text conversational chatbot engine.
 *
 * <p>Conversations flow through Groq AI with state context. No interactive
 * buttons are ever returned — the bot behaves like a human support agent.
 * {@code [LEAD:...]} internal capture tags are invisible to the user.
 * <p>Escalation fires exactly once per session on WhatsApp after a lead with
 * email has been captured and the user's message is affirmative.
 * Web channel never escalates.
 */
@Service
public class ChatbotEngine {

    private static final Logger log = LoggerFactory.getLogger(ChatbotEngine.class);

    private final GroqService groqService;
    private final LeadRepository leadRepository;
    private final BusinessService businessService;
    private final BusinessPromptComposer promptComposer;
    private final EscalationService escalationService;

    private final Cache<String, List<Map<String, String>>> conversationHistory = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .build();
    private final Cache<String, Map<String, String>> leadData = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .build();
    private final Cache<String, String> conversationStep = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .build();

    private static final int MAX_HISTORY = 20;
    private static final int TRIAL_LEAD_LIMIT = 50;

    private static final java.util.regex.Pattern EMAIL_PATTERN =
        java.util.regex.Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    private static final java.util.regex.Pattern NAME_PATTERN =
        java.util.regex.Pattern.compile("(?:mi nombre es|me llamo|soy)\\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private static final java.util.regex.Pattern HUMAN_HANDOFF_PATTERN =
        java.util.regex.Pattern.compile(
            "\\b(?:hablar con (?:una )?(?:persona|humano|gente|alguien|asesor|agente)|" +
            "(?:persona|humano|asesor|agente) (?:real|humano)|" +
            "atenci[oó]n humana|" +
            "quiero (?:hablar|contactar) con|" +
            "\\bhumano\\b)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE
                | java.util.regex.Pattern.UNICODE_CHARACTER_CLASS);

    public ChatbotEngine(GroqService groqService, LeadRepository leadRepository,
                         BusinessService businessService, BusinessPromptComposer promptComposer,
                         EscalationService escalationService) {
        this.groqService = groqService;
        this.leadRepository = leadRepository;
        this.businessService = businessService;
        this.promptComposer = promptComposer;
        this.escalationService = escalationService;
    }

    public void reset(String sessionKey) {
        resetConversation(sessionKey);
    }

    /**
     * Natural free-text greeting personalized from the business profile.
     * No buttons — sets step to "initial".
     */
    public ChatTurn greeting(String sessionKey, UUID businessId) {
        businessId = businessService.resolveOwnerUserId(businessId);
        String botName = "Naiara";
        String companyName = "Script9";
        if (businessId != null) {
            BusinessProfile profile = businessService.getProfileEntityByUserId(businessId);
            if (profile != null) {
                if (profile.getBotName() != null && !profile.getBotName().isBlank()) {
                    botName = profile.getBotName();
                }
                if (profile.getCompanyName() != null && !profile.getCompanyName().isBlank()) {
                    companyName = profile.getCompanyName();
                }
            }
        }
        String body = String.format("¡Hola! Soy %s, tu asistente de IA de %s. ¿En qué puedo ayudarte?", botName, companyName);
        conversationStep.put(sessionKey, "initial");
        return ChatTurn.text(body);
    }

    /**
     * Process an incoming message. Returns a free-text ChatTurn — never carries
     * buttons and contactForm is always false.
     */
    public ChatTurn process(String sessionKey, String message, UUID businessId, Channel channel) {
        String text = message == null ? "" : message.trim();
        businessId = businessService.resolveOwnerUserId(businessId);
        String stepVal = conversationStep.getIfPresent(sessionKey);
        String step = stepVal == null ? "initial" : stepVal;
        log.info("processMessage [{}]: step={} text='{}'", sessionKey, step, text);

        if (isReset(text)) {
            resetConversation(sessionKey);
            return greeting(sessionKey, businessId);
        }

        // Deterministic email capture: extract and merge contact data. The actual
        // save happens once in extractLead below. Mark email as captured so the AI
        // knows the contact data and escalation can trigger on WhatsApp.
        if (containsEmail(text)) {
            Map<String, String> data = extractContactFromUserMessage(text);
            data.putAll(leadData.get(sessionKey, k -> new HashMap<>()));
            leadData.put(sessionKey, data);
            leadData.get(sessionKey, k -> new HashMap<>()).put("emailCaptured", "true");
        }

        List<Map<String, String>> history = conversationHistory.get(sessionKey, k -> new ArrayList<>());

        String systemPrompt = resolveSystemPrompt(businessId);
        String stateCtx = buildStateContext(sessionKey);
        if (stateCtx != null) {
            systemPrompt = systemPrompt + "\n\nESTADO ACTUAL DE LA CONVERSACIÓN:\n" + stateCtx;
        }

        String aiResponse = groqService.chat(systemPrompt, history, text);

        if (GroqService.RATE_LIMITED_SENTINEL.equals(aiResponse)) {
            return ChatTurn.text("Estoy recibiendo muchas peticiones en este momento. Espera unos segundos y repite el mensaje, por favor.");
        }
        if (aiResponse == null) {
            log.warn("Groq returned null for key={}", sessionKey);
            return ChatTurn.text("Perdona, no he podido procesar tu mensaje en este momento. "
                + "¿Podrías intentarlo de nuevo?");
        }

        LeadExtractionResult extraction = extractLead(sessionKey, text, aiResponse, businessId, channel);
        String cleanResponse = extraction.cleanResponse();

        if (cleanResponse == null || cleanResponse.isBlank()) {
            log.warn("Groq returned empty response for key={}, user='{}'", sessionKey, text);
            // NEVER tell the user we didn't understand when we actually captured
            // their contact data. Build a deterministic confirmation instead.
            cleanResponse = buildFallbackReply(text, leadData.getIfPresent(sessionKey));
        }

        history.add(Map.of("role", "user", "content", text));
        history.add(Map.of("role", "assistant", "content", cleanResponse));

        while (history.size() > MAX_HISTORY) {
            history.remove(0);
            history.remove(0);
        }

        // Escalation (WhatsApp only): fire once after email captured + affirmative
        if (channel == Channel.WHATSAPP && hasEmailCaptured(sessionKey) && isAffirmative(text)) {
            if (!hasEscalationFired(sessionKey)) {
                triggerEscalation(sessionKey, businessId);
                leadData.get(sessionKey, k -> new HashMap<>()).put("escalationFired", "true");
            }
        }

        // Human handoff (WhatsApp only): when the user explicitly asks for a
        // human agent, trigger escalation even without email capture so the
        // business is notified. Only fires once per session.
        if (channel == Channel.WHATSAPP && isHumanHandoffIntent(text)) {
            if (!hasEscalationFired(sessionKey)) {
                triggerEscalation(sessionKey, businessId);
                leadData.get(sessionKey, k -> new HashMap<>()).put("escalationFired", "true");
            }
        }

        log.info("AI chatbot [{}]: step={} input='{}' response='{}'", sessionKey, step, text, cleanResponse);
        return ChatTurn.text(cleanResponse, extraction.leadCaptured());
    }

    private static boolean isReset(String text) {
        String lower = text.toLowerCase();
        return lower.equals("reset") || lower.equals("reiniciar");
    }

    private boolean hasEmailCaptured(String key) {
        Map<String, String> data = leadData.getIfPresent(key);
        return data != null && "true".equals(data.get("emailCaptured"));
    }

    private boolean hasEscalationFired(String key) {
        Map<String, String> data = leadData.getIfPresent(key);
        return data != null && "true".equals(data.get("escalationFired"));
    }

    private static final java.util.regex.Pattern AFFIRMATIVE_PATTERN = java.util.regex.Pattern.compile(
        "\\b(?:si|sí|confirmo|adelante|dale|agenda|vale|ok|claro|perfecto)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE
            | java.util.regex.Pattern.UNICODE_CHARACTER_CLASS);

    private static boolean isAffirmative(String text) {
        String lower = text == null ? "" : text.toLowerCase();
        return AFFIRMATIVE_PATTERN.matcher(lower).find();
    }

    /**
     * Deterministic reply used when Groq returns an empty response. Never claims
     * "I didn't understand" when contact data was actually captured — it confirms
     * the captured data and hands over the real next step. No demo link is pushed
     * here: technical/empty fallbacks must not force the sale.
     */
    private String buildFallbackReply(String userText, Map<String, String> data) {
        String lower = (userText == null ? "" : userText).toLowerCase();
        boolean captured = data != null && data.get("email") != null && !data.get("email").isBlank();

        if (captured) {
            String name = data.get("name") == null ? "" : data.get("name");
            String salutation = name.isBlank() ? "¡Gracias!" : "¡Gracias, " + name + "!";
            if (isAffirmative(lower)) {
                return salutation + " He anotado tu correo (" + data.get("email") + ")."
                    + " En breve seguimos desde aquí.";
            }
            return salutation + " He apuntado tu correo (" + data.get("email") + ")."
                + " ¿En qué más puedo ayudarte?";
        }

        if (isAffirmative(lower)) {
            return "¡Genial! ¿En qué más puedo ayudarte?";
        }
        if (isDecline(lower)) {
            return "Entendido, no hay problema. Si necesitas algo más, aquí estoy.";
        }
        return "¿Podrías repetirme eso, por favor? No te he entendido bien.";
    }

    private static boolean isHumanHandoffIntent(String text) {
        if (text == null) return false;
        return HUMAN_HANDOFF_PATTERN.matcher(text).find();
    }

    private static boolean isDecline(String text) {
        String lower = text.toLowerCase();
        return lower.equals("no") || lower.equals("no gracias") || lower.equals("no, gracias")
            || lower.startsWith("no me interesa") || lower.startsWith("no quiero")
            || lower.startsWith("no hace falta");
    }

    private void resetConversation(String key) {
        conversationHistory.invalidate(key);
        leadData.invalidate(key);
        conversationStep.invalidate(key);
    }

    private static boolean containsEmail(String text) {
        return text.matches(".*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*");
    }

    private record LeadExtractionResult(String cleanResponse, boolean leadCaptured) {}

    /**
     * Extract [LEAD:...] tag from AI response or deterministic contact from user
     * message. Tag is stripped from visible text. Lead is persisted.
     */
    private LeadExtractionResult extractLead(String key, String userMessage, String aiResponse,
                                             UUID businessId, Channel channel) {
        String cleanResponse = aiResponse;
        String leadTag = "[LEAD:";
        int start = aiResponse.indexOf(leadTag);
        if (start != -1) {
            int end = aiResponse.indexOf("]", start);
            if (end != -1) {
                String leadDataStr = aiResponse.substring(start + leadTag.length(), end);
                cleanResponse = aiResponse.substring(0, start).trim();

                Map<String, String> data = leadData.get(key, k -> new HashMap<>());
                for (String part : leadDataStr.split("\\|")) {
                    String[] kv = part.split("=", 2);
                    if (kv.length == 2) {
                        data.put(kv[0].trim(), kv[1].trim());
                    }
                }
                leadData.put(key, data);
                boolean saved = saveLead(key, data, businessId, channel);
                return new LeadExtractionResult(cleanResponse, saved);
            }
        } else {
            Map<String, String> data = extractContactFromUserMessage(userMessage);
            if (!data.isEmpty()) {
                data.putAll(leadData.get(key, k -> new HashMap<>()));
                leadData.put(key, data);
                boolean saved = saveLead(key, data, businessId, channel);
                return new LeadExtractionResult(cleanResponse, saved);
            }
        }
        return new LeadExtractionResult(cleanResponse, false);
    }

    /**
     * Deterministic contact extraction from a free-text user message.
     * Returns an empty map when no email is present.
     */
    private Map<String, String> extractContactFromUserMessage(String text) {
        Map<String, String> data = new HashMap<>();
        if (text == null) {
            return data;
        }
        java.util.regex.Matcher emailMatcher = EMAIL_PATTERN.matcher(text);
        if (!emailMatcher.find()) {
            return data;
        }
        data.put("email", emailMatcher.group());

        java.util.regex.Matcher nameMatcher = NAME_PATTERN.matcher(text);
        if (nameMatcher.find()) {
            data.put("name", nameMatcher.group(1));
        } else {
            String before = text.substring(0, emailMatcher.start()).trim().replaceAll("[^A-Za-zÁÉÍÓÚÑáéíóúñ ]", "");
            String firstToken = before.split(" ")[0];
            // Never treat filler words like "vale"/"ok"/"hola" as the lead's name.
            if (!firstToken.isEmpty() && Character.isUpperCase(firstToken.charAt(0))
                    && !isFillerWord(firstToken)) {
                data.put("name", firstToken);
            }
        }
        return data;
    }

    private static boolean isFillerWord(String token) {
        String lower = token.toLowerCase();
        return Set.of("vale", "ok", "okey", "hola", "buenos", "buenas", "si", "sí", "no",
            "claro", "perfecto", "genial", "entendido", "mira", "oye", "perdona").contains(lower);
    }

    private boolean saveLead(String key, Map<String, String> data, UUID businessId, Channel channel) {
        try {
            String name = data.getOrDefault("name", "Desconocido");
            String email = data.get("email");
            String service = data.getOrDefault("service", "");

            String firstName = name.contains(" ") ? name.substring(0, name.indexOf(" ")) : name;
            String lastName = name.contains(" ") ? name.substring(name.indexOf(" ") + 1) : "";

            if (channel == Channel.WEB) {
                return saveWebLead(key, firstName, lastName, email, service, businessId);
            }
            return saveWhatsAppLead(key, firstName, lastName, email, service, businessId);
        } catch (Exception e) {
            log.error("Failed to save chatbot lead: key={}", key, e);
            return false;
        }
    }

    private boolean saveWhatsAppLead(String phone, String firstName, String lastName,
                                     String email, String service, UUID businessId) {
        String phoneE164 = phone.startsWith("+") ? phone : "+" + phone;
        Optional<Lead> existing = leadRepository.findByPhoneAndDeletedAtIsNull(phoneE164);
        if (existing.isPresent()) {
            Lead lead = existing.get();
            if (email != null) lead.setEmail(email);
            if (!service.isEmpty()) lead.setNotes("Servicio de interés: " + service);
            lead.setSource(LeadSource.WHATSAPP);
            if (lead.getCreatedBy() == null && businessId != null) lead.setCreatedBy(businessId);
            leadRepository.save(lead);
            log.info("AI chatbot lead updated: phone={}", phoneE164);
            return true;
        } else {
            if (businessId == null) {
                log.warn("Skip WhatsApp lead creation for {}: no business profile resolved (created_by NOT NULL)", phoneE164);
                return false;
            }
            long totalLeads = leadRepository.countByCreatedByAndDeletedAtIsNull(businessId);
            if (totalLeads >= TRIAL_LEAD_LIMIT) {
                log.warn("WhatsApp lead limit reached ({}) — skipping lead creation for phone {}", TRIAL_LEAD_LIMIT, phoneE164);
                return false;
            }
            Lead lead = Lead.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phone(phoneE164)
                .company(null)
                .status(LeadStatus.NEW)
                .source(LeadSource.WHATSAPP)
                .notes("Servicio de interés: " + service)
                .doNotCall(false)
                .createdBy(businessId)
                .build();
            leadRepository.save(lead);
            log.info("AI chatbot lead created: phone={}", phoneE164);
            return true;
        }
    }

    private boolean saveWebLead(String sessionId, String firstName, String lastName,
                                String email, String service, UUID businessId) {
        long totalLeads = businessId == null ? 0 : leadRepository.countByCreatedByAndDeletedAtIsNull(businessId);
        if (businessId == null || totalLeads >= TRIAL_LEAD_LIMIT) {
            log.warn("Lead limit reached ({}) — skipping web lead creation for session {}", TRIAL_LEAD_LIMIT, sessionId);
            return false;
        }

        String safeService = service == null || service.isBlank() ? "web-chat" : service;
        Lead lead = Lead.builder()
            .firstName(firstName)
            .lastName(lastName)
            .email(email)
            .phone(null)
            .company(null)
            .status(LeadStatus.NEW)
            .source(LeadSource.WEB_CHAT)
            .notes("Servicio de interés: " + safeService + " |origen: web chat |sessionId: " + sessionId)
            .doNotCall(false)
            .createdBy(businessId)
            .build();
        leadRepository.save(lead);
        log.info("Chat lead created: sessionId={} name={}", sessionId, firstName);
        return true;
    }

    /**
     * Trigger escalation after a lead with email has been captured and the user
     * confirms affirmatively, or when the user explicitly asks for a human.
     * WhatsApp only. Fire-and-forget.
     */
    private void triggerEscalation(String phone, UUID businessId) {
        if (businessId == null) {
            log.debug("Escalation skipped: no business profile (businessId null) phone={}", phone);
            return;
        }
        try {
            String phoneE164 = phone.startsWith("+") ? phone : "+" + phone;
            Optional<Lead> existing = leadRepository.findByPhoneAndDeletedAtIsNull(phoneE164);
            if (existing.isPresent()) {
                escalationService.qualify(existing.get().getId(), businessId);
                return;
            }
            // No lead captured yet (e.g. handoff requested before email): create a
            // minimal phone-only lead so the escalation pipeline has a target and
            // the business is notified.
            Lead lead = Lead.builder()
                .firstName("Cliente WhatsApp")
                .lastName("")
                .phone(phoneE164)
                .status(LeadStatus.NEW)
                .source(LeadSource.WHATSAPP)
                .notes("Petición de contacto con asesor humano")
                .doNotCall(false)
                .createdBy(businessId)
                .build();
            leadRepository.save(lead);
            escalationService.qualify(lead.getId(), businessId);
        } catch (Exception e) {
            log.error("Failed to trigger escalation: phone={} businessId={}", phone, businessId, e);
        }
    }

    private String buildStateContext(String key) {
        String stepVal = conversationStep.getIfPresent(key);
        String step = stepVal == null ? "initial" : stepVal;
        StringBuilder sb = new StringBuilder();
        sb.append("Paso actual: ").append(step).append(". ");
        Map<String, String> data = leadData.getIfPresent(key);
        if (data != null) {
            String name = data.get("name");
            String email = data.get("email");
            if (name != null && !name.isBlank()) sb.append("Nombre: ").append(name).append(". ");
            if (email != null && !email.isBlank()) sb.append("Email: ").append(email).append(". ");
            if ("true".equals(data.get("emailCaptured"))) {
                sb.append("Datos de contacto capturados. ");
            }
        }
        return sb.toString();
    }

    private String resolveSystemPrompt(UUID userId) {
        if (userId == null) {
            return promptComposer.composeDefault();
        }
        BusinessProfile profile = businessService.getProfileEntityByUserId(userId);
        return promptComposer.compose(profile);
    }
}
