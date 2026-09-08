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
import dev.portfolio.finance.exception.category.CategoryNotFoundException;
import dev.portfolio.finance.exception.category.DuplicateCategoryException;
import dev.portfolio.finance.service.CategoryService;
import dev.portfolio.finance.dto.category.CategoryResponse;
import dev.portfolio.finance.dto.category.UpdateCategoryRequest;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    private static final String TEST_EMAIL =
            "test@example.com";

    @Mock
    private CategoryService categoryService;

    private MockMvc mockMvc;

    private TestingAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        CategoryController controller =
                new CategoryController(
                        categoryService
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
                        LocalDateTime.of(2026, 9, 8, 10, 0),
                        LocalDateTime.of(2026, 9, 8, 10, 0)
                ),
                new CategoryResponse(
                        2L,
                        "Groceries",
                        true,
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
}