package com.callsagents.backend.business.dto;

public record WidgetConfigResponse(
    String botName,
    String greeting,
    String chatColor,
    String companyName
) {
}
