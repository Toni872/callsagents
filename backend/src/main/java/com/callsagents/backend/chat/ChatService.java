package com.callsagents.backend.chat;

import com.callsagents.backend.business.entity.BusinessProfile;
import com.callsagents.backend.business.service.BusinessPromptComposer;
import com.callsagents.backend.business.service.BusinessService;
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
import java.util.*;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final GroqService groqService;
    private final LeadRepository leadRepository;
    private final BusinessService businessService;
    private final BusinessPromptComposer promptComposer;

    // Bounded cache: max 1000 sessions, evict after 30min inactivity
    private final Cache<String, List<Map<String, String>>> conversationHistory = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .build();

    private static final int MAX_HISTORY = 20;

    public ChatService(GroqService groqService, LeadRepository leadRepository,
                       BusinessService businessService, BusinessPromptComposer promptComposer) {
        this.groqService = groqService;
        this.leadRepository = leadRepository;
        this.businessService = businessService;
        this.promptComposer = promptComposer;
    }

    public ChatResponse processMessage(String sessionId, String message) {
        return processMessage(sessionId, message, null);
    }

    public ChatResponse processMessage(String sessionId, String message, UUID businessId) {
        if (!groqService.isConfigured()) {
            return new ChatResponse(sessionId, "El chat no está disponible ahora mismo. Inténtalo más tarde.", false);
        }

        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) {
            return new ChatResponse(sessionId, "¿En qué puedo ayudarte?", false);
        }

        List<Map<String, String>> history = conversationHistory.get(sessionId, k -> new ArrayList<>());

        String systemPrompt = resolveSystemPrompt(businessId);
        String aiResponse = groqService.chat(systemPrompt, history, text);
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
                leadCaptured = saveLead(sessionId, data, businessId);
            }
        }

        log.info("Chat [{}]: input='{}' response='{}' leadCaptured={}", sessionId, text, aiResponse, leadCaptured);
        return new ChatResponse(sessionId, aiResponse, leadCaptured);
    }

    private static final int TRIAL_LEAD_LIMIT = 50;

    private String resolveSystemPrompt(UUID businessId) {
        if (businessId == null) {
            return promptComposer.composeDefault();
        }
        BusinessProfile profile = businessService.getProfileEntityByUserId(businessId);
        return promptComposer.compose(profile);
    }

    private boolean saveLead(String sessionId, Map<String, String> data, UUID businessId) {
        try {
            // Trial lead limit check (per business)
            long totalLeads = leadRepository.countByCreatedBy(businessId);
            if (totalLeads >= TRIAL_LEAD_LIMIT) {
                log.warn("Lead limit reached ({}) — skipping lead creation for session {}", TRIAL_LEAD_LIMIT, sessionId);
                return false;
            }

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
                .source(LeadSource.WEB_CHAT)
                .notes("Servicio de interés: " + service + " |来源: web chat |sessionId: " + sessionId)
                .doNotCall(false)
                .createdBy(businessId)
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
