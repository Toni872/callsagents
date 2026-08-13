package com.callsagents.backend.calendar.service;

import com.callsagents.backend.appointments.entity.Appointment;
import com.callsagents.backend.appointments.entity.AppointmentStatus;
import com.callsagents.backend.appointments.repository.AppointmentRepository;
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
    private final AppointmentRepository appointmentRepo;
    private final EncryptionService encryption;
    private final List<CalendarProvider> providers;

    public CalendarSyncService(
        CalendarIntegrationRepository integrationRepo,
        AppointmentRepository appointmentRepo,
        EncryptionService encryption,
        List<CalendarProvider> providers
    ) {
        this.integrationRepo = integrationRepo;
        this.appointmentRepo = appointmentRepo;
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
            String eventId = runWithRefresh(integration,
                t -> provider.createEvent(t, integration.getExternalCalendarId(), toPayload(appointment)));
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

    /**
     * Push updates of an already-synced Appointment to Google. If the appointment
     * has no external event yet (created before sync existed, or sync failed at
     * create), fall back to creating the event.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateAppointment(Appointment appointment) {
        if (appointment.getExternalEventId() == null || appointment.getExternalEventId().isBlank()) {
            syncAppointment(appointment);
            return;
        }
        UUID agentUserId = appointment.getUserId(); // creator is the agent (MVP assumption)
        var integrationOpt = findActive(agentUserId, CalendarProviderType.GOOGLE);
        if (integrationOpt.isEmpty()) {
            log.debug("updateAppointment: no active Google integration for user={}", agentUserId);
            return;
        }
        var integration = integrationOpt.get();

        var provider = providerOf(integration.getProvider());
        if (!provider.isConfigured()) {
            log.warn("updateAppointment: provider {} not configured in env", provider.provider());
            markFailure(integration, "Provider not configured (missing env vars)");
            return;
        }

        try {
            runWithRefresh(integration,
                t -> provider.updateEvent(t, integration.getExternalCalendarId(),
                    appointment.getExternalEventId(), toPayload(appointment)));
            appointment.setExternalSyncedAt(Instant.now());
            markSuccess(integration);
            log.info("Updated Google event {} for appointment {}",
                appointment.getExternalEventId(), appointment.getId());
        } catch (Exception e) {
            log.warn("updateAppointment failed for appointment {}: {}", appointment.getId(), e.getMessage());
            markFailure(integration, e.getMessage());
        }
    }

    /**
     * Delete the external event for an Appointment. Called BEFORE the appointment
     * row is removed, so the linkage fields are still available to read.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteAppointmentEvent(Appointment appointment) {
        String eventId = appointment.getExternalEventId();
        if (eventId == null || eventId.isBlank()) return;
        UUID agentUserId = appointment.getUserId(); // creator is the agent (MVP assumption)
        var integrationOpt = findActive(agentUserId, CalendarProviderType.GOOGLE);
        if (integrationOpt.isEmpty()) {
            log.debug("deleteAppointmentEvent: no active Google integration for user={}", agentUserId);
            return;
        }
        var integration = integrationOpt.get();
        try {
            runWithRefresh(integration,
                t -> {
                    providerOf(integration.getProvider())
                        .deleteEvent(t, integration.getExternalCalendarId(), eventId);
                    return null;
                });
            markSuccess(integration);
            log.info("Deleted Google event {} for appointment {}", eventId, appointment.getId());
        } catch (Exception e) {
            log.warn("deleteAppointmentEvent failed for appointment {}: {}", appointment.getId(), e.getMessage());
            markFailure(integration, e.getMessage());
        }
    }

    /**
     * Backfill: create Google events for existing appointments that never synced
     * (created before the integration existed, or sync failed at create time).
     * Only FUTURE, actionable appointments (PENDING/CONFIRMED) — past/completed
     * history stays in the app, not in Google.
     */
    public BackfillResult backfillUnsynced() {
        List<Appointment> candidates = appointmentRepo
            .findAllByExternalEventIdIsNullAndScheduledAtGreaterThanEqualAndStatusIn(
                Instant.now(),
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED));

        int created = 0;
        int failed = 0;
        for (Appointment a : candidates) {
            if (a.getExternalEventId() != null && !a.getExternalEventId().isBlank()) {
                continue; // already synced since the query ran
            }
            syncAppointment(a); // REQUIRES_NEW: own transaction per appointment
            if (a.getExternalEventId() != null && !a.getExternalEventId().isBlank()) {
                appointmentRepo.save(a); // persist the linkage
                created++;
            } else {
                failed++;
            }
        }
        log.info("Calendar backfill: scanned={}, created={}, failed={}",
            candidates.size(), created, failed);
        return new BackfillResult(candidates.size(), created, failed);
    }

    /** Result of a backfill run. */
    public record BackfillResult(int scanned, int created, int failed) {}

    // -------- token lifecycle --------

    /**
     * Decrypt the access token, refreshing it when it is expired or about to
     * expire (5-min safety margin). Null expiry means "unknown" → use as-is.
     */
    private String usableToken(CalendarIntegration integration) {
        String token = encryption.decrypt(integration.getAccessTokenEncrypted());
        Instant expiresAt = integration.getAccessTokenExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(Instant.now().plusSeconds(300))) {
            return refresh(integration);
        }
        return token;
    }

    /** Exchange the stored refresh token for a fresh access token and persist it. */
    private String refresh(CalendarIntegration integration) {
        String refreshToken = encryption.decrypt(integration.getRefreshTokenEncrypted());
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException("No refresh token stored — user must re-authenticate");
        }
        var provider = providerOf(integration.getProvider());
        var refreshed = provider.refreshAccessToken(refreshToken);
        integration.setAccessTokenEncrypted(encryption.encrypt(refreshed.accessToken()));
        integration.setAccessTokenExpiresAt(refreshed.accessTokenExpiresAt());
        if (refreshed.refreshToken() != null) {
            integration.setRefreshTokenEncrypted(encryption.encrypt(refreshed.refreshToken()));
        }
        integrationRepo.save(integration);
        log.info("Refreshed access token for integration {} (provider {})",
            integration.getId(), integration.getProvider());
        return refreshed.accessToken();
    }

    /**
     * Run a provider call with a usable token. On a 401 (token expired early /
     * clock skew / revoked), refresh once and retry a single time.
     */
    private String runWithRefresh(CalendarIntegration integration, TokenCall call) {
        String token = usableToken(integration);
        try {
            return call.run(token);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                log.warn("401 on provider call — refreshing token and retrying once");
                return call.run(refresh(integration));
            }
            throw e;
        }
    }

    @FunctionalInterface
    private interface TokenCall {
        String run(String token);
    }

    /** Event payload shared by create/update sync. */
    private static CalendarProvider.EventPayload toPayload(Appointment appointment) {
        return new CalendarProvider.EventPayload(
            appointment.getNotes() != null ? appointment.getNotes() : "Reunión Callsagents",
            "Llamada de seguimiento - generado por Callsagents",
            appointment.getScheduledAt(),
            appointment.getScheduledAt().plusSeconds(
                appointment.getDurationMinutes() != null
                    ? appointment.getDurationMinutes() * 60L
                    : 1800L),
            null,
            List.of()
        );
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
