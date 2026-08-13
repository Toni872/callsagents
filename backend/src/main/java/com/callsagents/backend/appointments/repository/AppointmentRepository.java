package com.callsagents.backend.appointments.repository;

import com.callsagents.backend.appointments.entity.Appointment;
import com.callsagents.backend.appointments.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {
    long countByScheduledAtGreaterThanEqualAndStatusIn(Instant from, Collection<AppointmentStatus> statuses);

    /** Backfill target: future, actionable appointments that never synced. */
    List<Appointment> findAllByExternalEventIdIsNullAndScheduledAtGreaterThanEqualAndStatusIn(
        Instant from, Collection<AppointmentStatus> statuses);
}
