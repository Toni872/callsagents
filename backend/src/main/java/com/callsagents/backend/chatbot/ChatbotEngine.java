package com.callsagents.backend.chatbot;

import com.callsagents.backend.business.entity.BusinessProfile;
import com.callsagents.backend.business.service.BusinessPromptComposer;
import com.callsagents.backend.business.service.BusinessService;
import com.callsagents.backend.escalation.service.EscalationService;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.entity.LeadSource;
import com.callsagents.backend.leads.entity.LeadStatus;
import com.callsagents.backend.leads.repository.LeadRepository;
import com.callsagents.backend.voice.domain.VoiceProviderType;
import com.callsagents.backend.voice.service.VoiceCallService;
import com.callsagents.backend.voice.service.VoiceProvider;
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
import java.util.UUID;

/**
 * The shared, channel-agnostic state machine for the conversational chatbot.
 *
 * <p>This engine contains the ENTIRE interactive FSM that used to live in
 * {@code WhatsAppAiChatbotService}, extracted so that the same flow (greeting,
 * intent, timing, confirmation, voice offer) runs identically on WhatsApp and
 * on the web widget. It never calls a channel SDK (Vonage, Retell, ...): it
 * returns data-only {@link ChatTurn} objects and lets each channel adapter
 * decide how to present them.
 *
 * <p>Conversation state is keyed by a {@code sessionKey} — a phone number (E.164,
 * without dependency on it) for WhatsApp, or a session UUID for the web widget.
 */
@Service
public class ChatbotEngine {

    private static final Logger log = LoggerFactory.getLogger(ChatbotEngine.class);

    private final GroqService groqService;
    private final LeadRepository leadRepository;
    private final BusinessService businessService;
    private final BusinessPromptComposer promptComposer;
    private final EscalationService escalationService;
    private final VoiceCallService voiceCallService;

    // Bounded caches: max 2000 entries each, evict after 30min inactivity
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
    private static final String DEFAULT_CONTACT_URL = "https://www.script-9.com/contacto";

