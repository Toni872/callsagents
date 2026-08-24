package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.entity.LeadSource;
import com.callsagents.backend.leads.entity.LeadStatus;
import com.callsagents.backend.leads.repository.LeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WhatsAppAiChatbotService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppAiChatbotService.class);

    private final GroqService groqService;
    private final LeadRepository leadRepository;
    private final VonageMessageService vonageMessageService;

    // Conversation history per phone number
    private final Map<String, List<Map<String, String>>> conversationHistory = new ConcurrentHashMap<>();
    // Collected lead data per phone
    private final Map<String, Map<String, String>> leadData = new ConcurrentHashMap<>();
    // Conversation step per phone (for interactive flow)
    private final Map<String, String> conversationStep = new ConcurrentHashMap<>();

    private static final int MAX_HISTORY = 20;

    // System prompt for AI with interactive step awareness
    private static final String SYSTEM_PROMPT = """
        Eres Naiara, asistente de ventas de Script9 — empresa de software y automatización con IA.

        PERSONALIDAD:
        - Profesional, cálida, directa. Hablas como una asistente de ventas real, no como un bot.
        - Usa el nombre del usuario de forma natural, NO en cada frase. Máximo 1 vez por intercambio de mensajes.
        - Responde en español, máximo 2-3 oraciones por mensaje.
        - Una sola pregunta por mensaje. NUNCA hagas dos preguntas juntas.

        FLUJO DE CONVERSACIÓN:
        1. Preséntate brevemente y pregunta en qué puede ayudar
        2. Entiende la necesidad del usuario (qué automatizar, qué problema tiene)
        3. Pregunta el nombre y email SOLO cuando el contexto lo justifique
        4. Confirma los datos recibidos: "OK, tu email es X"
        5. Pregunta sobre timing: "¿Cuándo te gustaría empezar?"
        6. Ofrece agendar una demo cuando tengas suficiente información

        REGLAS ESTRICTAS:
        - NUNCA repitas el nombre del usuario en cada respuesta
        - NUNCA hagas más de una pregunta por mensaje
        - SIEMPRE confirma los datos cuando el usuario los proporcione
        - Si el usuario pregunta por precios, di que depende del proyecto y ofrece una demo
        - Si el usuario dice "hola" o "inicio", reinicia la conversación
        - Si el usuario se despide, despídete de forma breve y natural

        CUÁNDO GUARDAR EL LEAD:
        Cuando tengas nombre Y email del usuario, añade al FINAL de tu respuesta:
        [LEAD:name=NOMBRE|email=EMAIL|service=SERVICIO]

        EJEMPLOS:
        Usuario: "Hola" → "¡Hola! Soy Naiara de Script9. ¿En qué puedo ayudarte?"
        Usuario: "Ventas" → "¿Cómo te llamas y tu email para enviarte info?"
        Usuario: "Antonio, antonio@email.com" → "OK, tu email es antonio@email.com. ¿Cuándo te gustaría empezar?"
        """;

    public WhatsAppAiChatbotService(GroqService groqService, LeadRepository leadRepository,
                                     VonageMessageService vonageMessageService) {
        this.groqService = groqService;
        this.leadRepository = leadRepository;
        this.vonageMessageService = vonageMessageService;
    }

    /**
     * Process an incoming message with AI + interactive messages.
     * Returns the text response, or null if Groq is not configured or buttons were sent.
     */
    public String processMessage(String phone, String message) {
        if (!groqService.isConfigured()) {
            return null;
        }

        String text = message == null ? "" : message.trim();
        String step = conversationStep.getOrDefault(phone, "initial");
        log.info("processMessage [{}]: step={} text='{}'", phone, step, text);

        // Global commands
        if (isReset(text)) {
            resetConversation(phone);
            sendInteractiveGreeting(phone);
            return null;
        }

        // Handle button/list replies — returns [responseText, buttonsSent]
        String[] result = handleButtonReply(phone, text, step);
        if (result[0] != null) {
            return result[0]; // Text response from handler
        }
        if ("true".equals(result[1])) {
            return null; // Interactive buttons already sent, skip AI
        }

        // Get or create conversation history
        List<Map<String, String>> history = conversationHistory.computeIfAbsent(phone, k -> new ArrayList<>());

        // Call Groq AI
        String aiResponse = groqService.chat(SYSTEM_PROMPT, history, text);

        if (aiResponse == null) {
            log.warn("Groq returned null for phone={}", phone);
            return "Disculpa, tuve un problema técnico. ¿Podrías repetir tu mensaje?";
        }

        // Update conversation history
        history.add(Map.of("role", "user", "content", text));
        history.add(Map.of("role", "assistant", "content", aiResponse));

        // Trim history if too long
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
            history.remove(0);
        }

        // Check if AI extracted lead data
        String cleanResponse = extractAndSaveLead(phone, aiResponse);

        // Determine next interactive step
        advanceStep(phone, text, cleanResponse, step);

        log.info("AI chatbot [{}]: step={} input='{}' response='{}'", phone, step, text, cleanResponse);
        return cleanResponse;
    }

    /**
     * Send the initial greeting with interactive buttons.
     */
    public void sendInteractiveGreeting(String phone) {
        String body = "Hola, soy Naiara de Script9.\n\n¿Qué te gustaría hacer?";
        List<String[]> buttons = List.of(
            new String[]{"intent_ventas", "Ventas"},
            new String[]{"intent_soporte", "Soporte"},
            new String[]{"intent_demo", "Agendar demo"}
        );
        boolean sent = vonageMessageService.sendButtons(phone, body, buttons);
        if (!sent) {
            // Fallback to text
            vonageMessageService.sendText(phone, "¡Hola! Soy Naiara de Script9. ¿En qué puedo ayudarte?");
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
    private String[] handleButtonReply(String phone, String text, String step) {
        // Intent buttons are GLOBAL — handled at any step
        if (text.startsWith("intent_")) {
            return switch (text) {
                case "intent_ventas" -> {
                    conversationStep.put(phone, "collecting_info");
                    conversationHistory.remove(phone);
                    yield new String[]{"Perfecto, te ayudo con ventas.\n\n¿Cómo te llamas y cuál es tu email?", "false"};
                }
                case "intent_soporte" -> {
                    conversationStep.put(phone, "support");
                    conversationHistory.remove(phone);
                    yield new String[]{"Claro, ¿en qué puedo ayudarte con soporte?", "false"};
                }
                case "intent_demo" -> {
                    conversationStep.put(phone, "collecting_info");
                    conversationHistory.remove(phone);
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
                    conversationStep.remove(phone);
                    leadData.remove(phone);
                    yield new String[]{"¡Genial! Te propongo una demo de 15 minutos donde vemos tu caso.\n\nTe envío un email con el link para agendar.\n\n¿Te parece bien esta semana?", "false"};
                }
                case "confirm_no" -> {
                    conversationStep.remove(phone);
                    leadData.remove(phone);
                    yield new String[]{"No te preocupes. Cuando quieras, aquí estoy.\n\n¡Hasta pronto!", "false"};
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
        Map<String, String> data = leadData.getOrDefault(phone, Map.of());
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
        leadData.computeIfAbsent(phone, k -> new HashMap<>()).put("timing", timing);
    }

    /**
     * Reset conversation state.
     */
    private void resetConversation(String phone) {
        conversationHistory.remove(phone);
        leadData.remove(phone);
        conversationStep.remove(phone);
    }

    private static boolean isReset(String text) {
        String lower = text.toLowerCase();
        return lower.equals("hola") || lower.equals("inicio") || lower.equals("reset") || lower.equals("reiniciar");
    }

    private static boolean containsEmail(String text) {
        return text.matches(".*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*");
    }

    /**
     * Extract [LEAD:...] tag from AI response, save lead, and return clean response.
     */
    private String extractAndSaveLead(String phone, String aiResponse) {
        String leadTag = "[LEAD:";
        int start = aiResponse.indexOf(leadTag);
        if (start == -1) return aiResponse;

        int end = aiResponse.indexOf("]", start);
        if (end == -1) return aiResponse;

        String leadDataStr = aiResponse.substring(start + leadTag.length(), end);
        String cleanResponse = aiResponse.substring(0, start).trim();

        Map<String, String> data = new HashMap<>();
        for (String part : leadDataStr.split("\\|")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                data.put(kv[0].trim(), kv[1].trim());
            }
        }

        // Merge with existing lead data
        leadData.computeIfAbsent(phone, k -> new HashMap<>()).putAll(data);

        saveLead(phone, leadData.get(phone));
        return cleanResponse;
    }

    private void saveLead(String phone, Map<String, String> data) {
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
                leadRepository.save(lead);
                log.info("AI chatbot lead updated: phone={}", phoneE164);
            } else {
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
                    .build();
                leadRepository.save(lead);
                log.info("AI chatbot lead created: phone={}", phoneE164);
            }
        } catch (Exception e) {
            log.error("Failed to save AI chatbot lead: phone={}", phone, e);
        }
    }
}
