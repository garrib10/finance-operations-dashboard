package dev.portfolio.finance.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import dev.portfolio.finance.dto.transaction.CreateTransactionRequest;
import dev.portfolio.finance.dto.transaction.PagedTransactionResponse;
import dev.portfolio.finance.dto.transaction.TransactionFilterRequest;
import dev.portfolio.finance.dto.transaction.UpdateTransactionRequest;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.exception.GlobalExceptionHandler;
import dev.portfolio.finance.exception.transaction.InvalidTransactionFilterException;
import dev.portfolio.finance.exception.transaction.TransactionNotFoundException;
import dev.portfolio.finance.service.TransactionService;

class TransactionControllerTest {

    private TransactionService transactionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        transactionService = org.mockito.Mockito.mock(TransactionService.class);

        TransactionController transactionController =
                new TransactionController(transactionService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(transactionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateTransactionWhenRequestIsValid() throws Exception {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "user@example.com",
                        null
                );

        mockMvc.perform(post("/api/transactions")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": 2,
                                  "type": "EXPENSE",
                                  "amount": 125.50,
                                  "description": "Groceries",
                                  "transactionDate": "2026-09-08"
                                }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateTransactionRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateTransactionRequest.class);

        verify(transactionService).createTransaction(
                eq("user@example.com"),
                requestCaptor.capture()
        );

        CreateTransactionRequest capturedRequest = requestCaptor.getValue();

        assertEquals(2L, capturedRequest.categoryId());
        assertEquals(
                new BigDecimal("125.50"),
                capturedRequest.amount()
        );
        assertEquals("Groceries", capturedRequest.description());
    }

    @Test
    void shouldRejectTransactionWhenAmountIsInvalid() throws Exception {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "user@example.com",
                        null
                );

        mockMvc.perform(post("/api/transactions")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": 2,
                                  "type": "EXPENSE",
                                  "amount": -10.00,
                                  "description": "Groceries",
                                  "transactionDate": "2026-09-08"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectTransactionWhenDescriptionIsBlank() throws Exception {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "user@example.com",
                        null
                );

        mockMvc.perform(post("/api/transactions")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": 2,
                                  "type": "EXPENSE",
                                  "amount": 125.50,
                                  "description": "",
                                  "transactionDate": "2026-09-08"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnTransactionNotFoundWhenTransactionDoesNotExist()
            throws Exception {

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "user@example.com",
                        null
                );

        when(transactionService.getTransactionById(
                "user@example.com",
                999L
        )).thenThrow(
                new TransactionNotFoundException(
                        "Transaction not found."
                )
        );

        mockMvc.perform(get("/api/transactions/999")
                        .principal(authentication))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidTransactionFilters() throws Exception {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "user@example.com",
                        null
                );

        when(transactionService.searchTransactions(
                eq("user@example.com"),
                any(TransactionFilterRequest.class)
        )).thenThrow(
                new InvalidTransactionFilterException(
                        "Minimum amount cannot be greater than maximum amount."
                )
        );

        mockMvc.perform(get("/api/transactions")
                        .principal(authentication)
                        .param("minAmount", "500")
                        .param("maxAmount", "100"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldDeleteTransactionWhenTransactionExists() throws Exception {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "user@example.com",
                        null
                );

        mockMvc.perform(delete("/api/transactions/1")
                        .principal(authentication))
                .andExpect(status().isNoContent());

        verify(transactionService).deleteTransaction(
                "user@example.com",
                1L
        );
    }

    @Test
    void shouldReturnTransactionsForAuthenticatedUser() throws Exception {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "user@example.com",
                        null
                );

        PagedTransactionResponse response =
                new PagedTransactionResponse(
                        java.util.List.of(),
                        0,
                        20,
                        0L,
                        0
                );

        when(transactionService.searchTransactions(
                eq("user@example.com"),
                any(TransactionFilterRequest.class)
        )).thenReturn(response);

        mockMvc.perform(get("/api/transactions")
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(transactionService).searchTransactions(
                eq("user@example.com"),
                any(TransactionFilterRequest.class)
        );
    }

    @Test
    void shouldUpdateTransactionWhenRequestIsValid() throws Exception {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "user@example.com",
                        null
                );

        mockMvc.perform(put("/api/transactions/1")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": 2,
                                  "type": "EXPENSE",
                                  "amount": 150.00,
                                  "description": "Updated groceries",
                                  "transactionDate": "2026-09-08"
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateTransactionRequest> requestCaptor =
                ArgumentCaptor.forClass(UpdateTransactionRequest.class);

        verify(transactionService).updateTransaction(
                eq("user@example.com"),
                eq(1L),
                requestCaptor.capture()
        );

        UpdateTransactionRequest capturedRequest = requestCaptor.getValue();

        assertEquals(2L, capturedRequest.categoryId());
        assertEquals(
                TransactionType.EXPENSE,
                capturedRequest.type()
        );
        assertEquals(
                new BigDecimal("150.00"),
                capturedRequest.amount()
        );
        assertEquals(
                "Updated groceries",
                capturedRequest.description()
        );
        assertEquals(
                LocalDate.of(2026, 9, 8),
                capturedRequest.transactionDate()
        );
    }
}