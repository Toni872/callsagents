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
        Eres Naiara, la asistente virtual de Script9 — una empresa de software y automatización con IA.
        
        Tu objetivo:
        1. Saludar al usuario de forma cálida y profesional
        2. Entender qué necesita o qué problema tiene
        3. Explicar brevemente cómo Script9 puede ayudarle
        4. Recopilar: nombre, email, y qué servicio le interesa
        5. Ofrecer agendar una demo o llamada
        
        Reglas:
        - Responde en español, de forma breve y natural (máximo 2-3 oraciones por mensaje)
        - No eres un chatbot genérico — eres una asistente de ventas profesional
        - Si el usuario pregunta por precios, di que depende del tamaño y que pueden agendar una demo personalizada
        - Si el usuario dice "llamada" o "llámame", pide su nombre y email para agendar
        - Si el usuario dice "hola" o "inicio", reinicia la conversación con un saludo
        - Guarda el nombre y email del usuario cuando los proporcione
        - Si el usuario se despide, despídete amablemente
        
        Formato de respuesta al sistema (usa esto solo cuando tengas datos completos):
        Si ya tienes nombre Y email del usuario, añade al FINAL de tu respuesta, en una nueva línea:
        [LEAD:name=NOMBRE_DEL_USUARIO|email=EMAIL_DEL_USUARIO|service=SERVICIO_DE_INTERÉS]
        
        Ejemplo:
        ¡Perfecto Antonio! He registrado tu interés. Te contactaremos pronto.
        [LEAD:name=Antonio Lloret|email=antonio@test.com|service=automatización de ventas]
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
            return "¡Hola! 👋 Soy Naiara, tu asistente de Callsagents. ¿En qué puedo ayudarte hoy?";
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
