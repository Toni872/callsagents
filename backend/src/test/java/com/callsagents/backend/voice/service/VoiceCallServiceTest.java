package com.callsagents.backend.voice.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.callsagents.backend.campaigns.entity.Campaign;
import com.callsagents.backend.campaigns.entity.CampaignStatus;
import com.callsagents.backend.campaigns.repository.CampaignRepository;
import com.callsagents.backend.common.exception.BadRequestException;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import com.callsagents.backend.voice.domain.CampaignVoiceConfig;
import com.callsagents.backend.voice.domain.VoiceCall;
import com.callsagents.backend.voice.domain.VoiceCallStatus;
import com.callsagents.backend.voice.domain.VoiceProviderType;
import com.callsagents.backend.voice.repo.VoiceCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoiceCallServiceTest {

    @Mock private VoiceCallRepository repo;
    @Mock private VapiProvider vapiProvider;
    @Mock private RetellProvider retellProvider;
    @Mock private CampaignRepository campaignRepository;
    @Mock private PromptComposer promptComposer;

    private VoiceCallService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CALL_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new VoiceCallService(repo, List.of(vapiProvider, retellProvider),
            campaignRepository, promptComposer);
        when(vapiProvider.provider()).thenReturn(VoiceProviderType.VAPI);
        when(retellProvider.provider()).thenReturn(VoiceProviderType.RETELL);
    }

    @Test
    @DisplayName("placeCall: when provider not configured, throws IllegalStateException")
    void placeCall_notConfigured() {
        when(vapiProvider.isConfigured()).thenReturn(false);

        var req = new VoiceProvider.StartCallRequest("+5491112345678", null, Map.of(), null);

        assertThatThrownBy(() -> service.placeCall(VoiceProviderType.VAPI, req, USER_ID, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("VAPI");
    }

    @Test
    @DisplayName("placeCall: when configured, calls provider.startCall and persists row with returned callId")
    void placeCall_success() {
        when(vapiProvider.isConfigured()).thenReturn(true);
        when(vapiProvider.startCall(any()))
            .thenReturn(new VoiceProvider.StartCallResult("vapi-call-999", VoiceCallStatus.RINGING));

        var req = new VoiceProvider.StartCallRequest("+5491112345678", null, Map.of("campaign", "Q4"), null);
        VoiceCall saved = service.placeCall(VoiceProviderType.VAPI, req, USER_ID, null);

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
    @DisplayName("placeCall: without campaignId, legacy behavior — metadata untouched, no dynamic variables")
    void placeCall_withoutCampaignId_keepsLegacyMetadata() {
        when(vapiProvider.isConfigured()).thenReturn(true);
        when(vapiProvider.startCall(any()))
            .thenReturn(new VoiceProvider.StartCallResult("vapi-call-999", VoiceCallStatus.RINGING));
        when(repo.save(any(VoiceCall.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new VoiceProvider.StartCallRequest("+5491112345678", null, Map.of("campaign", "Q4"), null);
        service.placeCall(VoiceProviderType.VAPI, req, USER_ID, null);

        ArgumentCaptor<VoiceProvider.StartCallRequest> captor =
            ArgumentCaptor.forClass(VoiceProvider.StartCallRequest.class);
        verify(vapiProvider).startCall(captor.capture());
        assertThat(captor.getValue().dynamicVariables()).isNull();
        assertThat(captor.getValue().metadata()).isEqualTo(Map.of("campaign", "Q4"));
    }

    @Test
    @DisplayName("placeCall: campaignId given but campaign missing → 404, no provider call")
    void placeCall_campaignMissing_throwsNotFound() {
        when(vapiProvider.isConfigured()).thenReturn(true);
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

        var req = new VoiceProvider.StartCallRequest("+5491112345678", null, Map.of(), null);

        assertThatThrownBy(() -> service.placeCall(VoiceProviderType.VAPI, req, USER_ID, campaignId))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(vapiProvider, never()).startCall(any());
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("placeCall: VAPI with campaignId → rejected (voice config is Retell-only)")
    void placeCall_vapiWithCampaignId_rejected() {
        when(vapiProvider.isConfigured()).thenReturn(true);
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findById(campaignId))
            .thenReturn(Optional.of(campaignWithVoiceConfig(campaignId)));

        var req = new VoiceProvider.StartCallRequest("+5491112345678", null, Map.of(), null);

        assertThatThrownBy(() -> service.placeCall(VoiceProviderType.VAPI, req, USER_ID, campaignId))
            .isInstanceOf(BadRequestException.class);
        verify(vapiProvider, never()).startCall(any());
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("placeCall: RETELL with configured campaign → variables built and campaignId merged into metadata")
    void placeCall_retellWithCampaignConfig_buildsVariablesAndMergesMetadata() {
        when(retellProvider.isConfigured()).thenReturn(true);
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findById(campaignId))
            .thenReturn(Optional.of(campaignWithVoiceConfig(campaignId)));
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("campaign_prompt", "prompt");
        vars.put("company", "Acme");
        when(promptComposer.buildVariables(any(CampaignVoiceConfig.class))).thenReturn(vars);
        when(retellProvider.startCall(any()))
            .thenReturn(new VoiceProvider.StartCallResult("r-1", VoiceCallStatus.SCHEDULED));
        when(repo.save(any(VoiceCall.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new VoiceProvider.StartCallRequest("+5491112345678", null, Map.of("campaign", "Q4"), null);
        service.placeCall(VoiceProviderType.RETELL, req, USER_ID, campaignId);

        ArgumentCaptor<VoiceProvider.StartCallRequest> reqCaptor =
            ArgumentCaptor.forClass(VoiceProvider.StartCallRequest.class);
        verify(retellProvider).startCall(reqCaptor.capture());
        VoiceProvider.StartCallRequest sent = reqCaptor.getValue();
        assertThat(sent.dynamicVariables()).isEqualTo(vars);
        assertThat(sent.metadata()).containsEntry("campaign", "Q4");
        assertThat(sent.metadata()).containsEntry("campaignId", campaignId.toString());

        ArgumentCaptor<VoiceCall> callCaptor = ArgumentCaptor.forClass(VoiceCall.class);
        verify(repo).save(callCaptor.capture());
        assertThat(callCaptor.getValue().getMetadata()).containsEntry("campaignId", campaignId.toString());
    }

    @Test
    @DisplayName("placeCall: RETELL with empty voice config → no dynamic variables, campaignId still merged")
    void placeCall_retellEmptyConfig_noDynamicVariables() {
        when(retellProvider.isConfigured()).thenReturn(true);
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findById(campaignId))
            .thenReturn(Optional.of(campaignWithoutVoiceConfig(campaignId)));
        when(promptComposer.buildVariables(any(CampaignVoiceConfig.class))).thenReturn(Map.of());
        when(retellProvider.startCall(any()))
            .thenReturn(new VoiceProvider.StartCallResult("r-1", VoiceCallStatus.SCHEDULED));
        when(repo.save(any(VoiceCall.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new VoiceProvider.StartCallRequest("+5491112345678", null, Map.of(), null);
        service.placeCall(VoiceProviderType.RETELL, req, USER_ID, campaignId);

        ArgumentCaptor<VoiceProvider.StartCallRequest> captor =
            ArgumentCaptor.forClass(VoiceProvider.StartCallRequest.class);
        verify(retellProvider).startCall(captor.capture());
        assertThat(captor.getValue().dynamicVariables()).isEmpty();
        assertThat(captor.getValue().metadata()).containsEntry("campaignId", campaignId.toString());
    }

    @Test
    @DisplayName("placeCall: RETELL variable build failure → warn logged and call proceeds without variables (NFR-4)")
    void placeCall_retellBuildVariablesFailure_fallsBackToEmptyVars() {
        when(retellProvider.isConfigured()).thenReturn(true);
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findById(campaignId))
            .thenReturn(Optional.of(campaignWithVoiceConfig(campaignId)));
        when(promptComposer.buildVariables(any(CampaignVoiceConfig.class)))
            .thenThrow(new RuntimeException("boom"));
        when(retellProvider.startCall(any()))
            .thenReturn(new VoiceProvider.StartCallResult("r-1", VoiceCallStatus.SCHEDULED));
        when(repo.save(any(VoiceCall.class))).thenAnswer(inv -> inv.getArgument(0));

        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(VoiceCallService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var req = new VoiceProvider.StartCallRequest("+5491112345678", null, Map.of(), null);
            service.placeCall(VoiceProviderType.RETELL, req, USER_ID, campaignId);

            assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.WARN
                    && e.getFormattedMessage().contains("Failed to build campaign prompt"));
            ArgumentCaptor<VoiceProvider.StartCallRequest> captor =
                ArgumentCaptor.forClass(VoiceProvider.StartCallRequest.class);
            verify(retellProvider).startCall(captor.capture());
            assertThat(captor.getValue().dynamicVariables()).isEmpty();
            verify(repo).save(any(VoiceCall.class));
        } finally {
            logger.detachAppender(appender);
        }
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
        when(repo.findByProviderAndProviderCallId(VoiceProviderType.VAPI, "vapi-call-999"))
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
        when(repo.findByProviderAndProviderCallId(VoiceProviderType.VAPI, "ghost"))
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

    private static Campaign campaignWithVoiceConfig(UUID id) {
        return Campaign.builder()
            .id(id)
            .name("Campaign")
            .status(CampaignStatus.DRAFT)
            .company("Acme")
            .website("https://acme.com")
            .industry("SaaS")
            .services("CRM")
            .tone("cercano")
            .createdBy(UUID.randomUUID())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private static Campaign campaignWithoutVoiceConfig(UUID id) {
        return Campaign.builder()
            .id(id)
            .name("Campaign")
            .status(CampaignStatus.DRAFT)
            .createdBy(UUID.randomUUID())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
}
