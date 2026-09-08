package dev.portfolio.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import dev.portfolio.finance.dto.transaction.CreateTransactionRequest;
import dev.portfolio.finance.dto.transaction.PagedTransactionResponse;
import dev.portfolio.finance.dto.transaction.TransactionFilterRequest;
import dev.portfolio.finance.dto.transaction.TransactionResponse;
import dev.portfolio.finance.dto.transaction.UpdateTransactionRequest;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.Transaction;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.category.CategoryNotFoundException;
import dev.portfolio.finance.exception.transaction.InvalidTransactionFilterException;
import dev.portfolio.finance.exception.transaction.TransactionNotFoundException;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.support.TestDataFactory;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final String TEST_EMAIL = "test@example.com";
    private static final Long TRANSACTION_ID = 1L;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private TransactionService transactionService;

    // ---------------------------------------------------------
    // GET TRANSACTION
    // ---------------------------------------------------------

    @Test
    void shouldReturnTransactionWhenOwnedByAuthenticatedUser() {
        // Arrange
        User user = TestDataFactory.createUser();

        Category category =
                TestDataFactory.createCategory(user);

        Transaction transaction =
                TestDataFactory.createExpenseTransaction(
                        user,
                        category
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(transactionRepository.findByIdAndUserId(
                TRANSACTION_ID,
                user.getId()
        )).thenReturn(Optional.of(transaction));

        // Act
        TransactionResponse response =
                transactionService.getTransactionById(
                        TEST_EMAIL,
                        TRANSACTION_ID
                );

        // Assert
        assertEquals(
                category.getName(),
                response.categoryName()
        );

        assertEquals(
                transaction.getType(),
                response.type()
        );

        assertEquals(
                transaction.getAmount(),
                response.amount()
        );

        assertEquals(
                transaction.getDescription(),
                response.description()
        );

        assertEquals(
                transaction.getTransactionDate(),
                response.transactionDate()
        );

        verify(userRepository)
                .findByEmail(TEST_EMAIL);

        verify(transactionRepository)
                .findByIdAndUserId(
                        TRANSACTION_ID,
                        user.getId()
                );
    }

    @Test
    void shouldThrowExceptionWhenTransactionIsNotOwnedByAuthenticatedUser() {
        // Arrange
        User user = TestDataFactory.createUser();

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(transactionRepository.findByIdAndUserId(
                TRANSACTION_ID,
                user.getId()
        )).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                TransactionNotFoundException.class,
                () -> transactionService.getTransactionById(
                        TEST_EMAIL,
                        TRANSACTION_ID
                )
        );

        verify(userRepository)
                .findByEmail(TEST_EMAIL);

        verify(transactionRepository)
                .findByIdAndUserId(
                        TRANSACTION_ID,
                        user.getId()
                );
    }

    // ---------------------------------------------------------
    // CREATE TRANSACTION
    // ---------------------------------------------------------

    @Test
    void shouldCreateTransactionForAuthenticatedUser() {
        // Arrange
        User user = TestDataFactory.createUser();

        Category category =
                TestDataFactory.createCategory(
                        user,
                        "Groceries"
                );

        CreateTransactionRequest request =
                new CreateTransactionRequest(
                        1L,
                        TransactionType.EXPENSE,
                        new BigDecimal("125.50"),
                        "  Weekly groceries  ",
                        LocalDate.of(2026, 9, 5)
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(categoryRepository.findByIdAndUserId(
                request.categoryId(),
                user.getId()
        )).thenReturn(Optional.of(category));

        when(transactionRepository.save(
                any(Transaction.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        // Act
        TransactionResponse response =
                transactionService.createTransaction(
                        TEST_EMAIL,
                        request
                );

        // Assert
        assertEquals(
                "Groceries",
                response.categoryName()
        );

        assertEquals(
                TransactionType.EXPENSE,
                response.type()
        );

        assertEquals(
                new BigDecimal("125.50"),
                response.amount()
        );

        assertEquals(
                "Weekly groceries",
                response.description()
        );

        assertEquals(
                LocalDate.of(2026, 9, 5),
                response.transactionDate()
        );

        verify(categoryRepository)
                .findByIdAndUserId(
                        request.categoryId(),
                        user.getId()
                );

        verify(transactionRepository)
                .save(any(Transaction.class));
    }

    @Test
    void shouldThrowCategoryNotFoundWhenCreatingTransactionWithNonOwnedCategory() {
        // Arrange
        User user = TestDataFactory.createUser();

        CreateTransactionRequest request =
                new CreateTransactionRequest(
                        999L,
                        TransactionType.EXPENSE,
                        new BigDecimal("50.00"),
                        "Test expense",
                        LocalDate.of(2026, 9, 5)
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(categoryRepository.findByIdAndUserId(
                request.categoryId(),
                user.getId()
        )).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                CategoryNotFoundException.class,
                () -> transactionService.createTransaction(
                        TEST_EMAIL,
                        request
                )
        );

        verify(transactionRepository, never())
                .save(any(Transaction.class));
    }

    // ---------------------------------------------------------
    // UPDATE TRANSACTION
    // ---------------------------------------------------------

    @Test
    void shouldUpdateOwnedTransaction() {
        // Arrange
        User user = TestDataFactory.createUser();

        Category originalCategory =
                TestDataFactory.createCategory(
                        user,
                        "Groceries"
                );

        Category updatedCategory =
                TestDataFactory.createCategory(
                        user,
                        "Dining"
                );

        Transaction transaction =
                TestDataFactory.createExpenseTransaction(
                        user,
                        originalCategory
                );

        UpdateTransactionRequest request =
                new UpdateTransactionRequest(
                        2L,
                        TransactionType.EXPENSE,
                        new BigDecimal("80.00"),
                        "  Dinner with friends  ",
                        LocalDate.of(2026, 9, 6)
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(transactionRepository.findByIdAndUserId(
                TRANSACTION_ID,
                user.getId()
        )).thenReturn(Optional.of(transaction));

        when(categoryRepository.findByIdAndUserId(
                request.categoryId(),
                user.getId()
        )).thenReturn(Optional.of(updatedCategory));

        when(transactionRepository.save(transaction))
                .thenReturn(transaction);

        // Act
        TransactionResponse response =
                transactionService.updateTransaction(
                        TEST_EMAIL,
                        TRANSACTION_ID,
                        request
                );

        // Assert
        assertEquals(
                "Dining",
                response.categoryName()
        );

        assertEquals(
                new BigDecimal("80.00"),
                response.amount()
        );

        assertEquals(
                "Dinner with friends",
                response.description()
        );

        assertEquals(
                LocalDate.of(2026, 9, 6),
                response.transactionDate()
        );

        verify(transactionRepository)
                .save(transaction);
    }

    @Test
    void shouldThrowCategoryNotFoundWhenUpdatingWithNonOwnedCategory() {
        // Arrange
        User user = TestDataFactory.createUser();

        Category category =
                TestDataFactory.createCategory(
                        user,
                        "Groceries"
                );

        Transaction transaction =
                TestDataFactory.createExpenseTransaction(
                        user,
                        category
                );

        UpdateTransactionRequest request =
                new UpdateTransactionRequest(
                        999L,
                        TransactionType.EXPENSE,
                        new BigDecimal("80.00"),
                        "Updated expense",
                        LocalDate.of(2026, 9, 6)
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(transactionRepository.findByIdAndUserId(
                TRANSACTION_ID,
                user.getId()
        )).thenReturn(Optional.of(transaction));

        when(categoryRepository.findByIdAndUserId(
                request.categoryId(),
                user.getId()
        )).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                CategoryNotFoundException.class,
                () -> transactionService.updateTransaction(
                        TEST_EMAIL,
                        TRANSACTION_ID,
                        request
                )
        );

        verify(transactionRepository, never())
                .save(transaction);
    }

    // ---------------------------------------------------------
    // DELETE TRANSACTION
    // ---------------------------------------------------------

    @Test
    void shouldDeleteOwnedTransaction() {
        // Arrange
        User user = TestDataFactory.createUser();

        Category category =
                TestDataFactory.createCategory(user);

        Transaction transaction =
                TestDataFactory.createExpenseTransaction(
                        user,
                        category
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(transactionRepository.findByIdAndUserId(
                TRANSACTION_ID,
                user.getId()
        )).thenReturn(Optional.of(transaction));

        // Act
        transactionService.deleteTransaction(
                TEST_EMAIL,
                TRANSACTION_ID
        );

        // Assert
        verify(transactionRepository)
                .delete(transaction);
    }

    // ---------------------------------------------------------
    // SEARCH / FILTERING
    // ---------------------------------------------------------

    @Test
    void shouldUseDefaultPaginationAndSortingWhenFiltersOmitted() {
        // Arrange
        User user = TestDataFactory.createUser();

        TransactionFilterRequest filters =
                new TransactionFilterRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        Page<Transaction> emptyPage =
                new PageImpl<>(List.of());

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(transactionRepository.findAll(
                ArgumentMatchers.<Specification<Transaction>>any(),
                any(Pageable.class)
        )).thenReturn(emptyPage);

        // Act
        PagedTransactionResponse response =
                transactionService.searchTransactions(
                        TEST_EMAIL,
                        filters
                );

        // Assert
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(transactionRepository)
                .findAll(
                        ArgumentMatchers.<Specification<Transaction>>any(),
                        pageableCaptor.capture()
                );

        Pageable pageable =
                pageableCaptor.getValue();

        assertEquals(
                0,
                pageable.getPageNumber()
        );

        assertEquals(
                20,
                pageable.getPageSize()
        );

        assertEquals(
                "transactionDate: DESC",
                pageable.getSort().toString()
        );

        assertEquals(
                0,
                response.totalElements()
        );
    }

    @Test
    void shouldRejectStartDateAfterEndDate() {
        // Arrange
        User user = TestDataFactory.createUser();

        TransactionFilterRequest filters =
                new TransactionFilterRequest(
                        null,
                        null,
                        LocalDate.of(2026, 9, 30),
                        LocalDate.of(2026, 9, 1),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        // Act + Assert
        InvalidTransactionFilterException exception =
                assertThrows(
                        InvalidTransactionFilterException.class,
                        () -> transactionService.searchTransactions(
                                TEST_EMAIL,
                                filters
                        )
                );

        assertEquals(
                "Start date cannot be after end date",
                exception.getMessage()
        );

        verify(transactionRepository, never())
                .findAll(
                        ArgumentMatchers.<Specification<Transaction>>any(),
                        any(Pageable.class)
                );
    }

    @Test
    void shouldRejectMinimumAmountGreaterThanMaximumAmount() {
        // Arrange
        User user = TestDataFactory.createUser();

        TransactionFilterRequest filters =
                new TransactionFilterRequest(
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("500.00"),
                        new BigDecimal("100.00"),
                        null,
                        null,
                        null,
                        null
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        // Act + Assert
        InvalidTransactionFilterException exception =
                assertThrows(
                        InvalidTransactionFilterException.class,
                        () -> transactionService.searchTransactions(
                                TEST_EMAIL,
                                filters
                        )
                );

        assertEquals(
                "Minimum amount cannot be greater than maximum amount",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectInvalidSortField() {
        // Arrange
        User user = TestDataFactory.createUser();

        TransactionFilterRequest filters =
                new TransactionFilterRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "description",
                        "asc",
                        0,
                        20
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        // Act + Assert
        InvalidTransactionFilterException exception =
                assertThrows(
                        InvalidTransactionFilterException.class,
                        () -> transactionService.searchTransactions(
                                TEST_EMAIL,
                                filters
                        )
                );

        assertEquals(
                "Invalid sort field",
                exception.getMessage()
        );
    }

        // ---------------------------------------------------------
    // GET ALL TRANSACTIONS
    // ---------------------------------------------------------

    @Test
    void shouldReturnAllTransactionsForAuthenticatedUser() {
        // Arrange
        User user = TestDataFactory.createUser();

        Category category =
                TestDataFactory.createCategory(
                        user,
                        "Groceries"
                );

        Transaction firstTransaction =
                new Transaction(
                        user,
                        category,
                        TransactionType.EXPENSE,
                        new BigDecimal("125.50"),
                        "Weekly groceries",
                        LocalDate.of(2026, 9, 5)
                );

        Transaction secondTransaction =
                new Transaction(
                        user,
                        category,
                        TransactionType.EXPENSE,
                        new BigDecimal("45.00"),
                        "Additional groceries",
                        LocalDate.of(2026, 9, 4)
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(
                transactionRepository
                        .findAllByUserIdOrderByTransactionDateDesc(
                                user.getId()
                        )
        ).thenReturn(
                List.of(
                        firstTransaction,
                        secondTransaction
                )
        );

        // Act
        List<TransactionResponse> responses =
                transactionService.getAllTransactions(
                        TEST_EMAIL
                );

        // Assert
        assertEquals(2, responses.size());

        assertEquals(
                "Weekly groceries",
                responses.get(0).description()
        );

        assertEquals(
                new BigDecimal("125.50"),
                responses.get(0).amount()
        );

        assertEquals(
                "Additional groceries",
                responses.get(1).description()
        );

        verify(userRepository)
                .findByEmail(TEST_EMAIL);

        verify(transactionRepository)
                .findAllByUserIdOrderByTransactionDateDesc(
                        user.getId()
                );
    }

    // ---------------------------------------------------------
    // ADDITIONAL UPDATE / DELETE NEGATIVE TESTS
    // ---------------------------------------------------------

    @Test
    void shouldThrowTransactionNotFoundWhenUpdatingMissingTransaction() {
        // Arrange
        User user = TestDataFactory.createUser();

        UpdateTransactionRequest request =
                new UpdateTransactionRequest(
                        1L,
                        TransactionType.EXPENSE,
                        new BigDecimal("80.00"),
                        "Updated expense",
                        LocalDate.of(2026, 9, 6)
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(transactionRepository.findByIdAndUserId(
                TRANSACTION_ID,
                user.getId()
        )).thenReturn(Optional.empty());

        // Act + Assert
        TransactionNotFoundException exception =
                assertThrows(
                        TransactionNotFoundException.class,
                        () -> transactionService.updateTransaction(
                                TEST_EMAIL,
                                TRANSACTION_ID,
                                request
                        )
                );

        assertEquals(
                "Transaction not found",
                exception.getMessage()
        );

        verify(categoryRepository, never())
                .findByIdAndUserId(
                        request.categoryId(),
                        user.getId()
                );

        verify(transactionRepository, never())
                .save(any(Transaction.class));
    }

    @Test
    void shouldThrowTransactionNotFoundWhenDeletingMissingTransaction() {
        // Arrange
        User user = TestDataFactory.createUser();

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(transactionRepository.findByIdAndUserId(
                TRANSACTION_ID,
                user.getId()
        )).thenReturn(Optional.empty());

        // Act + Assert
        TransactionNotFoundException exception =
                assertThrows(
                        TransactionNotFoundException.class,
                        () -> transactionService.deleteTransaction(
                                TEST_EMAIL,
                                TRANSACTION_ID
                        )
                );

        assertEquals(
                "Transaction not found",
                exception.getMessage()
        );

        verify(transactionRepository, never())
                .delete(any(Transaction.class));
    }

    // ---------------------------------------------------------
    // ADDITIONAL SEARCH / FILTER VALIDATION
    // ---------------------------------------------------------

    @Test
    void shouldRejectNegativeMinimumAmount() {
        // Arrange
        User user = TestDataFactory.createUser();

        TransactionFilterRequest filters =
                new TransactionFilterRequest(
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("-1.00"),
                        null,
                        null,
                        null,
                        null,
                        null
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        // Act + Assert
        InvalidTransactionFilterException exception =
                assertThrows(
                        InvalidTransactionFilterException.class,
                        () -> transactionService.searchTransactions(
                                TEST_EMAIL,
                                filters
                        )
                );

        assertEquals(
                "Minimum amount cannot be negative",
                exception.getMessage()
        );

        verify(transactionRepository, never())
                .findAll(
                        ArgumentMatchers
                                .<Specification<Transaction>>any(),
                        any(Pageable.class)
                );
    }

    @Test
    void shouldRejectNegativeMaximumAmount() {
        // Arrange
        User user = TestDataFactory.createUser();

        TransactionFilterRequest filters =
                new TransactionFilterRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("-1.00"),
                        null,
                        null,
                        null,
                        null
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        // Act + Assert
        InvalidTransactionFilterException exception =
                assertThrows(
                        InvalidTransactionFilterException.class,
                        () -> transactionService.searchTransactions(
                                TEST_EMAIL,
                                filters
                        )
                );

        assertEquals(
                "Maximum amount cannot be negative",
                exception.getMessage()
        );

        verify(transactionRepository, never())
                .findAll(
                        ArgumentMatchers
                                .<Specification<Transaction>>any(),
                        any(Pageable.class)
                );
    }

    @Test
    void shouldRejectInvalidSortDirection() {
        // Arrange
        User user = TestDataFactory.createUser();

        TransactionFilterRequest filters =
                new TransactionFilterRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "transactionDate",
                        "sideways",
                        0,
                        20
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        // Act + Assert
        InvalidTransactionFilterException exception =
                assertThrows(
                        InvalidTransactionFilterException.class,
                        () -> transactionService.searchTransactions(
                                TEST_EMAIL,
                                filters
                        )
                );

        assertEquals(
                "Sort direction must be asc or desc",
                exception.getMessage()
        );

        verify(transactionRepository, never())
                .findAll(
                        ArgumentMatchers
                                .<Specification<Transaction>>any(),
                        any(Pageable.class)
                );
    }

    @Test
    void shouldRejectNegativePageNumber() {
        // Arrange
        User user = TestDataFactory.createUser();

        TransactionFilterRequest filters =
                new TransactionFilterRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        -1,
                        20
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        // Act + Assert
        InvalidTransactionFilterException exception =
                assertThrows(
                        InvalidTransactionFilterException.class,
                        () -> transactionService.searchTransactions(
                                TEST_EMAIL,
                                filters
                        )
                );

        assertEquals(
                "Page cannot be negative",
                exception.getMessage()
        );

        verify(transactionRepository, never())
                .findAll(
                        ArgumentMatchers
                                .<Specification<Transaction>>any(),
                        any(Pageable.class)
                );
    }

    @Test
    void shouldRejectPageSizeBelowMinimum() {
        // Arrange
        User user = TestDataFactory.createUser();

        TransactionFilterRequest filters =
                new TransactionFilterRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        0
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        // Act + Assert
        InvalidTransactionFilterException exception =
                assertThrows(
                        InvalidTransactionFilterException.class,
                        () -> transactionService.searchTransactions(
                                TEST_EMAIL,
                                filters
                        )
                );

        assertEquals(
                "Size must be between 1 and 100",
                exception.getMessage()
        );

        verify(transactionRepository, never())
                .findAll(
                        ArgumentMatchers
                                .<Specification<Transaction>>any(),
                        any(Pageable.class)
                );
    }

    @Test
    void shouldRejectPageSizeAboveMaximum() {
        // Arrange
        User user = TestDataFactory.createUser();

        TransactionFilterRequest filters =
                new TransactionFilterRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        101
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        // Act + Assert
        InvalidTransactionFilterException exception =
                assertThrows(
                        InvalidTransactionFilterException.class,
                        () -> transactionService.searchTransactions(
                                TEST_EMAIL,
                                filters
                        )
                );

        assertEquals(
                "Size must be between 1 and 100",
                exception.getMessage()
        );

        verify(transactionRepository, never())
                .findAll(
                        ArgumentMatchers
                                .<Specification<Transaction>>any(),
                        any(Pageable.class)
                );
    }

    @Test
    void shouldUseExplicitPaginationAndAscendingSort() {
        // Arrange
        User user = TestDataFactory.createUser();

        TransactionFilterRequest filters =
                new TransactionFilterRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "amount",
                        "asc",
                        1,
                        10
                );

        Page<Transaction> emptyPage =
                new PageImpl<>(List.of());

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(transactionRepository.findAll(
                ArgumentMatchers
                        .<Specification<Transaction>>any(),
                any(Pageable.class)
        )).thenReturn(emptyPage);

        // Act
        transactionService.searchTransactions(
                TEST_EMAIL,
                filters
        );

        // Assert
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(transactionRepository)
                .findAll(
                        ArgumentMatchers
                                .<Specification<Transaction>>any(),
                        pageableCaptor.capture()
                );

        Pageable pageable =
                pageableCaptor.getValue();

        assertEquals(
                1,
                pageable.getPageNumber()
        );

        assertEquals(
                10,
                pageable.getPageSize()
        );

        assertEquals(
                "amount: ASC",
                pageable.getSort().toString()
        );
    }

        @Test
    void shouldAcceptValidCompleteFilterRange() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        TransactionFilterRequest filters =
                new TransactionFilterRequest(
                        TransactionType.EXPENSE,
                        "groceries",
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30),
                        new BigDecimal("25.00"),
                        new BigDecimal("500.00"),
                        "transactionDate",
                        "asc",
                        0,
                        20
                );

        Page<Transaction> emptyPage =
                new PageImpl<>(List.of());

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(transactionRepository.findAll(
                ArgumentMatchers
                        .<Specification<Transaction>>any(),
                any(Pageable.class)
        )).thenReturn(emptyPage);

        // Act
        PagedTransactionResponse response =
                transactionService.searchTransactions(
                        TEST_EMAIL,
                        filters
                );

        // Assert
        assertEquals(
                0,
                response.totalElements()
        );

        verify(transactionRepository)
                .findAll(
                        ArgumentMatchers
                                .<Specification<Transaction>>any(),
                        any(Pageable.class)
                );
    }

    @Test
    void shouldUseExplicitDescendingSort() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        TransactionFilterRequest filters =
                new TransactionFilterRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "amount",
                        "desc",
                        2,
                        15
                );

        Page<Transaction> emptyPage =
                new PageImpl<>(List.of());

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(transactionRepository.findAll(
                ArgumentMatchers
                        .<Specification<Transaction>>any(),
                any(Pageable.class)
        )).thenReturn(emptyPage);

        // Act
        transactionService.searchTransactions(
                TEST_EMAIL,
                filters
        );

        // Assert
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(transactionRepository)
                .findAll(
                        ArgumentMatchers
                                .<Specification<Transaction>>any(),
                        pageableCaptor.capture()
                );

        Pageable pageable =
                pageableCaptor.getValue();

        assertEquals(
                2,
                pageable.getPageNumber()
        );

        assertEquals(
                15,
                pageable.getPageSize()
        );

        assertEquals(
                "amount: DESC",
                pageable.getSort().toString()
        );
    }
}