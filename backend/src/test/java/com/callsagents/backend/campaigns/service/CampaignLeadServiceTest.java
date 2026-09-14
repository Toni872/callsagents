package com.callsagents.backend.campaigns.service;

import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.campaigns.dto.AddLeadRequest;
import com.callsagents.backend.campaigns.dto.CampaignLeadResponse;
import com.callsagents.backend.campaigns.entity.Campaign;
import com.callsagents.backend.campaigns.entity.CampaignLead;
import com.callsagents.backend.campaigns.entity.CampaignLeadId;
import com.callsagents.backend.campaigns.entity.CampaignStatus;
import com.callsagents.backend.campaigns.repository.CampaignLeadRepository;
import com.callsagents.backend.campaigns.repository.CampaignRepository;
import com.callsagents.backend.common.exception.ForbiddenException;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.entity.LeadSource;
import com.callsagents.backend.leads.entity.LeadStatus;
import com.callsagents.backend.leads.repository.LeadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignLeadServiceTest {

    @Mock
    private CampaignLeadRepository campaignLeadRepository;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private com.callsagents.backend.auth.repository.UserRepository userRepository;

    @InjectMocks
    private CampaignLeadService campaignLeadService;

    private final UUID ownerId = UUID.randomUUID();

    @Test
    void addLeadRejectsLeadOfAnotherOwner() {
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = campaignWithIdAndOwner(UUID.randomUUID(), ownerId);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        UUID foreignLeadId = UUID.randomUUID();
        when(leadRepository.findById(foreignLeadId))
            .thenReturn(Optional.of(leadWithOwner(foreignLeadId, UUID.randomUUID())));

        AddLeadRequest req = new AddLeadRequest(foreignLeadId, null, null);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
            () -> campaignLeadService.addLead(campaignId, req, ownerId, UserRole.AGENT));
        assertEquals("You can only manage your own leads", ex.getMessage());
        verify(campaignLeadRepository, never()).save(any(CampaignLead.class));
    }

    @Test
    void addLeadSucceedsWithLeadOfSameOwner() {
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = campaignWithIdAndOwner(campaignId, ownerId);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        UUID leadId = UUID.randomUUID();
        Lead lead = leadWithOwner(leadId, ownerId);
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));
        when(campaignLeadRepository.findById(any(CampaignLeadId.class))).thenReturn(Optional.empty());
        when(campaignLeadRepository.save(any(CampaignLead.class))).thenAnswer(inv -> inv.getArgument(0));

        AddLeadRequest req = new AddLeadRequest(leadId, null, null);

        CampaignLeadResponse response = campaignLeadService.addLead(campaignId, req, ownerId, UserRole.AGENT);

        assertNotNull(response);
        assertEquals(campaignId, response.campaignId());
        assertEquals(leadId, response.leadId());
        verify(campaignLeadRepository).save(any(CampaignLead.class));
    }

    @Test
    void addLeadAllowsAdminToUseForeignLead() {
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = campaignWithIdAndOwner(UUID.randomUUID(), ownerId);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        UUID foreignLeadId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Lead lead = leadWithOwner(foreignLeadId, UUID.randomUUID());
        when(leadRepository.findById(foreignLeadId)).thenReturn(Optional.of(lead));
        when(campaignLeadRepository.findById(any(CampaignLeadId.class))).thenReturn(Optional.empty());
        when(campaignLeadRepository.save(any(CampaignLead.class))).thenAnswer(inv -> inv.getArgument(0));

        AddLeadRequest req = new AddLeadRequest(foreignLeadId, null, null);

        CampaignLeadResponse response = campaignLeadService.addLead(campaignId, req, adminId, UserRole.ADMIN);

        assertNotNull(response);
        verify(campaignLeadRepository).save(any(CampaignLead.class));
    }

    private static Campaign campaignWithIdAndOwner(UUID id, UUID ownerId) {
        return Campaign.builder()
            .id(id)
            .name("Q4 Outbound")
            .status(CampaignStatus.DRAFT)
            .createdBy(ownerId)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private static Lead leadWithOwner(UUID id, UUID ownerId) {
        return Lead.builder()
            .id(id)
            .firstName("Jane")
            .lastName("Doe")
            .status(LeadStatus.NEW)
            .source(LeadSource.MANUAL)
            .createdBy(ownerId)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
}