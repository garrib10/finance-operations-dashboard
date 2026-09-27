package dev.portfolio.finance.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import dev.portfolio.finance.config.AuthSessionProperties;
import dev.portfolio.finance.entity.RefreshSession;
import dev.portfolio.finance.entity.RefreshSessionRevocationReason;
import dev.portfolio.finance.entity.RefreshToken;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.auth.InvalidCredentialsException;
import dev.portfolio.finance.exception.auth.InvalidRefreshTokenException;
import dev.portfolio.finance.exception.auth.SessionUnavailableException;
import dev.portfolio.finance.repository.RefreshSessionRepository;
import dev.portfolio.finance.repository.RefreshTokenRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.IssuedRefreshToken;
import dev.portfolio.finance.security.JwtService;
import dev.portfolio.finance.security.RefreshCookieService.PresentedRefreshCookie;
import dev.portfolio.finance.security.RefreshTokenGenerator;
import jakarta.persistence.PersistenceException;

/**
 * Refresh-session lifecycle: issuance at login, rotation, reuse revocation, and logout.
 *
 * <p>Lock order, used by every flow that mutates a user's sessions or credentials
 * (login, refresh, reuse, logout, password change): 1) resolve the user ID without a
 * lock, 2) {@code SELECT ... FOR UPDATE} the user row, 3) reload token/session rows
 * (also {@code FOR UPDATE}), 4) mutate, 5) commit. No flow locks a session or token
 * before the user row, so these flows cannot deadlock each other. Terminal
 * outcomes (including reuse revocation) are returned, not thrown, so their writes
 * commit before the caller responds. Callers attach cookies only after a method
 * returns, i.e. after commit.
 */
@Service
public class RefreshSessionService {

    private static final Logger log = LoggerFactory.getLogger(RefreshSessionService.class);

