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

    // Conversation history per phone number (keeps last N messages)
    private final Map<String, List<Map<String, String>>> conversationHistory = new ConcurrentHashMap<>();
    // Collected lead data per phone
    private final Map<String, Map<String, String>> leadData = new ConcurrentHashMap<>();

    private static final int MAX_HISTORY = 20;

    private static final String SYSTEM_PROMPT = """
        Eres Naiara, asistente de ventas de Script9 — empresa de software y automatización con IA.
        
        PERSONALIDAD:
        - Profesional, cálida, directa. Hablas como una asistente de ventas real, no como un bot.
        - Usa el nombre del usuario de forma natural, NO en cada frase. Máximo 1 vez por intercambio de mensajes.
        - Responde en español, máximo 2-3 oraciones por mensaje.
        - Una sola pregunta por mensaje. NUNCA hagas dos preguntas juntas.
        
        FLUJO DE CONVERSACIÓN:
        1. Primer mensaje: preséntate brevemente y pregunta en qué puede ayudar
        2. Entiende la necesidad del usuario (qué automatizar, qué problema tiene)
        3. Pregunta el nombre y email SOLO cuando el contexto lo justifique (no al principio)
        4. Confirma los datos recibidos: "OK, tu email es X"
        5. Pregunta sobre timing: "¿Cuándo te gustaría empezar?"
        6. Ofrece agendar una demo cuando tengas suficiente información
        
        REGLAS ESTRICTAS:
        - NUNCA repitas el nombre del usuario en cada respuesta
        - NUNCA hagas más de una pregunta por mensaje
        - SIEMPRE confirma los datos cuando el usuario los proporcione
        - Si el usuario pregunta por precios, di que depende del proyecto y ofrece una demo personalizada
        - Si el usuario dice "hola" o "inicio", reinicia la conversación
        - Si el usuario se despide, despídete de forma breve y natural
        
        CUÁNDO GUARDAR EL LEAD:
        Cuando tengas nombre Y email del usuario, añade al FINAL de tu respuesta:
        [LEAD:name=NOMBRE|email=EMAIL|service=SERVICIO]
        
        EJEMPLOS DE BUENAS RESPUESTAS:
        
        Usuario: "Hola"
        Bot: "¡Hola! Soy Naiara de Script9. ¿En qué puedo ayudarte?"
        
        Usuario: "Quiero automatizar mi negocio"
        Bot: "¿Qué parte de tu negocio te gustaría automatizar? Por ejemplo: ventas, atención al cliente, procesos internos..."
        
        Usuario: "Ventas"
        Bot: "Perfecto. ¿Cómo te llamas y cuál es tu email para poder enviarte información?"
        
        Usuario: "Antonio, antonio@email.com"
        Bot: "OK Antonio, tu email es antonio@email.com. ¿Cuándo te gustaría empezar a automatizar tus ventas?"
        
        Usuario: "Lo antes posible"
        Bot: "Genial. Te propongo agendar una demo personalizada donde vemos tu caso. ¿Te viene bien esta semana?"
        """;


    public WhatsAppAiChatbotService(GroqService groqService, LeadRepository leadRepository) {
        this.groqService = groqService;
        this.leadRepository = leadRepository;
    }

    /**
     * Process an incoming message with AI.
     * Returns the AI response, or null if Groq is not configured.
     */
    public String processMessage(String phone, String message) {
        if (!groqService.isConfigured()) {
            return null; // Fall back to basic WhatsAppService
        }

        String text = message == null ? "" : message.trim();

        // Global commands
        if (isReset(text)) {
            conversationHistory.remove(phone);
            leadData.remove(phone);
            return "¡Hola! 👋 Soy Naiara, tu asistente de Script9. ¿En qué puedo ayudarte hoy?";
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
            history.remove(0); // Remove user + assistant pair
        }

        // Check if AI extracted lead data
        String cleanResponse = extractAndSaveLead(phone, aiResponse);

        log.info("AI chatbot [{}]: input='{}' response='{}'", phone, text, cleanResponse);
        return cleanResponse;
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

        // Parse lead data
        Map<String, String> data = new HashMap<>();
        for (String part : leadDataStr.split("\\|")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                data.put(kv[0].trim(), kv[1].trim());
            }
        }

        saveLead(phone, data);
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

    private static boolean isReset(String text) {
        String lower = text.toLowerCase();
        return lower.equals("hola") || lower.equals("inicio") || lower.equals("reset") || lower.equals("reiniciar");
    }
}
