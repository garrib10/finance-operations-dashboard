package dev.portfolio.finance.specification;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.Transaction;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class TransactionSpecificationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private User primaryUser;
    private User otherUser;

    private Category groceriesCategory;
    private Category incomeCategory;
    private Category otherUserCategory;

    @BeforeEach
    void setUp() {
        primaryUser = userRepository.save(
                new User(
                        "Test",
                        "User",
                        "test@example.com",
                        "password-hash"
                )
        );

        otherUser = userRepository.save(
                new User(
                        "Other",
                        "User",
                        "other@example.com",
                        "password-hash"
                )
        );

        groceriesCategory = categoryRepository.save(
                new Category(
                        primaryUser,
                        "Groceries",
                        true
                )
        );

        incomeCategory = categoryRepository.save(
                new Category(
                        primaryUser,
                        "Income",
                        false
                )
        );

        otherUserCategory = categoryRepository.save(
                new Category(
                        otherUser,
                        "Groceries",
                        true
                )
        );
    }

    @Test
    void shouldFilterTransactionsByUser() {
        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "125.00",
                "Weekly groceries",
                LocalDate.of(2026, 9, 1)
        );

        saveTransaction(
                otherUser,
                otherUserCategory,
                TransactionType.EXPENSE,
                "80.00",
                "Other user groceries",
                LocalDate.of(2026, 9, 1)
        );

        List<Transaction> results = transactionRepository.findAll(
                TransactionSpecification.belongsToUser(
                        primaryUser.getId()
                )
        );

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getUser().getId())
                .isEqualTo(primaryUser.getId());
    }

    @Test
    void shouldFilterTransactionsByType() {
        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "125.00",
                "Groceries",
                LocalDate.of(2026, 9, 1)
        );

        saveTransaction(
                primaryUser,
                incomeCategory,
                TransactionType.INCOME,
                "2500.00",
                "Paycheck",
                LocalDate.of(2026, 9, 2)
        );

        Specification<Transaction> specification =
                TransactionSpecification.belongsToUser(primaryUser.getId())
                        .and(
                                TransactionSpecification.hasType(
                                        TransactionType.EXPENSE
                                )
                        );

        List<Transaction> results =
                transactionRepository.findAll(specification);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getType())
                .isEqualTo(TransactionType.EXPENSE);
    }

    @Test
    void shouldNotRestrictResultsWhenTypeIsNull() {
        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "125.00",
                "Groceries",
                LocalDate.of(2026, 9, 1)
        );

        saveTransaction(
                primaryUser,
                incomeCategory,
                TransactionType.INCOME,
                "2500.00",
                "Paycheck",
                LocalDate.of(2026, 9, 2)
        );

        Specification<Transaction> specification =
                TransactionSpecification.belongsToUser(primaryUser.getId())
                        .and(
                                TransactionSpecification.hasType(null)
                        );

        List<Transaction> results =
                transactionRepository.findAll(specification);

        assertThat(results).hasSize(2);
    }

    @Test
    void shouldFilterDescriptionCaseInsensitivelyAndTrimSearchText() {
        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "125.00",
                "Weekly Grocery Shopping",
                LocalDate.of(2026, 9, 1)
        );

        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "65.00",
                "Gas station",
                LocalDate.of(2026, 9, 2)
        );

        Specification<Transaction> specification =
                TransactionSpecification.belongsToUser(primaryUser.getId())
                        .and(
                                TransactionSpecification.descriptionContains(
                                        "  GROCERY  "
                                )
                        );

        List<Transaction> results =
                transactionRepository.findAll(specification);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getDescription())
                .isEqualTo("Weekly Grocery Shopping");
    }

    @Test
    void shouldNotRestrictResultsWhenDescriptionSearchIsBlank() {
        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "125.00",
                "Groceries",
                LocalDate.of(2026, 9, 1)
        );

        saveTransaction(
                primaryUser,
                incomeCategory,
                TransactionType.INCOME,
                "2500.00",
                "Paycheck",
                LocalDate.of(2026, 9, 2)
        );

        Specification<Transaction> specification =
                TransactionSpecification.belongsToUser(primaryUser.getId())
                        .and(
                                TransactionSpecification.descriptionContains(
                                        "   "
                                )
                        );

        List<Transaction> results =
                transactionRepository.findAll(specification);

        assertThat(results).hasSize(2);
    }

    @Test
    void shouldIncludeTransactionsOnOrAfterStartDate() {
        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "40.00",
                "Before range",
                LocalDate.of(2026, 8, 31)
        );

        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "50.00",
                "Start date",
                LocalDate.of(2026, 9, 1)
        );

        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "60.00",
                "After start date",
                LocalDate.of(2026, 9, 2)
        );

        Specification<Transaction> specification =
                TransactionSpecification.belongsToUser(primaryUser.getId())
                        .and(
                                TransactionSpecification.dateOnOrAfter(
                                        LocalDate.of(2026, 9, 1)
                                )
                        );

        List<Transaction> results =
                transactionRepository.findAll(specification);

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(Transaction::getTransactionDate)
                .containsExactlyInAnyOrder(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 2)
                );
    }

    @Test
    void shouldIncludeTransactionsOnOrBeforeEndDate() {
        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "40.00",
                "Before end date",
                LocalDate.of(2026, 8, 31)
        );

        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "50.00",
                "End date",
                LocalDate.of(2026, 9, 1)
        );

        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "60.00",
                "After end date",
                LocalDate.of(2026, 9, 2)
        );

        Specification<Transaction> specification =
                TransactionSpecification.belongsToUser(primaryUser.getId())
                        .and(
                                TransactionSpecification.dateOnOrBefore(
                                        LocalDate.of(2026, 9, 1)
                                )
                        );

        List<Transaction> results =
                transactionRepository.findAll(specification);

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(Transaction::getTransactionDate)
                .containsExactlyInAnyOrder(
                        LocalDate.of(2026, 8, 31),
                        LocalDate.of(2026, 9, 1)
                );
    }

    @Test
    void shouldIncludeTransactionsAtOrAboveMinimumAmount() {
        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "49.99",
                "Below minimum",
                LocalDate.of(2026, 9, 1)
        );

        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "50.00",
                "Exact minimum",
                LocalDate.of(2026, 9, 2)
        );

        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "75.00",
                "Above minimum",
                LocalDate.of(2026, 9, 3)
        );

        Specification<Transaction> specification =
                TransactionSpecification.belongsToUser(primaryUser.getId())
                        .and(
                                TransactionSpecification.amountAtLeast(
                                        new BigDecimal("50.00")
                                )
                        );

        List<Transaction> results =
                transactionRepository.findAll(specification);

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(Transaction::getAmount)
                .allSatisfy(amount ->
                        assertThat(amount)
                                .isGreaterThanOrEqualTo(
                                        new BigDecimal("50.00")
                                )
                );
    }

    @Test
    void shouldIncludeTransactionsAtOrBelowMaximumAmount() {
        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "25.00",
                "Below maximum",
                LocalDate.of(2026, 9, 1)
        );

        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "50.00",
                "Exact maximum",
                LocalDate.of(2026, 9, 2)
        );

        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "75.00",
                "Above maximum",
                LocalDate.of(2026, 9, 3)
        );

        Specification<Transaction> specification =
                TransactionSpecification.belongsToUser(primaryUser.getId())
                        .and(
                                TransactionSpecification.amountAtMost(
                                        new BigDecimal("50.00")
                                )
                        );

        List<Transaction> results =
                transactionRepository.findAll(specification);

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(Transaction::getAmount)
                .allSatisfy(amount ->
                        assertThat(amount)
                                .isLessThanOrEqualTo(
                                        new BigDecimal("50.00")
                                )
                );
    }

    @Test
    void shouldCombineMultipleTransactionFilters() {
        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "125.00",
                "Weekly grocery run",
                LocalDate.of(2026, 9, 5)
        );

        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "35.00",
                "Small grocery purchase",
                LocalDate.of(2026, 9, 5)
        );

        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "125.00",
                "Restaurant dinner",
                LocalDate.of(2026, 9, 5)
        );

        saveTransaction(
                primaryUser,
                incomeCategory,
                TransactionType.INCOME,
                "125.00",
                "Grocery reimbursement",
                LocalDate.of(2026, 9, 5)
        );

        saveTransaction(
                primaryUser,
                groceriesCategory,
                TransactionType.EXPENSE,
                "125.00",
                "Old grocery purchase",
                LocalDate.of(2026, 8, 20)
        );

        saveTransaction(
                otherUser,
                otherUserCategory,
                TransactionType.EXPENSE,
                "125.00",
                "Weekly grocery run",
                LocalDate.of(2026, 9, 5)
        );

        Specification<Transaction> specification =
                TransactionSpecification.belongsToUser(primaryUser.getId())
                        .and(
                                TransactionSpecification.hasType(
                                        TransactionType.EXPENSE
                                )
                        )
                        .and(
                                TransactionSpecification.descriptionContains(
                                        "grocery"
                                )
                        )
                        .and(
                                TransactionSpecification.dateOnOrAfter(
                                        LocalDate.of(2026, 9, 1)
                                )
                        )
                        .and(
                                TransactionSpecification.dateOnOrBefore(
                                        LocalDate.of(2026, 9, 30)
                                )
                        )
                        .and(
                                TransactionSpecification.amountAtLeast(
                                        new BigDecimal("100.00")
                                )
                        )
                        .and(
                                TransactionSpecification.amountAtMost(
                                        new BigDecimal("150.00")
                                )
                        );

        List<Transaction> results =
                transactionRepository.findAll(specification);

        assertThat(results).hasSize(1);

        Transaction result = results.getFirst();

        assertThat(result.getUser().getId())
                .isEqualTo(primaryUser.getId());

        assertThat(result.getType())
                .isEqualTo(TransactionType.EXPENSE);

        assertThat(result.getDescription())
                .isEqualTo("Weekly grocery run");

        assertThat(result.getAmount())
                .isEqualByComparingTo("125.00");

        assertThat(result.getTransactionDate())
                .isEqualTo(LocalDate.of(2026, 9, 5));
    }

    private Transaction saveTransaction(
            User user,
            Category category,
            TransactionType type,
            String amount,
            String description,
            LocalDate transactionDate
    ) {
        return transactionRepository.save(
                new Transaction(
                        user,
                        category,
                        type,
                        new BigDecimal(amount),
                        description,
                        transactionDate
                )
        );
    }
}