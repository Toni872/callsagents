package com.callsagents.backend.business.service;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.business.dto.BusinessProfileRequest;
import com.callsagents.backend.business.dto.BusinessProfileResponse;
import com.callsagents.backend.business.dto.WidgetConfigResponse;
import com.callsagents.backend.business.entity.BusinessProfile;
import com.callsagents.backend.business.repository.BusinessProfileRepository;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BusinessService {

    private static final Logger log = LoggerFactory.getLogger(BusinessService.class);

    private static final String DEFAULT_BOT_NAME = "Naiara";
    private static final String DEFAULT_GREETING = "Hola! Soy tu asistente virtual. En que puedo ayudarte hoy?";
    private static final String DEFAULT_CHAT_COLOR = "#25D366";

    private final BusinessProfileRepository profileRepository;
    private final UserRepository userRepository;

    public BusinessService(BusinessProfileRepository profileRepository, UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public BusinessProfileResponse create(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        BusinessProfile profile = BusinessProfile.builder()
            .user(user)
            .companyName("")
            .onboardingComplete(false)
            .escalationEnabled(true)
            .replyTimeoutMinutes(30)
            .build();

        BusinessProfile saved = profileRepository.save(profile);
        log.info("Created business profile for userId={}", userId);
        return BusinessProfileResponse.fromEntity(saved);
    }

    @Transactional
    public BusinessProfileResponse getByUserId(UUID userId) {
        BusinessProfile profile = profileRepository.findByUserId(userId)
            .orElseGet(() -> {
                User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
                BusinessProfile newProfile = BusinessProfile.builder()
                    .user(user)
                    .companyName("")
                    .onboardingComplete(false)
                    .build();
                BusinessProfile saved = profileRepository.save(newProfile);
                log.info("Auto-created business profile for userId={}", userId);
                return saved;
            });
        return BusinessProfileResponse.fromEntity(profile);
    }

    @Transactional(readOnly = true)
    public BusinessProfile getProfileEntityByUserId(UUID userId) {
        return profileRepository.findByUserId(userId).orElse(null);
    }

    @Transactional(readOnly = true)
    public BusinessProfile getProfileEntityByWhatsappNumber(String whatsappNumber) {
        if (whatsappNumber == null || whatsappNumber.isBlank()) return null;
        // Normalize: strip leading + for lookup
        String normalized = whatsappNumber.startsWith("+") ? whatsappNumber.substring(1) : whatsappNumber;
        return profileRepository.findByWhatsappNumber(normalized)
            .orElseGet(() -> profileRepository.findByWhatsappNumber("+" + normalized).orElse(null));
    }

    @Transactional
    public BusinessProfileResponse update(UUID userId, BusinessProfileRequest request) {
        BusinessProfile profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Business profile not found for user: " + userId));

        if (request.companyName() != null) profile.setCompanyName(request.companyName());
        if (request.website() != null) profile.setWebsite(request.website());
        if (request.industry() != null) profile.setIndustry(request.industry());
        if (request.services() != null) profile.setServices(request.services());
        if (request.tone() != null) profile.setTone(request.tone());
        if (request.botName() != null) profile.setBotName(request.botName());
        if (request.greeting() != null) profile.setGreeting(request.greeting());
        if (request.chatColor() != null) profile.setChatColor(request.chatColor());
        if (request.escalationEnabled() != null) profile.setEscalationEnabled(request.escalationEnabled());
        if (request.replyTimeoutMinutes() != null) profile.setReplyTimeoutMinutes(request.replyTimeoutMinutes());
        if (request.followupMessage() != null) profile.setFollowupMessage(request.followupMessage());
        if (request.voiceAgentId() != null) profile.setVoiceAgentId(request.voiceAgentId());
        if (request.whatsappNumber() != null) profile.setWhatsappNumber(request.whatsappNumber());

        profile.setOnboardingComplete(true);

        BusinessProfile saved = profileRepository.save(profile);
        log.info("Updated business profile for userId={}", userId);
        return BusinessProfileResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public WidgetConfigResponse getWidgetConfig(UUID userId) {
        BusinessProfile profile = profileRepository.findByUserId(userId).orElse(null);

        if (profile == null) {
            return new WidgetConfigResponse(
                DEFAULT_BOT_NAME,
                DEFAULT_GREETING,
                DEFAULT_CHAT_COLOR,
                ""
            );
        }

        return new WidgetConfigResponse(
            profile.getBotName() != null ? profile.getBotName() : DEFAULT_BOT_NAME,
            profile.getGreeting() != null ? profile.getGreeting() : DEFAULT_GREETING,
            profile.getChatColor() != null ? profile.getChatColor() : DEFAULT_CHAT_COLOR,
            profile.getCompanyName() != null ? profile.getCompanyName() : ""
        );
    }

    @Transactional
    public void markOnboardingComplete(UUID userId) {
        BusinessProfile profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Business profile not found for user: " + userId));
        profile.setOnboardingComplete(true);
        profileRepository.save(profile);
        log.info("Marked onboarding complete for userId={}", userId);
    }
}
