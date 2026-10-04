package dev.portfolio.finance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import dev.portfolio.finance.entity.Transaction;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.repository.projection.CategorySpendingProjection;
import dev.portfolio.finance.repository.projection.CategoryTransactionUsageProjection;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    @EntityGraph(attributePaths = "category")
    List<Transaction> findAllByUserIdOrderByTransactionDateDesc(
            Long userId
    );

    /** Filtered, paged search; the category is fetched in the same query as the page. */
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Transaction> findAll(Specification<Transaction> specification, Pageable pageable);

    Optional<Transaction> findByIdAndUserId(
            Long id,
            Long userId
    );

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.user.id = :userId
              AND t.category.id = :categoryId
              AND t.type = :type
              AND t.transactionDate >= :startDate
              AND t.transactionDate <= :endDate
            """)
    BigDecimal sumAmountByUserCategoryTypeAndDateRange(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.user.id = :userId
              AND t.type = :type
            """)
    BigDecimal sumAmountByUserAndType(
            @Param("userId") Long userId,
            @Param("type") TransactionType type
    );

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.user.id = :userId
              AND t.type = :type
              AND t.transactionDate >= :startDate
              AND t.transactionDate <= :endDate
            """)
    BigDecimal sumAmountByUserTypeAndDateRange(
            @Param("userId") Long userId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @EntityGraph(attributePaths = "category")
    List<Transaction>
    findTop5ByUserIdOrderByTransactionDateDescCreatedAtDesc(
            Long userId
    );

    @Query("""
            SELECT
                t.category.id AS categoryId,
                t.category.name AS categoryName,
                t.category.iconKey AS categoryIconKey,
                SUM(t.amount) AS amountSpent
            FROM Transaction t
            WHERE t.user.id = :userId
              AND t.type = :type
              AND t.transactionDate >= :startDate
              AND t.transactionDate <= :endDate
            GROUP BY
                t.category.id,
                t.category.name,
                t.category.iconKey
            ORDER BY SUM(t.amount) DESC
            """)
    List<CategorySpendingProjection> findSpendingByCategory(
            @Param("userId") Long userId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Usage per category in one grouped query: counts include income, spending sums only
     * {@code expense}. Both month figures use the inclusive reporting-month range. Categories
     * without transactions are absent; callers treat them as zero. COUNT(CASE …) ignores the
     * NULLs from non-matching rows and returns a whole number on both H2 and MySQL.
     */
    @Query("""
            SELECT
                t.category.id AS categoryId,
                COUNT(t) AS transactionCount,
                COUNT(CASE
                    WHEN t.transactionDate >= :startDate
                        AND t.transactionDate <= :endDate
                    THEN 1 END) AS currentMonthTransactionCount,
                MAX(t.transactionDate) AS lastTransactionDate,
                COALESCE(SUM(CASE WHEN t.type = :expense THEN t.amount ELSE 0 END), 0) AS allTimeSpent,
                COALESCE(SUM(CASE
                    WHEN t.type = :expense
                        AND t.transactionDate >= :startDate
                        AND t.transactionDate <= :endDate
                    THEN t.amount ELSE 0 END), 0) AS currentMonthSpent
            FROM Transaction t
            WHERE t.user.id = :userId
            GROUP BY t.category.id
            """)
    List<CategoryTransactionUsageProjection> summarizeUsageByCategory(
            @Param("userId") Long userId,
            @Param("expense") TransactionType expense,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /** Whether any of this user's transactions references the category (existence only). */
    boolean existsByCategoryIdAndUserId(
            Long categoryId,
            Long userId
    );
}
