package com.callsagents.backend.users.dto;

import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.auth.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * User representation for GET /api/users responses.
 * Includes status so admin can see who is active vs disabled.
 */
public record UserListItem(
    UUID id,
    String email,
    String fullName,
    UserRole role,
    UserStatus status,
    Instant lastLoginAt,
    Instant createdAt
) {}
