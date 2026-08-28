package com.callsagents.backend.leads.service;

import com.callsagents.backend.audit.entity.AuditAction;
import com.callsagents.backend.auth.dto.UserDto;
import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.entity.UserRole;
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
import com.callsagents.backend.leads.repository.LeadSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class LeadService {

    private static final Logger log = LoggerFactory.getLogger(LeadService.class);
    private static final int CSV_HEADER_EXPECTED = 6;
    private static final String[] CSV_HEADER = {"firstName", "lastName", "email", "phone", "company", "source"};
    private static final int TRIAL_LEAD_LIMIT = 50;

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public LeadService(LeadRepository leadRepository, UserRepository userRepository, AuditService auditService) {
        this.leadRepository = leadRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<LeadResponse> findAll(LeadFilter filter, Pageable pageable, UUID currentUserId) {
        Specification<Lead> spec = LeadSpecifications.build(filter);
        spec = (spec == null)
            ? LeadSpecifications.ownedBy(currentUserId)
            : spec.and(LeadSpecifications.ownedBy(currentUserId));
        Page<Lead> page = leadRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public LeadResponse findById(UUID id, UUID currentUserId) {
        Lead lead = leadRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Lead not found: " + id));
        if (lead.getCreatedBy() == null || !lead.getCreatedBy().equals(currentUserId)) {
            throw new ResourceNotFoundException("Lead not found: " + id);
        }
        return toResponse(lead);
    }

    @Transactional
    public LeadResponse create(CreateLeadRequest req, UUID currentUserId) {
        // Trial lead limit check (per tenant)
        long totalLeads = leadRepository.countByCreatedBy(currentUserId);
        if (totalLeads >= TRIAL_LEAD_LIMIT) {
            throw new BadRequestException(
                "Límite de leads alcanzado (" + TRIAL_LEAD_LIMIT + "). " +
                "Contacta soporte para ampliar tu plan."
            );
        }

        validateContact(req.email(), req.phone());
        LeadSource source = parseSource(req.source());

        Lead lead = Lead.builder()
            .firstName(req.firstName().trim())
            .lastName(req.lastName().trim())
            .email(blankToNull(req.email()))
            .phone(blankToNull(req.phone()))
            .company(blankToNull(req.company()))
            .status(LeadStatus.NEW)
            .source(source)
            .assignedTo(null)
            .createdBy(currentUserId)
            .notes(req.notes())
            .customFields(req.customFields())
            .doNotCall(Boolean.FALSE)
            .build();

        Lead saved = leadRepository.save(lead);
        auditService.log(currentUserId, "Lead", saved.getId(), AuditAction.CREATE);
        return toResponse(saved);
    }

    @Transactional
    public LeadResponse update(UUID id, UpdateLeadRequest req, UUID currentUserId) {
        Lead lead = leadRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Lead not found: " + id));

        if (lead.getCreatedBy() == null || !lead.getCreatedBy().equals(currentUserId)) {
            throw new ForbiddenException("You can only update your own leads");
        }

        if (req.firstName() != null) lead.setFirstName(req.firstName().trim());
        if (req.lastName() != null) lead.setLastName(req.lastName().trim());
        if (req.email() != null) lead.setEmail(blankToNull(req.email()));
        if (req.phone() != null) lead.setPhone(blankToNull(req.phone()));
        if (req.company() != null) lead.setCompany(blankToNull(req.company()));
        if (req.status() != null) lead.setStatus(LeadStatus.valueOf(req.status().toUpperCase()));
        if (req.source() != null) lead.setSource(parseSource(req.source()));
        if (req.notes() != null) lead.setNotes(req.notes());
        if (req.customFields() != null) lead.setCustomFields(req.customFields());
        if (req.assignedToId() != null) {
            if (!userRepository.existsById(req.assignedToId())) {
                throw new BadRequestException("Assigned user does not exist: " + req.assignedToId());
            }
            lead.setAssignedTo(req.assignedToId());
        }

        validateContact(lead.getEmail(), lead.getPhone());
        Lead saved = leadRepository.save(lead);
        auditService.log(currentUserId, "Lead", saved.getId(), AuditAction.UPDATE);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id, UUID currentUserId, UserRole role) {
        Lead lead = leadRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Lead not found: " + id));
        if ((lead.getCreatedBy() == null || !lead.getCreatedBy().equals(currentUserId))
            && role != UserRole.ADMIN) {
            throw new ForbiddenException("You can only delete your own leads");
        }
        leadRepository.deleteById(id);
        auditService.log(currentUserId, "Lead", id, AuditAction.DELETE);
    }

    @Transactional
    public ImportResultDto importCsv(MultipartFile file, UUID currentUserId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty");
        }
        if (file.getSize() > 5L * 1024L * 1024L) {
            throw new BadRequestException("CSV file exceeds 5MB limit");
        }

        List<ImportResultDto.ImportErrorDto> errors = new ArrayList<>();
        List<Lead> toSave = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new BadRequestException("CSV file is empty");
            }
            String[] headerCols = splitCsvLine(header);
            if (!validateHeader(headerCols)) {
                throw new BadRequestException("CSV header must be: " + String.join(",", CSV_HEADER));
            }

            String line;
            int rowNumber = 0;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) {
                    continue;
                }
                totalRows++;
                String[] cols = splitCsvLine(line);
                if (cols.length != CSV_HEADER_EXPECTED) {
                    errors.add(new ImportResultDto.ImportErrorDto(rowNumber,
                        "Expected " + CSV_HEADER_EXPECTED + " columns, got " + cols.length));
                    continue;
                }
                try {
                    String firstName = cols[0].trim();
                    String lastName = cols[1].trim();
                    String email = blankToNull(cols[2]);
                    String phone = blankToNull(cols[3]);
                    String company = blankToNull(cols[4]);
                    String sourceRaw = cols[5].trim();

                    if (firstName.isEmpty() || lastName.isEmpty()) {
                        throw new BadRequestException("firstName and lastName are required");
                    }
                    validateContact(email, phone);

                    Lead lead = Lead.builder()
                        .firstName(firstName)
                        .lastName(lastName)
                        .email(email)
                        .phone(phone)
                        .company(company)
                        .source(parseSource(sourceRaw))
                        .status(LeadStatus.NEW)
                        .doNotCall(Boolean.FALSE)
                        .createdBy(currentUserId)
                        .build();
                    toSave.add(lead);
                    successCount++;
                } catch (BadRequestException ex) {
                    errors.add(new ImportResultDto.ImportErrorDto(rowNumber, ex.getMessage()));
                } catch (Exception ex) {
                    errors.add(new ImportResultDto.ImportErrorDto(rowNumber, "Invalid row: " + ex.getMessage()));
                }
            }
        } catch (IOException ex) {
            throw new BadRequestException("Failed to read CSV file: " + ex.getMessage());
        }

        if (!toSave.isEmpty()) {
            leadRepository.saveAll(toSave);
            auditService.log(currentUserId, "Lead", null, AuditAction.CREATE,
                "{\"imported\":" + successCount + "}");
        }

        int errorCount = errors.size();
        log.info("CSV import completed: total={} success={} errors={}", totalRows, successCount, errorCount);
        return new ImportResultDto(totalRows, successCount, errorCount, errors);
    }

    private LeadResponse toResponse(Lead lead) {
        UserDto assignee = null;
        if (lead.getAssignedTo() != null) {
            assignee = userRepository.findById(lead.getAssignedTo())
                .map(this::toUserDto)
                .orElse(null);
        }
        return LeadResponse.fromEntity(lead, assignee);
    }

    private UserDto toUserDto(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(), user.getTrialEndsAt());
    }

    private static void validateContact(String email, String phone) {
        if ((email == null || email.isBlank()) && (phone == null || phone.isBlank())) {
            throw new BadRequestException("Either email or phone must be provided");
        }
    }

    private static LeadSource parseSource(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("source is required");
        }
        try {
            return LeadSource.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid source: " + raw + ". Allowed: "
                + java.util.Arrays.toString(LeadSource.values()));
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static boolean validateHeader(String[] cols) {
        if (cols == null || cols.length != CSV_HEADER_EXPECTED) return false;
        for (int i = 0; i < CSV_HEADER_EXPECTED; i++) {
            if (!cols[i].trim().equalsIgnoreCase(CSV_HEADER[i])) {
                return false;
            }
        }
        return true;
    }

    private static String[] splitCsvLine(String line) {
        if (line.contains("\"")) {
            throw new BadRequestException("Quoted CSV values are not supported in MVP");
        }
        return line.split(",", -1);
    }

    @SuppressWarnings("unused")
    private Map<String, Object> snapshotForAudit(Lead lead) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("firstName", lead.getFirstName());
        snapshot.put("lastName", lead.getLastName());
        snapshot.put("status", lead.getStatus());
        Optional.ofNullable(lead.getUpdatedAt()).ifPresent(t -> snapshot.put("updatedAt", t.toString()));
        snapshot.put("capturedAt", Instant.now().toString());
        return snapshot;
    }
}
