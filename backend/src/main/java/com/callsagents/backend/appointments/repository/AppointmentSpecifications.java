package com.callsagents.backend.appointments.repository;

import com.callsagents.backend.appointments.dto.AppointmentFilter;
import com.callsagents.backend.appointments.entity.Appointment;
import com.callsagents.backend.appointments.entity.AppointmentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AppointmentSpecifications {

    private AppointmentSpecifications() {
    }

    public static Specification<Appointment> hasLead(UUID leadId) {
        return (root, query, cb) -> leadId == null ? cb.conjunction() : cb.equal(root.get("leadId"), leadId);
    }

    public static Specification<Appointment> hasUser(UUID userId) {
        return (root, query, cb) -> userId == null ? cb.conjunction() : cb.equal(root.get("userId"), userId);
    }

    public static Specification<Appointment> ownedBy(UUID userId) {
        return hasUser(userId);
    }

    public static Specification<Appointment> hasStatus(AppointmentStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Appointment> build(AppointmentFilter filter) {
        if (filter == null) {
            return null;
        }
        List<Specification<Appointment>> specs = new ArrayList<>();
        specs.add(hasLead(filter.leadId()));
        specs.add(hasUser(filter.userId()));
        specs.add(hasStatus(filter.status()));
        return specs.stream().reduce(Specification::and).orElse(null);
    }
}
