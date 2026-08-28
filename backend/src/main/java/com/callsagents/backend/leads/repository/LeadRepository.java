package com.callsagents.backend.leads.repository;

import com.callsagents.backend.leads.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {
    Optional<Lead> findByEmail(String email);
    Optional<Lead> findByPhone(String phone);

    // Dashboard metrics — derived methods, no JPQL needed
    long countByAssignedToIsNotNull();

    // Multi-tenant (per-user) scoping metrics
    long countByCreatedBy(UUID createdBy);
    long countByCreatedByAndAssignedToIsNotNull(UUID createdBy);
}
