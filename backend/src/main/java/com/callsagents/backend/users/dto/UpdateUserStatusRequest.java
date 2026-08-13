package com.callsagents.backend.users.dto;

import com.callsagents.backend.auth.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for PATCH /api/users/{id}/status — admin toggling an account.
 */
public record UpdateUserStatusRequest(
    @NotNull UserStatus status
) {}
