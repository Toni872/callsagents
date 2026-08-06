package com.callsagents.backend.campaigns.repository;

import com.callsagents.backend.campaigns.entity.CampaignLead;
import com.callsagents.backend.campaigns.entity.CampaignLeadId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignLeadRepository extends JpaRepository<CampaignLead, CampaignLeadId> {
}
