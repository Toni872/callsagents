package com.callsagents.backend.voice.controller;

import com.callsagents.backend.voice.domain.VoiceCall;
import com.callsagents.backend.voice.domain.VoiceCallStatus;
import com.callsagents.backend.voice.domain.VoiceProviderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Read DTO for voice call entities. NEVER includes provider API keys or
 * sensitive tokens (we don't store any).
 */
public record VoiceCallDto(
    UUID id,
    UUID leadId,
    UUID appointmentId,
    UUID userId,
    VoiceProviderType provider,
    String providerCallId,
    String phoneNumber,
    VoiceCallStatus status,
    String direction,
    Instant startedAt,
    Instant endedAt,
    Integer durationSeconds,
    BigDecimal costUsd,
    String transcript,
    String recordingUrl,
    String errorMessage,
    Map<String, Object> metadata,
    Instant createdAt
) {
    public static VoiceCallDto from(VoiceCall c) {
        return new VoiceCallDto(
            c.getId(),
            c.getLead() != null ? c.getLead().getId() : null,
            c.getAppointment() != null ? c.getAppointment().getId() : null,
            c.getUserId(),
            c.getProvider(),
            c.getProviderCallId(),
            c.getPhoneNumber(),
            c.getStatus(),
            c.getDirection(),
            c.getStartedAt(),
            c.getEndedAt(),
            c.getDurationSeconds(),
            c.getCostUsd(),
            c.getTranscript(),
            c.getRecordingUrl(),
            c.getErrorMessage(),
            c.getMetadata(),
            c.getCreatedAt()
        );
    }
}
