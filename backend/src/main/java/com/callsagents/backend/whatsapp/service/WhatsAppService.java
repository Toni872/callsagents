package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.entity.LeadSource;
import com.callsagents.backend.leads.entity.LeadStatus;
import com.callsagents.backend.leads.repository.LeadRepository;
import com.callsagents.backend.whatsapp.domain.ConversationState;
import com.callsagents.backend.whatsapp.domain.ConversationStep;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

    private final LeadRepository leadRepository;
    // Bounded cache: max 2000 conversations, evict after 30min inactivity
    private final Cache<String, ConversationState> conversations = Caffeine.newBuilder()
        .maximumSize(2_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .build();

    public WhatsAppService(LeadRepository leadRepository) {
        this.leadRepository = leadRepository;
    }

    public String processMessage(String phone, String body) {
        return processMessage(phone, body, null);
    }

    public String processMessage(String phone, String body, UUID businessId) {
        String text = body == null ? "" : body.trim().toLowerCase();
        ConversationState state = conversations.getIfPresent(phone);
        if (state == null) {
            state = ConversationState.initial(phone);
        }

        // Global commands — always handled regardless of step
        if (isReset(text)) {
            conversations.invalidate(phone);
            return "¡Hasta pronto! Si necesitas algo más, escríbeme.";
        }
        if (isCallRequest(text)) {
        conversations.put(phone(state), state.withStep(ConversationStep.COMPLETED));
            return "Perfecto, te llamaremos pronto. ¿Cuál es tu email para que te confirmemos la llamada?";
        }

        // Route by conversation step
        String reply = switch (state.step()) {
            case INITIAL -> handleInitial(state, text);
            case SERVICE_RECEIVED -> handleService(state, text);
            case NAME_RECEIVED -> handleName(state, text, businessId);
            case EMAIL_RECEIVED -> handleEmail(state, text);
            case COMPLETED -> "¿Hay algo más en lo que te pueda ayudar? Si quieres empezar de nuevo, escríbeme 'hola'.";
        };

        log.debug("WhatsApp [{}]: step={} input='{}' reply='{}'", phone, state.step(), text, reply);
        return reply;
    }

    private String handleInitial(ConversationState state, String text) {
        if (text.isEmpty()) {
            return "¡Hola! Soy el asistente de Script9. ¿En qué puedo ayudarte?";
        }
        // First real message → treat as service interest
        String service = extractService(text);
        conversations.put(phone(state), state.withService(service));
        return "Genial, te interesa: " + service + ". ¿Cómo te llamas? (nombre y apellidos)";
    }

    private String handleService(ConversationState state, String text) {
        if (text.isEmpty()) {
            return "¿Cómo te llamas? (nombre y apellidos)";
        }
        conversations.put(phone(state), state.withName(text));
        return "¡Encantado, " + text.split("\\s+")[0] + "! ¿Cuál es tu email de contacto?";
    }

    private String handleName(ConversationState state, String text, UUID businessId) {
        if (text.isEmpty() || !text.contains("@")) {
            return "Necesito un email válido, por ejemplo: tu@email.com";
        }
        conversations.put(phone(state), state.withEmail(text));
        saveLead(state.withEmail(text), businessId);
        return "¡Listo! Tu interés en " + state.serviceInterest() + " ha quedado registrado. "
            + "Te contactaremos pronto. ¿Hay algo más en lo que te pueda ayudar?";
    }

    private String handleEmail(ConversationState state, String text) {
        // After email is provided, conversation is effectively complete
        conversations.put(state.phone(), state.withStep(ConversationStep.COMPLETED));
        return "¿Hay algo más en lo que te pueda ayudar?";
    }

    private void saveLead(ConversationState state, UUID businessId) {
        try {
            String firstName = extractFirstName(state.name());
            String lastName = extractLastName(state.name());
            String phoneE164 = normalizePhone(phone(state));

            Optional<Lead> existing = leadRepository.findByPhone(phoneE164);
            if (existing.isPresent()) {
                Lead lead = existing.get();
                if (state.email() != null) lead.setEmail(state.email());
                if (state.serviceInterest() != null) lead.setNotes(state.serviceInterest());
                lead.setSource(LeadSource.WHATSAPP);
                if (lead.getCreatedBy() == null && businessId != null) lead.setCreatedBy(businessId);
                leadRepository.save(lead);
                log.info("WhatsApp lead updated: phone={} email={}", phoneE164, state.email());
            } else {
                if (businessId == null) {
                    log.warn("Skip WhatsApp lead creation for {}: no business profile resolved (created_by NOT NULL)", phoneE164);
                    return;
                }
                Lead lead = Lead.builder()
                    .firstName(firstName)
                    .lastName(lastName)
                    .email(state.email())
                    .phone(phoneE164)
                    .company(null)
                    .status(LeadStatus.NEW)
                    .source(LeadSource.WHATSAPP)
                    .notes("Servicio de interés: " + state.serviceInterest())
                    .doNotCall(false)
                    .createdBy(businessId)
                    .build();
                leadRepository.save(lead);
                log.info("WhatsApp lead created: phone={} email={}", phoneE164, state.email());
            }
        } catch (Exception e) {
            log.error("Failed to save WhatsApp lead: phone={}", phone(state), e);
        }
    }

    // --- Helpers ---

    private static boolean isReset(String text) {
        return text.equals("hola") || text.equals("inicio") || text.equals("reset") || text.equals("reiniciar");
    }

    private static boolean isCallRequest(String text) {
        return text.contains("llamada") || text.contains("call") || text.contains("llámame") || text.contains("llamame");
    }

    private static String extractService(String text) {
        // Take the first sentence or first 80 chars as the service interest
        String cleaned = text.replaceAll("[!?¡¿.]+", "").trim();
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    private static String extractFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Desconocido";
        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }

    private static String extractLastName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String[] parts = fullName.trim().split("\\s+");
        return parts.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length)) : "";
    }

    private static String normalizePhone(String phone) {
        // phone comes as "whatsapp:+34687723287" — strip prefix
        return phone.replace("whatsapp:", "").trim();
    }

    private static String phone(ConversationState state) {
        return state.phone();
    }
}
