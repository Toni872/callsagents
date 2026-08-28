package com.callsagents.backend.leads.service;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.auth.entity.UserStatus;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.common.audit.AuditService;
import com.callsagents.backend.common.dto.PageResponse;
import com.callsagents.backend.common.exception.BadRequestException;
import com.callsagents.backend.common.exception.ForbiddenException;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import com.callsagents.backend.leads.dto.CreateLeadRequest;
import com.callsagents.backend.leads.dto.ImportResultDto;
import com.callsagents.backend.leads.dto.LeadFilter;
import com.callsagents.backend.leads.dto.LeadResponse;
import com.callsagents.backend.leads.dto.UpdateLeadRequest;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.entity.LeadSource;
import com.callsagents.backend.leads.entity.LeadStatus;
import com.callsagents.backend.leads.repository.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private LeadService leadService;

    private UUID currentUserId;
    private User assignee;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        assignee = User.builder()
            .id(UUID.randomUUID())
            .email("agent@example.com")
            .fullName("Agent Smith")
            .role(UserRole.AGENT)
            .status(UserStatus.ACTIVE)
            .passwordHash("x")
            .build();
    }

    @Test
    void findAllReturnsMappedPage() {
        Lead lead = sampleLead(UUID.randomUUID());
        Pageable pageable = PageRequest.of(0, 20);
        Page<Lead> page = new PageImpl<>(List.of(lead), pageable, 1);
        when(leadRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        PageResponse<LeadResponse> result =
            leadService.findAll(new LeadFilter(null, null, null, null), pageable, currentUserId);

        assertEquals(1, result.totalElements());
        assertEquals(1, result.content().size());
        assertEquals(lead.getId(), result.content().get(0).id());
    }

    @Test
    void findAllWithNullFilterStillAppliesOwnerScope() {
        Pageable pageable = PageRequest.of(0, 20);
        when(leadRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        leadService.findAll(null, pageable, currentUserId);

        ArgumentCaptor<Specification<Lead>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(leadRepository).findAll(captor.capture(), eq(pageable));
        assertNotNull(captor.getValue());
    }

    @Test
    void findByIdReturnsResponseWhenFound() {
        UUID id = UUID.randomUUID();
        Lead lead = sampleLead(id);
        when(leadRepository.findById(id)).thenReturn(Optional.of(lead));

        LeadResponse response = leadService.findById(id, currentUserId);

        assertEquals(id, response.id());
        assertEquals(lead.getFirstName(), response.firstName());
    }

    @Test
    void findByIdThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(leadRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> leadService.findById(id, currentUserId));
    }

    @Test
    void findByIdThrowsNotFoundWhenNotOwner() {
        UUID id = UUID.randomUUID();
        Lead lead = sampleLead(id);
        lead.setCreatedBy(UUID.randomUUID());
        when(leadRepository.findById(id)).thenReturn(Optional.of(lead));

        assertThrows(ResourceNotFoundException.class,
            () -> leadService.findById(id, currentUserId));
    }

    @Test
    void createRejectsWhenNoEmailOrPhone() {
        CreateLeadRequest req = new CreateLeadRequest("A", "B", null, null, null, "MANUAL", null, null);

        assertThrows(BadRequestException.class, () -> leadService.create(req, currentUserId));
        verify(leadRepository, never()).save(any());
    }

    @Test
    void createAcceptsValidRequestWithEmail() {
        UUID id = UUID.randomUUID();
        Lead saved = sampleLead(id);
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> {
            Lead arg = inv.getArgument(0);
            arg.setId(id);
            return arg;
        });

        CreateLeadRequest req = new CreateLeadRequest("Ana", "Lopez", "ana@x.com", null, "Acme", "MANUAL", "hi", null);

        LeadResponse response = leadService.create(req, currentUserId);

        assertNotNull(response.id());
        assertEquals("Ana", response.firstName());
        assertEquals(LeadStatus.NEW, response.status());
        assertEquals(LeadSource.MANUAL, response.source());
        verify(auditService).log(eq(currentUserId), eq("Lead"), eq(id), eq(com.callsagents.backend.audit.entity.AuditAction.CREATE));
    }

    @Test
    void createSetsCreatedByToCurrentUser() {
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateLeadRequest req = new CreateLeadRequest("Ana", "Lopez", "ana@x.com", null, null, "MANUAL", null, null);

        leadService.create(req, currentUserId);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        assertEquals(currentUserId, captor.getValue().getCreatedBy());
    }

    @Test
    void updateThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        UpdateLeadRequest req = new UpdateLeadRequest("Other", null, null, null, null, null, null, null, null, null);
        when(leadRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> leadService.update(id, req, currentUserId));
    }

    @Test
    void updateThrowsForbiddenWhenNotOwner() {
        UUID id = UUID.randomUUID();
        Lead existing = sampleLead(id);
        existing.setCreatedBy(UUID.randomUUID());
        when(leadRepository.findById(id)).thenReturn(Optional.of(existing));

        UpdateLeadRequest req = new UpdateLeadRequest("Other", null, null, null, null, null, null, null, null, null);

        assertThrows(ForbiddenException.class, () -> leadService.update(id, req, currentUserId));
    }

    @Test
    void updateAppliesProvidedFieldsAndPreservesOthers() {
        UUID id = UUID.randomUUID();
        Lead existing = sampleLead(id);
        existing.setNotes("old note");
        when(leadRepository.findById(id)).thenReturn(Optional.of(existing));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateLeadRequest req = new UpdateLeadRequest(null, null, "new@x.com", null, null, null, null, null, null, null);

        LeadResponse response = leadService.update(id, req, currentUserId);

        assertEquals("new@x.com", response.email());
        assertEquals("old note", response.notes());
        verify(auditService).log(eq(currentUserId), eq("Lead"), eq(id), eq(com.callsagents.backend.audit.entity.AuditAction.UPDATE));
    }

    @Test
    void updateRejectsInvalidAssignee() {
        UUID id = UUID.randomUUID();
        Lead existing = sampleLead(id);
        when(leadRepository.findById(id)).thenReturn(Optional.of(existing));

        UpdateLeadRequest req = new UpdateLeadRequest(null, null, null, null, null, null, null, UUID.randomUUID(), null, null);

        assertThrows(BadRequestException.class, () -> leadService.update(id, req, currentUserId));
        verify(userRepository).existsById(any());
    }

    @Test
    void deleteThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(leadRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> leadService.delete(id, currentUserId, UserRole.AGENT));
        verify(leadRepository, never()).deleteById(any());
    }

    @Test
    void deleteThrowsForbiddenWhenNotOwner() {
        UUID id = UUID.randomUUID();
        Lead existing = sampleLead(id);
        existing.setCreatedBy(UUID.randomUUID());
        when(leadRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThrows(ForbiddenException.class,
            () -> leadService.delete(id, currentUserId, UserRole.AGENT));
        verify(leadRepository, never()).deleteById(any());
    }

    @Test
    void deleteSucceedsAndAudits() {
        UUID id = UUID.randomUUID();
        Lead lead = sampleLead(id);
        when(leadRepository.findById(id)).thenReturn(Optional.of(lead));

        leadService.delete(id, currentUserId, UserRole.ADMIN);

        verify(leadRepository).deleteById(id);
        verify(auditService).log(eq(currentUserId), eq("Lead"), eq(id), eq(com.callsagents.backend.audit.entity.AuditAction.DELETE));
    }

    @Test
    void importCsvRejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "leads.csv", "text/csv", new byte[0]);
        assertThrows(BadRequestException.class, () -> leadService.importCsv(empty, currentUserId));
    }

    @Test
    void importCsvRejectsBadHeader() {
        String content = "foo,bar,baz\n1,2,3\n";
        MockMultipartFile file = new MockMultipartFile("file", "leads.csv", "text/csv", content.getBytes());
        assertThrows(BadRequestException.class, () -> leadService.importCsv(file, currentUserId));
    }

    @Test
    void importCsvParsesValidRowsAndCountsErrors() throws IOException {
        String content = "firstName,lastName,email,phone,company,source\n"
            + "Ana,Lopez,ana@x.com,,Acme,MANUAL\n"
            + ",Perez,perez@x.com,,,\n"
            + "Solo,Phone,,+5491155550000,,IMPORT\n";
        MockMultipartFile file = new MockMultipartFile("file", "leads.csv", "text/csv", content.getBytes());
        when(leadRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ImportResultDto result = leadService.importCsv(file, currentUserId);

        assertEquals(3, result.totalRows());
        assertEquals(2, result.successCount());
        assertEquals(1, result.errorCount());
        assertEquals(2, result.errors().get(0).rowNumber());
        verify(leadRepository, times(1)).saveAll(any());
    }

    @Test
    void importCsvSetsCreatedByToCurrentUser() throws IOException {
        String content = "firstName,lastName,email,phone,company,source\n"
            + "Ana,Lopez,ana@x.com,,Acme,MANUAL\n";
        MockMultipartFile file = new MockMultipartFile("file", "leads.csv", "text/csv", content.getBytes());
        when(leadRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        leadService.importCsv(file, currentUserId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Lead>> captor = ArgumentCaptor.forClass(List.class);
        verify(leadRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(currentUserId, captor.getValue().get(0).getCreatedBy());
    }

    @Test
    void importCsvRejectsQuotedValues() {
        String content = "firstName,lastName,email,phone,company,source\n"
            + "\"A\",\"B\",\"a@x.com\",\"\",\"\",\"MANUAL\"\n";
        MockMultipartFile file = new MockMultipartFile("file", "leads.csv", "text/csv", content.getBytes());
        assertThrows(BadRequestException.class, () -> leadService.importCsv(file, currentUserId));
    }

    @Test
    void assigneeDtoPopulatedWhenAssigned() {
        UUID id = UUID.randomUUID();
        Lead lead = sampleLead(id);
        lead.setAssignedTo(assignee.getId());
        when(leadRepository.findById(id)).thenReturn(Optional.of(lead));
        when(userRepository.findById(assignee.getId())).thenReturn(Optional.of(assignee));

        LeadResponse response = leadService.findById(id, currentUserId);

        assertNotNull(response.assignedTo());
        assertEquals(assignee.getEmail(), response.assignedTo().email());
    }

    @Test
    void assigneeDtoIsNullWhenUnassigned() {
        UUID id = UUID.randomUUID();
        Lead lead = sampleLead(id);
        lead.setAssignedTo(null);
        when(leadRepository.findById(id)).thenReturn(Optional.of(lead));

        LeadResponse response = leadService.findById(id, currentUserId);

        assertNull(response.assignedTo());
    }

    @Test
    void deleteByIdIsCalledWithCorrectArgument() {
        UUID id = UUID.randomUUID();
        Lead lead = sampleLead(id);
        when(leadRepository.findById(id)).thenReturn(Optional.of(lead));

        leadService.delete(id, currentUserId, UserRole.ADMIN);

        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(leadRepository).deleteById(captor.capture());
        assertEquals(id, captor.getValue());
        verify(auditService).log(eq(currentUserId), eq("Lead"), eq(id), eq(com.callsagents.backend.audit.entity.AuditAction.DELETE));
    }

    @Test
    void createRejectsInvalidSource() {
        CreateLeadRequest req = new CreateLeadRequest("A", "B", "a@x.com", null, null, "INVALID_SOURCE", null, null);
        assertThrows(BadRequestException.class, () -> leadService.create(req, currentUserId));
    }

    @Test
    void updateClearsEmailWhenEmptyStringProvided() {
        UUID id = UUID.randomUUID();
        Lead existing = sampleLead(id);
        existing.setEmail("x@y.com");
        when(leadRepository.findById(id)).thenReturn(Optional.of(existing));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateLeadRequest req = new UpdateLeadRequest(null, null, "", null, null, null, null, null, null, null);

        LeadResponse response = leadService.update(id, req, currentUserId);

        assertNull(response.email());
        assertNotNull(response.phone());
    }

    @Test
    void createSetsDefaults() {
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> {
            Lead arg = inv.getArgument(0);
            arg.setId(UUID.randomUUID());
            arg.setCreatedAt(Instant.now());
            arg.setUpdatedAt(Instant.now());
            return arg;
        });

        CreateLeadRequest req = new CreateLeadRequest("Ana", "Lopez", "ana@x.com", null, null, "MANUAL", null, null);

        LeadResponse response = leadService.create(req, currentUserId);

        assertEquals(LeadStatus.NEW, response.status());
        assertFalse(response.doNotCall());
        assertNull(response.assignedTo());
        assertTrue(response.customFields() == null);
    }

    private Lead sampleLead(UUID id) {
        return Lead.builder()
            .id(id)
            .createdBy(currentUserId)
            .firstName("Test")
            .lastName("Lead")
            .email("test@x.com")
            .phone("+5491100000000")
            .company("Acme")
            .status(LeadStatus.NEW)
            .source(LeadSource.MANUAL)
            .doNotCall(Boolean.FALSE)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
}