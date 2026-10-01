package dev.portfolio.finance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import dev.portfolio.finance.entity.BuiltInCategory;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.CategoryIcon;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.support.TestDataFactory;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class CategoryRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void shouldFindAllCategoriesForUserOrderedByNameAscending() {

        User user = userRepository.save(
                new User(
                        "Test",
                        "User",
                        "test@example.com",
                        "hashed-password"
                )
        );

        categoryRepository.save(
                Category.custom(user, "Utilities", true)
        );

        categoryRepository.save(
                Category.custom(user, "Groceries", true)
        );

        categoryRepository.save(
                Category.custom(user, "Dining", true)
        );

        List<Category> categories =
                categoryRepository
                        .findAllByUserIdOrderByNameAscIdAsc(
                                user.getId()
                        );

        assertThat(categories)
                .extracting(Category::getName)
                .containsExactly(
                        "Dining",
                        "Groceries",
                        "Utilities"
                );
    }

    @Test
    void shouldOnlyReturnCategoriesOwnedByUser() {

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

        categoryRepository.save(
                Category.custom(
                        firstUser,
                        "Groceries",
                        true
                )
        );

        categoryRepository.save(
                Category.custom(
                        secondUser,
                        "Travel",
                        true
                )
        );

        List<Category> categories =
                categoryRepository
                        .findAllByUserIdOrderByNameAscIdAsc(
                                firstUser.getId()
                        );

        assertThat(categories)
                .hasSize(1);

        assertThat(categories.getFirst().getName())
                .isEqualTo("Groceries");

        assertThat(categories.getFirst().getUser().getId())
                .isEqualTo(firstUser.getId());
    }

    @Test
    void shouldFindCategoryByIdAndUserId() {

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
                        Category.custom(
                                user,
                                "Groceries",
                                true
                        )
                );

        var result =
                categoryRepository.findByIdAndUserId(
                        category.getId(),
                        user.getId()
                );

        assertThat(result)
                .isPresent();

        assertThat(result.get().getName())
                .isEqualTo("Groceries");
    }

    @Test
    void shouldNotFindCategoryOwnedByDifferentUser() {

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
                        Category.custom(
                                owner,
                                "Groceries",
                                true
                        )
                );

        var result =
                categoryRepository.findByIdAndUserId(
                        category.getId(),
                        otherUser.getId()
                );

        assertThat(result)
                .isEmpty();
    }

    @Test
    void shouldDetectExistingCategoryByNormalizedName() {

        User user = userRepository.save(
                new User(
                        "Test",
                        "User",
                        "test@example.com",
                        "hashed-password"
                )
        );

        categoryRepository.save(
                Category.custom(
                        user,
                        "Groceries",
                        true
                )
        );

        boolean exists =
                categoryRepository
                        .existsByUserIdAndNormalizedName(
                                user.getId(),
                                "groceries"
                        );

        assertThat(exists)
                .isTrue();
    }

    @Test
    void shouldNotTreatAnotherUsersCategoryAsDuplicate() {

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

        categoryRepository.save(
                Category.custom(
                        firstUser,
                        "Groceries",
                        true
                )
        );

        boolean exists =
                categoryRepository
                        .existsByUserIdAndNormalizedName(
                                secondUser.getId(),
                                "groceries"
                        );

        assertThat(exists)
                .isFalse();
    }

    @Test
    void shouldPersistAndLoadCategoryMetadata() {
        User user = userRepository.save(new User("Meta", "User", "meta@example.com", "hashed-password"));
        Category custom = categoryRepository.saveAndFlush(Category.custom(user, "  Eating  Out ", false));
        Category builtIn = categoryRepository.saveAndFlush(Category.builtIn(user, BuiltInCategory.HEALTHCARE));
        entityManager.clear();

        Category loadedCustom = categoryRepository.findById(custom.getId()).orElseThrow();
        assertThat(loadedCustom.getName()).isEqualTo("Eating Out");
        assertThat(loadedCustom.getNormalizedName()).isEqualTo("eating out");
        assertThat(loadedCustom.isBuiltIn()).isFalse();
        assertThat(loadedCustom.getIcon()).isEqualTo(CategoryIcon.TAG);
        assertThat(loadedCustom.isBudgetEnabled()).isFalse();

        Category loadedBuiltIn = categoryRepository.findById(builtIn.getId()).orElseThrow();
        assertThat(loadedBuiltIn.getName()).isEqualTo("Healthcare");
        assertThat(loadedBuiltIn.getNormalizedName()).isEqualTo("healthcare");
        assertThat(loadedBuiltIn.isBuiltIn()).isTrue();
        assertThat(loadedBuiltIn.getIcon()).isEqualTo(CategoryIcon.HEART_PULSE);
        assertThat(loadedBuiltIn.isBudgetEnabled()).isTrue();
        assertThat(jdbc.queryForObject("SELECT icon_key FROM categories WHERE id = ?", String.class, builtIn.getId()))
                .isEqualTo("heart-pulse");
    }

    @Test
    void shouldRejectEquivalentNormalizedNamesForSameUser() {
        User user = userRepository.save(new User("Dup", "User", "dup@example.com", "hashed-password"));
        categoryRepository.saveAndFlush(Category.custom(user, "Eating Out", true));

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(Category.custom(user, "  EATING   out", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowSameNormalizedNameForDifferentUsers() {
        User first = userRepository.save(new User("First", "User", "first@example.com", "hashed-password"));
        User second = userRepository.save(new User("Second", "User", "second@example.com", "hashed-password"));

        categoryRepository.saveAndFlush(Category.custom(first, "Eating Out", true));
        categoryRepository.saveAndFlush(Category.custom(second, "eating out", true));

        assertThat(categoryRepository.existsByUserIdAndNormalizedName(first.getId(), "eating out")).isTrue();
        assertThat(categoryRepository.existsByUserIdAndNormalizedName(second.getId(), "eating out")).isTrue();
    }

    @Test
    void shouldExcludeTheCategoryItselfWhenCheckingRenames() {
        User user = userRepository.save(new User("Rename", "User", "rename@example.com", "hashed-password"));
        Category dining = categoryRepository.saveAndFlush(Category.custom(user, "Dining", true));
        Category travel = categoryRepository.saveAndFlush(Category.custom(user, "Travel", true));

        assertThat(categoryRepository.existsByUserIdAndNormalizedNameAndIdNot(user.getId(), "dining", dining.getId()))
                .isFalse();
        assertThat(categoryRepository.existsByUserIdAndNormalizedNameAndIdNot(user.getId(), "dining", travel.getId()))
                .isTrue();
    }

    @Test
    void shouldBreakNameTiesByIdForDeterministicOrdering() {
        User user = userRepository.save(new User("Order", "User", "order@example.com", "hashed-password"));
        Category first = categoryRepository.saveAndFlush(Category.custom(user, "Zeta", true));
        Category second = categoryRepository.saveAndFlush(Category.custom(user, "Alpha", true));
        // Collation-equal display names can tie in MySQL; force a tie to prove the ID tie-break.
        jdbc.update("UPDATE categories SET name = 'Same' WHERE user_id = ?", user.getId());
        entityManager.clear();

        assertThat(categoryRepository.findAllByUserIdOrderByNameAscIdAsc(user.getId()))
                .extracting(Category::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Test
    void referenceChecksFindTransactionsAndBudgetsFromAnyPeriodForTheOwnerOnly() {
        User owner = userRepository.save(new User("Ref", "Owner", "ref-owner@example.com", "hashed-password"));
        User other = userRepository.save(new User("Ref", "Other", "ref-other@example.com", "hashed-password"));
        Category unused = categoryRepository.saveAndFlush(Category.custom(owner, "Unused", true));
        Category spent = categoryRepository.saveAndFlush(Category.custom(owner, "Spent", true));
        Category budgeted = categoryRepository.saveAndFlush(Category.custom(owner, "Budgeted", true));

        transactionRepository.saveAndFlush(TestDataFactory.createTransaction(owner, spent, TransactionType.EXPENSE,
                new java.math.BigDecimal("5.00"), "Old purchase", java.time.LocalDate.of(2019, 1, 15)));
        budgetRepository.saveAndFlush(TestDataFactory.createBudget(owner, budgeted,
                new java.math.BigDecimal("50.00"), 1, 2019));

        assertThat(transactionRepository.existsByCategoryIdAndUserId(spent.getId(), owner.getId())).isTrue();
        assertThat(budgetRepository.existsByCategoryIdAndUserId(budgeted.getId(), owner.getId())).isTrue();
        assertThat(transactionRepository.existsByCategoryIdAndUserId(budgeted.getId(), owner.getId())).isFalse();
        assertThat(budgetRepository.existsByCategoryIdAndUserId(spent.getId(), owner.getId())).isFalse();
        assertThat(transactionRepository.existsByCategoryIdAndUserId(unused.getId(), owner.getId())).isFalse();
        assertThat(budgetRepository.existsByCategoryIdAndUserId(unused.getId(), owner.getId())).isFalse();
        assertThat(transactionRepository.existsByCategoryIdAndUserId(spent.getId(), other.getId())).isFalse();
        assertThat(budgetRepository.existsByCategoryIdAndUserId(budgeted.getId(), other.getId())).isFalse();
    }
}
