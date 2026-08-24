package com.callsagents.backend.chat;

import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.entity.LeadSource;
import com.callsagents.backend.leads.entity.LeadStatus;
import com.callsagents.backend.leads.repository.LeadRepository;
import com.callsagents.backend.whatsapp.service.GroqService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final GroqService groqService;
    private final LeadRepository leadRepository;

    private final Map<String, List<Map<String, String>>> conversationHistory = new ConcurrentHashMap<>();

    private static final int MAX_HISTORY = 20;

    private static final String SYSTEM_PROMPT = """
        Eres Naiara, asistente de ventas de Script9 — empresa de software y automatización con IA.

        PERSONALIDAD:
        - Profesional, cálida, directa. Hablas como una asistente de ventas real, no como un bot.
        - Usa el nombre del usuario de forma natural, NO en cada frase. Máximo 1 vez por intercambio.
        - Responde en español, máximo 2-3 oraciones por mensaje.
        - Una sola pregunta por mensaje. NUNCA hagas dos preguntas juntas.

        FLUJO DE CONVERSACIÓN:
        1. Preséntate brevemente y pregunta en qué puede ayudar
        2. Entiende la necesidad del usuario
        3. Pregunta nombre y email cuando el contexto lo justifique
        4. Confirma los datos recibidos
        5. Pregunta sobre timing
        6. Ofrece agendar una demo

        REGLAS ESTRICTAS:
        - NUNCA repitas el nombre del usuario en cada respuesta
        - NUNCA hagas más de una pregunta por mensaje
        - SIEMPRE confirma los datos cuando el usuario los proporcione
        - Si pregunta por precios, di que depende del proyecto y ofrece una demo

        CUÁNDO GUARDAR EL LEAD:
        Cuando tengas nombre Y email, añade al FINAL:
        [LEAD:name=NOMBRE|email=EMAIL|service=SERVICIO]
        """;

    public ChatService(GroqService groqService, LeadRepository leadRepository) {
        this.groqService = groqService;
        this.leadRepository = leadRepository;
    }

    public ChatResponse processMessage(String sessionId, String message) {
        if (!groqService.isConfigured()) {
            return new ChatResponse(sessionId, "El chat no está disponible ahora mismo. Inténtalo más tarde.", false);
        }

        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) {
            return new ChatResponse(sessionId, "¿En qué puedo ayudarte?", false);
        }

        List<Map<String, String>> history = conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());

        String aiResponse = groqService.chat(SYSTEM_PROMPT, history, text);
        if (aiResponse == null) {
            return new ChatResponse(sessionId, "Disculpa, tuve un problema técnico. ¿Podrías repetir?", false);
        }

        history.add(Map.of("role", "user", "content", text));
        history.add(Map.of("role", "assistant", "content", aiResponse));

        while (history.size() > MAX_HISTORY) {
            history.remove(0);
            history.remove(0);
        }

        // Extract lead data
        boolean leadCaptured = false;
        String leadTag = "[LEAD:";
        int start = aiResponse.indexOf(leadTag);
        if (start != -1) {
            int end = aiResponse.indexOf("]", start);
            if (end != -1) {
                String leadDataStr = aiResponse.substring(start + leadTag.length(), end);
                aiResponse = aiResponse.substring(0, start).trim();

                Map<String, String> data = new HashMap<>();
                for (String part : leadDataStr.split("\\|")) {
                    String[] kv = part.split("=", 2);
                    if (kv.length == 2) data.put(kv[0].trim(), kv[1].trim());
                }
                leadCaptured = saveLead(sessionId, data);
            }
        }

        log.info("Chat [{}]: input='{}' response='{}' leadCaptured={}", sessionId, text, aiResponse, leadCaptured);
        return new ChatResponse(sessionId, aiResponse, leadCaptured);
    }

    private boolean saveLead(String sessionId, Map<String, String> data) {
        try {
            String name = data.getOrDefault("name", "Desconocido");
            String email = data.get("email");
            String service = data.getOrDefault("service", "web-chat");

            String firstName = name.contains(" ") ? name.substring(0, name.indexOf(" ")) : name;
            String lastName = name.contains(" ") ? name.substring(name.indexOf(" ") + 1) : "";

            Lead lead = Lead.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phone(null)
                .company(null)
                .status(LeadStatus.NEW)
                .source(LeadSource.WHATSAPP) // TODO: add WEB_CHAT source
                .notes("Servicio de interés: " + service + " |来源: web chat |sessionId: " + sessionId)
                .doNotCall(false)
                .build();
            leadRepository.save(lead);
            log.info("Chat lead created: sessionId={} name={}", sessionId, name);
            return true;
        } catch (Exception e) {
            log.error("Failed to save chat lead: sessionId={}", sessionId, e);
            return false;
        }
    }
}
