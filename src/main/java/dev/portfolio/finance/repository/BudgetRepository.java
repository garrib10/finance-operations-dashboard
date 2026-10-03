package dev.portfolio.finance.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import dev.portfolio.finance.entity.Budget;
import dev.portfolio.finance.repository.projection.CategoryBudgetCountProjection;
import dev.portfolio.finance.repository.projection.CurrentMonthBudgetProjection;

public interface BudgetRepository
        extends JpaRepository<Budget, Long> {

    @EntityGraph(attributePaths = "category")
    List<Budget> findAllByUserIdOrderByYearDescMonthDesc(
            Long userId
    );

    Optional<Budget> findByIdAndUserId(
            Long id,
            Long userId
    );

    boolean existsByUserIdAndCategoryIdAndMonthAndYear(
            Long userId,
            Long categoryId,
            int month,
            int year
    );

    /** Budget counts per category across all months; categories without budgets are absent. */
    @Query("""
            SELECT b.category.id AS categoryId, COUNT(b) AS budgetCount
            FROM Budget b
            WHERE b.user.id = :userId
            GROUP BY b.category.id
            """)
    List<CategoryBudgetCountProjection> countBudgetsByCategory(
            @Param("userId") Long userId
    );

    /** This user's budgets for one month, without loading entities or categories. */
    @Query("""
            SELECT b.id AS budgetId, b.category.id AS categoryId, b.monthlyLimit AS monthlyLimit
            FROM Budget b
            WHERE b.user.id = :userId
              AND b.month = :month
              AND b.year = :year
            """)
    List<CurrentMonthBudgetProjection> findMonthBudgets(
            @Param("userId") Long userId,
            @Param("month") int month,
            @Param("year") int year
    );

    /** Whether any of this user's budgets, in any month or year, references the category. */
    boolean existsByCategoryIdAndUserId(
            Long categoryId,
            Long userId
    );
}
