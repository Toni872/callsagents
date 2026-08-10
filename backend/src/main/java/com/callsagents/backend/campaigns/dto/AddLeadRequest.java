package com.callsagents.backend.campaigns.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddLeadRequest(
    @NotNull UUID leadId,
    String status,
    UUID assignedToId
) {
}