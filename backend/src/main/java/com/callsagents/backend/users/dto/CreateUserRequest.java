package com.callsagents.backend.users.dto;

import com.callsagents.backend.auth.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for POST /api/users — admin creating a new user.
 *
 * Email/password are validated; role is required (no default — admin must
 * explicitly choose the role for the new account).
 */
public record CreateUserRequest(
    @NotBlank
    @Email
    @Size(max = 255)
    String email,

    @NotBlank
    @Size(min = 8, max = 100)
    String password,

    @NotBlank
    @Size(max = 255)
    String fullName,

    @NotNull
    UserRole role
) {}
