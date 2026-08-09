package com.callsagents.backend.voice.domain;

import com.callsagents.backend.appointments.entity.Appointment;
import com.callsagents.backend.leads.entity.Lead;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks a single voice call attempt through a provider (Vapi, Retell) OR
 * a manually-logged call with no provider.
 *
 * For provider calls, the lifecycle is driven by webhooks — the provider
 * pushes status updates to /api/voice/webhook/{provider} and we update
 * the row.
 *
 * For manual calls, the user logs the result via the UI without ever
 * placing an outbound call.
 */
@Entity
@Table(name = "voice_calls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceCall {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 32)
    private VoiceProviderType provider;

    @Column(name = "provider_call_id", length = 255)
    private String providerCallId;

    @Column(name = "phone_number", nullable = false, length = 32)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private VoiceCallStatus status;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "cost_usd", precision = 10, scale = 4)
    private BigDecimal costUsd;

    @Column(name = "transcript", columnDefinition = "text")
    private String transcript;

    @Column(name = "recording_url", length = 512)
    private String recordingUrl;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @org.hibernate.annotations.JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = VoiceCallStatus.SCHEDULED;
        if (this.direction == null) this.direction = "OUTBOUND";
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
