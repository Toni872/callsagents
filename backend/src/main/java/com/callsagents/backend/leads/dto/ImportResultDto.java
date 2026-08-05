package com.callsagents.backend.leads.dto;

import java.util.List;

public record ImportResultDto(
    int totalRows,
    int successCount,
    int errorCount,
    List<ImportErrorDto> errors
) {
    public record ImportErrorDto(
        int rowNumber,
        String message
    ) {
    }
}
