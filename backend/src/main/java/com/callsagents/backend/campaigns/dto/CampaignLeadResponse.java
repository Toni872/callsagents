package com.callsagents.backend.campaigns.dto;

import com.callsagents.backend.auth.dto.UserDto;
import com.callsagents.backend.campaigns.entity.CampaignLead;
import com.callsagents.backend.campaigns.entity.CampaignLeadStatus;
import com.callsagents.backend.leads.entity.Lead;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for a lead attached to a campaign.
 *
 * Embeds the basic Lead fields so the UI can render the campaign-leads
 * list without needing a second call per row.
 */
public record CampaignLeadResponse(
    UUID campaignId,
    UUID leadId,
    String leadFirstName,
    String leadLastName,
    String leadEmail,
    String leadPhone,
    String leadCompany,
    CampaignLeadStatus status,
    Integer attempts,
    Instant lastAttemptAt,
    Instant nextAttemptAt,
    UserDto assignedTo,
    Instant createdAt,
    Instant updatedAt
) {
    public static CampaignLeadResponse from(CampaignLead cl, Lead lead, UserDto assignee) {
        return new CampaignLeadResponse(
            cl.getCampaignId(),
            cl.getLeadId(),
            lead != null ? lead.getFirstName() : null,
            lead != null ? lead.getLastName() : null,
            lead != null ? lead.getEmail() : null,
            lead != null ? lead.getPhone() : null,
            lead != null ? lead.getCompany() : null,
            cl.getStatus(),
            cl.getAttempts(),
            cl.getLastAttemptAt(),
            cl.getNextAttemptAt(),
            assignee,
            cl.getCreatedAt(),
            cl.getUpdatedAt()
        );
    }
}