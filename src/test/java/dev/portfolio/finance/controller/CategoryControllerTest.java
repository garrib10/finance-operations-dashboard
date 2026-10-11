package dev.portfolio.finance.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import java.time.LocalDateTime;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import dev.portfolio.finance.dto.category.CreateCategoryRequest;
import dev.portfolio.finance.exception.GlobalExceptionHandler;
import dev.portfolio.finance.exception.category.CategoryBuiltInException;
import dev.portfolio.finance.exception.category.CategoryExceptionHandler;
import dev.portfolio.finance.exception.category.CategoryInUseException;
import dev.portfolio.finance.exception.category.CategoryNotFoundException;
import dev.portfolio.finance.exception.category.DuplicateCategoryException;
import dev.portfolio.finance.exception.category.InvalidCategoryNameException;
import dev.portfolio.finance.exception.category.CategoryValidationException;
import dev.portfolio.finance.service.CategoryService;
import dev.portfolio.finance.service.CategorySummaryService;
import dev.portfolio.finance.dto.category.CategorySummaryListResponse;
import dev.portfolio.finance.dto.category.CategorySummaryResponse;
import dev.portfolio.finance.dto.category.CurrentMonthBudgetResponse;
import dev.portfolio.finance.entity.BudgetStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import dev.portfolio.finance.dto.category.CategoryResponse;
import dev.portfolio.finance.dto.category.UpdateCategoryRequest;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    private static final String TEST_EMAIL =
            "test@example.com";

    @Mock
    private CategoryService categoryService;

    @Mock
    private CategorySummaryService categorySummaryService;

    private MockMvc mockMvc;

    private TestingAuthenticationToken authentication;

    private final GlobalExceptionHandler globalExceptionHandler =
            new GlobalExceptionHandler();

    @BeforeEach
    void setUp() {
        CategoryController controller =
                new CategoryController(
                        categoryService,
                        categorySummaryService
                );

        mockMvc =
                MockMvcBuilders
                        .standaloneSetup(controller)
                        .setControllerAdvice(
                                new CategoryExceptionHandler(globalExceptionHandler),
                                globalExceptionHandler
                        )
                        .build();

        authentication =
                new TestingAuthenticationToken(
                        TEST_EMAIL,
                        null
                );
    }

    @Test
    void shouldCreateCategoryWhenRequestIsValid()
            throws Exception {

        String requestBody = """
                {
                  "name": "Groceries",
                  "budgetEnabled": true
                }
                """;

        when(categoryService.createCategory(
                any(String.class),
                any(CreateCategoryRequest.class)
        )).thenReturn(null);

        mockMvc.perform(
                        post("/api/categories")
                                .principal(authentication)
                                .contentType("application/json")
                                .content(requestBody)
                )
                .andExpect(
                        status().isCreated()
                );

        verify(categoryService)
                .createCategory(
                        any(String.class),
                        any(CreateCategoryRequest.class)
                );
    }

    @Test
    void shouldReturnBadRequestWhenCategoryNameIsBlank()
            throws Exception {

        String requestBody = """
                {
                  "name": "",
                  "budgetEnabled": true
                }
                """;

        mockMvc.perform(
                        post("/api/categories")
                                .principal(authentication)
                                .contentType("application/json")
                                .content(requestBody)
                )
                .andExpect(
                        status().isBadRequest()
                );

        verify(
                categoryService,
                never()
        ).createCategory(
                any(String.class),
                any(CreateCategoryRequest.class)
        );
    }

    @Test
    void shouldReturnConflictWhenCategoryAlreadyExists()
            throws Exception {

        String requestBody = """
                {
                  "name": "Groceries",
                  "budgetEnabled": true
                }
                """;

        when(categoryService.createCategory(
                any(String.class),
                any(CreateCategoryRequest.class)
        )).thenThrow(
                new DuplicateCategoryException(
                        "Category already exists"
                )
        );

        mockMvc.perform(
                        post("/api/categories")
                                .principal(authentication)
                                .contentType("application/json")
                                .content(requestBody)
                )
                .andExpect(
                        status().isConflict()
                );
    }

    @Test
    void shouldReturnBadRequestWhenCategoryNameCannotBeNormalized()
            throws Exception {

        when(categoryService.createCategory(
                any(String.class),
                any(CreateCategoryRequest.class)
        )).thenThrow(
                new InvalidCategoryNameException(
                        InvalidCategoryNameException.Reason.CONTROL_CHARACTER
                )
        );

        mockMvc.perform(
                        post("/api/categories")
                                .principal(authentication)
                                .contentType("application/json")
                                .content("""
                                        {
                                          "name": "Bad\\u0000Name",
                                          "budgetEnabled": true
                                        }
                                        """)
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.error").value("Validation Failed")
                )
                .andExpect(
                        jsonPath("$.fields.name").value("Category name contains unsupported characters")
                );
    }

    @Test
    void shouldReturnCanonicalResponseWithoutInternalMetadata()
            throws Exception {

        when(categoryService.getCategoryById(TEST_EMAIL, 1L))
                .thenReturn(new CategoryResponse(1L, "Groceries", true, true, "shopping-cart",
                        LocalDateTime.of(2026, 9, 8, 10, 0), LocalDateTime.of(2026, 9, 8, 10, 5)));

        mockMvc.perform(
                        get("/api/categories/1")
                                .principal(authentication)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Groceries"))
                .andExpect(jsonPath("$.budgetEnabled").value(true))
                .andExpect(jsonPath("$.builtIn").value(true))
                .andExpect(jsonPath("$.iconKey").value("shopping-cart"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.normalizedName").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.*", org.hamcrest.Matchers.hasSize(7)));
    }

    @Test
    void shouldReturnCategoryWhenCategoryExists()
            throws Exception {

        when(categoryService.getCategoryById(
                TEST_EMAIL,
                1L
        )).thenReturn(null);

        mockMvc.perform(
                        get("/api/categories/1")
                                .principal(authentication)
                )
                .andExpect(
                        status().isOk()
                );

        verify(categoryService)
                .getCategoryById(
                        TEST_EMAIL,
                        1L
                );
    }

    @Test
    void shouldReturnNotFoundWhenCategoryDoesNotExist()
            throws Exception {

        when(categoryService.getCategoryById(
                TEST_EMAIL,
                99L
        )).thenThrow(
                new CategoryNotFoundException(
                        "Category not found"
                )
        );

        mockMvc.perform(
                        get("/api/categories/99")
                                .principal(authentication)
                )
                .andExpect(
                        status().isNotFound()
                );
    }

    @Test
    void shouldDeleteCategoryAndReturnNoContent()
            throws Exception {

        mockMvc.perform(
                        delete("/api/categories/1")
                                .principal(authentication)
                )
                .andExpect(
                        status().isNoContent()
                );

        verify(categoryService)
                .deleteCategory(
                        TEST_EMAIL,
                        1L
                );
    }

    @Test
void shouldReturnAllCategories()
        throws Exception {

    when(categoryService.getAllCategories(
        TEST_EMAIL
)).thenReturn(
        java.util.List.of(
                new CategoryResponse(
                        1L,
                        "Dining",
                        true,
                        false,
                        "tag",
                        LocalDateTime.of(2026, 9, 8, 10, 0),
                        LocalDateTime.of(2026, 9, 8, 10, 0)
                ),
                new CategoryResponse(
                        2L,
                        "Groceries",
                        true,
                        false,
                        "tag",
                        LocalDateTime.of(2026, 9, 8, 10, 5),
                        LocalDateTime.of(2026, 9, 8, 10, 5)
                )
        )
);

    mockMvc.perform(
                    get("/api/categories")
                            .principal(authentication)
            )
            .andExpect(
                    status().isOk()
            );

    verify(categoryService)
            .getAllCategories(
                    TEST_EMAIL
            );
}

@Test
void shouldUpdateCategoryWhenRequestIsValid()
        throws Exception {

    String requestBody = """
            {
              "name": "Restaurants",
              "budgetEnabled": false
            }
            """;

    when(categoryService.updateCategory(
        any(String.class),
        any(Long.class),
        any(UpdateCategoryRequest.class)
)).thenReturn(
        new CategoryResponse(
                1L,
                "Restaurants",
                false,
                false,
                "tag",
                LocalDateTime.of(2026, 9, 8, 10, 0),
                LocalDateTime.of(2026, 9, 8, 10, 30)
        )
);

    mockMvc.perform(
                    put("/api/categories/1")
                            .principal(authentication)
                            .contentType("application/json")
                            .content(requestBody)
            )
            .andExpect(
                    status().isOk()
            );

    verify(categoryService)
            .updateCategory(
                    any(String.class),
                    any(Long.class),
                    any(UpdateCategoryRequest.class)
            );
}

    // ---------------------------------------------------------------- Phase 2 contract

    private static final CategoryResponse CUSTOM = new CategoryResponse(7L, "Pet Care", true, false,
            "piggy-bank", LocalDateTime.of(2026, 9, 30, 9, 0), LocalDateTime.of(2026, 9, 30, 9, 0));

    @Test
    void createReturns201WithCanonicalBodyAndPassesIconKey() throws Exception {
        org.mockito.ArgumentCaptor<CreateCategoryRequest> request =
                org.mockito.ArgumentCaptor.forClass(CreateCategoryRequest.class);
        when(categoryService.createCategory(org.mockito.ArgumentMatchers.eq(TEST_EMAIL), request.capture()))
                .thenReturn(CUSTOM);

        mockMvc.perform(post("/api/categories").principal(authentication).contentType("application/json")
                        .content("""
                                {"name": "Pet Care", "budgetEnabled": true, "iconKey": "piggy-bank",
                                 "builtIn": true, "userId": 99, "normalizedName": "x", "id": 5}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.builtIn").value(false))
                .andExpect(jsonPath("$.iconKey").value("piggy-bank"))
                .andExpect(jsonPath("$.normalizedName").doesNotExist());

        // Ownership, built-in status, IDs, and the normalized value are not part of the request.
        org.assertj.core.api.Assertions.assertThat(request.getValue())
                .isEqualTo(new CreateCategoryRequest("Pet Care", true, "piggy-bank"));
    }

    @Test
    void invalidIconKeyReturnsFieldValidationWithoutCallingService() throws Exception {
        for (String icon : java.util.List.of("paw", "Tag", "<svg onload=x>", "https://example.com/i.svg",
                "../tag", "fa fa-tag")) {
            mockMvc.perform(post("/api/categories").principal(authentication).contentType("application/json")
                            .content(jsonBody("Pets", icon)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Validation Failed"))
                    .andExpect(jsonPath("$.fields.iconKey").value("Icon must be one of the approved category icons"));
            mockMvc.perform(put("/api/categories/7").principal(authentication).contentType("application/json")
                            .content(jsonBody("Pets", icon)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.iconKey").exists());
        }
        verify(categoryService, never()).createCategory(any(String.class), any(CreateCategoryRequest.class));
        verify(categoryService, never()).updateCategory(any(String.class), any(Long.class),
                any(UpdateCategoryRequest.class));
    }

    @Test
    void blankAndOversizedNamesReturnFieldValidation() throws Exception {
        mockMvc.perform(post("/api/categories").principal(authentication).contentType("application/json")
                        .content(jsonBody("   ", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").value("Category name is required"));
        mockMvc.perform(put("/api/categories/7").principal(authentication).contentType("application/json")
                        .content(jsonBody("x".repeat(101), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").value("Category name must be 100 characters or fewer"));
    }

    @Test
    void duplicateReturns409WithStableCode() throws Exception {
        when(categoryService.updateCategory(any(String.class), any(Long.class), any(UpdateCategoryRequest.class)))
                .thenThrow(new DuplicateCategoryException("Category already exists"));

        mockMvc.perform(put("/api/categories/7").principal(authentication).contentType("application/json")
                        .content(jsonBody("Dining", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_DUPLICATE"))
                .andExpect(jsonPath("$.message").value("Category already exists"));
    }

    @Test
    void missingOrForeignCategoryReturns404WithStableCodeForEveryOperation() throws Exception {
        CategoryNotFoundException notFound = new CategoryNotFoundException("Category not found");
        when(categoryService.getCategoryById(TEST_EMAIL, 404L)).thenThrow(notFound);
        when(categoryService.updateCategory(org.mockito.ArgumentMatchers.eq(TEST_EMAIL),
                org.mockito.ArgumentMatchers.eq(404L), any(UpdateCategoryRequest.class))).thenThrow(notFound);
        org.mockito.Mockito.doThrow(notFound).when(categoryService).deleteCategory(TEST_EMAIL, 404L);

        for (var request : java.util.List.of(
                get("/api/categories/404"),
                put("/api/categories/404").contentType("application/json").content(jsonBody("Pets", null)),
                delete("/api/categories/404"))) {
            mockMvc.perform(request.principal(authentication))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Category not found"));
        }
    }

    @Test
    void builtInMutationReturns403WithStableCode() throws Exception {
        when(categoryService.updateCategory(any(String.class), any(Long.class), any(UpdateCategoryRequest.class)))
                .thenThrow(new CategoryBuiltInException());
        org.mockito.Mockito.doThrow(new CategoryBuiltInException()).when(categoryService).deleteCategory(TEST_EMAIL, 1L);

        mockMvc.perform(put("/api/categories/1").principal(authentication).contentType("application/json")
                        .content(jsonBody("Housing", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATEGORY_BUILT_IN"))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Built-in categories cannot be changed or deleted."));
        mockMvc.perform(delete("/api/categories/1").principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATEGORY_BUILT_IN"));
    }

    @Test
    void inUseDeleteReturns409WithStableCode() throws Exception {
        org.mockito.Mockito.doThrow(new CategoryInUseException()).when(categoryService).deleteCategory(TEST_EMAIL, 7L);

        mockMvc.perform(delete("/api/categories/7").principal(authentication))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"))
                .andExpect(jsonPath("$.message").value(
                        "This category is used by transactions or budgets and cannot be deleted."));
    }

    @Test
    void successfulDeleteReturns204WithEmptyBody() throws Exception {
        mockMvc.perform(delete("/api/categories/7").principal(authentication))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
    }

    @Test
    void zeroNegativeAndNonNumericIdsReturnFieldValidation() throws Exception {
        for (String id : java.util.List.of("0", "-1", "abc", "1.5", "99999999999999999999")) {
            for (var request : java.util.List.of(
                    get("/api/categories/" + id),
                    put("/api/categories/" + id).contentType("application/json").content(jsonBody("Pets", null)),
                    delete("/api/categories/" + id))) {
                mockMvc.perform(request.principal(authentication))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.fields.id").value("Category ID must be a positive whole number"));
            }
        }
        org.mockito.Mockito.verifyNoInteractions(categoryService);
    }

    @Test
    void malformedAndMistypedBodiesReturnSafe400() throws Exception {
        mockMvc.perform(post("/api/categories").principal(authentication).contentType("application/json")
                        .content("{\"name\": \"Pets\", "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Request body is missing or malformed; check field names and types"));
        mockMvc.perform(post("/api/categories").principal(authentication).contentType("application/json")
                        .content("{\"name\": \"Pets\", \"budgetEnabled\": \"sometimes\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.budgetEnabled").value("Budget enabled must be true or false"));
        mockMvc.perform(put("/api/categories/7").principal(authentication).contentType("application/json")
                        .content("{\"name\": \"Pets\", \"budgetEnabled\": true, \"iconKey\": [\"tag\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.iconKey").value("Icon must be text"));
        mockMvc.perform(post("/api/categories").principal(authentication).contentType("application/json")
                        .content("{\"name\": {\"x\": 1}, \"budgetEnabled\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").value("Category name must be text"));
        mockMvc.perform(post("/api/categories").principal(authentication).contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").doesNotExist());
        mockMvc.perform(post("/api/categories").principal(authentication).contentType("text/plain").content("Pets"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("The request could not be processed"));
        org.mockito.Mockito.verifyNoInteractions(categoryService);
    }

    @Test
    void unexpectedDatabaseErrorsNeverLeakSqlOrConstraintNames() throws Exception {
        when(categoryService.createCategory(any(String.class), any(CreateCategoryRequest.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "could not execute statement [insert into categories ...]; "
                                + "constraint [fk_categories_user]; SQL [insert into categories values (?)]"));

        String body = mockMvc.perform(post("/api/categories").principal(authentication)
                        .contentType("application/json").content(jsonBody("Pets", null)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to complete the category request. Please try again."))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body.toLowerCase(java.util.Locale.ROOT))
                .doesNotContain("insert", "constraint", "fk_", "sql", "categories values");
    }

    private static String jsonBody(String name, String iconKey) {
        return "{\"name\": \"" + name + "\", \"budgetEnabled\": true"
                + (iconKey == null ? "" : ", \"iconKey\": \"" + iconKey.replace("\"", "\\\"") + "\"") + "}";
    }

@Test
void shouldReturnTheCategorySummaryForTheAuthenticatedUser()
        throws Exception {

    when(categorySummaryService.getSummary(TEST_EMAIL, null, null)).thenReturn(new CategorySummaryListResponse(10, 2026, 10, 2026, List.of(
            new CategorySummaryResponse(7L, "Pet Care", "paw-print", false, true, 3, 2, 1,
                    LocalDate.of(2026, 10, 2), new BigDecimal("60.00"), new BigDecimal("140.00"),
                    new CurrentMonthBudgetResponse(4L, new BigDecimal("100.00"), new BigDecimal("60.00"),
                            new BigDecimal("40.00"), new BigDecimal("60.00"), BudgetStatus.CAUTION),
                    false),
            new CategorySummaryResponse(8L, "Unused", "tag", false, true, 0, 0, 0,
                    null, new BigDecimal("0.00"), new BigDecimal("0.00"), null, true))));

    mockMvc.perform(get("/api/categories/summary").principal(authentication))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.month").value(10))
            .andExpect(jsonPath("$.year").value(2026))
            .andExpect(jsonPath("$.serverCurrentMonth").value(10))
            .andExpect(jsonPath("$.serverCurrentYear").value(2026))
            .andExpect(jsonPath("$.categories[0].id").value(7))
            .andExpect(jsonPath("$.categories[0].iconKey").value("paw-print"))
            .andExpect(jsonPath("$.categories[0].budgetEnabled").value(true))
            .andExpect(jsonPath("$.categories[0].transactionCount").value(3))
            .andExpect(jsonPath("$.categories[0].currentMonthTransactionCount").value(2))
            .andExpect(jsonPath("$.categories[0].budgetCount").value(1))
            .andExpect(jsonPath("$.categories[0].lastTransactionDate").value("2026-10-02"))
            .andExpect(jsonPath("$.categories[0].currentMonthSpent").value(60.00))
            .andExpect(jsonPath("$.categories[0].allTimeSpent").value(140.00))
            .andExpect(jsonPath("$.categories[0].currentMonthBudget.budgetId").value(4))
            .andExpect(jsonPath("$.categories[0].currentMonthBudget.status").value("CAUTION"))
            .andExpect(jsonPath("$.categories[0].canDelete").value(false))
            .andExpect(jsonPath("$.categories[1].currentMonthTransactionCount").value(0))
            .andExpect(jsonPath("$.categories[1].lastTransactionDate").isEmpty())
            .andExpect(jsonPath("$.categories[1].currentMonthBudget").isEmpty())
            .andExpect(jsonPath("$.categories[1].canDelete").value(true));

    verify(categorySummaryService).getSummary(TEST_EMAIL, null, null);
    verify(categoryService, never()).getCategoryById(any(), any());
}

@Test
void shouldPassARequestedMonthToTheSummaryAndReportBothMonths() throws Exception {
    when(categorySummaryService.getSummary(TEST_EMAIL, 8, 2026))
            .thenReturn(new CategorySummaryListResponse(8, 2026, 10, 2026, List.of()));

    mockMvc.perform(get("/api/categories/summary").param("month", "8").param("year", "2026")
                    .principal(authentication))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.month").value(8))
            .andExpect(jsonPath("$.year").value(2026))
            .andExpect(jsonPath("$.serverCurrentMonth").value(10))
            .andExpect(jsonPath("$.serverCurrentYear").value(2026))
            .andExpect(jsonPath("$.categories").isEmpty());

    verify(categorySummaryService).getSummary(TEST_EMAIL, 8, 2026);
}

@Test
void shouldReportAnInvalidPeriodAsFieldValidation() throws Exception {
    when(categorySummaryService.getSummary(TEST_EMAIL, 8, null)).thenThrow(new CategoryValidationException(
            java.util.Map.of("year", "Month and year must be given together")));

    mockMvc.perform(get("/api/categories/summary").param("month", "8").principal(authentication))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Validation Failed"))
            .andExpect(jsonPath("$.fields.year").value("Month and year must be given together"))
            .andExpect(jsonPath("$.fields.month").doesNotExist());
}

@Test
void shouldNameTheMalformedSummaryParameter() throws Exception {
    mockMvc.perform(get("/api/categories/summary").param("month", "abc").param("year", "2026")
                    .principal(authentication))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Validation Failed"))
            .andExpect(jsonPath("$.fields.month").value("Month must be a whole number between 1 and 12"))
            .andExpect(jsonPath("$.fields.id").doesNotExist());

    mockMvc.perform(get("/api/categories/summary").param("month", "8").param("year", "abcd")
                    .principal(authentication))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields.year").value("Year must be a whole number"))
            .andExpect(jsonPath("$.fields.id").doesNotExist());

    org.mockito.Mockito.verifyNoInteractions(categorySummaryService);
}
}