    private static final java.util.regex.Pattern EMAIL_PATTERN =
        java.util.regex.Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    private static final java.util.regex.Pattern NAME_PATTERN =
        java.util.regex.Pattern.compile("(?:mi nombre es|me llamo|soy)\\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    // Post-decision steps: re-sending the exact button that triggered the decision
    // is a no-op so the conversation does not get stuck; anything else keeps flowing.
    private static final String ALREADY_HANDLED_MSG =
        "Ya procesé tu respuesta. Si necesitas algo más, escribe 'hola' para reiniciar.";

    public ChatbotEngine(GroqService groqService, LeadRepository leadRepository,
                         BusinessService businessService, BusinessPromptComposer promptComposer,
                         EscalationService escalationService, VoiceCallService voiceCallService) {
        this.groqService = groqService;
        this.leadRepository = leadRepository;
        this.businessService = businessService;
        this.promptComposer = promptComposer;
        this.escalationService = escalationService;
        this.voiceCallService = voiceCallService;
    }

    /**
     * Reset the conversation state for a session/channel key so that the next
     * turn behaves like a brand-new conversation.
     */
    public void reset(String sessionKey) {
        resetConversation(sessionKey);
    }

    /**
     * Build the initial greeting turn with the interactive intent buttons.
     * Sets the step to {@code awaiting_intent}.
     */
    public ChatTurn greeting(String sessionKey, UUID businessId) {
        // Accept either a business profile id (web widget) or a user id (WhatsApp).
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
        String body = String.format("Hola, soy %s de %s.\n\n¿Qué te gustaría hacer?", botName, companyName);
        List<ChatButton> buttons = List.of(
            new ChatButton("intent_ventas", "Ventas"),
            new ChatButton("intent_soporte", "Soporte"),
            new ChatButton("intent_demo", "Agendar demo")
        );
        conversationStep.put(sessionKey, "awaiting_intent");
        return new ChatTurn(body, buttons, false, false);
    }

    /**
     * Process an incoming message for a given session and channel, returning the
     * data-only turn for the channel adapter to present.
     */
    public ChatTurn process(String sessionKey, String message, UUID businessId, Channel channel) {
        String text = message == null ? "" : message.trim();
        // Accept either a business profile id (web widget) or a user id (WhatsApp).
        businessId = businessService.resolveOwnerUserId(businessId);
        String stepVal = conversationStep.getIfPresent(sessionKey);
        String step = stepVal == null ? "initial" : stepVal;
        log.info("processMessage [{}]: step={} text='{}'", sessionKey, step, text);

        // Global commands
        if (isReset(text)) {
            resetConversation(sessionKey);
            return greeting(sessionKey, businessId);
        }

        // Handle button/list replies and free-text intent. Returns non-null when handled:
        // a text reply (buttons == null) or a button turn (buttons != null).
        ChatTurn handled = handleButtonReply(sessionKey, text, step, businessId, channel);
        if (handled != null) {
            recordButtonReplyHistory(sessionKey, text, handled);
            return handled;
        }

        // Bug 1 fix: when the user sends an email during collecting_info, skip the
        // Groq call entirely — extract deterministically, save the lead, and advance
        // directly to the timing buttons.  This avoids wasting tokens and ~15s of
        // latency for a response the user never sees.
        if ("collecting_info".equals(step) && containsEmail(text)) {
            Map<String, String> data = extractContactFromUserMessage(text);
            data.putAll(leadData.get(sessionKey, k -> new HashMap<>()));
            leadData.put(sessionKey, data);
            boolean saved = saveLead(sessionKey, data, businessId, channel);

            List<Map<String, String>> history = conversationHistory.get(sessionKey, k -> new ArrayList<>());
            history.add(Map.of("role", "user", "content", text));
            while (history.size() > MAX_HISTORY) {
                history.remove(0);
                history.remove(0);
            }

            conversationStep.put(sessionKey, "awaiting_timing");
            return ChatTurn.buttons(null, sendTimingButtons(), saved);
        }

        // Get or create conversation history
        List<Map<String, String>> history = conversationHistory.get(sessionKey, k -> new ArrayList<>());

        // Bug 3 fix: inject conversation state context into the system prompt so the
        // model knows the current step, lead data, and confirmation status.
        String systemPrompt = resolveSystemPrompt(businessId);
        String stateCtx = buildStateContext(sessionKey);
        if (stateCtx != null) {
            systemPrompt = systemPrompt + "\n\nESTADO ACTUAL DE LA CONVERSACIÓN:\n" + stateCtx;
        }

        // Call Groq AI (free text) — lead extraction via a [LEAD:...] tag at the
        // end of the response, parsed below before the text is shown to the user.
        String aiResponse = groqService.chat(systemPrompt, history, text);

        // Bug 5 fix: distinguish HTTP 429 (rate limited) from other errors
        if (GroqService.RATE_LIMITED_SENTINEL.equals(aiResponse)) {
            return ChatTurn.text("Estoy recibiendo muchas peticiones en este momento. Espera unos segundos y repite el mensaje, por favor.");
        }
        if (aiResponse == null) {
            log.warn("Groq returned null for key={}", sessionKey);
            return ChatTurn.text("Disculpa, tuve un problema técnico. ¿Podrías repetir tu mensaje?");
        }

        // Extract lead data from the [LEAD:...] tag and strip it from the visible text
        LeadExtractionResult extraction = extractLead(sessionKey, text, aiResponse, businessId, channel);

        String cleanResponse = extraction.cleanResponse();

        // Bug 4 fix: if Groq returned empty text, show a friendly fallback instead of
        // an empty bubble.  Log as WARN and add the fallback to history.
        if (cleanResponse == null || cleanResponse.isBlank()) {
            log.warn("Groq returned empty response for key={}, user='{}'", sessionKey, text);
            cleanResponse = "¿Podrías repetirme eso, por favor? No te he entendido bien.";
        }

        // Update conversation history with the clean, tag-free response
        history.add(Map.of("role", "user", "content", text));
        history.add(Map.of("role", "assistant", "content", cleanResponse));

        // Trim history if too long
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
            history.remove(0);
        }

        // Determine next interactive step; if buttons were sent, skip the AI text
        ChatTurn advanced = advanceStep(sessionKey, text, cleanResponse, step, extraction.leadCaptured());
        if (advanced != null) {
            return advanced;
        }

        // Optional voice offer detector — offer to move to a voice call only on
        // WhatsApp, when the chat conversation is not advancing toward a sale
        // and the offer has not already been made. Returns the offer buttons.
        if (channel == Channel.WHATSAPP && shouldOfferVoiceCall(sessionKey, businessId)) {
            leadData.get(sessionKey, k -> new HashMap<>()).put("voiceOfferSent", "true");
            return sendVoiceCallOfferTurn(sessionKey, extraction.leadCaptured());
        }

        log.info("AI chatbot [{}]: step={} input='{}' response='{}'", sessionKey, step, text, cleanResponse);
        return ChatTurn.text(cleanResponse, extraction.leadCaptured());
    }

