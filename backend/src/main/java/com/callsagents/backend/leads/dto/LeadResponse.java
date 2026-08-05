package com.callsagents.backend.leads.dto;

import com.callsagents.backend.auth.dto.UserDto;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.entity.LeadSource;
import com.callsagents.backend.leads.entity.LeadStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record LeadResponse(
    UUID id,
    String firstName,
    String lastName,
    String email,
    String phone,
    String company,
    LeadStatus status,
    LeadSource source,
    UserDto assignedTo,
    String notes,
    Map<String, Object> customFields,
    Instant consentAt,
    Boolean doNotCall,
    LocalDate dataRetentionUntil,
    Instant createdAt,
    Instant updatedAt
) {
    public static LeadResponse fromEntity(Lead lead, UserDto assignee) {
        return new LeadResponse(
            lead.getId(),
            lead.getFirstName(),
            lead.getLastName(),
            lead.getEmail(),
            lead.getPhone(),
            lead.getCompany(),
            lead.getStatus(),
            lead.getSource(),
            assignee,
            lead.getNotes(),
            lead.getCustomFields(),
            lead.getConsentAt(),
            lead.getDoNotCall(),
            lead.getDataRetentionUntil(),
            lead.getCreatedAt(),
            lead.getUpdatedAt()
        );
    }
}
