package com.callsagents.backend.calls.service;

import com.callsagents.backend.audit.entity.AuditAction;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.calls.dto.CallFilter;
import com.callsagents.backend.calls.dto.CallResponse;
import com.callsagents.backend.calls.dto.CreateCallRequest;
import com.callsagents.backend.calls.dto.UpdateCallRequest;
import com.callsagents.backend.calls.entity.Call;
import com.callsagents.backend.calls.entity.CallOutcome;
import com.callsagents.backend.calls.entity.CallStatus;
import com.callsagents.backend.calls.repository.CallRepository;
import com.callsagents.backend.calls.repository.CallSpecifications;
import com.callsagents.backend.campaigns.repository.CampaignRepository;
import com.callsagents.backend.common.audit.AuditService;
import com.callsagents.backend.leads.repository.LeadRepository;
import com.callsagents.backend.common.dto.PageResponse;
import com.callsagents.backend.common.exception.BadRequestException;
import com.callsagents.backend.common.exception.ForbiddenException;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class CallService {

    private final CallRepository callRepository;
    private final CampaignRepository campaignRepository;
    private final LeadRepository leadRepository;
    private final AuditService auditService;

    public CallService(
        CallRepository callRepository,
        CampaignRepository campaignRepository,
        LeadRepository leadRepository,
        AuditService auditService
    ) {
        this.callRepository = callRepository;
        this.campaignRepository = campaignRepository;
        this.leadRepository = leadRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CallResponse> findAll(CallFilter filter, Pageable pageable, UUID currentUserId) {
        Specification<Call> spec = CallSpecifications.build(filter);
        spec = (spec == null)
            ? CallSpecifications.ownedBy(currentUserId)
            : spec.and(CallSpecifications.ownedBy(currentUserId));
        Page<Call> page = callRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(CallResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public CallResponse findById(UUID id, UUID currentUserId) {
        Call call = callRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Call not found: " + id));
        if (call.getUserId() == null || !call.getUserId().equals(currentUserId)) {
            throw new ResourceNotFoundException("Call not found: " + id);
        }
        return CallResponse.fromEntity(call);
    }

    @Transactional
    public CallResponse create(CreateCallRequest req, UUID currentUserId) {
        if (req.campaignId() != null && !campaignRepository.existsById(req.campaignId())) {
            throw new BadRequestException("Campaign not found: " + req.campaignId());
        }
        if (req.leadId() != null && !leadRepository.existsById(req.leadId())) {
            throw new BadRequestException("Lead not found: " + req.leadId());
        }
        validateCallWindow(req.startedAt(), req.endedAt());

        Call call = Call.builder()
            .campaignId(req.campaignId())
            .leadId(req.leadId())
            .userId(currentUserId)
            .startedAt(req.startedAt())
            .endedAt(req.endedAt())
            .durationSeconds(req.durationSeconds())
            .status(parseStatus(req.status()))
            .outcome(parseOutcome(req.outcome()))
            .recordingUrl(blankToNull(req.recordingUrl()))
            .providerCallId(blankToNull(req.providerCallId()))
            .notes(req.notes())
            .build();

        Call saved = callRepository.save(call);
        auditService.log(currentUserId, "Call", saved.getId(), AuditAction.CREATE);
        return CallResponse.fromEntity(saved);
    }

    @Transactional
    public CallResponse update(UUID id, UpdateCallRequest req, UUID currentUserId, UserRole role) {
        Call call = callRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Call not found: " + id));

        if (call.getUserId() != null && !call.getUserId().equals(currentUserId)
            && role != UserRole.ADMIN && role != UserRole.SUPERVISOR) {
            throw new ForbiddenException("You can only update your own calls");
        }

        if (req.startedAt() != null) call.setStartedAt(req.startedAt());
        if (req.endedAt() != null) call.setEndedAt(req.endedAt());
        if (req.durationSeconds() != null) call.setDurationSeconds(req.durationSeconds());
        if (req.status() != null) call.setStatus(parseStatus(req.status()));
        if (req.outcome() != null) call.setOutcome(parseOutcome(req.outcome()));
        if (req.recordingUrl() != null) call.setRecordingUrl(blankToNull(req.recordingUrl()));
        if (req.providerCallId() != null) call.setProviderCallId(blankToNull(req.providerCallId()));
        if (req.notes() != null) call.setNotes(req.notes());

        validateCallWindow(call.getStartedAt(), call.getEndedAt());
        if (call.getDurationSeconds() != null && call.getDurationSeconds() < 0) {
            throw new BadRequestException("durationSeconds must be >= 0");
        }

        Call saved = callRepository.save(call);
        auditService.log(currentUserId, "Call", saved.getId(), AuditAction.UPDATE);
        return CallResponse.fromEntity(saved);
    }

    private static void validateCallWindow(java.time.Instant startedAt, java.time.Instant endedAt) {
        if (startedAt != null && endedAt != null && endedAt.isBefore(startedAt)) {
            throw new BadRequestException("endedAt must not be before startedAt");
        }
    }

    private static CallStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return CallStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid call status: " + raw);
        }
    }

    private static CallOutcome parseOutcome(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return CallOutcome.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid call outcome: " + raw);
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    @SuppressWarnings("unused")
    private Map<String, Object> toAuditMap(Call call) {
        return Map.of(
            "status", call.getStatus() == null ? "null" : call.getStatus().name(),
            "outcome", call.getOutcome() == null ? "null" : call.getOutcome().name()
        );
    }
}
