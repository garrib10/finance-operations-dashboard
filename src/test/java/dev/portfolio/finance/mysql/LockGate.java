package dev.portfolio.finance.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Deterministic lock ordering for concurrency tests. Installed as the answer for
 * {@code UserRepository.findByIdForUpdate}: the first call made on the gated thread
 * acquires the real MySQL row lock, then parks until released. The test then waits
 * until InnoDB reports the competing transaction waiting on a row lock, proving it is blocked
 * on that row lock, before releasing. No sleeps decide the ordering.
 */
final class LockGate implements Answer<Object> {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private volatile String gatedThread;
    private final AtomicBoolean fired = new AtomicBoolean();
    private final CountDownLatch acquired = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    void arm(String threadName) {
        gatedThread = threadName;
    }

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        // Spring spies JPA repository proxies by delegating to the real bean through the
        // default answer; callRealMethod() cannot reach an interface's implementation.
        Object result = org.mockito.Mockito.mockingDetails(invocation.getMock())
                .getMockCreationSettings().getDefaultAnswer().answer(invocation);
        if (Thread.currentThread().getName().equals(gatedThread) && fired.compareAndSet(false, true)) {
            acquired.countDown();
            if (!release.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                throw new IllegalStateException("Lock gate was never released");
            }
        }
        return result;
    }

    void awaitLockHeld() throws InterruptedException {
        if (!acquired.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            throw new IllegalStateException("Gated operation never acquired the user-row lock");
        }
    }

    /**
     * Polls performance_schema.data_lock_waits until a transaction is waiting on a row lock.
     * Not information_schema.innodb_trx: that table is served from a cache that is not
     * refreshed while it is read more often than every 0.1 s, so a 25 ms poll can keep
     * seeing a snapshot from before the wait began.
     */
    void awaitCompetitorBlocked() throws Exception {
        Instant deadline = Instant.now().plus(TIMEOUT);
        try (Connection root = MySqlIntegrationTestBase.rootConnection("fintrack");
             Statement statement = root.createStatement()) {
            while (Instant.now().isBefore(deadline)) {
                try (ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM performance_schema.data_lock_waits")) {
                    rows.next();
                    if (rows.getInt(1) > 0) {
                        return;
                    }
                }
                TimeUnit.MILLISECONDS.sleep(25); // poll interval only; ordering is lock-driven
            }
        }
        throw new IllegalStateException("Competing operation never blocked on the user-row lock");
    }

    void release() {
        release.countDown();
    }
}
