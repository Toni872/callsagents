package com.callsagents.backend.campaigns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Link table between Campaign and Lead — adds metadata (status, attempts, agent assignment).
 *
 * Note: V1__initial_schema.sql declared created_at/updated_at as NOT NULL on this
 * table, but the entity didn't model those columns. V3__campaign_leads_audit_timestamps.sql
 * makes those columns nullable/defaulted. The @PrePersist hook below ensures JPA
 * inserts set them.
 */
@Entity
@Table(name = "campaign_leads")
@IdClass(CampaignLeadId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignLead {

    @Id
    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Id
    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private CampaignLeadStatus status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
