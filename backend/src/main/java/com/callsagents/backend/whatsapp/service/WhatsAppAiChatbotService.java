package com.callsagents.backend.whatsapp.service;

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
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

@Component
public class WhatsAppAiChatbotService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppAiChatbotService.class);

    private final GroqService groqService;
    private final LeadRepository leadRepository;
    private final VonageMessageService vonageMessageService;
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

    // Terminal states: once reached, duplicate button clicks are no-ops
    private static final Set<String> TERMINAL_STATES = Set.of(
        "confirmed_yes", "confirmed_no", "voice_accepted", "voice_declined"
    );

    private static final String ALREADY_HANDLED_MSG =
        "Ya procesé tu respuesta. Si necesitás algo más, escribí 'hola' para reiniciar.";

    // System prompt for AI with interactive step awareness
    private String resolveSystemPrompt(UUID userId) {
        if (userId == null) {
            return promptComposer.composeDefault();
        }
        BusinessProfile profile = businessService.getProfileEntityByUserId(userId);
        return promptComposer.compose(profile);
    }

    public WhatsAppAiChatbotService(GroqService groqService, LeadRepository leadRepository,
                                     VonageMessageService vonageMessageService,
                                      BusinessService businessService, BusinessPromptComposer promptComposer,
                                      EscalationService escalationService, VoiceCallService voiceCallService) {
        this.groqService = groqService;
        this.leadRepository = leadRepository;
        this.vonageMessageService = vonageMessageService;
        this.businessService = businessService;
        this.promptComposer = promptComposer;
        this.escalationService = escalationService;
        this.voiceCallService = voiceCallService;
    }

    /**
     * Process an incoming message with AI + interactive messages.
     * Returns the text response, or null if Groq is not configured or buttons were sent.
     */
    public String processMessage(String phone, String message) {
        return processMessage(phone, message, null);
    }

    /**
     * Whether the Groq AI backend is configured. Lets callers distinguish
     * "chatbot unavailable (fall back to the basic state machine)" from
     * "chatbot handled the message and already sent buttons/greeting"
     * (processMessage returns null in both cases).
     */
    public boolean isGroqConfigured() {
        return groqService.isConfigured();
    }

    public String processMessage(String phone, String message, UUID businessId) {
        if (!groqService.isConfigured()) {
            return null;
        }

        String text = message == null ? "" : message.trim();
        var stepVal = conversationStep.getIfPresent(phone);
        String step = stepVal == null ? "initial" : stepVal;
        log.info("processMessage [{}]: step={} text='{}'", phone, step, text);

        // Global commands
        if (isReset(text)) {
            resetConversation(phone);
            sendInteractiveGreeting(phone);
            return null;
        }

        // Handle button/list replies — returns [responseText, buttonsSent]
        String[] result = handleButtonReply(phone, text, step, businessId);
        if (result[0] != null) {
            return result[0]; // Text response from handler
        }
        if ("true".equals(result[1])) {
            return null; // Interactive buttons already sent, skip AI
        }

        // Get or create conversation history
        List<Map<String, String>> history = conversationHistory.get(phone, k -> new ArrayList<>());

        // Call Groq AI with structured output for lead extraction
        GroqService.LeadExtraction extraction = groqService.chatStructured(
            resolveSystemPrompt(businessId), history, text
        );

        if (extraction == null) {
            log.warn("Groq returned null for phone={}", phone);
            return "Disculpa, tuve un problema técnico. ¿Podrías repetir tu mensaje?";
        }

        String aiResponse = extraction.response();

        // Update conversation history
        history.add(Map.of("role", "user", "content", text));
        history.add(Map.of("role", "assistant", "content", aiResponse));

        // Trim history if too long
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
            history.remove(0);
        }

        // Check if AI extracted lead data from structured response
        String cleanResponse = aiResponse;
        GroqService.LeadData extractedLead = extraction.lead();
        if (extractedLead != null && !extractedLead.isEmpty()) {
            Map<String, String> data = leadData.get(phone, k -> new HashMap<>());
            if (extractedLead.name() != null) data.put("name", extractedLead.name());
            if (extractedLead.email() != null) data.put("email", extractedLead.email());
            if (extractedLead.service() != null) data.put("service", extractedLead.service());
            if (extractedLead.timing() != null) data.put("timing", extractedLead.timing());
            leadData.put(phone, data);
            saveLead(phone, data, businessId);
        }

        // Determine next interactive step
        advanceStep(phone, text, cleanResponse, step);

        // Optional voice offer detector — offer to move to a voice call only when
        // the chat conversation is not advancing toward a sale and the offer has
        // not already been made in this conversation. Returns null to skip the AI
        // text since the offer buttons were just sent.
        if (shouldOfferVoiceCall(phone, businessId)) {
            leadData.get(phone, k -> new HashMap<>()).put("voiceOfferSent", "true");
            sendVoiceCallOfferButtons(phone);
            return null;
        }

        log.info("AI chatbot [{}]: step={} input='{}' response='{}'", phone, step, text, cleanResponse);
        return cleanResponse;
    }

    /**
     * Send the initial greeting with interactive buttons.
     */
    public void sendInteractiveGreeting(String phone) {
        sendInteractiveGreeting(phone, null);
    }

    public void sendInteractiveGreeting(String phone, UUID businessId) {
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
        List<String[]> buttons = List.of(
            new String[]{"intent_ventas", "Ventas"},
            new String[]{"intent_soporte", "Soporte"},
            new String[]{"intent_demo", "Agendar demo"}
        );
        boolean sent = vonageMessageService.sendButtons(phone, body, buttons);
        if (!sent) {
            // Fallback to text
            vonageMessageService.sendText(phone, String.format("¡Hola! Soy %s de %s. ¿En qué puedo ayudarte?", botName, companyName));
        }
        conversationStep.put(phone, "awaiting_intent");
    }

    /**
     * Handle structured button/list replies.
     * Returns [responseText, "true"/"false"] — responseText is non-null for text responses,
     * "true" in [1] means interactive buttons were sent (caller should skip AI).
     * Both null/false means not handled, caller should run AI.
     */
    @SuppressWarnings("unchecked")
    private String[] handleButtonReply(String phone, String text, String step, UUID businessId) {
        // Terminal-state guard: if already processed, return polite message and no-op
        if (TERMINAL_STATES.contains(step)) {
            return new String[]{ALREADY_HANDLED_MSG, "false"};
        }

        // Intent buttons are GLOBAL — handled at any step
        if (text.startsWith("intent_")) {
            return switch (text) {
                case "intent_ventas" -> {
                    conversationStep.put(phone, "collecting_info");
                    conversationHistory.invalidate(phone);
                    yield new String[]{"Perfecto, te ayudo con ventas.\n\n¿Cómo te llamas y cuál es tu email?", "false"};
                }
                case "intent_soporte" -> {
                    conversationStep.put(phone, "support");
                    conversationHistory.invalidate(phone);
                    yield new String[]{"Claro, ¿en qué puedo ayudarte con soporte?", "false"};
                }
                case "intent_demo" -> {
                    conversationStep.put(phone, "collecting_info");
                    conversationHistory.invalidate(phone);
                    yield new String[]{"Genial, agendemos una demo.\n\n¿Cómo te llamas y tu email?", "false"};
                }
                default -> new String[]{null, "false"};
            };
        }

        // Timing selection
        if ("awaiting_timing".equals(step)) {
            String timingText = switch (text) {
                case "timing_now" -> "Lo antes posible";
                case "timing_month" -> "Este mes";
                case "timing_later" -> "Solo explorando";
                default -> null;
            };
            if (timingText != null) {
                saveTiming(phone, timingText);
                conversationStep.put(phone, "confirmation");
                sendConfirmationButtons(phone);
                return new String[]{null, "true"}; // Buttons sent, skip AI
            }
        }

        // Confirmation
        if ("confirmation".equals(step)) {
            return switch (text) {
                case "confirm_yes" -> {
                    conversationStep.put(phone, "confirmed_yes");
                    triggerEscalation(phone, businessId);
                    yield new String[]{"¡Genial! Te propongo una demo de 15 minutos donde vemos tu caso.\n\nTe envío un email con el link para agendar.\n\n¿Te parece bien esta semana?", "false"};
                }
                case "confirm_no" -> {
                    conversationStep.put(phone, "confirmed_no");
                    yield new String[]{"No te preocupes. Cuando quieras, aquí estoy.\n\n¡Hasta pronto!", "false"};
                }
                default -> new String[]{null, "false"};
            };
        }

        // Voice call acceptance/decline
        if ("awaiting_voice_decision".equals(step)) {
            return switch (text) {
                case "accept_voice_call" -> {
                    conversationStep.put(phone, "voice_accepted");
                    boolean placed = acceptVoiceCall(phone, businessId);
                    if (placed) {
                        yield new String[]{"Perfecto, te estoy conectando con un asesor por teléfono...", "false"};
                    }
                    yield new String[]{"Estamos teniendo un problema para conectarte por teléfono. Un asesor te va a contactar por chat en breve.", "false"};
                }
                case "decline_voice_call" -> {
                    conversationStep.put(phone, "voice_declined");
                    yield new String[]{"No hay problema, seguimos por chat. ¿En qué más te ayudo?", "false"};
                }
                default -> new String[]{null, "false"};
            };
        }

        return new String[]{null, "false"}; // Not a button reply or unhandled step
    }

    /**
     * Send timing selection buttons.
     */
    private void sendTimingButtons(String phone) {
        String body = "¿Cuándo te gustaría empezar?";
        List<String[]> buttons = List.of(
            new String[]{"timing_now", "Lo antes posible"},
            new String[]{"timing_month", "Este mes"},
            new String[]{"timing_later", "Solo explorando"}
        );
        vonageMessageService.sendButtons(phone, body, buttons);
        conversationStep.put(phone, "awaiting_timing");
    }

    /**
     * Send confirmation buttons.
     */
    private void sendConfirmationButtons(String phone) {
        Map<String, String> cachedData = leadData.getIfPresent(phone);
        Map<String, String> data = cachedData == null ? Map.of() : cachedData;
        String name = data.getOrDefault("name", "");
        String email = data.getOrDefault("email", "");
        String timing = data.getOrDefault("timing", "");

        String body = String.format(
            "¿Confirmo los datos?\n\nNombre: %s\nEmail: %s\nTiming: %s\n\n¿Agendo la demo?",
            name, email, timing
        );
        List<String[]> buttons = List.of(
            new String[]{"confirm_yes", "Si, agendar"},
            new String[]{"confirm_no", "No, gracias"}
        );
        vonageMessageService.sendButtons(phone, body, buttons);
    }

    /**
     * Advance conversation step based on AI response.
     */
    private void advanceStep(String phone, String userMessage, String aiResponse, String currentStep) {
        // If AI asked for name/email, move to collecting_info
        if ("initial".equals(currentStep) || "awaiting_intent".equals(currentStep)) {
            if (aiResponse.toLowerCase().contains("llamas") || aiResponse.toLowerCase().contains("email")) {
                conversationStep.put(phone, "collecting_info");
            }
        }

        // If user provided email, advance to timing
        if ("collecting_info".equals(currentStep)) {
            if (containsEmail(userMessage)) {
                sendTimingButtons(phone);
            }
        }
    }

    /**
     * Save timing data for the lead.
     */
    private void saveTiming(String phone, String timing) {
        leadData.get(phone, k -> new HashMap<>()).put("timing", timing);
    }

    /**
     * Reset conversation state.
     */
    private void resetConversation(String phone) {
        conversationHistory.invalidate(phone);
        leadData.invalidate(phone);
        conversationStep.invalidate(phone);
    }

    private static boolean isReset(String text) {
        String lower = text.toLowerCase();
        return lower.equals("hola") || lower.equals("inicio") || lower.equals("reset") || lower.equals("reiniciar");
    }

    private static boolean containsEmail(String text) {
        return text.matches(".*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*");
    }

    private void saveLead(String phone, Map<String, String> data, UUID businessId) {
        try {
            String name = data.getOrDefault("name", "Desconocido");
            String email = data.get("email");
            String service = data.getOrDefault("service", "");

            String firstName = name.contains(" ") ? name.substring(0, name.indexOf(" ")) : name;
            String lastName = name.contains(" ") ? name.substring(name.indexOf(" ") + 1) : "";
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
            } else {
                if (businessId == null) {
                    log.warn("Skip WhatsApp lead creation for {}: no business profile resolved (created_by NOT NULL)", phoneE164);
                    return;
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
            }
        } catch (Exception e) {
            log.error("Failed to save AI chatbot lead: phone={}", phone, e);
        }
    }

    /**
     * Trigger the escalation orchestrator after a lead confirms a demo.
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
    private boolean shouldOfferVoiceCall(String phone, UUID businessId) {
        if (businessId == null) {
            return false;
        }
        List<Map<String, String>> history = conversationHistory.getIfPresent(phone);
        if (history == null || history.size() < 8) {
            return false;
        }
        String step = conversationStep.getIfPresent(phone);
        if ("confirmation".equals(step) || "awaiting_timing".equals(step)
                || "awaiting_voice_decision".equals(step) || "support".equals(step)) {
            return false;
        }
        Map<String, String> data = leadData.getIfPresent(phone);
        if (data != null && "true".equals(data.get("voiceOfferSent"))) {
            return false;
        }
        return true;
    }

    /**
     * Send the optional voice call offer buttons. Never imposed on the lead.
     */
    private void sendVoiceCallOfferButtons(String phone) {
        Map<String, String> cachedData = leadData.getIfPresent(phone);
        String name = cachedData == null ? "" : cachedData.getOrDefault("name", "");
        String greeting = name.isBlank() ? "" : name + ", ";
        String body = String.format(
            "%sLa conversación por chat no está avanzando y quiero ayudarte mejor.\n\n¿Preferís que te llame un asesor por voz?",
            greeting
        );
        List<String[]> buttons = List.of(
            new String[]{"accept_voice_call", "Sí, llámame"},
            new String[]{"decline_voice_call", "No, sigo con chat"}
        );
        vonageMessageService.sendButtons(phone, body, buttons);
        conversationStep.put(phone, "awaiting_voice_decision");
    }

    /**
     * Fire-and-forget: place an outbound voice call to the lead via the Retell
     * AI agent. Returns true when the provider accepted the call; false when it
     * was skipped (missing profile/agent) or failed (provider not configured or
     * from-number blank) so the caller can show an honest degraded message.
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
            voiceCallService.placeCall(
                VoiceProviderType.RETELL,
                new VoiceProvider.StartCallRequest(phoneE164, agentId, callMetadata, null),
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
}