    private final RefreshSessionRepository sessionRepository;
    private final RefreshTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenGenerator tokenGenerator;
    private final JwtService jwtService;
    private final AuthSessionProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public RefreshSessionService(
            RefreshSessionRepository sessionRepository,
            RefreshTokenRepository tokenRepository,
            UserRepository userRepository,
            RefreshTokenGenerator tokenGenerator,
            JwtService jwtService,
            AuthSessionProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.sessionRepository = sessionRepository;
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.tokenGenerator = tokenGenerator;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Verifies credentials against the <em>locked</em> user row and starts an
     * independent family in the same transaction. Because password changes take the
     * same lock, a login checked against a superseded hash can never commit a session
     * after the change commits. BCrypt runs while the row is locked; that bounded cost
     * (one hash per login, per user) is accepted to keep this guarantee.
     */
    public IssuedSession startSession(Long userId, Predicate<User> credentialsMatch) {
        try {
            return transactions.execute(status -> {
                User user = userRepository.findByIdForUpdate(userId)
                        .filter(credentialsMatch)
                        .orElseThrow(() -> {
                            log.info("auth.login.failed category=invalid_credentials");
                            return new InvalidCredentialsException("Invalid email or password");
                        });
                RefreshSession session = sessionRepository.save(
                        RefreshSession.start(user, clock, properties.refreshSessionTtl()));
                IssuedRefreshToken token = tokenGenerator.generate();
                tokenRepository.save(RefreshToken.issue(session, token.tokenHash(), clock));
                log.info("auth.session.started userId={} sessionId={}", user.getId(), session.getId());
                return new IssuedSession(jwtService.generateToken(user), token.rawToken(), session.getExpiresAt());
            });
        } catch (DataAccessException | TransactionException | PersistenceException ex) {
            log.error("auth.session.persistence_failed operation=login userId={} category={}",
                    userId, ex.getClass().getSimpleName());
            throw new SessionUnavailableException();
        }
    }

    /**
     * Revokes every active family for a user. Must run inside the caller's transaction
     * (the password change), after the caller has locked the user row, so the credential
     * update and the revocations commit or roll back together. Already-revoked families
     * are untouched; expired ones are left for cleanup. Token history is kept.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int revokeAllForUser(Long userId, RefreshSessionRevocationReason reason) {
        userRepository.findByIdForUpdate(userId);
        Instant now = clock.instant();
        List<RefreshSession> active = sessionRepository.findUnrevokedByUserIdForUpdate(userId).stream()
                .filter(session -> session.isActiveAt(now))
                .toList();
        active.forEach(session -> session.revoke(reason, clock));
        log.info("auth.session.revoke_all userId={} reason={} count={}", userId, reason, active.size());
        return active.size();
    }

    public RefreshResult refresh(PresentedRefreshCookie cookie) {
        Optional<byte[]> hash = presentedHash(cookie, "refresh");
        if (hash.isEmpty()) {
            return RefreshResult.REJECTED;
        }
        try {
            Optional<Long> ownerId = tokenRepository.findOwnerUserIdByTokenHash(hash.get());
            if (ownerId.isEmpty()) {
                log.info("auth.refresh.rejected category=unknown");
                return RefreshResult.REJECTED;
            }
            return transactions.execute(status -> rotateLocked(ownerId.get(), hash.get()));
        } catch (DataAccessException | TransactionException | PersistenceException ex) {
            log.error("auth.session.persistence_failed operation=refresh category={}",
                    ex.getClass().getSimpleName());
            return RefreshResult.UNAVAILABLE;
        }
    }

    public LogoutOutcome logout(PresentedRefreshCookie cookie) {
        Optional<byte[]> hash = presentedHash(cookie, "logout");
        if (hash.isEmpty()) {
            return LogoutOutcome.NO_OP;
        }
        try {
            Optional<Long> ownerId = tokenRepository.findOwnerUserIdByTokenHash(hash.get());
            if (ownerId.isEmpty()) {
                log.info("auth.logout.noop category=unknown");
                return LogoutOutcome.NO_OP;
            }
            return transactions.execute(status -> revokeLocked(ownerId.get(), hash.get()));
        } catch (DataAccessException | TransactionException | PersistenceException ex) {
            log.error("auth.session.persistence_failed operation=logout category={}",
                    ex.getClass().getSimpleName());
            return LogoutOutcome.UNAVAILABLE;
        }
    }

    private RefreshResult rotateLocked(Long ownerId, byte[] hash) {
        Optional<User> user = userRepository.findByIdForUpdate(ownerId);
        Optional<RefreshToken> locked = user.flatMap(u -> tokenRepository.findByTokenHashForUpdate(hash));
        if (locked.isEmpty() || !ownerId.equals(locked.get().getSession().getUser().getId())) {
            log.info("auth.refresh.rejected category=unknown");
            return RefreshResult.REJECTED;
        }
        RefreshToken token = locked.get();
        RefreshSession session = token.getSession();
        String terminal = terminalCategory(session);
        if (terminal != null) {
            log.info("auth.refresh.rejected category={} userId={} sessionId={}", terminal, ownerId, session.getId());
            return RefreshResult.REJECTED;
        }
        if (token.isConsumed()) {
            session.revoke(RefreshSessionRevocationReason.REUSE_DETECTED, clock);
            log.warn("auth.refresh.reuse_detected userId={} sessionId={} action=family_revoked",
                    ownerId, session.getId());
            return RefreshResult.REJECTED;
        }
        token.markConsumed(clock);
        IssuedRefreshToken replacement = tokenGenerator.generate();
        tokenRepository.save(RefreshToken.issue(session, replacement.tokenHash(), clock));
        log.info("auth.refresh.rotated userId={} sessionId={}", ownerId, session.getId());
        return RefreshResult.rotated(new IssuedSession(
                jwtService.generateToken(user.get()), replacement.rawToken(), session.getExpiresAt()));
    }

    private LogoutOutcome revokeLocked(Long ownerId, byte[] hash) {
        Optional<RefreshToken> locked = userRepository.findByIdForUpdate(ownerId)
                .flatMap(u -> tokenRepository.findByTokenHashForUpdate(hash));
        if (locked.isEmpty()) {
            log.info("auth.logout.noop category=unknown");
            return LogoutOutcome.NO_OP;
        }
        RefreshToken token = locked.get();
        RefreshSession session = token.getSession();
        String terminal = terminalCategory(session);
        if (terminal != null) {
            log.info("auth.logout.noop category={} userId={} sessionId={}", terminal, ownerId, session.getId());
            return LogoutOutcome.NO_OP;
        }
        // A consumed token still identifies its family: logout racing a just-completed
        // rotation must end the family, and it is recorded as a logout, not reuse.
        session.revoke(RefreshSessionRevocationReason.LOGOUT, clock);
        log.info("auth.logout.revoked userId={} sessionId={} consumedToken={}",
                ownerId, session.getId(), token.isConsumed());
        return LogoutOutcome.REVOKED;
    }

    private String terminalCategory(RefreshSession session) {
        if (session.isRevoked()) {
            return "revoked";
        }
        Instant now = clock.instant();
        return session.isActiveAt(now) ? null : "expired";
    }

    private Optional<byte[]> presentedHash(PresentedRefreshCookie cookie, String operation) {
        String event = operation.equals("refresh") ? "auth.refresh.rejected" : "auth.logout.noop";
        switch (cookie.state()) {
            case ABSENT -> {
                log.info("{} category=cookie_absent", event);
                return Optional.empty();
            }
            case DUPLICATE -> {
                log.info("{} category=cookie_duplicate", event);
                return Optional.empty();
            }
            default -> {
                try {
                    return Optional.of(tokenGenerator.hashPresentedToken(cookie.value()));
                } catch (InvalidRefreshTokenException ex) {
                    log.info("{} category=malformed", event);
                    return Optional.empty();
                }
            }
        }
    }

    /** Short-lived handoff to the controller; redacted so it cannot leak through logs. */
    public record IssuedSession(String accessToken, String rawRefreshToken, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedSession[tokens redacted]";
        }
    }

    public record RefreshResult(Status status, IssuedSession issued) {

        static final RefreshResult REJECTED = new RefreshResult(Status.REJECTED, null);
        static final RefreshResult UNAVAILABLE = new RefreshResult(Status.UNAVAILABLE, null);

        public enum Status { ROTATED, REJECTED, UNAVAILABLE }

        static RefreshResult rotated(IssuedSession issued) {
            return new RefreshResult(Status.ROTATED, issued);
        }

        @Override
        public String toString() {
            return "RefreshResult[status=" + status + ", tokens redacted]";
        }
    }

    public enum LogoutOutcome { REVOKED, NO_OP, UNAVAILABLE }
}
