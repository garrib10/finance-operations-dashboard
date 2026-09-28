package dev.portfolio.finance.entity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * One refresh-session family (one login on one device). Times come only from the
 * supplied {@link Clock}; Instants are persisted as UTC DATETIME(6) values.
 * Never serialize this entity or include it in responses.
 */
@Entity
@Table(name = "refresh_sessions")
@JsonAutoDetect(getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE,
        fieldVisibility = Visibility.NONE)
public class RefreshSession implements Persistable<String> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "revocation_reason", length = 24)
    private RefreshSessionRevocationReason revocationReason;

    /** Assigned IDs would otherwise make Spring Data merge instead of persist. */
    @Transient
    private boolean newEntity = true;

    protected RefreshSession() {
    }

    private RefreshSession(User user, Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** Starts a family whose absolute expiration never moves. */
    public static RefreshSession start(User user, Clock clock, Duration absoluteLifetime) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(absoluteLifetime, "absoluteLifetime");
        Instant createdAt = now(clock);
        Instant expiresAt = createdAt.plus(absoluteLifetime).truncatedTo(ChronoUnit.MICROS);
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Refresh session expiration must follow creation");
        }
        return new RefreshSession(user, createdAt, expiresAt);
    }

    /** Revocation is final; the original time and reason are never overwritten. */
    public void revoke(RefreshSessionRevocationReason reason, Clock clock) {
        Objects.requireNonNull(reason, "reason");
        Instant at = now(clock);
        if (revokedAt != null) {
            throw new IllegalStateException("Refresh session is already revoked");
        }
        if (at.isBefore(createdAt)) {
            throw new IllegalArgumentException("Refresh session revocation cannot precede creation");
        }
        revokedAt = at;
        revocationReason = reason;
    }

    /** Active means neither revoked nor at/after its absolute expiration. */
    public boolean isActiveAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return revokedAt == null && instant.isBefore(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    static Instant now(Clock clock) {
        return Objects.requireNonNull(clock, "clock").instant().truncatedTo(ChronoUnit.MICROS);
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        newEntity = false;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public User getUser() {
        return user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public RefreshSessionRevocationReason getRevocationReason() {
        return revocationReason;
    }

    @Override
    public String toString() {
        return "RefreshSession[details redacted]";
    }
}
