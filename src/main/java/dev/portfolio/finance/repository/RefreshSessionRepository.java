package dev.portfolio.finance.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.portfolio.finance.entity.RefreshSession;

/** Service-layer only; never inject into controllers. */
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, String> {

    List<RefreshSession> findByUser_IdOrderByCreatedAtAsc(Long userId);

    /** Ownership-scoped lookup for current-family revocation. */
    Optional<RefreshSession> findByIdAndUser_Id(String id, Long userId);

    /** Unrevoked families for a user, used by future revoke-all behavior. */
    List<RefreshSession> findByUser_IdAndRevokedAtIsNull(Long userId);

    /** Families whose absolute expiration precedes the cutoff, for future cleanup. */
    List<RefreshSession> findByExpiresAtBefore(Instant cutoff);
}
