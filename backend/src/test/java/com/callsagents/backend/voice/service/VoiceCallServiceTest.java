package com.callsagents.backend.voice.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.business.entity.BusinessProfile;
import com.callsagents.backend.business.repository.BusinessProfileRepository;
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
    @Mock private BusinessProfileRepository businessProfileRepository;

    private VoiceCallService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CALL_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new VoiceCallService(repo, List.of(vapiProvider, retellProvider),
            campaignRepository, promptComposer, businessProfileRepository);
        when(vapiProvider.provider()).thenReturn(VoiceProviderType.VAPI);
        when(retellProvider.provider()).thenReturn(VoiceProviderType.RETELL);
    }

    @Test
    @DisplayName("placeCall: when provider not configured, throws IllegalStateException")
    void placeCall_notConfigured() {
        when(vapiProvider.isConfigured()).thenReturn(false);

        var req = new VoiceProvider.StartCallRequest("+5491112345678", null, Map.of(), null);

        assertThatThrownBy(() -> service.placeCall(VoiceProviderType.VAPI, req, USER_ID, null, UserRole.AGENT))
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
        VoiceCall saved = service.placeCall(VoiceProviderType.VAPI, req, USER_ID, null, UserRole.AGENT);

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
        service.placeCall(VoiceProviderType.VAPI, req, USER_ID, null, UserRole.AGENT);

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

        assertThatThrownBy(() -> service.placeCall(VoiceProviderType.VAPI, req, USER_ID, campaignId, UserRole.AGENT))
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

        assertThatThrownBy(() -> service.placeCall(VoiceProviderType.VAPI, req, USER_ID, campaignId, UserRole.AGENT))
            .isInstanceOf(BadRequestException.class);
        verify(vapiProvider, never()).startCall(any());
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("placeCall: campaign belongs to another user and caller is not ADMIN → 404, no provider call")
    void placeCall_campaignForeignByAgent_rejected() {
        when(vapiProvider.isConfigured()).thenReturn(true);
        UUID campaignId = UUID.randomUUID();
        Campaign foreign = campaignWithVoiceConfig(campaignId);
        foreign.setCreatedBy(UUID.randomUUID());
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(foreign));

        var req = new VoiceProvider.StartCallRequest("+5491112345678", null, Map.of(), null);

        assertThatThrownBy(() -> service.placeCall(VoiceProviderType.VAPI, req, USER_ID, campaignId, UserRole.AGENT))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Campaign not found");
        verify(vapiProvider, never()).startCall(any());
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("placeCall: ADMIN can place a call on a campaign owned by another user")
    void placeCall_foreignCampaignByAdmin_ok() {
        when(retellProvider.isConfigured()).thenReturn(true);
        UUID campaignId = UUID.randomUUID();
        Campaign foreign = campaignWithVoiceConfig(campaignId);
        foreign.setCreatedBy(UUID.randomUUID());
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(foreign));
        when(promptComposer.buildVariables(any(CampaignVoiceConfig.class))).thenReturn(Map.of());
        when(retellProvider.startCall(any()))
            .thenReturn(new VoiceProvider.StartCallResult("r-1", VoiceCallStatus.SCHEDULED));
        when(repo.save(any(VoiceCall.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new VoiceProvider.StartCallRequest("+5491112345678", null, Map.of(), null);
        service.placeCall(VoiceProviderType.RETELL, req, USER_ID, campaignId, UserRole.ADMIN);

        verify(retellProvider).startCall(any());
        verify(repo).save(any(VoiceCall.class));
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
        service.placeCall(VoiceProviderType.RETELL, req, USER_ID, campaignId, UserRole.AGENT);

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
        service.placeCall(VoiceProviderType.RETELL, req, USER_ID, campaignId, UserRole.AGENT);

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
            service.placeCall(VoiceProviderType.RETELL, req, USER_ID, campaignId, UserRole.AGENT);

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

    @Test
    @DisplayName("recordWebCall: uses the business profile's user and persists a WEB/SCHEDULED row with null phone")
    void recordWebCall_attributesToBusinessUser() {
        UUID businessId = UUID.randomUUID();
        User owner = User.builder().id(USER_ID).email("owner@acme.com").role(UserRole.ADMIN).build();
        when(businessProfileRepository.findById(businessId))
            .thenReturn(Optional.of(businessProfile(businessId, owner)));
        when(repo.save(any(VoiceCall.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.recordWebCall(businessId.toString(), "call_retell_1", Map.of("agentId", "agent_x"));

        assertThat(result).isPresent();
        ArgumentCaptor<VoiceCall> captor = ArgumentCaptor.forClass(VoiceCall.class);
        verify(repo).save(captor.capture());
        VoiceCall persisted = captor.getValue();
        assertThat(persisted.getUserId()).isEqualTo(USER_ID);
        assertThat(persisted.getProvider()).isEqualTo(VoiceProviderType.RETELL);
        assertThat(persisted.getProviderCallId()).isEqualTo("call_retell_1");
        assertThat(persisted.getPhoneNumber()).isNull();
        assertThat(persisted.getDirection()).isEqualTo("WEB");
        assertThat(persisted.getStatus()).isEqualTo(VoiceCallStatus.SCHEDULED);
        assertThat(persisted.getMetadata()).containsEntry("agentId", "agent_x");
    }

    @Test
    @DisplayName("recordWebCall: unknown business_id falls back to the first business profile")
    void recordWebCall_unknownBusinessId_fallsBackToFirstProfile() {
        UUID otherId = UUID.randomUUID();
        User owner = User.builder().id(otherId).email("owner@acme.com").role(UserRole.AGENT).build();
        when(businessProfileRepository.findById(any())).thenReturn(Optional.empty());
        when(businessProfileRepository.findAll())
            .thenReturn(List.of(businessProfile(UUID.randomUUID(), owner)));
        when(repo.save(any(VoiceCall.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.recordWebCall(UUID.randomUUID().toString(), "call_retell_2", Map.of());

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(otherId);
    }

    @Test
    @DisplayName("recordWebCall: invalid business_id is tolerated and falls back")
    void recordWebCall_invalidBusinessId_tolerated() {
        User owner = User.builder().id(USER_ID).email("owner@acme.com").role(UserRole.ADMIN).build();
        when(businessProfileRepository.findAll())
            .thenReturn(List.of(businessProfile(UUID.randomUUID(), owner)));
        when(repo.save(any(VoiceCall.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.recordWebCall("not-a-uuid", "call_retell_3", null);

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(USER_ID);
        assertThat(result.get().getMetadata()).isEmpty();
    }

    @Test
    @DisplayName("recordWebCall: no business profile at all -> empty result, nothing persisted")
    void recordWebCall_noBusinessProfile_returnsEmpty() {
        when(businessProfileRepository.findAll()).thenReturn(List.of());

        var result = service.recordWebCall(null, "call_retell_4", Map.of());

        assertThat(result).isEmpty();
        verify(repo, never()).save(any());
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
            .createdBy(USER_ID)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private static Campaign campaignWithoutVoiceConfig(UUID id) {
        return Campaign.builder()
            .id(id)
            .name("Campaign")
            .status(CampaignStatus.DRAFT)
            .createdBy(USER_ID)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private static BusinessProfile businessProfile(UUID id, User user) {
        return BusinessProfile.builder()
            .id(id)
            .user(user)
            .companyName("Acme")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
}
