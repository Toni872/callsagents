package com.callsagents.backend.escalation.service;

import com.callsagents.backend.escalation.entity.Escalation;
import com.callsagents.backend.escalation.entity.EscalationStage;
import com.callsagents.backend.escalation.repository.EscalationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Scans for escalations whose reply window has elapsed and elevates them to a
 * Retell outbound voice call (fallback). The reply timeout is per-business
 * (from {@code business_profiles}), so there is no global config key here.
 */
@Component
public class EscalationScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(EscalationScheduledTask.class);

    private final EscalationRepository escalationRepository;
    private final EscalationService escalationService;

    public EscalationScheduledTask(EscalationRepository escalationRepository,
                                   EscalationService escalationService) {
        this.escalationRepository = escalationRepository;
        this.escalationService = escalationService;
    }

    /**
     * Simple in-process guard against overlapping scheduler ticks processing the
     * same escalation concurrently (a single instance is assumed).
     */
    private final Object lock = new Object();

    @Scheduled(fixedDelay = 60000)
    public void checkTimeouts() {
        synchronized (lock) {
            try {
                Instant now = Instant.now();
                List<Escalation> due = escalationRepository
                    .findByStageAndWaitingUntilBefore(EscalationStage.WAITING_REPLY, now);
                if (due.isEmpty()) {
                    return;
                }
                log.info("Escalation timeout scan: {} due for voice escalation", due.size());
                for (Escalation escalation : due) {
                    escalationService.escalateToVoice(escalation.getId());
                }
            } catch (Exception e) {
                log.error("Escalation timeout scan failed", e);
            }
        }
    }
}
