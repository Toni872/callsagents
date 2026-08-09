package com.callsagents.backend.voice.service;

import com.callsagents.backend.voice.domain.VoiceCall;
import com.callsagents.backend.voice.domain.VoiceCallStatus;
import com.callsagents.backend.voice.domain.VoiceProviderType;
import com.callsagents.backend.voice.repo.VoiceCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoiceCallServiceTest {

    @Mock private VoiceCallRepository repo;
    @Mock private VapiProvider vapiProvider;
    @Mock private RetellProvider retellProvider;

    private VoiceCallService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CALL_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new VoiceCallService(repo, List.of(vapiProvider, retellProvider));
        when(vapiProvider.provider()).thenReturn(VoiceProviderType.VAPI);
        when(retellProvider.provider()).thenReturn(VoiceProviderType.RETELL);
    }

    @Test
    @DisplayName("placeCall: when provider not configured, throws IllegalStateException")
    void placeCall_notConfigured() {
        when(vapiProvider.isConfigured()).thenReturn(false);

        var req = new VoiceProvider.StartCallRequest("+5491112345678", null, Map.of());

        assertThatThrownBy(() -> service.placeCall(VoiceProviderType.VAPI, req, USER_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("VAPI");
    }

    @Test
    @DisplayName("placeCall: when configured, calls provider.startCall and persists row with returned callId")
    void placeCall_success() {
        when(vapiProvider.isConfigured()).thenReturn(true);
        when(vapiProvider.startCall(any()))
            .thenReturn(new VoiceProvider.StartCallResult("vapi-call-999", VoiceCallStatus.RINGING));

        var req = new VoiceProvider.StartCallRequest("+5491112345678", null, Map.of("campaign", "Q4"));
        VoiceCall saved = service.placeCall(VoiceProviderType.VAPI, req, USER_ID);

        ArgumentCaptor<VoiceCall> captor = ArgumentCaptor.forClass(VoiceCall.class);
        verify(repo).save(captor.capture());
        VoiceCall persisted = captor.getValue();
        assertThat(persisted.getUserId()).isEqualTo(USER_ID);
        assertThat(persisted.getProvider()).isEqualTo(VoiceProviderType.VAPI);
        assertThat(persisted.getProviderCallId()).isEqualTo("vapi-call-999");
        assertThat(persisted.getPhoneNumber()).isEqualTo("+5491112345678");
        assertThat(persisted.getStatus()).isEqualTo(VoiceCallStatus.RINGING);
        assertThat(persisted.getMetadata()).containsEntry("campaign", "Q4");
    }

    @Test
    @DisplayName("applyWebhook: when call found, updates status + duration + endedAt")
    void applyWebhook_updatesState() {
        VoiceCall existing = VoiceCall.builder()
            .id(CALL_ID)
            .userId(USER_ID)
            .provider(VoiceProviderType.VAPI)
            .providerCallId("vapi-call-999")
            .phoneNumber("+5491112345678")
            .status(VoiceCallStatus.RINGING)
            .direction("OUTBOUND")
            .build();
        when(repo.findByProviderAndProviderCallId("VAPI", "vapi-call-999"))
            .thenReturn(Optional.of(existing));
        when(repo.save(any(VoiceCall.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.applyWebhook(
            "vapi", "vapi-call-999", VoiceCallStatus.ENDED,
            120, null, "Hola, ¿hola?", "https://recording.url/x", null, null);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(VoiceCallStatus.ENDED);
        assertThat(result.get().getDurationSeconds()).isEqualTo(120);
        assertThat(result.get().getTranscript()).isEqualTo("Hola, ¿hola?");
        assertThat(result.get().getRecordingUrl()).isEqualTo("https://recording.url/x");
        assertThat(result.get().getEndedAt()).isNotNull(); // status ENDED → endedAt set
    }

    @Test
    @DisplayName("applyWebhook: when call NOT found, returns empty and does not save")
    void applyWebhook_unknownCall() {
        when(repo.findByProviderAndProviderCallId("VAPI", "ghost"))
            .thenReturn(Optional.empty());

        var result = service.applyWebhook(
            "vapi", "ghost", VoiceCallStatus.ENDED, null, null, null, null, null, null);

        assertThat(result).isEmpty();
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("applyWebhook: when provider name unknown (case-insensitive), returns empty")
    void applyWebhook_unknownProvider() {
        var result = service.applyWebhook(
            "telepathix", "x", VoiceCallStatus.ENDED, null, null, null, null, null, null);

        assertThat(result).isEmpty();
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("logManualCall: persists call with default status ENDED + direction OUTBOUND")
    void logManualCall_defaultsApplied() {
        VoiceCall raw = VoiceCall.builder()
            .userId(USER_ID)
            .phoneNumber("+5491100000000")
            .build();
        when(repo.save(any(VoiceCall.class))).thenAnswer(inv -> {
            VoiceCall c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        VoiceCall result = service.logManualCall(raw);

        assertThat(result.getStatus()).isEqualTo(VoiceCallStatus.ENDED);
        assertThat(result.getDirection()).isEqualTo("OUTBOUND");
    }
}
