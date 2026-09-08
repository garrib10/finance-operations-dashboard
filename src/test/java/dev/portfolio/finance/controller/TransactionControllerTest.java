package dev.portfolio.finance.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import dev.portfolio.finance.service.TransactionService;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    private static final String TEST_EMAIL = "test@example.com";
    private static final Long TRANSACTION_ID = 1L;

    @Mock
    private TransactionService transactionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TransactionController transactionController =
                new TransactionController(transactionService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(transactionController)
                .build();
    }

    @Test
    void shouldDeleteTransactionAndReturnNoContent() throws Exception {
        // Arrange
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        TEST_EMAIL,
                        null,
                        List.of()
                );

        // Act + Assert
        mockMvc.perform(
                        delete(
                                "/api/transactions/{id}",
                                TRANSACTION_ID
                        )
                                .principal(authentication)
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(transactionService)
                .deleteTransaction(
                        TEST_EMAIL,
                        TRANSACTION_ID
                );
    }
}