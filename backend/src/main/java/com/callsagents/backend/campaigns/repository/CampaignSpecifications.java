package com.callsagents.backend.campaigns.repository;

import com.callsagents.backend.campaigns.dto.CampaignFilter;
import com.callsagents.backend.campaigns.entity.Campaign;
import com.callsagents.backend.campaigns.entity.CampaignStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CampaignSpecifications {

    private CampaignSpecifications() {
    }

    public static Specification<Campaign> hasStatus(CampaignStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Campaign> createdBy(UUID userId) {
        return (root, query, cb) -> userId == null ? cb.conjunction() : cb.equal(root.get("createdBy"), userId);
    }

    /**
     * Voice-config filter. The service layer normalizes blank values to null,
     * so "configured" == any of the 5 voice columns NOT NULL (and the inverse
     * for false). A null flag adds no constraint.
     */
    public static Specification<Campaign> voiceConfigured(Boolean hasVoiceConfig) {
        if (hasVoiceConfig == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            Predicate[] predicates = {
                hasVoiceConfig ? cb.isNotNull(root.get("company")) : cb.isNull(root.get("company")),
                hasVoiceConfig ? cb.isNotNull(root.get("website")) : cb.isNull(root.get("website")),
                hasVoiceConfig ? cb.isNotNull(root.get("industry")) : cb.isNull(root.get("industry")),
                hasVoiceConfig ? cb.isNotNull(root.get("services")) : cb.isNull(root.get("services")),
                hasVoiceConfig ? cb.isNotNull(root.get("tone")) : cb.isNull(root.get("tone"))
            };
            return hasVoiceConfig ? cb.or(predicates) : cb.and(predicates);
        };
    }

    public static Specification<Campaign> build(CampaignFilter filter) {
        if (filter == null) {
            return null;
        }
        List<Specification<Campaign>> specs = new ArrayList<>();
        specs.add(hasStatus(filter.status()));
        specs.add(createdBy(filter.createdById()));
        specs.add(voiceConfigured(filter.hasVoiceConfig()));
        return specs.stream().reduce(Specification::and).orElse(null);
    }
}
