package dev.portfolio.finance.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import dev.portfolio.finance.config.AuthSessionProperties;
import dev.portfolio.finance.repository.RefreshSessionRepository;
import jakarta.persistence.PersistenceException;

/**
 * Deletes refresh families whose absolute expiration is older than the retention
 * period ({@code expires_at < now - retention}). Revocation alone never makes a family
 * eligible. Deleting a family cascades to its token history in the database.
 *
 * <p>Runs daily at 03:30 UTC in bounded batches, each in its own short transaction.
 * It is safe to run on several instances: the delete re-checks the cutoff, and IDs
 * another instance already deleted simply match no rows.
 */
@Service
public class RefreshSessionCleanupService {

    static final int BATCH_SIZE = 500;
    static final int MAX_BATCHES_PER_RUN = 20;

    private static final Logger log = LoggerFactory.getLogger(RefreshSessionCleanupService.class);

    private final RefreshSessionRepository sessionRepository;
    private final AuthSessionProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final int batchSize;
    private final int maxBatches;

    @Autowired
    public RefreshSessionCleanupService(
            RefreshSessionRepository sessionRepository,
            AuthSessionProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this(sessionRepository, properties, clock, transactionManager, BATCH_SIZE, MAX_BATCHES_PER_RUN);
    }

    RefreshSessionCleanupService(
            RefreshSessionRepository sessionRepository,
            AuthSessionProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager,
            int batchSize,
            int maxBatches
    ) {
        this.sessionRepository = sessionRepository;
        this.properties = properties;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.maxBatches = maxBatches;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    public void scheduledCleanup() {
        cleanup();
    }

    /** Returns the number of families deleted; never throws. */
    public int cleanup() {
        if (!properties.cleanupEnabled()) {
            log.info("auth.session.cleanup category=disabled deleted=0");
            return 0;
        }
        Instant cutoff = clock.instant().minus(properties.retention());
        int deleted = 0;
        int batches = 0;
        try {
            while (batches < maxBatches) {
                Integer removed = transactions.execute(status -> deleteBatch(cutoff));
                batches++;
                deleted += removed;
                if (removed < batchSize) {
                    break;
                }
            }
            log.info("auth.session.cleanup category=completed deleted={} batches={}", deleted, batches);
        } catch (DataAccessException | TransactionException | PersistenceException ex) {
            log.warn("auth.session.cleanup category=failed deleted={} batches={} error={}",
                    deleted, batches, ex.getClass().getSimpleName());
        }
        return deleted;
    }

    private int deleteBatch(Instant cutoff) {
        List<String> ids = sessionRepository.findIdsExpiredBefore(cutoff, PageRequest.of(0, batchSize));
        return ids.isEmpty() ? 0 : sessionRepository.deleteExpiredByIds(ids, cutoff);
    }
}
