package dev.portfolio.finance.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import dev.portfolio.finance.entity.RefreshToken;
import jakarta.persistence.LockModeType;

/** Lookups use only the 32-byte SHA-256 digest. Service-layer only. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Returns consumed tokens too, so later phases can detect reuse. */
    Optional<RefreshToken> findByTokenHash(byte[] tokenHash);

    List<RefreshToken> findBySession_IdOrderByIdAsc(String sessionId);

    /** Owner only, read before the rotation transaction to choose the user-row lock. */
    @Query("select s.user.id from RefreshToken t join t.session s where t.tokenHash = :hash")
    Optional<Long> findOwnerUserIdByTokenHash(@Param("hash") byte[] tokenHash);

    /** Reloads the token and its family under a write lock, after the user row is locked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t join fetch t.session where t.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") byte[] tokenHash);
}
