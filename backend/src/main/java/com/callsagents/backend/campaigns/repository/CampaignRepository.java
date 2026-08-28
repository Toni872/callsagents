package com.callsagents.backend.campaigns.repository;

import com.callsagents.backend.campaigns.entity.Campaign;
import com.callsagents.backend.campaigns.entity.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID>, JpaSpecificationExecutor<Campaign> {
    long countByStatus(CampaignStatus status);

    // Multi-tenant (per-user) scoping variant
    long countByStatusAndCreatedBy(CampaignStatus status, UUID createdBy);
}
