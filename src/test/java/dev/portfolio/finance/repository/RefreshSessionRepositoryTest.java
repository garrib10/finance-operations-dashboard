package dev.portfolio.finance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import dev.portfolio.finance.entity.DateFormatPreference;
import dev.portfolio.finance.entity.RefreshSession;
import dev.portfolio.finance.entity.RefreshSessionRevocationReason;
import dev.portfolio.finance.entity.RefreshToken;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.support.MutableClock;
import dev.portfolio.finance.support.ProfilePhotoTestSupport;
import dev.portfolio.finance.support.TestDataFactory;
import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshSessionRepositoryTest {

    private static final Instant START = Instant.parse("2026-09-27T12:00:00.123456Z");
    private static final Duration TTL = Duration.ofDays(30);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RefreshSessionRepository sessionRepository;

    @Autowired
    private RefreshTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    private final MutableClock clock = new MutableClock(START);
    private User owner;
    private User stranger;

    @BeforeEach
    void setUp() {
        owner = TestDataFactory.createUser("Owner", "User", "owner@example.com", "owner-hash");
        owner.updatePreferences(DateFormatPreference.ISO, 25);
        owner.changeProfilePhotoKey(ProfilePhotoTestSupport.KEY);
        userRepository.save(owner);
        stranger = userRepository.save(TestDataFactory.createUser("Other", "User", "other@example.com"));
        entityManager.flush();
    }

    private RefreshSession persistSession(User user) {
        RefreshSession session = sessionRepository.saveAndFlush(RefreshSession.start(user, clock, TTL));
        clock.advance(Duration.ofSeconds(1));
        return session;
    }

    @Test
    void persistsSessionForOwnerWithExactUtcTimes() {
        RefreshSession saved = persistSession(owner);
        assertThat(saved.isNew()).isFalse();
        entityManager.clear();

        RefreshSession loaded = sessionRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.isNew()).isFalse();
        assertThat(loaded.getUser().getId()).isEqualTo(owner.getId());
        assertThat(loaded.getCreatedAt()).isEqualTo(START);
        assertThat(loaded.getExpiresAt()).isEqualTo(START.plus(TTL));
        assertThat(loaded.getRevokedAt()).isNull();
        assertThat(loaded.getRevocationReason()).isNull();
    }

    @Test
    void userCanHoldIndependentSessionFamilies() {
        RefreshSession laptop = persistSession(owner);
        RefreshSession phone = persistSession(owner);
        persistSession(stranger);
        entityManager.clear();

        assertThat(sessionRepository.findByUser_IdOrderByCreatedAtAsc(owner.getId()))
                .extracting(RefreshSession::getId)
                .containsExactly(laptop.getId(), phone.getId());
    }

    @Test
    void revokedSessionRemainsQueryableAndLeavesOtherFamiliesActive() {
        RefreshSession laptop = persistSession(owner);
        RefreshSession phone = persistSession(owner);
        laptop.revoke(RefreshSessionRevocationReason.LOGOUT, clock);
        sessionRepository.saveAndFlush(laptop);
        entityManager.clear();

        RefreshSession loaded = sessionRepository.findById(laptop.getId()).orElseThrow();
        assertThat(loaded.getRevokedAt()).isEqualTo(START.plusSeconds(2));
        assertThat(loaded.getRevocationReason()).isEqualTo(RefreshSessionRevocationReason.LOGOUT);
        assertThat(sessionRepository.findByUser_IdAndRevokedAtIsNull(owner.getId()))
                .extracting(RefreshSession::getId)
                .containsExactly(phone.getId());
        assertThat(sessionRepository.findByUser_IdOrderByCreatedAtAsc(owner.getId())).hasSize(2);
    }

    @Test
    void ownershipLookupCannotBeSubstitutedByAnotherUser() {
        RefreshSession session = persistSession(owner);
        entityManager.clear();

        assertThat(sessionRepository.findByIdAndUser_Id(session.getId(), owner.getId())).isPresent();
        assertThat(sessionRepository.findByIdAndUser_Id(session.getId(), stranger.getId())).isEmpty();
        assertThat(sessionRepository.findByUser_IdOrderByCreatedAtAsc(stranger.getId())).isEmpty();
        assertThat(sessionRepository.findByUser_IdAndRevokedAtIsNull(stranger.getId())).isEmpty();
    }

    @Test
    void ownerCanOnlyBeAssignedAtCreation() throws NoSuchFieldException {
        assertThat(java.util.Arrays.stream(RefreshSession.class.getMethods())
                .filter(method -> java.util.Arrays.asList(method.getParameterTypes()).contains(User.class))
                .map(java.lang.reflect.Method::getName))
                .containsExactly("start");
        assertThat(RefreshSession.class.getDeclaredField("user")
                .getAnnotation(jakarta.persistence.JoinColumn.class).updatable()).isFalse();

        RefreshSession session = persistSession(owner);
        session.revoke(RefreshSessionRevocationReason.LOGOUT, clock);
        sessionRepository.saveAndFlush(session);
        entityManager.clear();

        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getUser().getId())
                .isEqualTo(owner.getId());
    }

    @Test
    void findsSessionsExpiringBeforeCutoff() {
        RefreshSession early = persistSession(owner);
        clock.advance(Duration.ofDays(2));
        RefreshSession late = persistSession(stranger);

        assertThat(sessionRepository.findByExpiresAtBefore(early.getExpiresAt())).isEmpty();
        assertThat(sessionRepository.findByExpiresAtBefore(early.getExpiresAt().plusNanos(1000)))
                .extracting(RefreshSession::getId).containsExactly(early.getId());
        assertThat(sessionRepository.findByExpiresAtBefore(late.getExpiresAt().plusSeconds(1)))
                .extracting(RefreshSession::getId).containsExactlyInAnyOrder(early.getId(), late.getId());
    }

    @Test
    void deletingSessionCascadesToTokenHistoryOnly() {
        RefreshSession doomed = persistSession(owner);
        RefreshSession kept = persistSession(owner);
        byte[] doomedHash = new byte[32];
        byte[] keptHash = new byte[32];
        keptHash[0] = 1;
        tokenRepository.saveAndFlush(RefreshToken.issue(doomed, doomedHash, clock));
        tokenRepository.saveAndFlush(RefreshToken.issue(kept, keptHash, clock));
        entityManager.clear();

        sessionRepository.deleteById(doomed.getId());
        sessionRepository.flush();
        entityManager.clear();

        assertThat(sessionRepository.findById(doomed.getId())).isEmpty();
        assertThat(tokenRepository.findByTokenHash(doomedHash)).isEmpty();
        assertThat(tokenRepository.findBySession_IdOrderByIdAsc(doomed.getId())).isEmpty();
        assertThat(tokenRepository.findByTokenHash(keptHash)).isPresent();
        assertThat(userRepository.findById(owner.getId())).isPresent();
    }

    @Test
    void sessionsLeaveExistingAccountAndPhotoDataUnchanged() {
        RefreshSession session = persistSession(owner);
        session.revoke(RefreshSessionRevocationReason.PASSWORD_CHANGE, clock);
        sessionRepository.saveAndFlush(session);
        entityManager.clear();

        User loaded = userRepository.findById(owner.getId()).orElseThrow();
        assertThat(loaded.getEmail()).isEqualTo("owner@example.com");
        assertThat(loaded.getPasswordHash()).isEqualTo("owner-hash");
        assertThat(loaded.getDisplayName()).isEqualTo("Owner User");
        assertThat(loaded.getDateFormat()).isEqualTo(DateFormatPreference.ISO);
        assertThat(loaded.getTransactionPageSize()).isEqualTo(25);
        assertThat(loaded.getProfilePhotoKey()).isEqualTo(ProfilePhotoTestSupport.KEY);
    }
}