    /**
     * Handle structured button/list replies.
     * Returns null when unhandled (caller should run AI).
     */
    private ChatTurn handleButtonReply(String key, String text, String step, UUID businessId, Channel channel) {
        // Post-decision guard: only the exact button that already triggered a
        // decision is a no-op. Any other message flows to the AI normally.
        if (isHandledRepeat(step, text)) {
            return ChatTurn.text(ALREADY_HANDLED_MSG);
        }

        // Intent buttons are GLOBAL — handled at any step
        if (text.startsWith("intent_")) {
            return switch (text) {
                case "intent_ventas" -> {
                    conversationStep.put(key, "collecting_info");
                    conversationHistory.invalidate(key);
                    yield ChatTurn.textContact("Perfecto, te ayudo con ventas.\n\n¿Cómo te llamas y cuál es tu correo?");
                }
                case "intent_soporte" -> {
                    conversationStep.put(key, "support");
                    conversationHistory.invalidate(key);
                    yield ChatTurn.text("Claro, ¿en qué puedo ayudarte con soporte?");
                }
                case "intent_demo" -> {
                    conversationStep.put(key, "collecting_info");
                    conversationHistory.invalidate(key);
                    yield ChatTurn.textContact("Genial, agendemos una demo.\n\n¿Cómo te llamas y cuál es tu correo?");
                }
                default -> null;
            };
        }

        // Timing selection (buttons or free text)
        if ("awaiting_timing".equals(step)) {
            String timingText = switch (text) {
                case "timing_now" -> "Lo antes posible";
                case "timing_month" -> "Este mes";
                case "timing_later" -> "Solo explorando";
                default -> inferTiming(text);
            };
            if (timingText != null) {
                saveTiming(key, timingText);
                conversationStep.put(key, "confirmation");
                return sendConfirmationTurn(key);
            }
        }

        // Confirmation (buttons or free text)
        if ("confirmation".equals(step)) {
            boolean yes = "confirm_yes".equals(text) || isAffirmative(text);
            boolean no = "confirm_no".equals(text) || (!yes && isNegative(text));
            if (yes) {
                conversationStep.put(key, "confirmed_yes");
                if (channel == Channel.WHATSAPP) {
                    triggerEscalation(key, businessId);
                } else {
                    log.debug("Escalation skipped for web chat (WEB channel) key={}", key);
                }
                String contactUrl = resolveContactUrl(businessId);
                return ChatTurn.text("¡Genial! Te propongo una demo de 15 minutos donde vemos tu caso.\n\nAgenda directamente aquí: " + contactUrl);
            }
            if (no) {
                conversationStep.put(key, "confirmed_no");
                return ChatTurn.text("No te preocupes. Cuando quieras, aquí estoy.\n\n¡Hasta pronto!");
            }
            return null;
        }

        // Voice call acceptance/decline (buttons or free text) — WhatsApp only
        if ("awaiting_voice_decision".equals(step)) {
            if (channel != Channel.WHATSAPP) {
                return ChatTurn.text("No puedo gestionar llamadas de voz desde este canal. ¿En qué más te ayudo?");
            }
            if ("accept_voice_call".equals(text) || isVoiceAccept(text)) {
                return ChatTurn.text(acceptVoiceCallAction(key, businessId));
            }
            if ("decline_voice_call".equals(text) || isVoiceDecline(text)) {
                return ChatTurn.text(declineVoiceCallAction(key));
            }
            return null;
        }

        return null; // Not a button reply or unhandled step
    }

    /**
     * True when the user re-sends the exact decision button that already settled
     * this stage of the conversation. Other messages are never blocked.
     */
    private static boolean isHandledRepeat(String step, String text) {
        return ("confirmed_yes".equals(step) && "confirm_yes".equals(text))
            || ("confirmed_no".equals(step) && "confirm_no".equals(text))
            || ("voice_accepted".equals(step) && "accept_voice_call".equals(text))
            || ("voice_declined".equals(step) && "decline_voice_call".equals(text));
    }

