package dev.portfolio.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.portfolio.finance.dto.transaction.TransactionResponse;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.Transaction;
import dev.portfolio.finance.entity.User;
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

    @Test
    void shouldReturnTransactionWhenOwnedByAuthenticatedUser() {
        // Arrange
        User user = TestDataFactory.createUser();
        Category category = TestDataFactory.createCategory(user);
        Transaction transaction =
                TestDataFactory.createExpenseTransaction(user, category);

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
        assertEquals(category.getName(), response.categoryName());
        assertEquals(transaction.getType(), response.type());
        assertEquals(transaction.getAmount(), response.amount());
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
}