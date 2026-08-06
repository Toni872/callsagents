package com.callsagents.backend.calendar.domain;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.calendar.domain.CalendarProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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

import java.time.Instant;
import java.util.UUID;

/**
 * Represents the link between a Callsagents user and an external calendar
 * provider (Google Calendar, Outlook). Stores OAuth tokens encrypted.
 *
 * One row per (user_id, provider). When the user disconnects, the row is
 * deleted (we don't keep a tombstone for security — tokens are gone).
 */
@Entity
@Table(name = "calendar_integrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarIntegration {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** The Callsagents user who connected the calendar. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Provider enum value, persisted as STRING. */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private CalendarProviderType provider;

    /** Which calendar to use (Google 'primary' usually, or any calendar id). */
    @Column(name = "external_calendar_id", length = 255)
    private String externalCalendarId;

    /** Email of the connected Google/Microsoft account (for display). */
    @Column(name = "external_account_email", length = 255)
    private String externalAccountEmail;

    /**
     * AES-GCM ciphertext of the OAuth access token. Encoded as base64.
     * Decryption uses the master key from ENCRYPTION_KEY (AES-256).
     */
    @Column(name = "access_token_encrypted", nullable = false, length = 2048)
    private String accessTokenEncrypted;

    /** Optional — some OAuth flows don't issue a refresh token (e.g. service accounts). */
    @Column(name = "refresh_token_encrypted", length = 2048)
    private String refreshTokenEncrypted;

    /** When the access token expires. After this we use refresh_token to get a new one. */
    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    /** Space-separated scopes that the user authorized (for audit). */
    @Column(name = "scopes", length = 1024)
    private String scopes;

    /** Toggle: user can disable integration without disconnecting. */
    @Column(name = "sync_enabled", nullable = false)
    private Boolean syncEnabled;

    /** Last sync attempt metadata (for troubleshooting). */
    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "last_sync_status")
    private CalendarSyncStatus lastSyncStatus;

    @Column(name = "last_sync_error", columnDefinition = "text")
    private String lastSyncError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optional association — not strictly needed at runtime, used for joins. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.id == null) this.id = UUID.randomUUID();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.syncEnabled == null) this.syncEnabled = true;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
