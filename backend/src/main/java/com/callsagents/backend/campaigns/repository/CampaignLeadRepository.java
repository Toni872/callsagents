package com.callsagents.backend.campaigns.repository;

import com.callsagents.backend.campaigns.entity.CampaignLead;
import com.callsagents.backend.campaigns.entity.CampaignLeadId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CampaignLeadRepository extends JpaRepository<CampaignLead, CampaignLeadId> {

    boolean existsByCampaignIdAndLeadId(UUID campaignId, UUID leadId);

    Page<CampaignLead> findByCampaignId(UUID campaignId, Pageable pageable);

    long deleteByCampaignIdAndLeadId(UUID campaignId, UUID leadId);
}