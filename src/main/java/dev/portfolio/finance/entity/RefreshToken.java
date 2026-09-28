package dev.portfolio.finance.entity;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

/**
 * One issued refresh token in a session family. Only the 32-byte SHA-256 digest is
 * stored; the raw or encoded token never reaches this entity. Consumed rows are
 * retained so later phases can detect reuse. Never serialize this entity.
 */
@Entity
@Table(name = "refresh_tokens")
@JsonAutoDetect(getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE,
        fieldVisibility = Visibility.NONE)
public class RefreshToken {

    public static final int HASH_LENGTH = 32;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RefreshSession session;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "token_hash", nullable = false, updatable = false, unique = true, length = HASH_LENGTH)
    private byte[] tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected RefreshToken() {
    }

    private RefreshToken(RefreshSession session, byte[] tokenHash, Instant createdAt) {
        this.session = session;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
    }

    /** Records a new token for a session that is active at the clock's current time. */
    public static RefreshToken issue(RefreshSession session, byte[] tokenHash, Clock clock) {
        Objects.requireNonNull(session, "session");
        byte[] hash = copyHash(tokenHash);
        Instant createdAt = RefreshSession.now(clock);
        if (!session.isActiveAt(createdAt) || createdAt.isBefore(session.getCreatedAt())) {
            throw new IllegalStateException("Refresh tokens require an active refresh session");
        }
        return new RefreshToken(session, hash, createdAt);
    }

    /** Consumption happens once; a second attempt is a reuse signal, never a silent no-op. */
    public void markConsumed(Clock clock) {
        Instant at = RefreshSession.now(clock);
        if (consumedAt != null) {
            throw new IllegalStateException("Refresh token is already consumed");
        }
        if (at.isBefore(createdAt)) {
            throw new IllegalArgumentException("Refresh token consumption cannot precede creation");
        }
        consumedAt = at;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    private static byte[] copyHash(byte[] tokenHash) {
        if (tokenHash == null || tokenHash.length != HASH_LENGTH) {
            throw new IllegalArgumentException("Refresh token hash must be exactly 32 bytes");
        }
        return tokenHash.clone();
    }

    public Long getId() {
        return id;
    }

    public RefreshSession getSession() {
        return session;
    }

    public byte[] getTokenHash() {
        return tokenHash.clone();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    @Override
    public String toString() {
        return "RefreshToken[details redacted]";
    }
}
