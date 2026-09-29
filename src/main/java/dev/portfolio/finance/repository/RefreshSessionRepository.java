package dev.portfolio.finance.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import dev.portfolio.finance.entity.RefreshSession;

/** Service-layer only; never inject into controllers. */
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, String> {

    List<RefreshSession> findByUser_IdOrderByCreatedAtAsc(Long userId);

    /** Ownership-scoped lookup for current-family revocation. */
    Optional<RefreshSession> findByIdAndUser_Id(String id, Long userId);

    /** Unrevoked families for a user; revoke-all loads these under the user-row lock. */
    List<RefreshSession> findByUser_IdAndRevokedAtIsNull(Long userId);

    /**
     * Unrevoked families, locked. Must be a locking read: under InnoDB REPEATABLE READ a
     * plain SELECT can return the transaction's earlier snapshot and miss a family
     * committed by a login that held the user lock just before this transaction got it.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s where s.user.id = :userId and s.revokedAt is null")
    List<RefreshSession> findUnrevokedByUserIdForUpdate(@Param("userId") Long userId);

    /** Families whose absolute expiration precedes the cutoff. */
    List<RefreshSession> findByExpiresAtBefore(Instant cutoff);

    /** IDs only, oldest first, served by idx_refresh_sessions_expires_at; bounded by the page size. */
    @Query("select s.id from RefreshSession s where s.expiresAt < :cutoff order by s.expiresAt asc")
    List<String> findIdsExpiredBefore(@Param("cutoff") Instant cutoff, Pageable page);

    /**
     * Bulk delete without loading entity graphs. Token history is removed by the
     * database's ON DELETE CASCADE. The cutoff is re-checked so a concurrent run
     * can only delete eligible rows, and deleting already-deleted IDs is a no-op.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshSession s where s.id in :ids and s.expiresAt < :cutoff")
    int deleteExpiredByIds(@Param("ids") Collection<String> ids, @Param("cutoff") Instant cutoff);
}
