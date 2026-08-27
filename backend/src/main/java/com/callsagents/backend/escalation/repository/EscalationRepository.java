package com.callsagents.backend.escalation.repository;

import com.callsagents.backend.escalation.entity.Escalation;
import com.callsagents.backend.escalation.entity.EscalationStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EscalationRepository extends JpaRepository<Escalation, UUID> {

    Optional<Escalation> findFirstByLeadIdAndStageInOrderByCreatedAtDesc(UUID leadId, List<EscalationStage> stages);

    List<Escalation> findByStageAndWaitingUntilBefore(EscalationStage stage, Instant now);

    Optional<Escalation> findFirstByLeadIdOrderByCreatedAtDesc(UUID leadId);
}
