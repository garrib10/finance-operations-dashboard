package dev.portfolio.finance.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import dev.portfolio.finance.dto.account.ChangePasswordRequest;
import dev.portfolio.finance.dto.auth.LoginRequest;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.auth.InvalidCredentialsException;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.RefreshCookieService.PresentedRefreshCookie;
import dev.portfolio.finance.security.RefreshTokenGenerator;
import dev.portfolio.finance.service.AccountService;
import dev.portfolio.finance.service.AuthService;
import dev.portfolio.finance.service.RefreshSessionService;
import dev.portfolio.finance.service.RefreshSessionService.IssuedSession;
import dev.portfolio.finance.service.RefreshSessionService.LogoutOutcome;
import dev.portfolio.finance.service.RefreshSessionService.RefreshResult;

/**
 * Real MySQL/InnoDB races between login, refresh, logout, and password change. Each
 * operation runs on its own thread, transaction, and pooled connection. Ordered tests
 * use {@link LockGate}; the barrier tests start operations simultaneously and assert
 * invariants that must hold for either winner.
 */
class AuthSessionConcurrencyIT extends MySqlIntegrationTestBase {

    private static final String OLD = "River meadow lantern 42!";
    private static final String NEXT = "Another quiet forest 73!";

    @Autowired private AuthService authService;
    @Autowired private AccountService accountService;
    @Autowired private RefreshSessionService sessions;
    @Autowired private RefreshTokenGenerator generator;
    @Autowired private PasswordEncoder encoder;
    @MockitoSpyBean private UserRepository users;

    private ExecutorService pool;
    private LockGate gate;
    private User user;

    @BeforeEach
    void setUp() {
        AtomicInteger counter = new AtomicInteger();
        pool = Executors.newFixedThreadPool(4, task -> new Thread(task, "race-" + counter.incrementAndGet()));
        gate = new LockGate();
        doAnswer(gate).when(users).findByIdForUpdate(any());
        user = users.saveAndFlush(new User("Race", "User", "race-" + UUID.randomUUID() + "@example.com",
                encoder.encode(OLD)));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        gate.release();
        pool.shutdownNow();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    }

    // ----- operations (each is its own transaction) ---------------------------

    private IssuedSession login(String password) {
        return authService.login(new LoginRequest(user.getEmail(), password));
    }

    private RefreshResult refresh(String raw) {
        return sessions.refresh(new PresentedRefreshCookie(PresentedRefreshCookie.State.PRESENT, raw));
    }

    private LogoutOutcome logout(String raw) {
        return sessions.logout(new PresentedRefreshCookie(PresentedRefreshCookie.State.PRESENT, raw));
    }

    private int changePassword() {
        return accountService.changePassword(user.getEmail(), new ChangePasswordRequest(OLD, NEXT));
    }

    /** Outcome of a concurrent operation: its value, or the exception type it threw. */
    private record Outcome<T>(T value, Class<? extends Throwable> error) { }

    private <T> Outcome<T> await(Future<T> future) throws Exception {
        try {
            return new Outcome<>(future.get(90, TimeUnit.SECONDS), null);
        } catch (java.util.concurrent.ExecutionException ex) {
            return new Outcome<>(null, ex.getCause().getClass());
        }
    }

    /** Runs {@code first} holding the user lock, proves {@code second} blocks on it, then releases. */
    private <A, B> List<Outcome<?>> ordered(Callable<A> first, Callable<B> second) throws Exception {
        gate.arm("race-1");
        Future<A> a = pool.submit(first);
        gate.awaitLockHeld();
        Future<B> b = pool.submit(second);
        gate.awaitCompetitorBlocked();
        gate.release();
        return List.of(await(a), await(b));
    }

    // ----- committed state, read through fresh connections ---------------------

    private List<String> reasons() {
        return jdbc.queryForList("SELECT COALESCE(revocation_reason, 'ACTIVE') FROM refresh_sessions "
                + "WHERE user_id = ? ORDER BY created_at, id", String.class, user.getId());
    }

    private String reasonOf(String raw) {
        return jdbc.queryForObject("SELECT COALESCE(s.revocation_reason, 'ACTIVE') FROM refresh_sessions s "
                + "JOIN refresh_tokens t ON t.session_id = s.id WHERE t.token_hash = ?", String.class,
                (Object) generator.hashPresentedToken(raw));
    }

