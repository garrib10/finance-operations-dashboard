package dev.portfolio.finance.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.portfolio.finance.entity.RefreshToken;

/** Lookups use only the 32-byte SHA-256 digest. Service-layer only. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Returns consumed tokens too, so later phases can detect reuse. */
    Optional<RefreshToken> findByTokenHash(byte[] tokenHash);

    List<RefreshToken> findBySession_IdOrderByIdAsc(String sessionId);
}
