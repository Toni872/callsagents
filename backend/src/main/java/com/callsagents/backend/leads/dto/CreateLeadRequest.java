package com.callsagents.backend.leads.dto;

import com.callsagents.backend.leads.entity.LeadSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateLeadRequest(
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @Size(max = 255) String email,
    @Size(max = 32) String phone,
    @Size(max = 255) String company,
    @NotBlank String source,
    @Size(max = 4096) String notes,
    Map<String, Object> customFields
) {
    public LeadSource sourceAsEnum() {
        return LeadSource.valueOf(source.toUpperCase());
    }
}
