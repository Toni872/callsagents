package com.callsagents.backend.auth.service;

import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.business.service.BusinessService;
import com.callsagents.backend.common.exception.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class TrialEnforcementService {

    private static final Logger log = LoggerFactory.getLogger(TrialEnforcementService.class);

    private final UserRepository userRepository;
    private final BusinessService businessService;

    public TrialEnforcementService(UserRepository userRepository, BusinessService businessService) {
        this.userRepository = userRepository;
        this.businessService = businessService;
    }

    /** True when the trial has expired and write privileges must be blocked. Admins are exempt. */
    public boolean isTrialExpired(UUID userId) {
        if (userId == null) return false;
        return userRepository.findById(userId)
            .map(user -> user.getRole() != UserRole.ADMIN
                && user.getTrialEndsAt() != null
                && Instant.now().isAfter(user.getTrialEndsAt()))
            .orElse(false);
    }

    /** Throws ForbiddenException when the trial is expired. */
    public void ensureTrialActive(UUID userId) {
        if (isTrialExpired(userId)) {
            throw new ForbiddenException("Tu período de prueba (7 días) ha finalizado. Contacta soporte para ampliar tu plan.");
        }
    }

    /** Trial status for a business owner (chatbot flows). Null/unknown business → not expired. */
    public boolean isBusinessOwnerTrialExpired(UUID businessId) {
        if (businessId == null) return false;
        UUID ownerId = businessService.resolveOwnerUserId(businessId);
        return ownerId != null && isTrialExpired(ownerId);
    }
}
