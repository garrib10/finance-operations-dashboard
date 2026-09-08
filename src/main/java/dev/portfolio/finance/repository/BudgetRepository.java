package dev.portfolio.finance.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.portfolio.finance.entity.Budget;

public interface BudgetRepository
        extends JpaRepository<Budget, Long> {

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
}