package com.callsagents.backend.leads.dto;

import com.callsagents.backend.leads.entity.LeadSource;
import com.callsagents.backend.leads.entity.LeadStatus;

import java.util.UUID;

public record LeadFilter(
    LeadStatus status,
    LeadSource source,
    UUID assignedToId,
    String search
) {
}
