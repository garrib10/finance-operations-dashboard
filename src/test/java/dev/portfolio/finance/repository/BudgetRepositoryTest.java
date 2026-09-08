package dev.portfolio.finance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import dev.portfolio.finance.entity.Budget;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.User;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class BudgetRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Test
    void shouldFindAllBudgetsForUserOrderedByYearAndMonthDescending() {

        User user = userRepository.save(
                new User(
                        "Test",
                        "User",
                        "test@example.com",
                        "hashed-password"
                )
        );

        Category groceries =
                categoryRepository.save(
                        new Category(
                                user,
                                "Groceries",
                                true
                        )
                );

        Category dining =
                categoryRepository.save(
                        new Category(
                                user,
                                "Dining",
                                true
                        )
                );

        Category travel =
                categoryRepository.save(
                        new Category(
                                user,
                                "Travel",
                                true
                        )
                );

        budgetRepository.save(
                new Budget(
                        user,
                        groceries,
                        new BigDecimal("700.00"),
                        8,
                        2026
                )
        );

        budgetRepository.save(
                new Budget(
                        user,
                        dining,
                        new BigDecimal("400.00"),
                        9,
                        2026
                )
        );

        budgetRepository.save(
                new Budget(
                        user,
                        travel,
                        new BigDecimal("1000.00"),
                        12,
                        2025
                )
        );

        List<Budget> budgets =
                budgetRepository
                        .findAllByUserIdOrderByYearDescMonthDesc(
                                user.getId()
                        );

        assertThat(budgets)
                .hasSize(3);

        assertThat(budgets)
                .extracting(
                        budget ->
                                budget.getYear()
                                        + "-"
                                        + budget.getMonth()
                )
                .containsExactly(
                        "2026-9",
                        "2026-8",
                        "2025-12"
                );
    }

    @Test
    void shouldOnlyReturnBudgetsOwnedByUser() {

        User firstUser = userRepository.save(
                new User(
                        "First",
                        "User",
                        "first@example.com",
                        "hashed-password"
                )
        );

        User secondUser = userRepository.save(
                new User(
                        "Second",
                        "User",
                        "second@example.com",
                        "hashed-password"
                )
        );

        Category firstCategory =
                categoryRepository.save(
                        new Category(
                                firstUser,
                                "Groceries",
                                true
                        )
                );

        Category secondCategory =
                categoryRepository.save(
                        new Category(
                                secondUser,
                                "Travel",
                                true
                        )
                );

        budgetRepository.save(
                new Budget(
                        firstUser,
                        firstCategory,
                        new BigDecimal("700.00"),
                        9,
                        2026
                )
        );

        budgetRepository.save(
                new Budget(
                        secondUser,
                        secondCategory,
                        new BigDecimal("1200.00"),
                        9,
                        2026
                )
        );

        List<Budget> budgets =
                budgetRepository
                        .findAllByUserIdOrderByYearDescMonthDesc(
                                firstUser.getId()
                        );

        assertThat(budgets)
                .hasSize(1);

        assertThat(
                budgets.getFirst()
                        .getUser()
                        .getId()
        )
                .isEqualTo(firstUser.getId());
    }

    @Test
    void shouldFindBudgetByIdAndUserId() {

        User user = userRepository.save(
                new User(
                        "Test",
                        "User",
                        "test@example.com",
                        "hashed-password"
                )
        );

        Category category =
                categoryRepository.save(
                        new Category(
                                user,
                                "Groceries",
                                true
                        )
                );

        Budget budget =
                budgetRepository.save(
                        new Budget(
                                user,
                                category,
                                new BigDecimal("700.00"),
                                9,
                                2026
                        )
                );

        var result =
                budgetRepository.findByIdAndUserId(
                        budget.getId(),
                        user.getId()
                );

        assertThat(result)
                .isPresent();

        assertThat(result.get().getMonthlyLimit())
                .isEqualByComparingTo("700.00");

        assertThat(result.get().getMonth())
                .isEqualTo(9);

        assertThat(result.get().getYear())
                .isEqualTo(2026);
    }

    @Test
    void shouldNotFindBudgetOwnedByDifferentUser() {

        User owner = userRepository.save(
                new User(
                        "Owner",
                        "User",
                        "owner@example.com",
                        "hashed-password"
                )
        );

        User otherUser = userRepository.save(
                new User(
                        "Other",
                        "User",
                        "other@example.com",
                        "hashed-password"
                )
        );

        Category category =
                categoryRepository.save(
                        new Category(
                                owner,
                                "Groceries",
                                true
                        )
                );

        Budget budget =
                budgetRepository.save(
                        new Budget(
                                owner,
                                category,
                                new BigDecimal("700.00"),
                                9,
                                2026
                        )
                );

        var result =
                budgetRepository.findByIdAndUserId(
                        budget.getId(),
                        otherUser.getId()
                );

        assertThat(result)
                .isEmpty();
    }

    @Test
    void shouldDetectExistingBudgetForUserCategoryMonthAndYear() {

        User user = userRepository.save(
                new User(
                        "Test",
                        "User",
                        "test@example.com",
                        "hashed-password"
                )
        );

        Category category =
                categoryRepository.save(
                        new Category(
                                user,
                                "Groceries",
                                true
                        )
                );

        budgetRepository.save(
                new Budget(
                        user,
                        category,
                        new BigDecimal("700.00"),
                        9,
                        2026
                )
        );

        boolean exists =
                budgetRepository
                        .existsByUserIdAndCategoryIdAndMonthAndYear(
                                user.getId(),
                                category.getId(),
                                9,
                                2026
                        );

        assertThat(exists)
                .isTrue();
    }

    @Test
    void shouldReturnFalseWhenBudgetPeriodDoesNotMatch() {

        User user = userRepository.save(
                new User(
                        "Test",
                        "User",
                        "test@example.com",
                        "hashed-password"
                )
        );

        Category category =
                categoryRepository.save(
                        new Category(
                                user,
                                "Groceries",
                                true
                        )
                );

        budgetRepository.save(
                new Budget(
                        user,
                        category,
                        new BigDecimal("700.00"),
                        9,
                        2026
                )
        );

        boolean exists =
                budgetRepository
                        .existsByUserIdAndCategoryIdAndMonthAndYear(
                                user.getId(),
                                category.getId(),
                                10,
                                2026
                        );

        assertThat(exists)
                .isFalse();
    }
}