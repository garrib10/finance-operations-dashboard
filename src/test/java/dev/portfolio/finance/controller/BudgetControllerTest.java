package dev.portfolio.finance.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import dev.portfolio.finance.dto.budget.CreateBudgetRequest;
import dev.portfolio.finance.exception.GlobalExceptionHandler;
import dev.portfolio.finance.exception.budget.BudgetNotFoundException;
import dev.portfolio.finance.exception.budget.DuplicateBudgetException;
import dev.portfolio.finance.service.BudgetService;
import dev.portfolio.finance.dto.budget.BudgetResponse;
import dev.portfolio.finance.dto.budget.UpdateBudgetRequest;

@ExtendWith(MockitoExtension.class)
class BudgetControllerTest {

    private static final String TEST_EMAIL =
            "test@example.com";

    @Mock
    private BudgetService budgetService;

    private MockMvc mockMvc;

    private TestingAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        BudgetController controller =
                new BudgetController(
                        budgetService
                );

        mockMvc =
                MockMvcBuilders
                        .standaloneSetup(controller)
                        .setControllerAdvice(
                                new GlobalExceptionHandler()
                        )
                        .build();

        authentication =
                new TestingAuthenticationToken(
                        TEST_EMAIL,
                        null
                );
    }

    @Test
    void shouldCreateBudgetWhenRequestIsValid()
            throws Exception {

        String requestBody = """
                {
                  "categoryId": 1,
                  "monthlyLimit": 700.00,
                  "month": 9,
                  "year": 2026
                }
                """;

        when(budgetService.createBudget(
                any(String.class),
                any(CreateBudgetRequest.class)
        )).thenReturn(null);

        mockMvc.perform(
                        post("/api/budgets")
                                .principal(authentication)
                                .contentType("application/json")
                                .content(requestBody)
                )
                .andExpect(
                        status().isCreated()
                );

        verify(budgetService)
                .createBudget(
                        any(String.class),
                        any(CreateBudgetRequest.class)
                );
    }

    @Test
    void shouldReturnBadRequestWhenMonthlyLimitIsInvalid()
            throws Exception {

        String requestBody = """
                {
                  "categoryId": 1,
                  "monthlyLimit": 0,
                  "month": 9,
                  "year": 2026
                }
                """;

        mockMvc.perform(
                        post("/api/budgets")
                                .principal(authentication)
                                .contentType("application/json")
                                .content(requestBody)
                )
                .andExpect(
                        status().isBadRequest()
                );

        verify(
                budgetService,
                never()
        ).createBudget(
                any(String.class),
                any(CreateBudgetRequest.class)
        );
    }

    @Test
    void shouldReturnBadRequestWhenMonthIsInvalid()
            throws Exception {

        String requestBody = """
                {
                  "categoryId": 1,
                  "monthlyLimit": 700.00,
                  "month": 13,
                  "year": 2026
                }
                """;

        mockMvc.perform(
                        post("/api/budgets")
                                .principal(authentication)
                                .contentType("application/json")
                                .content(requestBody)
                )
                .andExpect(
                        status().isBadRequest()
                );

        verify(
                budgetService,
                never()
        ).createBudget(
                any(String.class),
                any(CreateBudgetRequest.class)
        );
    }

    @Test
    void shouldReturnBadRequestWhenYearIsInvalid()
            throws Exception {

        String requestBody = """
                {
                  "categoryId": 1,
                  "monthlyLimit": 700.00,
                  "month": 9,
                  "year": 1999
                }
                """;

        mockMvc.perform(
                        post("/api/budgets")
                                .principal(authentication)
                                .contentType("application/json")
                                .content(requestBody)
                )
                .andExpect(
                        status().isBadRequest()
                );

        verify(
                budgetService,
                never()
        ).createBudget(
                any(String.class),
                any(CreateBudgetRequest.class)
        );
    }

    @Test
    void shouldReturnConflictWhenBudgetAlreadyExists()
            throws Exception {

        String requestBody = """
                {
                  "categoryId": 1,
                  "monthlyLimit": 700.00,
                  "month": 9,
                  "year": 2026
                }
                """;

        when(budgetService.createBudget(
                any(String.class),
                any(CreateBudgetRequest.class)
        )).thenThrow(
                new DuplicateBudgetException(
                        "Budget already exists for this category and month"
                )
        );

        mockMvc.perform(
                        post("/api/budgets")
                                .principal(authentication)
                                .contentType("application/json")
                                .content(requestBody)
                )
                .andExpect(
                        status().isConflict()
                );
    }

    @Test
    void shouldReturnBudgetWhenBudgetExists()
            throws Exception {

        when(budgetService.getBudgetById(
                TEST_EMAIL,
                1L
        )).thenReturn(null);

        mockMvc.perform(
                        get("/api/budgets/1")
                                .principal(authentication)
                )
                .andExpect(
                        status().isOk()
                );

        verify(budgetService)
                .getBudgetById(
                        TEST_EMAIL,
                        1L
                );
    }

    @Test
    void shouldReturnNotFoundWhenBudgetDoesNotExist()
            throws Exception {

        when(budgetService.getBudgetById(
                TEST_EMAIL,
                99L
        )).thenThrow(
                new BudgetNotFoundException(
                        "Budget not found"
                )
        );

        mockMvc.perform(
                        get("/api/budgets/99")
                                .principal(authentication)
                )
                .andExpect(
                        status().isNotFound()
                );
    }

    @Test
    void shouldReturnBudgetAnalytics()
            throws Exception {

        when(budgetService.getBudgetAnalytics(
                TEST_EMAIL,
                1L
        )).thenReturn(null);

        mockMvc.perform(
                        get("/api/budgets/1/analytics")
                                .principal(authentication)
                )
                .andExpect(
                        status().isOk()
                );

        verify(budgetService)
                .getBudgetAnalytics(
                        TEST_EMAIL,
                        1L
                );
    }

    @Test
    void shouldDeleteBudgetAndReturnNoContent()
            throws Exception {

        mockMvc.perform(
                        delete("/api/budgets/1")
                                .principal(authentication)
                )
                .andExpect(
                        status().isNoContent()
                );

        verify(budgetService)
                .deleteBudget(
                        TEST_EMAIL,
                        1L
                );
    }

    @Test
