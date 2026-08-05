package com.callsagents.backend.calls.repository;

import com.callsagents.backend.calls.dto.CallFilter;
import com.callsagents.backend.calls.entity.Call;
import com.callsagents.backend.calls.entity.CallOutcome;
import com.callsagents.backend.calls.entity.CallStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CallSpecifications {

    private CallSpecifications() {
    }

    public static Specification<Call> hasCampaign(UUID campaignId) {
        return (root, query, cb) -> campaignId == null ? cb.conjunction() : cb.equal(root.get("campaignId"), campaignId);
    }

    public static Specification<Call> hasUser(UUID userId) {
        return (root, query, cb) -> userId == null ? cb.conjunction() : cb.equal(root.get("userId"), userId);
    }

    public static Specification<Call> hasLead(UUID leadId) {
        return (root, query, cb) -> leadId == null ? cb.conjunction() : cb.equal(root.get("leadId"), leadId);
    }

    public static Specification<Call> hasStatus(CallStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Call> hasOutcome(CallOutcome outcome) {
        return (root, query, cb) -> outcome == null ? cb.conjunction() : cb.equal(root.get("outcome"), outcome);
    }

    public static Specification<Call> build(CallFilter filter) {
        if (filter == null) {
            return null;
        }
        List<Specification<Call>> specs = new ArrayList<>();
        specs.add(hasCampaign(filter.campaignId()));
        specs.add(hasUser(filter.userId()));
        specs.add(hasLead(filter.leadId()));
        specs.add(hasStatus(filter.status()));
        specs.add(hasOutcome(filter.outcome()));
        return specs.stream().reduce(Specification::and).orElse(null);
    }
}
