package com.callsagents.backend.leads.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record UpdateLeadRequest(
    @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @Size(max = 255) String email,
    @Size(max = 32) String phone,
    @Size(max = 255) String company,
    String status,
    String source,
    UUID assignedToId,
    @Size(max = 4096) String notes,
    Map<String, Object> customFields
) {
    @AssertTrue(message = "At least one field must be provided for update")
    public boolean isAnyFieldPresent() {
        return firstName != null
            || lastName != null
            || email != null
            || phone != null
            || company != null
            || status != null
            || source != null
            || assignedToId != null
            || notes != null
            || customFields != null;
    }
}
