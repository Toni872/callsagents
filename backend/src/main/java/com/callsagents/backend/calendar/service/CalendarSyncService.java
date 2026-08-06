package com.callsagents.backend.calendar.service;

import com.callsagents.backend.appointments.entity.Appointment;
import com.callsagents.backend.calendar.domain.CalendarIntegration;
import com.callsagents.backend.calendar.domain.CalendarProviderType;
import com.callsagents.backend.calendar.domain.CalendarSyncStatus;
import com.callsagents.backend.calendar.repo.CalendarIntegrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrator for calendar sync. Wraps the per-provider CalendarProvider impls
 * (Google, Outlook), handles token decryption, status tracking, and error
 * capture.
 *
 * Stateless; safe to be a Spring singleton.
 */
@Service
public class CalendarSyncService {

    private static final Logger log = LoggerFactory.getLogger(CalendarSyncService.class);

    private final CalendarIntegrationRepository integrationRepo;
    private final EncryptionService encryption;
    private final List<CalendarProvider> providers;

    public CalendarSyncService(
        CalendarIntegrationRepository integrationRepo,
        EncryptionService encryption,
        List<CalendarProvider> providers
    ) {
        this.integrationRepo = integrationRepo;
        this.encryption = encryption;
        this.providers = providers;
    }

    public CalendarProvider providerOf(CalendarProviderType type) {
        return providers.stream()
            .filter(p -> p.provider() == type)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No provider bean registered for " + type));
    }

    /** All integrations for a user (without tokens). */
    public List<CalendarIntegration> listForUser(UUID userId) {
        return integrationRepo.findAllByUserId(userId);
    }

    /** The active integration for a user + provider, if one exists. */
    public Optional<CalendarIntegration> findActive(UUID userId, CalendarProviderType provider) {
        return integrationRepo.findByUserIdAndProvider(userId, provider)
            .filter(CalendarIntegration::getSyncEnabled);
    }

    /**
     * Push an Appointment to the user's calendar. Best-effort — failures update
     * the integration's last_sync_status to FAILED but do not throw (the appointment
     * itself is already persisted; sync is an additive concern).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncAppointment(Appointment appointment) {
        UUID agentUserId = appointment.getUserId(); // creator is the agent (MVP assumption)
        var integrationOpt = findActive(agentUserId, CalendarProviderType.GOOGLE);
        if (integrationOpt.isEmpty()) {
            log.debug("syncAppointment: no active Google integration for user={}", agentUserId);
            return;
        }
        var integration = integrationOpt.get();

        var provider = providerOf(integration.getProvider());
        if (!provider.isConfigured()) {
            log.warn("syncAppointment: provider {} not configured in env", provider.provider());
            markFailure(integration, "Provider not configured (missing env vars)");
            return;
        }

        try {
            String accessToken = encryption.decrypt(integration.getAccessTokenEncrypted());
            String eventId = provider.createEvent(accessToken, integration.getExternalCalendarId(),
                new CalendarProvider.EventPayload(
                    appointment.getNotes() != null ? appointment.getNotes() : "Reunión Callsagents",
                    "Llamada de seguimiento - generado por Callsagents",
                    appointment.getScheduledAt(),
                    appointment.getScheduledAt().plusSeconds(
                        appointment.getDurationMinutes() != null
                            ? appointment.getDurationMinutes() * 60L
                            : 1800L),
                    null,
                    List.of()
                ));
            // Persist the linkage on the appointment itself — separate update so we
            // don't tie ourselves to the outer transaction.
            appointment.setExternalProvider(provider.provider().name());
            appointment.setExternalEventId(eventId);
            appointment.setExternalSyncedAt(Instant.now());
            markSuccess(integration);
            log.info("Synced appointment {} to {} as event {}", appointment.getId(),
                provider.provider(), eventId);
        } catch (Exception e) {
            log.warn("syncAppointment failed for appointment {}: {}", appointment.getId(), e.getMessage());
            markFailure(integration, e.getMessage());
        }
    }

    private void markSuccess(CalendarIntegration integration) {
        integration.setLastSyncAt(Instant.now());
        integration.setLastSyncStatus(CalendarSyncStatus.SYNCED);
        integration.setLastSyncError(null);
        integrationRepo.save(integration);
    }

    private void markFailure(CalendarIntegration integration, String error) {
        integration.setLastSyncAt(Instant.now());
        integration.setLastSyncStatus(CalendarSyncStatus.FAILED);
        // Cap error string to fit in DB column
        integration.setLastSyncError(error != null && error.length() > 500
            ? error.substring(0, 500) : error);
        integrationRepo.save(integration);
    }
}