void shouldReturnAllBudgets()
        throws Exception {

    when(budgetService.getAllBudgets(
            TEST_EMAIL
    )).thenReturn(
            java.util.List.of(
                    new BudgetResponse(
                            1L,
                            1L,
                            "Groceries",
                            new java.math.BigDecimal("700.00"),
                            9,
                            2026,
                            LocalDateTime.of(2026, 9, 8, 10, 0),
                            LocalDateTime.of(2026, 9, 8, 10, 0)
                    ),
                    new BudgetResponse(
                            2L,
                            2L,
                            "Dining",
                            new java.math.BigDecimal("400.00"),
                            9,
                            2026,
                            LocalDateTime.of(2026, 9, 8, 10, 5),
                            LocalDateTime.of(2026, 9, 8, 10, 5)
                    )
            )
    );

    mockMvc.perform(
                    get("/api/budgets")
                            .principal(authentication)
            )
            .andExpect(
                    status().isOk()
            );

    verify(budgetService)
            .getAllBudgets(
                    TEST_EMAIL
            );
}

@Test
void shouldUpdateBudgetWhenRequestIsValid()
        throws Exception {

    String requestBody = """
            {
              "categoryId": 2,
              "monthlyLimit": 900.00,
              "month": 10,
              "year": 2026
            }
            """;

    when(budgetService.updateBudget(
            any(String.class),
            any(Long.class),
            any(UpdateBudgetRequest.class)
    )).thenReturn(
            new BudgetResponse(
                    1L,
                    2L,
                    "Dining",
                    new java.math.BigDecimal("900.00"),
                    10,
                    2026,
                    LocalDateTime.of(2026, 9, 8, 10, 0),
                    LocalDateTime.of(2026, 9, 8, 10, 30)
            )
    );

    mockMvc.perform(
                    put("/api/budgets/1")
                            .principal(authentication)
                            .contentType("application/json")
                            .content(requestBody)
            )
            .andExpect(
                    status().isOk()
            );

    verify(budgetService)
            .updateBudget(
                    any(String.class),
                    any(Long.class),
                    any(UpdateBudgetRequest.class)
            );
}
}