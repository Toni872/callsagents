package com.callsagents.backend.campaigns.service;

import com.callsagents.backend.audit.entity.AuditAction;
import com.callsagents.backend.auth.dto.UserDto;
import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.campaigns.dto.CampaignFilter;
import com.callsagents.backend.campaigns.dto.CampaignResponse;
import com.callsagents.backend.campaigns.dto.CreateCampaignRequest;
import com.callsagents.backend.campaigns.dto.UpdateCampaignRequest;
import com.callsagents.backend.campaigns.entity.Campaign;
import com.callsagents.backend.campaigns.entity.CampaignStatus;
import com.callsagents.backend.campaigns.repository.CampaignRepository;
import com.callsagents.backend.campaigns.repository.CampaignSpecifications;
import com.callsagents.backend.common.audit.AuditService;
import com.callsagents.backend.common.dto.PageResponse;
import com.callsagents.backend.common.exception.BadRequestException;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public CampaignService(CampaignRepository campaignRepository, UserRepository userRepository, AuditService auditService) {
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CampaignResponse> findAll(CampaignFilter filter, Pageable pageable) {
        Page<Campaign> page = campaignRepository.findAll(CampaignSpecifications.build(filter), pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public CampaignResponse findById(UUID id) {
        Campaign campaign = campaignRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + id));
        return toResponse(campaign);
    }

    @Transactional
    public CampaignResponse create(CreateCampaignRequest req, UUID currentUserId) {
        validateSchedule(req.startAt(), req.endAt());

        Campaign campaign = Campaign.builder()
            .name(req.name().trim())
            .description(req.description())
            .status(CampaignStatus.DRAFT)
            .startAt(req.startAt())
            .endAt(req.endAt())
            .script(req.script())
            .createdBy(currentUserId)
            .build();

        Campaign saved = campaignRepository.save(campaign);
        auditService.log(currentUserId, "Campaign", saved.getId(), AuditAction.CREATE);
        return toResponse(saved);
    }

    @Transactional
    public CampaignResponse update(UUID id, UpdateCampaignRequest req, UUID currentUserId) {
        Campaign campaign = campaignRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + id));

        if (req.name() != null) campaign.setName(req.name().trim());
        if (req.description() != null) campaign.setDescription(req.description());
        if (req.startAt() != null) campaign.setStartAt(req.startAt());
        if (req.endAt() != null) campaign.setEndAt(req.endAt());
        if (req.script() != null) campaign.setScript(req.script());
        if (req.status() != null) {
            CampaignStatus target = parseStatus(req.status());
            validateStatusTransition(campaign.getStatus(), target);
            campaign.setStatus(target);
        }

        validateSchedule(campaign.getStartAt(), campaign.getEndAt());

        Campaign saved = campaignRepository.save(campaign);
        auditService.log(currentUserId, "Campaign", saved.getId(), AuditAction.UPDATE,
            java.util.Map.<String, Object>of("status", saved.getStatus().name()));
        return toResponse(saved);
    }

    @Transactional
    public CampaignResponse launch(UUID id, UUID currentUserId) {
        Campaign campaign = campaignRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + id));

        CampaignStatus current = campaign.getStatus();
        if (current == CampaignStatus.DRAFT || current == CampaignStatus.SCHEDULED) {
            campaign.setStatus(CampaignStatus.RUNNING);
        } else {
            throw new BadRequestException("Cannot launch a campaign in status " + current
                + "; only DRAFT or SCHEDULED can be launched");
        }

        Campaign saved = campaignRepository.save(campaign);
        auditService.log(currentUserId, "Campaign", saved.getId(), AuditAction.STATUS_CHANGE,
            java.util.Map.<String, Object>of("from", current.name(), "to", CampaignStatus.RUNNING.name()));
        return toResponse(saved);
    }

    @Transactional
    public CampaignResponse pause(UUID id, UUID currentUserId) {
        Campaign campaign = campaignRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + id));

        CampaignStatus current = campaign.getStatus();
        if (current != CampaignStatus.RUNNING) {
            throw new BadRequestException("Cannot pause a campaign in status " + current
                + "; only RUNNING can be paused");
        }
        campaign.setStatus(CampaignStatus.PAUSED);

        Campaign saved = campaignRepository.save(campaign);
        auditService.log(currentUserId, "Campaign", saved.getId(), AuditAction.STATUS_CHANGE,
            java.util.Map.<String, Object>of("from", CampaignStatus.RUNNING.name(), "to", CampaignStatus.PAUSED.name()));
        return toResponse(saved);
    }

    private CampaignResponse toResponse(Campaign campaign) {
        UserDto creator = userRepository.findById(campaign.getCreatedBy())
            .map(this::toUserDto)
            .orElse(null);
        return CampaignResponse.fromEntity(campaign, creator);
    }

    private UserDto toUserDto(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }

    private static CampaignStatus parseStatus(String raw) {
        try {
            return CampaignStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid campaign status: " + raw);
        }
    }

    private static void validateStatusTransition(CampaignStatus from, CampaignStatus to) {
        if (from == to) {
            throw new BadRequestException("Campaign is already in status " + from);
        }
        boolean allowed = switch (from) {
            case DRAFT -> to == CampaignStatus.SCHEDULED || to == CampaignStatus.RUNNING || to == CampaignStatus.CANCELLED;
            case SCHEDULED -> to == CampaignStatus.RUNNING || to == CampaignStatus.CANCELLED;
            case RUNNING -> to == CampaignStatus.PAUSED || to == CampaignStatus.FINISHED || to == CampaignStatus.CANCELLED;
            case PAUSED -> to == CampaignStatus.RUNNING || to == CampaignStatus.FINISHED || to == CampaignStatus.CANCELLED;
            case FINISHED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new BadRequestException("Invalid status transition: " + from + " -> " + to);
        }
    }

    private static void validateSchedule(java.time.Instant startAt, java.time.Instant endAt) {
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new BadRequestException("endAt must be after startAt");
        }
    }
}
