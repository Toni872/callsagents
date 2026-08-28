package com.callsagents.backend.leads.repository;

import com.callsagents.backend.leads.dto.LeadFilter;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.entity.LeadSource;
import com.callsagents.backend.leads.entity.LeadStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LeadSpecifications {

    private LeadSpecifications() {
    }

    public static Specification<Lead> hasStatus(LeadStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Lead> hasSource(LeadSource source) {
        return (root, query, cb) -> source == null ? cb.conjunction() : cb.equal(root.get("source"), source);
    }

    public static Specification<Lead> isAssignedTo(UUID userId) {
        return (root, query, cb) -> userId == null ? cb.conjunction() : cb.equal(root.get("assignedTo"), userId);
    }

    public static Specification<Lead> ownedBy(UUID userId) {
        return (root, query, cb) -> userId == null ? cb.conjunction() : cb.equal(root.get("createdBy"), userId);
    }

    public static Specification<Lead> searchText(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + q.toLowerCase() + "%";
            return cb.or(
                cb.like(cb.lower(root.get("firstName")), like),
                cb.like(cb.lower(root.get("lastName")), like),
                cb.like(cb.lower(root.get("email")), like),
                cb.like(cb.lower(root.get("phone")), like)
            );
        };
    }

    public static Specification<Lead> build(LeadFilter filter) {
        if (filter == null) {
            return null;
        }
        List<Specification<Lead>> specs = new ArrayList<>();
        specs.add(hasStatus(filter.status()));
        specs.add(hasSource(filter.source()));
        specs.add(isAssignedTo(filter.assignedToId()));
        specs.add(searchText(filter.search()));
        return specs.stream().reduce(Specification::and).orElse(null);
    }

    public static Predicate toPredicate(LeadFilter filter, jakarta.persistence.criteria.Root<Lead> root,
                                        jakarta.persistence.criteria.CriteriaQuery<?> query,
                                        jakarta.persistence.criteria.CriteriaBuilder cb) {
        return build(filter).toPredicate(root, query, cb);
    }
}
