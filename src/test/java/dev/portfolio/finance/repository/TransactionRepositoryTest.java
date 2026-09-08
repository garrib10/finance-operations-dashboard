package dev.portfolio.finance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.Transaction;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.projection.CategorySpendingProjection;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class TransactionRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void shouldFindAllTransactionsForUserOrderedByTransactionDateDescending() {

        User firstUser = saveUser(
                "First",
                "User",
                "first@example.com"
        );

        User secondUser = saveUser(
                "Second",
                "User",
                "second@example.com"
        );

        Category firstCategory =
                saveCategory(firstUser, "Groceries");

        Category secondCategory =
                saveCategory(secondUser, "Travel");

        saveTransaction(
                firstUser,
                firstCategory,
                TransactionType.EXPENSE,
                "50.00",
                "Older transaction",
                LocalDate.of(2026, 9, 1)
        );

        saveTransaction(
                firstUser,
                firstCategory,
                TransactionType.EXPENSE,
                "75.00",
                "Newest transaction",
                LocalDate.of(2026, 9, 10)
        );

        saveTransaction(
                firstUser,
                firstCategory,
                TransactionType.EXPENSE,
                "25.00",
                "Middle transaction",
                LocalDate.of(2026, 9, 5)
        );

        saveTransaction(
                secondUser,
                secondCategory,
                TransactionType.EXPENSE,
                "500.00",
                "Other user's transaction",
                LocalDate.of(2026, 9, 20)
        );

        List<Transaction> transactions =
                transactionRepository
                        .findAllByUserIdOrderByTransactionDateDesc(
                                firstUser.getId()
                        );

        assertThat(transactions)
                .hasSize(3);

        assertThat(transactions)
                .extracting(Transaction::getDescription)
                .containsExactly(
                        "Newest transaction",
                        "Middle transaction",
                        "Older transaction"
                );

        assertThat(transactions)
                .allMatch(transaction ->
                        transaction.getUser()
                                .getId()
                                .equals(firstUser.getId())
                );
    }

    @Test
    void shouldFindTransactionByIdAndUserId() {

        User user = saveUser(
                "Test",
                "User",
                "test@example.com"
        );

        Category category =
                saveCategory(user, "Groceries");

        Transaction transaction =
                saveTransaction(
                        user,
                        category,
                        TransactionType.EXPENSE,
                        "100.00",
                        "Groceries",
                        LocalDate.of(2026, 9, 5)
                );

        var result =
                transactionRepository.findByIdAndUserId(
                        transaction.getId(),
                        user.getId()
                );

        assertThat(result)
                .isPresent();

        assertThat(result.get().getDescription())
                .isEqualTo("Groceries");

        assertThat(result.get().getAmount())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void shouldNotFindTransactionOwnedByDifferentUser() {

        User owner = saveUser(
                "Owner",
                "User",
                "owner@example.com"
        );

        User otherUser = saveUser(
                "Other",
                "User",
                "other@example.com"
        );

        Category category =
                saveCategory(owner, "Groceries");

        Transaction transaction =
                saveTransaction(
                        owner,
                        category,
                        TransactionType.EXPENSE,
                        "100.00",
                        "Groceries",
                        LocalDate.of(2026, 9, 5)
                );

        var result =
                transactionRepository.findByIdAndUserId(
                        transaction.getId(),
                        otherUser.getId()
                );

        assertThat(result)
                .isEmpty();
    }

    @Test
    void shouldSumTransactionsByUserAndType() {

        User firstUser = saveUser(
                "First",
                "User",
                "first@example.com"
        );

        User secondUser = saveUser(
                "Second",
                "User",
                "second@example.com"
        );

        Category firstCategory =
                saveCategory(firstUser, "Other");

        Category secondCategory =
                saveCategory(secondUser, "Other");

        saveTransaction(
                firstUser,
                firstCategory,
                TransactionType.EXPENSE,
                "100.00",
                "Expense one",
                LocalDate.of(2026, 9, 1)
        );

        saveTransaction(
                firstUser,
                firstCategory,
                TransactionType.EXPENSE,
                "250.50",
                "Expense two",
                LocalDate.of(2026, 9, 2)
        );

        saveTransaction(
                firstUser,
                firstCategory,
                TransactionType.INCOME,
                "2000.00",
                "Income",
                LocalDate.of(2026, 9, 3)
        );

        saveTransaction(
                secondUser,
                secondCategory,
                TransactionType.EXPENSE,
                "999.00",
                "Other user's expense",
                LocalDate.of(2026, 9, 4)
        );

        BigDecimal total =
                transactionRepository.sumAmountByUserAndType(
                        firstUser.getId(),
                        TransactionType.EXPENSE
                );

        assertThat(total)
                .isEqualByComparingTo("350.50");
    }

    @Test
    void shouldReturnZeroWhenNoTransactionsMatchUserAndType() {

        User user = saveUser(
                "Test",
                "User",
                "test@example.com"
        );

        BigDecimal total =
                transactionRepository.sumAmountByUserAndType(
                        user.getId(),
                        TransactionType.EXPENSE
                );

        assertThat(total)
                .isEqualByComparingTo("0");
    }

    @Test
    void shouldSumTransactionsByUserTypeAndDateRange() {

        User user = saveUser(
                "Test",
                "User",
                "test@example.com"
        );

        Category category =
                saveCategory(user, "Groceries");

        saveTransaction(
                user,
                category,
                TransactionType.EXPENSE,
                "100.00",
                "Start boundary",
                LocalDate.of(2026, 9, 1)
        );

        saveTransaction(
                user,
                category,
                TransactionType.EXPENSE,
                "200.00",
                "Middle",
                LocalDate.of(2026, 9, 15)
        );

        saveTransaction(
                user,
                category,
                TransactionType.EXPENSE,
                "300.00",
                "End boundary",
                LocalDate.of(2026, 9, 30)
        );

        saveTransaction(
                user,
                category,
                TransactionType.EXPENSE,
                "400.00",
                "Outside range",
                LocalDate.of(2026, 10, 1)
        );

        saveTransaction(
                user,
                category,
                TransactionType.INCOME,
                "1000.00",
                "Wrong type",
                LocalDate.of(2026, 9, 10)
        );

        BigDecimal total =
                transactionRepository
                        .sumAmountByUserTypeAndDateRange(
                                user.getId(),
                                TransactionType.EXPENSE,
                                LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 9, 30)
                        );

        assertThat(total)
                .isEqualByComparingTo("600.00");
    }

    @Test
    void shouldSumTransactionsByUserCategoryTypeAndDateRange() {

        User user = saveUser(
                "Test",
                "User",
                "test@example.com"
        );

        Category groceries =
                saveCategory(user, "Groceries");

        Category dining =
                saveCategory(user, "Dining");

        saveTransaction(
                user,
                groceries,
                TransactionType.EXPENSE,
                "100.00",
                "Groceries one",
                LocalDate.of(2026, 9, 5)
        );

        saveTransaction(
                user,
                groceries,
                TransactionType.EXPENSE,
                "150.00",
                "Groceries two",
                LocalDate.of(2026, 9, 10)
        );

        saveTransaction(
                user,
                dining,
                TransactionType.EXPENSE,
                "500.00",
                "Dining",
                LocalDate.of(2026, 9, 12)
        );

        saveTransaction(
                user,
                groceries,
                TransactionType.INCOME,
                "1000.00",
                "Wrong type",
                LocalDate.of(2026, 9, 15)
        );

        saveTransaction(
                user,
                groceries,
                TransactionType.EXPENSE,
                "300.00",
                "Outside range",
                LocalDate.of(2026, 10, 1)
        );

        BigDecimal total =
                transactionRepository
                        .sumAmountByUserCategoryTypeAndDateRange(
                                user.getId(),
                                groceries.getId(),
                                TransactionType.EXPENSE,
                                LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 9, 30)
                        );

        assertThat(total)
                .isEqualByComparingTo("250.00");
    }

    @Test
    void shouldFindTopFiveMostRecentTransactionsForUser() {

        User user = saveUser(
                "Test",
                "User",
                "test@example.com"
        );

        Category category =
                saveCategory(user, "Groceries");

        for (int day = 1; day <= 6; day++) {

            saveTransaction(
                    user,
                    category,
                    TransactionType.EXPENSE,
                    "10.00",
                    "Transaction " + day,
                    LocalDate.of(2026, 9, day)
            );
        }

        List<Transaction> transactions =
                transactionRepository
                        .findTop5ByUserIdOrderByTransactionDateDescCreatedAtDesc(
                                user.getId()
                        );

        assertThat(transactions)
                .hasSize(5);

        assertThat(transactions)
                .extracting(Transaction::getDescription)
                .containsExactly(
                        "Transaction 6",
                        "Transaction 5",
                        "Transaction 4",
                        "Transaction 3",
                        "Transaction 2"
                );
    }

    @Test
    void shouldGroupSpendingByCategoryOrderedByHighestAmount() {

        User firstUser = saveUser(
                "First",
                "User",
                "first@example.com"
        );

        User secondUser = saveUser(
                "Second",
                "User",
                "second@example.com"
        );

        Category groceries =
                saveCategory(firstUser, "Groceries");

        Category dining =
                saveCategory(firstUser, "Dining");

        Category travel =
                saveCategory(firstUser, "Travel");

        Category secondUserCategory =
                saveCategory(secondUser, "Other");

        saveTransaction(
                firstUser,
                groceries,
                TransactionType.EXPENSE,
                "200.00",
                "Groceries one",
                LocalDate.of(2026, 9, 5)
        );

        saveTransaction(
                firstUser,
                groceries,
                TransactionType.EXPENSE,
                "150.00",
                "Groceries two",
                LocalDate.of(2026, 9, 10)
        );

        saveTransaction(
                firstUser,
                dining,
                TransactionType.EXPENSE,
                "500.00",
                "Dining",
                LocalDate.of(2026, 9, 15)
        );

        saveTransaction(
                firstUser,
                travel,
                TransactionType.EXPENSE,
                "100.00",
                "Travel",
                LocalDate.of(2026, 9, 20)
        );

        saveTransaction(
                firstUser,
                groceries,
                TransactionType.INCOME,
                "5000.00",
                "Wrong transaction type",
                LocalDate.of(2026, 9, 12)
        );

        saveTransaction(
                firstUser,
                groceries,
                TransactionType.EXPENSE,
                "900.00",
                "Outside date range",
                LocalDate.of(2026, 10, 1)
        );

        saveTransaction(
                secondUser,
                secondUserCategory,
                TransactionType.EXPENSE,
                "2000.00",
                "Other user's expense",
                LocalDate.of(2026, 9, 15)
        );

        List<CategorySpendingProjection> spending =
                transactionRepository.findSpendingByCategory(
                        firstUser.getId(),
                        TransactionType.EXPENSE,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30)
                );

        assertThat(spending)
                .hasSize(3);

        assertThat(spending.get(0).getCategoryName())
                .isEqualTo("Dining");

        assertThat(spending.get(0).getAmountSpent())
                .isEqualByComparingTo("500.00");

        assertThat(spending.get(1).getCategoryName())
                .isEqualTo("Groceries");

        assertThat(spending.get(1).getAmountSpent())
                .isEqualByComparingTo("350.00");

        assertThat(spending.get(2).getCategoryName())
                .isEqualTo("Travel");

        assertThat(spending.get(2).getAmountSpent())
                .isEqualByComparingTo("100.00");
    }

    private User saveUser(
            String firstName,
            String lastName,
            String email
    ) {
        return userRepository.save(
                new User(
                        firstName,
                        lastName,
                        email,
                        "hashed-password"
                )
        );
    }

    private Category saveCategory(
            User user,
            String name
    ) {
        return categoryRepository.save(
                new Category(
                        user,
                        name,
                        true
                )
        );
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