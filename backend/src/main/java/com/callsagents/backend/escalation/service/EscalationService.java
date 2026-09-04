package com.callsagents.backend.escalation.service;

import com.callsagents.backend.business.entity.BusinessProfile;
import com.callsagents.backend.business.service.BusinessService;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.common.exception.ForbiddenException;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import com.callsagents.backend.escalation.entity.Escalation;
import com.callsagents.backend.escalation.entity.EscalationStage;
import com.callsagents.backend.escalation.repository.EscalationRepository;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.repository.LeadRepository;
import com.callsagents.backend.voice.domain.VoiceCall;
import com.callsagents.backend.voice.domain.VoiceProviderType;
import com.callsagents.backend.voice.repo.VoiceCallRepository;
import com.callsagents.backend.voice.service.VoiceCallService;
import com.callsagents.backend.voice.service.VoiceProvider;
import com.callsagents.backend.whatsapp.service.VonageMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrator for the lead escalation flow.
 *
 * <p>After a WhatsApp chatbot qualifies a lead (the lead confirms a demo via
 * the interactive flow), {@link #qualify} sends a WhatsApp follow-up message,
 * waits a per-business timeout, and — if the lead never replies — elevates to a
 * Retell AI outbound voice call. The voice call is a FALLBACK ONLY: it is never
 * a first contact.
 *
 * <p>All entry points are exception-safe: external calls (Vonage, Retell) are
 * wrapped so a provider failure never breaks the lead conversation or the
 * scheduler tick. The orchestrator must NEVER crash the chatbot flow or the
 * webhook.
 */
@Service
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    private static final List<EscalationStage> NON_TERMINAL_STAGES = List.of(
        EscalationStage.QUALIFIED,
        EscalationStage.FOLLOWUP_SENT,
        EscalationStage.WAITING_REPLY,
        EscalationStage.VOICE_CALLED
    );

    private static final int DEFAULT_REPLY_TIMEOUT_MINUTES = 30;
    private static final String DEFAULT_FOLLOWUP_MESSAGE =
        "Hola {name}, vi que estuviste consultando sobre nuestros servicios. "
            + "¿Te gustaría que agendemos una llamada rápida para contarte más?";

    private final EscalationRepository repository;
    private final LeadRepository leadRepository;
    private final BusinessService businessService;
    private final VonageMessageService vonageMessageService;
    private final VoiceCallService voiceCallService;
    private final VoiceCallRepository voiceCallRepository;

    public EscalationService(EscalationRepository repository,
                             LeadRepository leadRepository,
                             BusinessService businessService,
                             VonageMessageService vonageMessageService,
                             VoiceCallService voiceCallService,
                             VoiceCallRepository voiceCallRepository) {
        this.repository = repository;
        this.leadRepository = leadRepository;
        this.businessService = businessService;
        this.vonageMessageService = vonageMessageService;
        this.voiceCallService = voiceCallService;
        this.voiceCallRepository = voiceCallRepository;
    }

    /**
     * Entry point from the chatbot when a lead confirms a demo.
     *
     * <p>Idempotent: if a non-terminal escalation already exists for the lead it
     * aborts (no duplicates). Never throws — failures are logged and swallowed so
     * the lead conversation is never interrupted.
     */
    @Transactional
    public void qualify(UUID leadId, UUID userId) {
        try {
            Lead lead = leadRepository.findById(leadId).orElse(null);
            if (lead == null) {
                log.warn("Escalation qualify skipped: lead not found leadId={}", leadId);
                return;
            }
            if (hasActiveEscalation(leadId)) {
                log.info("Escalation qualify skipped (non-terminal escalation exists): leadId={}", leadId);
                return;
            }

            BusinessProfile profile = businessService.getProfileEntityByUserId(userId);
            int timeoutMinutes = replyTimeoutMinutesOf(profile);
            boolean enabled = profile == null || Boolean.TRUE.equals(profile.getEscalationEnabled());

            Escalation escalation = Escalation.builder()
                .lead(lead)
                .userId(userId)
                .stage(EscalationStage.QUALIFIED)
                .build();

            // Escalation disabled for this business: record qualified, stop.
            if (!enabled) {
                repository.save(escalation);
                log.info("Escalation disabled for business; recorded QUALIFIED leadId={}", leadId);
                return;
            }

            // Do-not-call lead: record qualified, never message or call.
            if (Boolean.TRUE.equals(lead.getDoNotCall())) {
                repository.save(escalation);
                log.info("Lead is do-not-call; recorded QUALIFIED without followup leadId={}", leadId);
                return;
            }

            // No phone: cannot message nor call — abandon.
            if (isBlank(lead.getPhone())) {
                escalation.setStage(EscalationStage.ABANDONED);
                repository.save(escalation);
                log.info("Lead has no phone; escalation ABANDONED leadId={}", leadId);
                return;
            }

            String message = buildFollowupMessage(profile, lead.getFirstName());
            boolean sent = vonageMessageService.sendText(lead.getPhone(), message);
            if (!sent) {
                // Follow-up did not go out — do NOT arm the wait/voice escalation
                // on a message the lead may never have received.
                repository.save(escalation);
                log.warn("WhatsApp followup send failed; escalation stays QUALIFIED leadId={}", leadId);
                return;
            }

            Instant now = Instant.now();
            escalation.setStage(EscalationStage.FOLLOWUP_SENT);
            escalation.setFollowupSentAt(now);
            escalation.setWaitingUntil(now.plus(Duration.ofMinutes(timeoutMinutes)));
            repository.save(escalation);
            log.info("Escalation qualified + followup sent leadId={} timeoutMinutes={}", leadId, timeoutMinutes);
        } catch (Exception e) {
            log.error("Escalation qualify failed: leadId={} userId={}", leadId, userId, e);
        }
    }

    /**
     * Called when the lead replies via WhatsApp so we STOP escalating.
     * If the escalation is awaiting a reply, mark it RESOLVED (no voice call).
     */
    @Transactional
    public void handleReply(UUID leadId) {
        try {
            Optional<Escalation> active = repository
                .findFirstByLeadIdAndStageInOrderByCreatedAtDesc(leadId, NON_TERMINAL_STAGES);
            if (active.isEmpty()) {
                log.debug("Escalation handleReply: no active escalation leadId={}", leadId);
                return;
            }
            Escalation escalation = active.get();
            escalation.setStage(EscalationStage.RESOLVED);
            escalation.setVoiceOutcome("LEAD_REPLIED");
            repository.save(escalation);
            log.info("Escalation RESOLVED on lead reply: leadId={} escalationId={}", leadId, escalation.getId());
        } catch (Exception e) {
            log.error("Escalation handleReply failed: leadId={}", leadId, e);
        }
    }

    /**
     * Called by the scheduler for escalations whose WAITING_REPLY window has
     * passed. Places a Retell outbound voice call (fallback).
     */
    @Transactional
    public void escalateToVoice(UUID escalationId) {
        try {
            Escalation escalation = repository.findById(escalationId).orElse(null);
            if (escalation == null) {
                log.warn("Escalation escalateToVoice skipped: not found escalationId={}", escalationId);
                return;
            }
            if (escalation.getStage() != EscalationStage.WAITING_REPLY) {
                log.info("Escalation not WAITING_REPLY; skip voice escalate stage={} escalationId={}",
                    escalation.getStage(), escalationId);
                return;
            }

            Lead lead = escalation.getLead();
            if (lead == null) {
                escalation.setStage(EscalationStage.ABANDONED);
                repository.save(escalation);
                log.warn("Escalation lead missing; ABANDONED escalationId={}", escalationId);
                return;
            }
            if (Boolean.TRUE.equals(lead.getDoNotCall()) || isBlank(lead.getPhone())) {
                escalation.setStage(EscalationStage.ABANDONED);
                repository.save(escalation);
                log.info("Escalation lead not callable (doNotCall={}); ABANDONED escalationId={}",
                    lead.getDoNotCall(), escalationId);
                return;
            }

            BusinessProfile profile = businessService.getProfileEntityByUserId(escalation.getUserId());
            String agentId = profile != null ? profile.getVoiceAgentId() : null;

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("leadId", lead.getId().toString());
            metadata.put("escalationId", escalation.getId().toString());

            Map<String, Object> dynamicVars = new HashMap<>(voiceCallService.composeVariables(profile));

            VoiceCall call = voiceCallService.placeCall(
                VoiceProviderType.RETELL,
                new VoiceProvider.StartCallRequest(lead.getPhone(), agentId, metadata, dynamicVars),
                escalation.getUserId(),
                null
            );

            // placeCall persists without the lead (existing behaviour); attach it
            // explicitly via the repository since we're in a separate transaction.
            call.setLead(lead);
            voiceCallRepository.save(call);

            escalation.setStage(EscalationStage.VOICE_CALLED);
            escalation.setVoiceCalledAt(Instant.now());
            escalation.setProviderCallId(call.getProviderCallId());
            repository.save(escalation);
            log.info("Escalated to Retell voice: escalationId={} providerCallId={}",
                escalationId, call.getProviderCallId());
        } catch (Exception e) {
            log.error("Escalation escalateToVoice failed: escalationId={}", escalationId, e);
        }
    }

    /**
     * Cancel an active escalation (API use).
     */
    @Transactional
    public Escalation cancel(UUID escalationId, UUID currentUserId, UserRole role) {
        Escalation escalation = repository.findById(escalationId)
            .orElseThrow(() -> new ResourceNotFoundException("Escalation not found: " + escalationId));
        if (!isOwnerOrSupervisor(escalation, currentUserId, role)) {
            throw new ForbiddenException("You can only cancel escalations related to your business");
        }
        if (escalation.getStage() != EscalationStage.RESOLVED
            && escalation.getStage() != EscalationStage.ABANDONED
            && escalation.getStage() != EscalationStage.CANCELLED) {
            escalation.setStage(EscalationStage.CANCELLED);
            repository.save(escalation);
            log.info("Escalation cancelled: escalationId={}", escalationId);
        }
        return escalation;
    }

    @Transactional(readOnly = true)
    public Optional<Escalation> getForLead(UUID leadId, UUID currentUserId) {
        leadRepository.findById(leadId)
            .filter(lead -> lead.getCreatedBy() != null && lead.getCreatedBy().equals(currentUserId))
            .orElseThrow(() -> new ResourceNotFoundException("Lead not found: " + leadId));
        return repository.findFirstByLeadIdOrderByCreatedAtDesc(leadId);
    }

    private static boolean isOwnerOrSupervisor(Escalation escalation, UUID userId, UserRole role) {
        return (escalation.getUserId() != null && escalation.getUserId().equals(userId))
            || role == UserRole.ADMIN
            || role == UserRole.SUPERVISOR;
    }

    /** Active (non-terminal) escalations for a user. */
    @Transactional(readOnly = true)
    public List<Escalation> listActiveForUser(UUID userId) {
        return repository.findAll().stream()
            .filter(e -> userId.equals(e.getUserId()) && NON_TERMINAL_STAGES.contains(e.getStage()))
            .toList();
    }

    private boolean hasActiveEscalation(UUID leadId) {
        return repository
            .findFirstByLeadIdAndStageInOrderByCreatedAtDesc(leadId, NON_TERMINAL_STAGES)
            .isPresent();
    }

    private static int replyTimeoutMinutesOf(BusinessProfile profile) {
        if (profile != null && profile.getReplyTimeoutMinutes() != null) {
            return profile.getReplyTimeoutMinutes();
        }
        return DEFAULT_REPLY_TIMEOUT_MINUTES;
    }

    private static String buildFollowupMessage(BusinessProfile profile, String firstName) {
        String template = (profile != null && !isBlank(profile.getFollowupMessage()))
            ? profile.getFollowupMessage()
            : DEFAULT_FOLLOWUP_MESSAGE;
        String name = isBlank(firstName) ? "" : firstName;
        return template.replace("{name}", name);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
