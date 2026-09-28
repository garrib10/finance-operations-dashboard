package dev.portfolio.finance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import dev.portfolio.finance.entity.RefreshSession;
import dev.portfolio.finance.entity.RefreshToken;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.security.IssuedRefreshToken;
import dev.portfolio.finance.security.RefreshTokenGenerator;
import dev.portfolio.finance.support.MutableClock;
import dev.portfolio.finance.support.TestDataFactory;
import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenRepositoryTest {

    private static final Instant START = Instant.parse("2026-09-27T12:00:00Z");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RefreshTokenRepository tokenRepository;

    @Autowired
    private RefreshSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    private final RefreshTokenGenerator generator = new RefreshTokenGenerator();
    private final MutableClock clock = new MutableClock(START);
    private RefreshSession session;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(TestDataFactory.createUser());
        session = sessionRepository.saveAndFlush(RefreshSession.start(user, clock, Duration.ofDays(30)));
    }

    private RefreshToken persist(byte[] hash) {
        RefreshToken token = tokenRepository.saveAndFlush(RefreshToken.issue(session, hash, clock));
        clock.advance(Duration.ofMinutes(5));
        return token;
    }

    @Test
    void persistsOnlyHashAndFindsExactToken() {
        IssuedRefreshToken first = generator.generate();
        IssuedRefreshToken second = generator.generate();
        RefreshToken saved = persist(first.tokenHash());
        persist(second.tokenHash());
        entityManager.clear();

        RefreshToken found = tokenRepository
                .findByTokenHash(generator.hashPresentedToken(first.rawToken())).orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getTokenHash()).isEqualTo(first.tokenHash());
        assertThat(found.getSession().getId()).isEqualTo(session.getId());
        assertThat(found.getCreatedAt()).isEqualTo(START);

        byte[] nearMiss = first.tokenHash();
        nearMiss[31] ^= 1;
        assertThat(tokenRepository.findByTokenHash(nearMiss)).isEmpty();
        assertThat(tokenRepository.findByTokenHash(generator.generate().tokenHash())).isEmpty();

        Object rawColumn = entityManager.createNativeQuery(
                "SELECT token_hash FROM refresh_tokens WHERE id = :id")
                .setParameter("id", saved.getId()).getSingleResult();
        assertThat(rawColumn).isInstanceOf(byte[].class);
        assertThat((byte[]) rawColumn).hasSize(32).isEqualTo(first.tokenHash());
        assertThat(new String((byte[]) rawColumn, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain(first.rawToken());
    }

    @Test
    void sessionRetainsOrderedHistoryOfMultipleTokens() {
        RefreshToken first = persist(generator.generate().tokenHash());
        RefreshToken second = persist(generator.generate().tokenHash());
        RefreshToken third = persist(generator.generate().tokenHash());
        entityManager.clear();

        assertThat(tokenRepository.findBySession_IdOrderByIdAsc(session.getId()))
                .extracting(RefreshToken::getId)
                .containsExactly(first.getId(), second.getId(), third.getId());
        assertThat(tokenRepository.findBySession_IdOrderByIdAsc("00000000-0000-4000-8000-000000000000"))
                .isEmpty();
    }

    @Test
    void duplicateHashIsRejectedByDatabaseConstraint() {
        byte[] hash = generator.generate().tokenHash();
        persist(hash);

        assertThatThrownBy(() -> persist(hash)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void consumedTokenRemainsFindableForReuseDetection() {
        IssuedRefreshToken issued = generator.generate();
        RefreshToken token = persist(issued.tokenHash());
        token.markConsumed(clock);
        tokenRepository.saveAndFlush(token);
        entityManager.clear();

        RefreshToken loaded = tokenRepository.findByTokenHash(issued.tokenHash()).orElseThrow();
        assertThat(loaded.isConsumed()).isTrue();
        assertThat(loaded.getConsumedAt()).isEqualTo(START.plus(Duration.ofMinutes(5)));
        assertThatThrownBy(() -> loaded.markConsumed(clock)).isInstanceOf(IllegalStateException.class);
    }
}
