package com.callsagents.backend.campaigns.service;

import com.callsagents.backend.auth.dto.UserDto;
import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.campaigns.dto.AddLeadRequest;
import com.callsagents.backend.campaigns.dto.CampaignLeadResponse;
import com.callsagents.backend.campaigns.entity.Campaign;
import com.callsagents.backend.campaigns.entity.CampaignLead;
import com.callsagents.backend.campaigns.entity.CampaignLeadStatus;
import com.callsagents.backend.campaigns.repository.CampaignLeadRepository;
import com.callsagents.backend.campaigns.repository.CampaignRepository;
import com.callsagents.backend.common.dto.PageResponse;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.repository.LeadRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the assignment of leads to campaigns.
 *
 * Adding a lead is idempotent at the API level: if (campaignId, leadId) is
 * already present in campaign_leads, we return the existing row instead of
 * failing on the composite PK constraint.
 */
@Service
public class CampaignLeadService {

    private final CampaignLeadRepository campaignLeadRepository;
    private final CampaignRepository campaignRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;

    public CampaignLeadService(
        CampaignLeadRepository campaignLeadRepository,
        CampaignRepository campaignRepository,
        LeadRepository leadRepository,
        UserRepository userRepository
    ) {
        this.campaignLeadRepository = campaignLeadRepository;
        this.campaignRepository = campaignRepository;
        this.leadRepository = leadRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<CampaignLeadResponse> listLeads(UUID campaignId, int page, int size) {
        if (!campaignRepository.existsById(campaignId)) {
            throw new ResourceNotFoundException("Campaign not found: " + campaignId);
        }
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "leadId"));
        Page<CampaignLead> result = campaignLeadRepository.findByCampaignId(campaignId, pageable);
        return PageResponse.from(result.map(cl -> toResponse(cl)));
    }

    @Transactional
    public CampaignLeadResponse addLead(UUID campaignId, AddLeadRequest req, UUID currentUserId) {
        Campaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + campaignId));

        Lead lead = leadRepository.findById(req.leadId())
            .orElseThrow(() -> new ResourceNotFoundException("Lead not found: " + req.leadId()));

        // Idempotent add: return existing assignment if already present
        Optional<CampaignLead> existing = campaignLeadRepository.findById(
            new com.callsagents.backend.campaigns.entity.CampaignLeadId(campaignId, req.leadId())
        );
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        CampaignLeadStatus status = parseStatus(req.status());

        CampaignLead cl = CampaignLead.builder()
            .campaignId(campaign.getId())
            .leadId(lead.getId())
            .status(status)
            .attempts(0)
            .assignedTo(req.assignedToId())
            .build();

        CampaignLead saved = campaignLeadRepository.save(cl);
        return toResponse(saved);
    }

    @Transactional
    public void removeLead(UUID campaignId, UUID leadId, UUID currentUserId) {
        long removed = campaignLeadRepository.deleteByCampaignIdAndLeadId(campaignId, leadId);
        if (removed == 0) {
            throw new ResourceNotFoundException(
                "Lead " + leadId + " is not assigned to campaign " + campaignId
            );
        }
    }

    private CampaignLeadStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return CampaignLeadStatus.PENDING;
        }
        try {
            return CampaignLeadStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new com.callsagents.backend.common.exception.BadRequestException(
                "Invalid status: " + raw + ". Allowed: PENDING, IN_PROGRESS, COMPLETED, SKIPPED"
            );
        }
    }

    private CampaignLeadResponse toResponse(CampaignLead cl) {
        Lead lead = leadRepository.findById(cl.getLeadId()).orElse(null);
        UserDto assignee = cl.getAssignedTo() == null
            ? null
            : userRepository.findById(cl.getAssignedTo())
                .map(u -> new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name()))
                .orElse(null);
        return CampaignLeadResponse.from(cl, lead, assignee);
    }
}