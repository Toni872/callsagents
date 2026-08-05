package com.callsagents.backend.campaigns.repository;

import com.callsagents.backend.campaigns.dto.CampaignFilter;
import com.callsagents.backend.campaigns.entity.Campaign;
import com.callsagents.backend.campaigns.entity.CampaignStatus;
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

    public static Specification<Campaign> build(CampaignFilter filter) {
        if (filter == null) {
            return null;
        }
        List<Specification<Campaign>> specs = new ArrayList<>();
        specs.add(hasStatus(filter.status()));
        specs.add(createdBy(filter.createdById()));
        return specs.stream().reduce(Specification::and).orElse(null);
    }
}
