package com.callsagents.backend.voice.controller;

import com.callsagents.backend.voice.domain.VoiceCallStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Allowed fields when an authenticated user manually logs a call.
 *
 * <p>This is a strict DTO, NOT the {@code VoiceCall} entity: the caller can
 * only supply call metadata they own. {@code userId} is always bound
 * server-side from the authenticated principal; cost, transcript, recording
 * URL and provider fields are written exclusively by provider webhooks.
 */
public record LogCallRequest(
    @Pattern(regexp = "^\\+?[0-9\\s()\\-]{6,20}$", message = "Invalid phone number")
    @Size(max = 32) String phoneNumber,
    @Size(max = 16) String direction,
    VoiceCallStatus status,
    @Min(0) @Max(86400) Integer durationSeconds
) {
}