    private static boolean isAffirmative(String text) {
        String lower = text.toLowerCase();
        return containsAny(lower, "si", "sí", "confirmo", "adelante", "dale", "agenda", "vale", "ok", "claro", "perfecto");
    }

    private static boolean isNegative(String text) {
        String lower = text.toLowerCase();
        return containsAny(lower, "no", "gracias", "después", "despues", "mas adelante", "más adelante");
    }

    private static boolean isVoiceAccept(String text) {
        String lower = text.toLowerCase();
        return containsAny(lower, "si", "sí", "llama", "llámame");
    }

    private static boolean isVoiceDecline(String text) {
        return text.toLowerCase().contains("no");
    }

    /**
     * Best-effort intent inference for free text in the timing step. Button ids
     * are handled by the caller; this only matches natural-language answers.
     */
    private static String inferTiming(String text) {
        String lower = text.toLowerCase();
        if (containsAny(lower, "antes posible", "ahora", "ya", "cuanto antes")) {
            return "Lo antes posible";
        }
        if (lower.contains("mes")) {
            return "Este mes";
        }
        if (containsAny(lower, "explorar", "después", "despues", "mas adelante", "más adelante", "solo")) {
            return "Solo explorando";
        }
        return null;
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build the confirmation turn carrying the data summary as body text.
     */
    private ChatTurn sendConfirmationTurn(String key) {
        Map<String, String> cachedData = leadData.getIfPresent(key);
        Map<String, String> data = cachedData == null ? Map.of() : cachedData;
        String name = data.getOrDefault("name", "");
        String email = data.getOrDefault("email", "");
        String timing = data.getOrDefault("timing", "");

        String body = String.format(
            "¿Confirmas los datos?\n\nNombre: %s\nCorreo: %s\nPreferencia: %s\n\n¿Agendo la demo?",
            name, email, timing
        );
        List<ChatButton> buttons = List.of(
            new ChatButton("confirm_yes", "Sí, agendar"),
            new ChatButton("confirm_no", "No, gracias")
        );
        return ChatTurn.buttons(body, buttons);
    }

    /**
     * Advance conversation step based on AI response.
     * Returns the timing-buttons turn when the user provided an email so the
     * caller skips the AI text (single-message rule).
     */
    private ChatTurn advanceStep(String key, String userMessage, String aiResponse,
                                 String currentStep, boolean leadCaptured) {
        // If AI asked for name/email, move to collecting_info
        if ("initial".equals(currentStep) || "awaiting_intent".equals(currentStep)) {
            if (aiResponse.toLowerCase().contains("llamas") || aiResponse.toLowerCase().contains("correo")
                    || aiResponse.toLowerCase().contains("email")) {
                conversationStep.put(key, "collecting_info");
            }
        }

        // If user provided email, advance to timing (single reply: buttons only)
        if ("collecting_info".equals(currentStep)) {
            if (containsEmail(userMessage)) {
                conversationStep.put(key, "awaiting_timing");
                return ChatTurn.buttons(null, sendTimingButtons(), leadCaptured);
            }
        }
        return null;
    }

    private List<ChatButton> sendTimingButtons() {
        return List.of(
            new ChatButton("timing_now", "Lo antes posible"),
            new ChatButton("timing_month", "Este mes"),
            new ChatButton("timing_later", "Solo explorando")
        );
    }

    /**
     * Save timing data for the lead.
     */
    private void saveTiming(String key, String timing) {
        leadData.get(key, k -> new HashMap<>()).put("timing", timing);
    }

    /**
     * Reset conversation state.
     */
    private void resetConversation(String key) {
        conversationHistory.invalidate(key);
        leadData.invalidate(key);
        conversationStep.invalidate(key);
    }

    private static boolean isReset(String text) {
        String lower = text.toLowerCase();
        return lower.equals("hola") || lower.equals("inicio") || lower.equals("reset") || lower.equals("reiniciar");
    }

    private static boolean containsEmail(String text) {
        return text.matches(".*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*");
    }

    private record LeadExtractionResult(String cleanResponse, boolean leadCaptured) {}

    /**
     * Extract lead data from a trailing [LEAD:...] tag in the AI response and strip
     * the tag from the text visible to the user. Mirrors the old per-channel logic.
     * When the model omits the tag but the user already stated an email, capture the
     * lead deterministically from the user's own message. The tag is never shown to
     * the user; only the clean response is returned.
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
     * Deterministic contact extraction from a free-text user message (email plus a
     * best-effort name from an explicit "mi nombre es/me llamo/soy" phrase or a
     * capitalized first token). Returns an empty map when no email is present.
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
            if (!firstToken.isEmpty() && Character.isUpperCase(firstToken.charAt(0))) {
                data.put("name", firstToken);
            }
        }
        return data;
    }

    /**
     * Persist the captured lead, distinguishing the source channel. WhatsApp is
     * keyed by phone (E.164: update-or-create); the web widget by sessionId with
     * no phone (create-only, subject to the trial lead limit).
     *
     * @return true when a lead was created or updated.
     */
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
        Optional<Lead> existing = leadRepository.findByPhone(phoneE164);
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
        // Trial lead limit check (per business)
        long totalLeads = businessId == null ? 0 : leadRepository.countByCreatedBy(businessId);
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
     * Trigger the escalation orchestrator after a lead confirms a demo on WhatsApp.
     * The lead is looked up by phone (E.164, with the "+" prefix as saved).
     * Never propagates — this is fire-and-forget from the chatbot flow.
     */
    private void triggerEscalation(String phone, UUID businessId) {
        if (businessId == null) {
            log.debug("Escalation skipped: no business profile (businessId null) phone={}", phone);
            return;
        }
        try {
            String phoneE164 = phone.startsWith("+") ? phone : "+" + phone;
            leadRepository.findByPhone(phoneE164).ifPresent(lead ->
                escalationService.qualify(lead.getId(), businessId)
            );
        } catch (Exception e) {
            log.error("Failed to trigger escalation: phone={} businessId={}", phone, businessId, e);
        }
    }

    /**
     * Conservative detector for a non-effective sales conversation. Returns true
     * (offer a voice call) only when there is enough chatter, the conversation is
     * NOT in a productive step, the offer has not been sent before, and a
     * business is present.
     */
    private boolean shouldOfferVoiceCall(String key, UUID businessId) {
        if (businessId == null) {
            return false;
        }
        List<Map<String, String>> history = conversationHistory.getIfPresent(key);
        if (history == null || history.size() < 8) {
            return false;
        }
        String step = conversationStep.getIfPresent(key);
        if ("confirmation".equals(step) || "awaiting_timing".equals(step)
                || "awaiting_voice_decision".equals(step) || "support".equals(step)
                || "confirmed_yes".equals(step) || "confirmed_no".equals(step)) {
            return false;
        }
        Map<String, String> data = leadData.getIfPresent(key);
        if (data != null && "true".equals(data.get("voiceOfferSent"))) {
            return false;
        }
        return true;
    }

    /**
     * Build the optional voice call offer buttons turn (WhatsApp only).
     */
    private ChatTurn sendVoiceCallOfferTurn(String key, boolean leadCaptured) {
        Map<String, String> cachedData = leadData.getIfPresent(key);
        String name = cachedData == null ? "" : cachedData.getOrDefault("name", "");
        String body = name.isBlank()
            ? "Si te parece, ¿prefieres que un asesor te llame por teléfono para ayudarte de forma más directa?"
            : name + ", ¿prefieres que un asesor te llame por teléfono para ayudarte de forma más directa?";
        List<ChatButton> buttons = List.of(
            new ChatButton("accept_voice_call", "Sí, llámame"),
            new ChatButton("decline_voice_call", "No, prefiero seguir por chat")
        );
        conversationStep.put(key, "awaiting_voice_decision");
        return ChatTurn.buttons(body, buttons, leadCaptured);
    }

    /**
     * Accept the voice call: mark the step as voice_accepted and try to place the
     * call, returning an honest (or degraded) message to the user.
     */
    private String acceptVoiceCallAction(String key, UUID businessId) {
        conversationStep.put(key, "voice_accepted");
        boolean placed = acceptVoiceCall(key, businessId);
        if (placed) {
            return "Perfecto, te estoy conectando con un asesor por teléfono...";
        }
        return "Estamos teniendo un problema para conectarte por teléfono. Un asesor te va a contactar por chat en breve.";
    }

    /**
     * Decline the voice call: keep the conversation in chat.
     */
    private String declineVoiceCallAction(String key) {
        conversationStep.put(key, "voice_declined");
        return "No hay problema, seguimos por chat. ¿En qué más te ayudo?";
    }

    /**
     * Fire-and-forget: place an outbound voice call to the lead via the Retell
     * AI agent. Returns true when the provider accepted the call; false when it
     * was skipped (missing profile/agent) or failed.
     */
    private boolean acceptVoiceCall(String phone, UUID businessId) {
        if (businessId == null) {
            log.debug("Voice call skipped: no business profile (businessId null) phone={}", phone);
            return false;
        }
        try {
            String phoneE164 = phone.startsWith("+") ? phone : "+" + phone;
            BusinessProfile profile = businessService.getProfileEntityByUserId(businessId);
            String agentId = profile != null ? profile.getVoiceAgentId() : null;
            if (agentId == null || agentId.isBlank()) {
                log.warn("Voice call skipped: no voice_agent_id for businessId={} phone={}", businessId, phoneE164);
                return false;
            }
            Map<String, Object> metadata = Map.of("leadId", "", "acceptedByLead", "true");
            Lead[] called = new Lead[1];
            leadRepository.findByPhone(phoneE164).ifPresent(lead -> {
                called[0] = lead;
            });
            if (called[0] == null) {
                log.warn("Voice call skipped: no lead for phone={} businessId={}", phoneE164, businessId);
                return false;
            }
            Map<String, Object> callMetadata = new HashMap<>(metadata);
            callMetadata.put("leadId", called[0].getId().toString());
            callMetadata.put("acceptedByLead", "true");
            Map<String, Object> dynamicVars = new HashMap<>(voiceCallService.composeVariables(profile));
            voiceCallService.placeCall(
                VoiceProviderType.RETELL,
                new VoiceProvider.StartCallRequest(phoneE164, agentId, callMetadata, dynamicVars),
                businessId,
                null
            );
            log.info("Voice call placed for phone={} businessId={} leadId={}", phoneE164, businessId, called[0].getId());
            return true;
        } catch (Exception e) {
            log.error("Failed to place voice call: phone={} businessId={}", phone, businessId, e);
            return false;
        }
    }

    /**
     * Build a human-readable state context string so the LLM knows the current
     * conversation step, lead data, and whether a demo was confirmed.
     * Returns null when no useful state exists (e.g. fresh conversation).
     */
    private String buildStateContext(String key) {
        String step = conversationStep.getIfPresent(key);
        if (step == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Paso actual: ").append(step).append(". ");
        Map<String, String> data = leadData.getIfPresent(key);
        if (data != null) {
            String name = data.get("name");
            String email = data.get("email");
            String timing = data.get("timing");
            if (name != null && !name.isBlank()) sb.append("Nombre: ").append(name).append(". ");
            if (email != null && !email.isBlank()) sb.append("Email: ").append(email).append(". ");
            if (timing != null && !timing.isBlank()) sb.append("Preferencia: ").append(timing).append(". ");
        }
        if ("confirmed_yes".equals(step)) {
            sb.append("El usuario ya confirmó agendar la demo. ");
            sb.append("Responde acorde: cierra la venta con calidez, sin volver a pedir datos.");
        } else if ("confirmed_no".equals(step)) {
            sb.append("El usuario declinó la demo. Responde con cortesía y deja la puerta abierta.");
        }
        return sb.toString();
    }

    /**
     * Record a button-reply turn in conversation history so the AI has full context.
     * User entry always added; assistant entry only when the turn carries visible body text.
     * Maintains the MAX_HISTORY trim invariant.
     */
    private void recordButtonReplyHistory(String key, String userText, ChatTurn turn) {
        List<Map<String, String>> history = conversationHistory.get(key, k -> new ArrayList<>());
        history.add(Map.of("role", "user", "content", userText));
        String assistantText = turn.reply();
        if (assistantText != null) {
            history.add(Map.of("role", "assistant", "content", assistantText));
        }
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
            history.remove(0);
        }
    }

    /**
     * Resolve the business's contact/booking URL, falling back to the default
     * when the profile has not configured one.
     */
    private String resolveContactUrl(UUID businessId) {
        if (businessId != null) {
            BusinessProfile profile = businessService.getProfileEntityByUserId(businessId);
            if (profile != null && profile.getContactUrl() != null && !profile.getContactUrl().isBlank()) {
                return profile.getContactUrl();
            }
        }
        return DEFAULT_CONTACT_URL;
    }

    private String resolveSystemPrompt(UUID userId) {
        if (userId == null) {
            return promptComposer.composeDefault();
        }
        BusinessProfile profile = businessService.getProfileEntityByUserId(userId);
        return promptComposer.compose(profile);
    }
}