    private long activeFamilies() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_sessions WHERE user_id = ? AND revoked_at IS NULL",
                Long.class, user.getId());
    }

    private boolean passwordIs(String password) {
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, user.getId());
        return encoder.matches(password, hash);
    }

    // ----- same token --------------------------------------------------------

    @Test
    void sameTokenOrderedRaceRotatesOnceThenRevokesFamilyAsReuse() throws Exception {
        String token = login(OLD).rawRefreshToken();
        String otherDevice = login(OLD).rawRefreshToken();

        List<Outcome<?>> results = ordered(() -> refresh(token), () -> refresh(token));

        RefreshResult first = (RefreshResult) results.get(0).value();
        RefreshResult second = (RefreshResult) results.get(1).value();
        assertThat(first.status()).isEqualTo(RefreshResult.Status.ROTATED);
        assertThat(second.status()).isEqualTo(RefreshResult.Status.REJECTED);
        assertThat(reasonOf(token)).isEqualTo("REUSE_DETECTED");
        assertThat(refresh(first.issued().rawRefreshToken()).status()).isEqualTo(RefreshResult.Status.REJECTED);
        assertThat(reasonOf(otherDevice)).isEqualTo("ACTIVE");
        assertThat(refresh(otherDevice).status()).isEqualTo(RefreshResult.Status.ROTATED);
    }

    @Test
    void sameTokenSimultaneousRefreshNeverRotatesTwice() throws Exception {
        for (int round = 0; round < 5; round++) {
            String token = login(OLD).rawRefreshToken();
            CyclicBarrier start = new CyclicBarrier(2);
            Callable<RefreshResult> attempt = () -> {
                start.await(30, TimeUnit.SECONDS);
                return refresh(token);
            };
            List<Future<RefreshResult>> futures = List.of(pool.submit(attempt), pool.submit(attempt));
            List<RefreshResult.Status> statuses = new ArrayList<>();
            String replacement = null;
            for (Future<RefreshResult> future : futures) {
                RefreshResult result = future.get(90, TimeUnit.SECONDS);
                statuses.add(result.status());
                if (result.status() == RefreshResult.Status.ROTATED) {
                    replacement = result.issued().rawRefreshToken();
                }
            }

            assertThat(statuses).containsExactlyInAnyOrder(RefreshResult.Status.ROTATED, RefreshResult.Status.REJECTED);
            assertThat(reasonOf(token)).isEqualTo("REUSE_DETECTED");
            assertThat(refresh(replacement).status()).isEqualTo(RefreshResult.Status.REJECTED);
        }
    }

    // ----- refresh vs logout -------------------------------------------------

    @Test
    void refreshThenLogoutWithRotatedTokenEndsFamily() throws Exception {
        String token = login(OLD).rawRefreshToken();
        String otherDevice = login(OLD).rawRefreshToken();

        List<Outcome<?>> results = ordered(() -> refresh(token), () -> logout(token));

        RefreshResult rotated = (RefreshResult) results.get(0).value();
        assertThat(rotated.status()).isEqualTo(RefreshResult.Status.ROTATED);
        assertThat(results.get(1).value()).isEqualTo(LogoutOutcome.REVOKED);
        assertThat(reasonOf(token)).isEqualTo("LOGOUT");
        assertThat(refresh(rotated.issued().rawRefreshToken()).status()).isEqualTo(RefreshResult.Status.REJECTED);
        assertThat(reasonOf(otherDevice)).isEqualTo("ACTIVE");
    }

    @Test
    void logoutThenRefreshIsTerminal() throws Exception {
        String token = login(OLD).rawRefreshToken();
        String otherDevice = login(OLD).rawRefreshToken();

        List<Outcome<?>> results = ordered(() -> logout(token), () -> refresh(token));

        assertThat(results.get(0).value()).isEqualTo(LogoutOutcome.REVOKED);
        assertThat(((RefreshResult) results.get(1).value()).status()).isEqualTo(RefreshResult.Status.REJECTED);
        assertThat(reasonOf(token)).isEqualTo("LOGOUT");
        assertThat(reasonOf(otherDevice)).isEqualTo("ACTIVE");
    }

    // ----- refresh vs password change ---------------------------------------

    @Test
    void refreshThenPasswordChangeRevokesTheRotatedFamily() throws Exception {
        String token = login(OLD).rawRefreshToken();
        login(OLD);

        List<Outcome<?>> results = ordered(() -> refresh(token), this::changePassword);

        RefreshResult rotated = (RefreshResult) results.get(0).value();
        assertThat(rotated.status()).isEqualTo(RefreshResult.Status.ROTATED);
        assertThat(results.get(1).value()).isEqualTo(2);
        assertThat(reasons()).containsOnly("PASSWORD_CHANGE").hasSize(2);
        assertThat(refresh(rotated.issued().rawRefreshToken()).status()).isEqualTo(RefreshResult.Status.REJECTED);
        assertThat(passwordIs(NEXT)).isTrue();
    }

    @Test
    void passwordChangeThenRefreshIsRejectedWithoutSuccessor() throws Exception {
        String token = login(OLD).rawRefreshToken();
        long tokensBefore = jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Long.class);

        List<Outcome<?>> results = ordered(this::changePassword, () -> refresh(token));

        assertThat(results.get(0).value()).isEqualTo(1);
        assertThat(((RefreshResult) results.get(1).value()).status()).isEqualTo(RefreshResult.Status.REJECTED);
        assertThat(reasons()).containsExactly("PASSWORD_CHANGE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Long.class)).isEqualTo(tokensBefore);
    }

    @Test
    void simultaneousRefreshAndPasswordChangeLeaveNoUsableFamily() throws Exception {
        for (int round = 0; round < 3; round++) {
            String current = round % 2 == 0 ? OLD : NEXT;
            String next = round % 2 == 0 ? NEXT : OLD;
            String token = login(current).rawRefreshToken();
            CyclicBarrier start = new CyclicBarrier(2);
            Future<RefreshResult> refreshed = pool.submit(() -> {
                start.await(30, TimeUnit.SECONDS);
                return refresh(token);
            });
            Future<Integer> changed = pool.submit(() -> {
                start.await(30, TimeUnit.SECONDS);
                return accountService.changePassword(user.getEmail(), new ChangePasswordRequest(current, next));
            });

            RefreshResult result = refreshed.get(90, TimeUnit.SECONDS);
            changed.get(90, TimeUnit.SECONDS);

            assertThat(activeFamilies()).isZero();
            if (result.status() == RefreshResult.Status.ROTATED) {
                assertThat(refresh(result.issued().rawRefreshToken()).status())
                        .isEqualTo(RefreshResult.Status.REJECTED);
            }
        }
    }

    // ----- login (old password) vs password change ---------------------------

    @Test
    void oldPasswordLoginThenPasswordChangeRevokesThatSession() throws Exception {
        List<Outcome<?>> results = ordered(() -> login(OLD), this::changePassword);

        IssuedSession oldLogin = (IssuedSession) results.get(0).value();
        assertThat(oldLogin).isNotNull();
        assertThat(results.get(1).value()).isEqualTo(1);
        assertThat(reasonOf(oldLogin.rawRefreshToken())).isEqualTo("PASSWORD_CHANGE");
        assertThat(refresh(oldLogin.rawRefreshToken()).status()).isEqualTo(RefreshResult.Status.REJECTED);
        assertNewPasswordOnly();
    }

    @Test
    void passwordChangeThenOldPasswordLoginFails() throws Exception {
        List<Outcome<?>> results = ordered(this::changePassword, () -> login(OLD));

        assertThat(results.get(0).value()).isEqualTo(0);
        assertThat(results.get(1).error()).isEqualTo(InvalidCredentialsException.class);
        assertThat(reasons()).isEmpty();
        assertNewPasswordOnly();
    }

    private void assertNewPasswordOnly() {
        assertThat(activeFamilies()).isZero();
        assertThat(passwordIs(NEXT)).isTrue();
        try {
            login(OLD);
            throw new AssertionError("old password still authenticates");
        } catch (InvalidCredentialsException expected) {
            // generic invalid credentials
        }
        IssuedSession fresh = login(NEXT);
        assertThat(reasonOf(fresh.rawRefreshToken())).isEqualTo("ACTIVE");
        assertThat(activeFamilies()).isEqualTo(1);
    }

    // ----- multiple devices ----------------------------------------------------

    @Test
    void reuseAndLogoutAreFamilyScopedWhilePasswordChangeRevokesAll() {
        String laptop = login(OLD).rawRefreshToken();
        String phone = login(OLD).rawRefreshToken();
        String tablet = login(OLD).rawRefreshToken();

        refresh(laptop);
        assertThat(refresh(laptop).status()).isEqualTo(RefreshResult.Status.REJECTED);
        assertThat(logout(phone)).isEqualTo(LogoutOutcome.REVOKED);

        assertThat(reasonOf(laptop)).isEqualTo("REUSE_DETECTED");
        assertThat(reasonOf(phone)).isEqualTo("LOGOUT");
        assertThat(reasonOf(tablet)).isEqualTo("ACTIVE");

        assertThat(changePassword()).isEqualTo(1);
        assertThat(reasonOf(laptop)).isEqualTo("REUSE_DETECTED");
        assertThat(reasonOf(phone)).isEqualTo("LOGOUT");
        assertThat(reasonOf(tablet)).isEqualTo("PASSWORD_CHANGE");
    }
}
