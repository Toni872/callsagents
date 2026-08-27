package com.callsagents.backend.business.entity;

import com.callsagents.backend.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessProfile {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "website", length = 500)
    private String website;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "services", columnDefinition = "text")
    private String services;

    @Column(name = "tone", length = 20)
    @Builder.Default
    private String tone = "professional";

    @Column(name = "bot_name", length = 100)
    @Builder.Default
    private String botName = "Naiara";

    @Column(name = "greeting", columnDefinition = "text")
    private String greeting;

    @Column(name = "chat_color", length = 7)
    @Builder.Default
    private String chatColor = "#25D366";

    @Column(name = "whatsapp_number", length = 20)
    private String whatsappNumber;

    @Column(name = "escalation_enabled", nullable = false)
    @Builder.Default
    private Boolean escalationEnabled = true;

    @Column(name = "reply_timeout_minutes", nullable = false)
    @Builder.Default
    private Integer replyTimeoutMinutes = 30;

    @Column(name = "followup_message", columnDefinition = "text")
    private String followupMessage;

    @Column(name = "voice_agent_id", length = 100)
    private String voiceAgentId;

    @Column(name = "onboarding_complete", nullable = false)
    @Builder.Default
    private Boolean onboardingComplete = false;

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
