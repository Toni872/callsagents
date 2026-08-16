package com.callsagents.backend.voice.service;

import com.callsagents.backend.campaigns.entity.Campaign;
import com.callsagents.backend.campaigns.repository.CampaignRepository;
import com.callsagents.backend.common.exception.BadRequestException;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import com.callsagents.backend.voice.domain.CampaignVoiceConfig;
import com.callsagents.backend.voice.domain.VoiceCall;
import com.callsagents.backend.voice.domain.VoiceCallStatus;
import com.callsagents.backend.voice.domain.VoiceProviderType;
import com.callsagents.backend.voice.repo.VoiceCallRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrator for voice calls. Routes to the right provider, persists state,
 * and applies webhook updates.
 */
@Service
public class VoiceCallService {

    private static final Logger log = LoggerFactory.getLogger(VoiceCallService.class);

    private final VoiceCallRepository repo;
    private final List<VoiceProvider> providers;
    private final CampaignRepository campaignRepository;
    private final PromptComposer promptComposer;

    public VoiceCallService(VoiceCallRepository repo, List<VoiceProvider> providers,
                            CampaignRepository campaignRepository, PromptComposer promptComposer) {
        this.repo = repo;
        this.providers = providers;
        this.campaignRepository = campaignRepository;
        this.promptComposer = promptComposer;
    }

    public VoiceProvider providerOf(VoiceProviderType type) {
        return providers.stream()
            .filter(p -> p.provider() == type)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No provider bean registered for " + type));
    }

    /**
     * Initiate an outbound call through the given provider.
     *
     * <p>When a {@code campaignId} is supplied, the campaign's voice
     * configuration applies (Retell-only, FR-2/FR-3): dynamic variables are
     * resolved through the PromptComposer (NFR-4: any failure degrades to an
     * empty variables map plus a WARN log) and the campaignId is merged into
     * the metadata sent to the provider and persisted.
     */
    @Transactional
    public VoiceCall placeCall(VoiceProviderType type, VoiceProvider.StartCallRequest req,
                               UUID userId, UUID campaignId) {
        var provider = providerOf(type);
        if (!provider.isConfigured()) {
            throw new IllegalStateException(
                "Provider " + type + " is not configured. Set the appropriate env vars.");
        }
        Map<String, Object> dynamicVariables = null;
        if (campaignId != null) {
            Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));
            if (type == VoiceProviderType.VAPI) {
                throw new BadRequestException(
                    "Voice configuration is only supported for the RETELL provider");
            }
            dynamicVariables = new LinkedHashMap<>(safeBuildVariables(campaign));
        }
        var effectiveReq = new VoiceProvider.StartCallRequest(
            req.phoneNumber(), req.assistantId(),
            mergedMetadata(req.metadata(), campaignId), dynamicVariables);
        var result = provider.startCall(effectiveReq);

        VoiceCall call = VoiceCall.builder()
            .userId(userId)
            .provider(type)
            .providerCallId(result.providerCallId())
            .phoneNumber(req.phoneNumber())
            .status(result.initialStatus() != null ? result.initialStatus() : VoiceCallStatus.SCHEDULED)
            .direction("OUTBOUND")
            .metadata(effectiveReq.metadata())
            .build();
        return repo.save(call);
    }

    private Map<String, Object> mergedMetadata(Map<String, Object> metadata, UUID campaignId) {
        if (campaignId == null) {
            return metadata;
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        if (metadata != null) {
            merged.putAll(metadata);
        }
        merged.put("campaignId", campaignId.toString());
        return merged;
    }

    private Map<String, String> safeBuildVariables(Campaign campaign) {
        try {
            return promptComposer.buildVariables(voiceConfigOf(campaign));
        } catch (RuntimeException e) {
            log.warn("Failed to build campaign prompt variables; placing call without them: {}", e.getMessage());
            return Map.of();
        }
    }

    private static CampaignVoiceConfig voiceConfigOf(Campaign campaign) {
        return new CampaignVoiceConfig(
            campaign.getCompany(),
            campaign.getWebsite(),
            campaign.getIndustry(),
            campaign.getServices(),
            campaign.getTone());
    }

    /** Manually log a call (no provider, no external system). */
    @Transactional
    public VoiceCall logManualCall(VoiceCall call) {
        if (call.getStatus() == null) call.setStatus(VoiceCallStatus.ENDED);
        if (call.getDirection() == null) call.setDirection("OUTBOUND");
        if (call.getProvider() == null) call.setProvider(null);
        return repo.save(call);
    }

    /** Apply a webhook update from the provider. Idempotent (re-applying same status is fine). */
    @Transactional
    public Optional<VoiceCall> applyWebhook(String providerName, String providerCallId, VoiceCallStatus status,
                                            Integer durationSeconds, BigDecimal costUsd,
                                            String transcript, String recordingUrl,
                                            String errorMessage, Map<String, Object> metadata) {
        VoiceProviderType type;
        try {
            type = VoiceProviderType.valueOf(providerName.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Webhook for unknown provider: {}", providerName);
            return Optional.empty();
        }
        var callOpt = repo.findByProviderAndProviderCallId(type, providerCallId);
        if (callOpt.isEmpty()) {
            log.warn("Webhook for unknown call: provider={}, id={}", type, providerCallId);
            return Optional.empty();
        }
        VoiceCall call = callOpt.get();
        if (status != null) call.setStatus(status);
        if (durationSeconds != null) call.setDurationSeconds(durationSeconds);
        if (costUsd != null) call.setCostUsd(costUsd);
        if (transcript != null) call.setTranscript(transcript);
        if (recordingUrl != null) call.setRecordingUrl(recordingUrl);
        if (errorMessage != null) call.setErrorMessage(errorMessage);
        if (metadata != null) call.setMetadata(metadata);
        if (status == VoiceCallStatus.ENDED || status == VoiceCallStatus.FAILED || status == VoiceCallStatus.NO_ANSWER) {
            call.setEndedAt(Instant.now());
        }
        if (call.getStartedAt() == null && status == VoiceCallStatus.IN_PROGRESS) {
            call.setStartedAt(Instant.now());
        }
        log.info("Applied webhook for call {}: status={}", call.getId(), status);
        return Optional.of(repo.save(call));
    }

    public List<VoiceCall> listForUser(UUID userId) {
        return repo.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<VoiceCall> listForLead(UUID leadId) {
        return repo.findAllByLeadIdOrderByCreatedAtDesc(leadId);
    }

    public Optional<VoiceCall> findById(UUID id) {
        return repo.findById(id);
    }
}
