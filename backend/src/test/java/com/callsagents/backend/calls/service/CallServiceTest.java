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
import com.callsagents.backend.campaigns.repository.CampaignRepository;
import com.callsagents.backend.common.audit.AuditService;
import com.callsagents.backend.common.dto.PageResponse;
import com.callsagents.backend.common.exception.BadRequestException;
import com.callsagents.backend.common.exception.ForbiddenException;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import com.callsagents.backend.leads.repository.LeadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallServiceTest {

    @Mock
    private CallRepository callRepository;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private CallService callService;

    private final UUID currentUserId = UUID.randomUUID();

    @Test
    void createBuildsAndSavesCall() {
        UUID id = UUID.randomUUID();
        when(campaignRepository.existsById(any())).thenReturn(true);
        when(leadRepository.existsById(any())).thenReturn(true);
        when(callRepository.save(any(Call.class))).thenAnswer(inv -> {
            Call arg = inv.getArgument(0);
            arg.setId(id);
            arg.setCreatedAt(Instant.now());
            arg.setUpdatedAt(Instant.now());
            return arg;
        });

        UUID campaignId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant started = Instant.now();
        Instant ended = started.plusSeconds(120);
        CreateCallRequest req = new CreateCallRequest(
            campaignId, leadId, userId, started, ended, 120,
            "CONNECTED", "INTERESTED", "https://rec.example/x.mp3", "prov-1", "looks good");

        CallResponse response = callService.create(req, currentUserId);

        assertEquals(id, response.id());
        assertEquals(CallStatus.CONNECTED, response.status());
        assertEquals(CallOutcome.INTERESTED, response.outcome());
        ArgumentCaptor<Call> captor = ArgumentCaptor.forClass(Call.class);
        verify(callRepository).save(captor.capture());
        assertEquals(120, captor.getValue().getDurationSeconds());
        verify(auditService).log(eq(currentUserId), eq("Call"), eq(id), eq(AuditAction.CREATE));
    }

    @Test
    void createAllowsAllOptionalFieldsNull() {
        UUID id = UUID.randomUUID();
        when(campaignRepository.existsById(any())).thenReturn(true);
        when(leadRepository.existsById(any())).thenReturn(true);
        when(callRepository.save(any(Call.class))).thenAnswer(inv -> {
            Call arg = inv.getArgument(0);
            arg.setId(id);
            arg.setCreatedAt(Instant.now());
            arg.setUpdatedAt(Instant.now());
            return arg;
        });

        UUID campaignId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CreateCallRequest req = new CreateCallRequest(
            campaignId, leadId, userId, null, null, null,
            null, null, null, null, null);

        CallResponse response = callService.create(req, currentUserId);

        assertNull(response.status());
        assertNull(response.outcome());
        assertNull(response.durationSeconds());
    }

    @Test
    void createRejectsInvalidStatus() {
        when(campaignRepository.existsById(any())).thenReturn(true);
        when(leadRepository.existsById(any())).thenReturn(true);
        CreateCallRequest req = new CreateCallRequest(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            null, null, null, "NOT_A_STATUS", null, null, null, null);
        assertThrows(BadRequestException.class, () -> callService.create(req, currentUserId));
    }

    @Test
    void createRejectsEndedBeforeStarted() {
        when(campaignRepository.existsById(any())).thenReturn(true);
        when(leadRepository.existsById(any())).thenReturn(true);
        Instant start = Instant.now();
        Instant end = start.minusSeconds(60);
        CreateCallRequest req = new CreateCallRequest(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            start, end, 0, null, null, null, null, null);
        assertThrows(BadRequestException.class, () -> callService.create(req, currentUserId));
    }

    @Test
    void updateThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(callRepository.findById(id)).thenThrow(new ResourceNotFoundException("Call not found: " + id));
        UpdateCallRequest req = new UpdateCallRequest(null, null, null, "CONNECTED", null, null, null, null);
        assertThrows(ResourceNotFoundException.class, () -> callService.update(id, req, currentUserId, UserRole.ADMIN));
    }

    @Test
    void updateAppliesProvidedFields() {
        UUID id = UUID.randomUUID();
        Call existing = sampleCall(id);
        existing.setNotes("old");
        when(callRepository.findById(id)).thenReturn(java.util.Optional.of(existing));
        when(callRepository.save(any(Call.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCallRequest req = new UpdateCallRequest(null, null, null, "VOICEMAIL", null, null, null, "new notes");

        CallResponse response = callService.update(id, req, currentUserId, UserRole.ADMIN);

        assertEquals(CallStatus.VOICEMAIL, response.status());
        assertEquals("new notes", response.notes());
        verify(auditService).log(eq(currentUserId), eq("Call"), eq(id), eq(AuditAction.UPDATE));
    }

    @Test
    void updateRejectsNegativeDuration() {
        UUID id = UUID.randomUUID();
        Call existing = sampleCall(id);
        when(callRepository.findById(id)).thenReturn(java.util.Optional.of(existing));

        UpdateCallRequest req = new UpdateCallRequest(null, null, -1, null, null, null, null, null);

        assertThrows(BadRequestException.class, () -> callService.update(id, req, currentUserId, UserRole.ADMIN));
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(callRepository.findById(id)).thenReturn(java.util.Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> callService.findById(id, currentUserId));
    }

    @Test
    void findAllReturnsPagedResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        Call c = sampleCall(UUID.randomUUID());
        Page<Call> page = new PageImpl<>(List.of(c), pageable, 1);
        when(callRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PageResponse<CallResponse> result = callService.findAll(new CallFilter(null, null, null, null, null), pageable, currentUserId);

        assertEquals(1, result.totalElements());
        assertNotNull(result.content().get(0));
    }

    @Test
    void updateClearsRecordingUrlWhenEmpty() {
        UUID id = UUID.randomUUID();
        Call existing = sampleCall(id);
        existing.setRecordingUrl("https://old");
        when(callRepository.findById(id)).thenReturn(java.util.Optional.of(existing));
        when(callRepository.save(any(Call.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCallRequest req = new UpdateCallRequest(null, null, null, null, null, "", null, null);

        CallResponse response = callService.update(id, req, currentUserId, UserRole.ADMIN);

        assertNull(response.recordingUrl());
    }

    @Test
    void updateThrowsForbiddenWhenAgentEditsOtherUserCall() {
        UUID id = UUID.randomUUID();
        Call existing = sampleCall(id);
        existing.setUserId(UUID.randomUUID());
        when(callRepository.findById(id)).thenReturn(java.util.Optional.of(existing));

        UpdateCallRequest req = new UpdateCallRequest(null, null, null, "CONNECTED", null, null, null, null);

        assertThrows(ForbiddenException.class, () -> callService.update(id, req, currentUserId, UserRole.AGENT));
    }

    @Test
    void createRejectsInvalidOutcome() {
        when(campaignRepository.existsById(any())).thenReturn(true);
        when(leadRepository.existsById(any())).thenReturn(true);
        CreateCallRequest req = new CreateCallRequest(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            null, null, null, null, "NOT_AN_OUTCOME", null, null, null);
        assertThrows(BadRequestException.class, () -> callService.create(req, currentUserId));
    }

    @Test
    void createRejectsUnknownCampaign() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.existsById(campaignId)).thenReturn(false);
        CreateCallRequest req = new CreateCallRequest(
            campaignId, UUID.randomUUID(), UUID.randomUUID(),
            null, null, null, null, null, null, null, null);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> callService.create(req, currentUserId));
        assertEquals("Campaign not found: " + campaignId, ex.getMessage());
        verify(callRepository, never()).save(any(Call.class));
    }

    @Test
    void createRejectsUnknownLead() {
        when(campaignRepository.existsById(any())).thenReturn(true);
        UUID leadId = UUID.randomUUID();
        when(leadRepository.existsById(leadId)).thenReturn(false);
        CreateCallRequest req = new CreateCallRequest(
            UUID.randomUUID(), leadId, UUID.randomUUID(),
            null, null, null, null, null, null, null, null);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> callService.create(req, currentUserId));
        assertEquals("Lead not found: " + leadId, ex.getMessage());
        verify(callRepository, never()).save(any(Call.class));
    }

    private static Call sampleCall(UUID id) {
        return Call.builder()
            .id(id)
            .campaignId(UUID.randomUUID())
            .leadId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .startedAt(Instant.now())
            .endedAt(Instant.now().plusSeconds(60))
            .durationSeconds(60)
            .status(CallStatus.CONNECTED)
            .outcome(CallOutcome.NOT_REACHED)
            .build();
    }
}
