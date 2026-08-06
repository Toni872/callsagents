package com.callsagents.backend.calendar.service;

import com.callsagents.backend.appointments.entity.Appointment;
import com.callsagents.backend.appointments.entity.AppointmentStatus;
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
        service = new CalendarSyncService(integrationRepo, encryption, List.of(googleProvider, outlookProvider));
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
        when(googleProvider.createEvent(eq("decrypted-access"), any(), any())).thenReturn("external-event-id-999");
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
    @DisplayName("syncAppointment: when Google API throws, appointment stays and integration marked FAILED")
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
        when(googleProvider.createEvent(eq("decrypted-access"), any(), any()))
            .thenThrow(new RuntimeException("Google 401 — token rejected"));

        // Should NOT throw — sync failures are captured, not propagated
        service.syncAppointment(a);

        // Appointment fields are NOT updated (no event id)
        assertThat(a.getExternalEventId()).isNull();
        // Integration marked FAILED with the error message
        assertThat(integration.getLastSyncStatus()).isEqualTo(CalendarSyncStatus.FAILED);
        assertThat(integration.getLastSyncError()).contains("401");
        verify(integrationRepo, times(1)).save(integration);
    }
}
