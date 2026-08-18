package com.callsagents.backend.calendar.service;

import com.callsagents.backend.appointments.entity.Appointment;
import com.callsagents.backend.appointments.entity.AppointmentStatus;
import com.callsagents.backend.appointments.repository.AppointmentRepository;
import com.callsagents.backend.calendar.domain.CalendarIntegration;
import com.callsagents.backend.calendar.domain.CalendarProviderType;
import com.callsagents.backend.calendar.domain.CalendarSyncStatus;
import com.callsagents.backend.calendar.repo.CalendarIntegrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalendarSyncServiceTest {

    @Mock private CalendarIntegrationRepository integrationRepo;
    @Mock private AppointmentRepository appointmentRepo;
    @Mock private EncryptionService encryption;
    @Mock private GoogleCalendarProvider googleProvider;
    @Mock private OutlookCalendarProvider outlookProvider;

    private CalendarSyncService service;

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444");
    private static final UUID APPT_ID = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444");

    @BeforeEach
    void setUp() {
        // Match implementation: service is built with the @Mock providers as the
        // provider list. The service dispatches by enum via providerOf().
        service = new CalendarSyncService(integrationRepo, appointmentRepo, encryption, List.of(googleProvider, outlookProvider));
        // Stub the discriminator — by default @Mock returns null for methods that
        // return a reference type, but providerOf() filters by enum so it must resolve.
        when(googleProvider.provider()).thenReturn(CalendarProviderType.GOOGLE);
        when(outlookProvider.provider()).thenReturn(CalendarProviderType.OUTLOOK);
    }

    private Appointment sampleAppointment() {
        Appointment a = new Appointment();
        a.setId(APPT_ID);
        a.setLeadId(UUID.randomUUID());
        a.setUserId(USER_ID);
        a.setScheduledAt(Instant.now().plusSeconds(3600));
        a.setDurationMinutes(30);
        a.setStatus(AppointmentStatus.PENDING);
        return a;
    }

    private CalendarIntegration activeIntegration(boolean syncEnabled) {
        return CalendarIntegration.builder()
            .id(UUID.randomUUID())
            .userId(USER_ID)
            .provider(CalendarProviderType.GOOGLE)
            .externalCalendarId("primary")
            .accessTokenEncrypted("encrypted-token")
            .refreshTokenEncrypted(null)
            .scopes("calendar")
            .syncEnabled(syncEnabled)
            .build();
    }

    @Test
    @DisplayName("syncAppointment: when user has no Google integration, no-op")
    void noIntegration_noOp() {
        when(integrationRepo.findByUserIdAndProvider(USER_ID, CalendarProviderType.GOOGLE))
            .thenReturn(Optional.empty());

        service.syncAppointment(sampleAppointment());

        verify(googleProvider, never()).createEvent(anyString(), any(), any());
        verify(integrationRepo, never()).save(any());
    }

    @Test
    @DisplayName("syncAppointment: when sync_enabled = false, no-op (even if integration exists)")
    void integrationDisabled_noOp() {
        when(integrationRepo.findByUserIdAndProvider(USER_ID, CalendarProviderType.GOOGLE))
            .thenReturn(Optional.of(activeIntegration(false)));

        service.syncAppointment(sampleAppointment());

        verify(googleProvider, never()).createEvent(anyString(), any(), any());
    }

    @Test
    @DisplayName("syncAppointment: when Google not configured (no env), mark FAILED without throw")
    void googleNotConfigured_markFailure() {
        when(integrationRepo.findByUserIdAndProvider(USER_ID, CalendarProviderType.GOOGLE))
            .thenReturn(Optional.of(activeIntegration(true)));
        when(googleProvider.isConfigured()).thenReturn(false);

        service.syncAppointment(sampleAppointment());

        // The integration must be saved with FAILED status — and the call must NOT throw.
        verify(integrationRepo, times(1)).save(any());
        // No external call attempted
        verify(googleProvider, never()).createEvent(anyString(), any(), any());
    }

    @Test
    @DisplayName("syncAppointment: successful sync updates appointment fields and marks SYNCED")
    void syncAppointment_success() {
        Appointment a = sampleAppointment();
        CalendarIntegration integration = activeIntegration(true);
        when(integrationRepo.findByUserIdAndProvider(USER_ID, CalendarProviderType.GOOGLE))
            .thenReturn(Optional.of(integration));
        when(googleProvider.isConfigured()).thenReturn(true);
        when(googleProvider.provider()).thenReturn(CalendarProviderType.GOOGLE);
        when(googleProvider.createEvent(eq("decrypted-access"), any(), any()))
            .thenReturn(new CalendarProvider.EventRef("external-event-id-999",
                "https://calendar.google.com/calendar/event?eid=test-999"));
        when(encryption.decrypt("encrypted-token")).thenReturn("decrypted-access");

        service.syncAppointment(a);

        // Appointment was tagged with external links
        assertThat(a.getExternalEventId()).isEqualTo("external-event-id-999");
        assertThat(a.getExternalProvider()).isEqualTo("GOOGLE");
        assertThat(a.getExternalSyncedAt()).isNotNull();

        // Integration status updated to SYNCED
        assertThat(integration.getLastSyncStatus()).isEqualTo(CalendarSyncStatus.SYNCED);
        assertThat(integration.getLastSyncAt()).isNotNull();
        assertThat(integration.getLastSyncError()).isNull();
        verify(integrationRepo, times(1)).save(integration);
    }

    @Test
    @DisplayName("syncAppointment: when Google API throws (non-401), appointment stays and integration marked FAILED")
    void syncAppointment_providerFails() {
        Appointment a = sampleAppointment();
        CalendarIntegration integration = activeIntegration(true);
        when(integrationRepo.findByUserIdAndProvider(USER_ID, CalendarProviderType.GOOGLE))
            .thenReturn(Optional.of(integration));
        when(googleProvider.isConfigured()).thenReturn(true);
        when(googleProvider.provider()).thenReturn(CalendarProviderType.GOOGLE);
        // IMPORTANT: stub decrypt explicitly — Mockito's anyString() does NOT match null,
        // so without this the stub on createEvent (which throws) would never match.
        when(encryption.decrypt("encrypted-token")).thenReturn("decrypted-access");
        // NOT a 401: a 401 would trigger the token-refresh retry (covered by
        // syncAppointment_retriesOnceAfter401). Any other failure must be captured.
        when(googleProvider.createEvent(eq("decrypted-access"), any(), any()))
            .thenThrow(new RuntimeException("Google createEvent failed: HTTP 500"));

        // Should NOT throw — sync failures are captured, not propagated
        service.syncAppointment(a);

        // Appointment fields are NOT updated (no event id)
        assertThat(a.getExternalEventId()).isNull();
        // Integration marked FAILED with the error message
        assertThat(integration.getLastSyncStatus()).isEqualTo(CalendarSyncStatus.FAILED);
        assertThat(integration.getLastSyncError()).contains("500");
        verify(integrationRepo, times(1)).save(integration);
    }

    @Test
    @DisplayName("updateAppointment: pushes changes to the existing Google event")
    void updateAppointment_existingEvent_updates() {
        Appointment a = sampleAppointment();
        a.setExternalEventId("evt-existing");
        CalendarIntegration integration = activeIntegration(true);
        when(integrationRepo.findByUserIdAndProvider(USER_ID, CalendarProviderType.GOOGLE))
            .thenReturn(Optional.of(integration));
        when(googleProvider.isConfigured()).thenReturn(true);
        when(encryption.decrypt("encrypted-token")).thenReturn("decrypted-access");
        when(googleProvider.updateEvent(eq("decrypted-access"), eq("primary"), eq("evt-existing"), any()))
            .thenReturn(new CalendarProvider.EventRef("evt-existing",
                "https://calendar.google.com/calendar/event?eid=evt-existing"));

        service.updateAppointment(a);

        verify(googleProvider, times(1))
            .updateEvent(eq("decrypted-access"), eq("primary"), eq("evt-existing"), any());
        verify(googleProvider, never()).createEvent(anyString(), any(), any());
        assertThat(a.getExternalSyncedAt()).isNotNull();
        assertThat(integration.getLastSyncStatus()).isEqualTo(CalendarSyncStatus.SYNCED);
    }

    @Test
    @DisplayName("updateAppointment: appointment without an event falls back to create")
    void updateAppointment_noEvent_fallsBackToCreate() {
        Appointment a = sampleAppointment(); // no externalEventId yet
        CalendarIntegration integration = activeIntegration(true);
        when(integrationRepo.findByUserIdAndProvider(USER_ID, CalendarProviderType.GOOGLE))
            .thenReturn(Optional.of(integration));
        when(googleProvider.isConfigured()).thenReturn(true);
        when(encryption.decrypt("encrypted-token")).thenReturn("decrypted-access");
        when(googleProvider.createEvent(eq("decrypted-access"), any(), any()))
            .thenReturn(new CalendarProvider.EventRef("evt-new",
                "https://calendar.google.com/calendar/event?eid=evt-new"));

        service.updateAppointment(a);

        verify(googleProvider, times(1)).createEvent(anyString(), any(), any());
        verify(googleProvider, never()).updateEvent(anyString(), any(), anyString(), any());
        assertThat(a.getExternalEventId()).isEqualTo("evt-new");
    }

    @Test
    @DisplayName("deleteAppointmentEvent: deletes the Google event")
    void deleteAppointmentEvent_deletes() {
        Appointment a = sampleAppointment();
        a.setExternalEventId("evt-to-delete");
        CalendarIntegration integration = activeIntegration(true);
        when(integrationRepo.findByUserIdAndProvider(USER_ID, CalendarProviderType.GOOGLE))
            .thenReturn(Optional.of(integration));
        when(encryption.decrypt("encrypted-token")).thenReturn("decrypted-access");

        service.deleteAppointmentEvent(a);

        verify(googleProvider, times(1)).deleteEvent("decrypted-access", "primary", "evt-to-delete");
        assertThat(integration.getLastSyncStatus()).isEqualTo(CalendarSyncStatus.SYNCED);
    }

    @Test
    @DisplayName("backfillUnsynced: creates events for candidates and persists the linkage")
    void backfillUnsynced_creates() {
        Appointment a = sampleAppointment();
        CalendarIntegration integration = activeIntegration(true);
        when(appointmentRepo
            .findAllByExternalEventIdIsNullAndScheduledAtGreaterThanEqualAndStatusIn(any(), any()))
            .thenReturn(List.of(a));
        when(integrationRepo.findByUserIdAndProvider(USER_ID, CalendarProviderType.GOOGLE))
            .thenReturn(Optional.of(integration));
        when(googleProvider.isConfigured()).thenReturn(true);
        when(encryption.decrypt("encrypted-token")).thenReturn("decrypted-access");
        when(googleProvider.createEvent(eq("decrypted-access"), any(), any()))
            .thenReturn(new CalendarProvider.EventRef("evt-backfill",
                "https://calendar.google.com/calendar/event?eid=evt-backfill"));

        CalendarSyncService.BackfillResult result = service.backfillUnsynced();

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(a.getExternalEventId()).isEqualTo("evt-backfill");
        verify(appointmentRepo, times(1)).save(a);
    }

    @Test
    @DisplayName("syncAppointment: refreshes an expired access token before creating the event")
    void syncAppointment_refreshesExpiredToken() {
        Appointment a = sampleAppointment();
        CalendarIntegration integration = activeIntegration(true);
        integration.setAccessTokenExpiresAt(Instant.now().minusSeconds(60)); // expired
        integration.setRefreshTokenEncrypted("encrypted-refresh");
        when(integrationRepo.findByUserIdAndProvider(USER_ID, CalendarProviderType.GOOGLE))
            .thenReturn(Optional.of(integration));
        when(googleProvider.isConfigured()).thenReturn(true);
        when(encryption.decrypt("encrypted-token")).thenReturn("old-token");
        when(encryption.decrypt("encrypted-refresh")).thenReturn("refresh-token");
        when(googleProvider.refreshAccessToken("refresh-token"))
            .thenReturn(new CalendarProvider.TokenResponse("new-token", null, 3600L, "calendar", "Bearer"));
        when(googleProvider.createEvent(eq("new-token"), any(), any()))
            .thenReturn(new CalendarProvider.EventRef("evt-after-refresh",
                "https://calendar.google.com/calendar/event?eid=evt-after-refresh"));

        service.syncAppointment(a);

        verify(googleProvider, times(1)).refreshAccessToken("refresh-token");
        verify(googleProvider, times(1)).createEvent(eq("new-token"), any(), any());
        // refresh() persists + markSuccess persists
        verify(integrationRepo, times(2)).save(integration);
        assertThat(a.getExternalEventId()).isEqualTo("evt-after-refresh");
    }

    @Test
    @DisplayName("syncAppointment: on 401, refreshes the token and retries once")
    void syncAppointment_retriesOnceAfter401() {
        Appointment a = sampleAppointment();
        CalendarIntegration integration = activeIntegration(true);
        integration.setRefreshTokenEncrypted("encrypted-refresh");
        when(integrationRepo.findByUserIdAndProvider(USER_ID, CalendarProviderType.GOOGLE))
            .thenReturn(Optional.of(integration));
        when(googleProvider.isConfigured()).thenReturn(true);
        when(encryption.decrypt("encrypted-token")).thenReturn("stale-token");
        when(encryption.decrypt("encrypted-refresh")).thenReturn("refresh-token");
        when(googleProvider.refreshAccessToken("refresh-token"))
            .thenReturn(new CalendarProvider.TokenResponse("fresh-token", null, 3600L, "calendar", "Bearer"));
        when(googleProvider.createEvent(eq("stale-token"), any(), any()))
            .thenThrow(new RuntimeException("Google access token rejected (401) — user must re-authenticate"));
        when(googleProvider.createEvent(eq("fresh-token"), any(), any()))
            .thenReturn(new CalendarProvider.EventRef("evt-after-401",
                "https://calendar.google.com/calendar/event?eid=evt-after-401"));

        service.syncAppointment(a);

        verify(googleProvider, times(1)).refreshAccessToken("refresh-token");
        verify(googleProvider, times(1)).createEvent(eq("fresh-token"), any(), any());
        assertThat(a.getExternalEventId()).isEqualTo("evt-after-401");
    }
}
