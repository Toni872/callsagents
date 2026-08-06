package com.callsagents.backend.calls.repository;

import com.callsagents.backend.calls.entity.Call;
import com.callsagents.backend.calls.entity.CallStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface CallRepository extends JpaRepository<Call, UUID>, JpaSpecificationExecutor<Call> {
    long countByCreatedAtBetween(Instant from, Instant to);
    long countByCreatedAtBetweenAndStatus(Instant from, Instant to, CallStatus status);
}
