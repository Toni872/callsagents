package com.callsagents.backend.campaigns.service;

import com.callsagents.backend.audit.entity.AuditAction;
import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.auth.entity.UserStatus;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.campaigns.dto.CampaignFilter;
import com.callsagents.backend.campaigns.dto.CampaignResponse;
import com.callsagents.backend.campaigns.dto.CreateCampaignRequest;
import com.callsagents.backend.campaigns.dto.UpdateCampaignRequest;
import com.callsagents.backend.campaigns.entity.Campaign;
import com.callsagents.backend.campaigns.entity.CampaignStatus;
import com.callsagents.backend.campaigns.repository.CampaignRepository;
import com.callsagents.backend.common.audit.AuditService;
import com.callsagents.backend.common.dto.PageResponse;
import com.callsagents.backend.common.exception.BadRequestException;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampaignServiceTest {

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private CampaignService campaignService;

    private UUID currentUserId;
    private User creator;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        creator = User.builder()
            .id(currentUserId)
            .email("admin@example.com")
            .fullName("Admin")
            .role(UserRole.ADMIN)
            .status(UserStatus.ACTIVE)
            .passwordHash("x")
            .build();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(creator));
    }

    @Test
    void createSetsDraftStatus() {
        CreateCampaignRequest req = new CreateCampaignRequest("Promo Q1", "Outbound push", null, null, "Hello, this is...");
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> {
            Campaign c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });

        CampaignResponse response = campaignService.create(req, currentUserId);

        assertEquals(CampaignStatus.DRAFT, response.status());
        assertEquals("Promo Q1", response.name());
        assertEquals(currentUserId, response.createdBy().id());
        verify(auditService).log(eq(currentUserId), eq("Campaign"), any(UUID.class), eq(AuditAction.CREATE));
    }

    @Test
    void createRejectsEndBeforeStart() {
        Instant start = Instant.now();
        Instant end = start.minusSeconds(60);
        CreateCampaignRequest req = new CreateCampaignRequest("Bad", null, start, end, null);
        assertThrows(BadRequestException.class, () -> campaignService.create(req, currentUserId));
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void launchTransitionsDraftToRunning() {
        UUID id = UUID.randomUUID();
        Campaign c = sampleCampaign(id, CampaignStatus.DRAFT);
        when(campaignRepository.findById(id)).thenReturn(Optional.of(c));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse response = campaignService.launch(id, currentUserId);

        assertEquals(CampaignStatus.RUNNING, response.status());
        verify(auditService).log(eq(currentUserId), eq("Campaign"), eq(id), eq(AuditAction.STATUS_CHANGE),
            eq(Map.of("from", "DRAFT", "to", "RUNNING")));
    }

    @Test
    void launchRejectsFromRunning() {
        UUID id = UUID.randomUUID();
        Campaign c = sampleCampaign(id, CampaignStatus.RUNNING);
        when(campaignRepository.findById(id)).thenReturn(Optional.of(c));

        assertThrows(BadRequestException.class, () -> campaignService.launch(id, currentUserId));
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void pauseTransitionsRunningToPaused() {
        UUID id = UUID.randomUUID();
        Campaign c = sampleCampaign(id, CampaignStatus.RUNNING);
        when(campaignRepository.findById(id)).thenReturn(Optional.of(c));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse response = campaignService.pause(id, currentUserId);

        assertEquals(CampaignStatus.PAUSED, response.status());
        verify(auditService).log(eq(currentUserId), eq("Campaign"), eq(id),
            eq(AuditAction.STATUS_CHANGE), any(java.util.Map.class));
    }

    @Test
    void pauseRejectsFromPaused() {
        UUID id = UUID.randomUUID();
        Campaign c = sampleCampaign(id, CampaignStatus.PAUSED);
        when(campaignRepository.findById(id)).thenReturn(Optional.of(c));

        assertThrows(BadRequestException.class, () -> campaignService.pause(id, currentUserId));
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void updateRejectsInvalidStatusTransition() {
        UUID id = UUID.randomUUID();
        Campaign c = sampleCampaign(id, CampaignStatus.DRAFT);
        when(campaignRepository.findById(id)).thenReturn(Optional.of(c));

        UpdateCampaignRequest req = new UpdateCampaignRequest(null, null, null, null, null, "FINISHED");

        assertThrows(BadRequestException.class, () -> campaignService.update(id, req, currentUserId));
    }

    @Test
    void updateAcceptsValidStatusTransition() {
        UUID id = UUID.randomUUID();
        Campaign c = sampleCampaign(id, CampaignStatus.DRAFT);
        when(campaignRepository.findById(id)).thenReturn(Optional.of(c));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCampaignRequest req = new UpdateCampaignRequest(null, null, null, null, null, "SCHEDULED");

        CampaignResponse response = campaignService.update(id, req, currentUserId);

        assertEquals(CampaignStatus.SCHEDULED, response.status());
    }

    @Test
    void updateRejectsSameStatus() {
        UUID id = UUID.randomUUID();
        Campaign c = sampleCampaign(id, CampaignStatus.DRAFT);
        when(campaignRepository.findById(id)).thenReturn(Optional.of(c));

        UpdateCampaignRequest req = new UpdateCampaignRequest(null, null, null, null, null, "DRAFT");

        assertThrows(BadRequestException.class, () -> campaignService.update(id, req, currentUserId));
    }

    @Test
    void findByIdThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(campaignRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> campaignService.findById(id));
    }

    @Test
    void findByIdReturnsResponseWhenPresent() {
        UUID id = UUID.randomUUID();
        Campaign c = sampleCampaign(id, CampaignStatus.RUNNING);
        when(campaignRepository.findById(id)).thenReturn(Optional.of(c));

        CampaignResponse response = campaignService.findById(id);

        assertEquals(id, response.id());
        assertEquals("Test campaign", response.name());
        assertNotNull(response.createdBy());
    }

    @Test
    void findAllReturnsPagedResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        Campaign c = sampleCampaign(UUID.randomUUID(), CampaignStatus.DRAFT, currentUserId);
        Page<Campaign> page = new PageImpl<>(List.of(c), pageable, 1);
        when(campaignRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PageResponse<CampaignResponse> result = campaignService.findAll(new CampaignFilter(null, null), pageable);

        assertEquals(1, result.totalElements());
        assertEquals(1, result.content().size());
    }

    @Test
    void launchThrowsWhenCampaignMissing() {
        UUID id = UUID.randomUUID();
        when(campaignRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> campaignService.launch(id, currentUserId));
    }

    @Test
    void launchTransitionsScheduledToRunning() {
        UUID id = UUID.randomUUID();
        Campaign c = sampleCampaign(id, CampaignStatus.SCHEDULED);
        when(campaignRepository.findById(id)).thenReturn(Optional.of(c));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse response = campaignService.launch(id, currentUserId);

        assertEquals(CampaignStatus.RUNNING, response.status());
    }

    private static Campaign sampleCampaign(UUID id, CampaignStatus status) {
        return sampleCampaign(id, status, UUID.randomUUID());
    }

    private static Campaign sampleCampaign(UUID id, CampaignStatus status, UUID createdBy) {
        return Campaign.builder()
            .id(id)
            .name("Test campaign")
            .description("d")
            .status(status)
            .startAt(Instant.now())
            .endAt(Instant.now().plusSeconds(3600))
            .script("s")
            .createdBy(createdBy)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
}
