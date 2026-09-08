package dev.portfolio.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.portfolio.finance.dto.category.CategoryResponse;
import dev.portfolio.finance.dto.category.CreateCategoryRequest;
import dev.portfolio.finance.dto.category.UpdateCategoryRequest;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.category.CategoryNotFoundException;
import dev.portfolio.finance.exception.category.DuplicateCategoryException;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.support.TestDataFactory;


@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final String TEST_EMAIL = "test@example.com";
    private static final Long CATEGORY_ID = 1L;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void shouldCreateCategoryForAuthenticatedUser() {
        // Arrange
        User user = TestDataFactory.createUser();

        CreateCategoryRequest request =
                new CreateCategoryRequest(
                        "  Groceries  ",
                        true
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(categoryRepository.existsByUserIdAndNameIgnoreCase(
                user.getId(),
                "Groceries"
        )).thenReturn(false);

        when(categoryRepository.save(
                org.mockito.ArgumentMatchers.any(Category.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CategoryResponse response =
                categoryService.createCategory(
                        TEST_EMAIL,
                        request
                );

        // Assert
        assertEquals(
                "Groceries",
                response.name()
        );

        assertEquals(
                true,
                response.budgetEnabled()
        );

        verify(userRepository)
                .findByEmail(TEST_EMAIL);

        verify(categoryRepository)
                .existsByUserIdAndNameIgnoreCase(
                        user.getId(),
                        "Groceries"
                );

        verify(categoryRepository)
                .save(
                        org.mockito.ArgumentMatchers.any(Category.class)
                );
    }

    @Test
    void shouldThrowDuplicateCategoryExceptionWhenCategoryAlreadyExists() {
        // Arrange
        User user = TestDataFactory.createUser();

        CreateCategoryRequest request =
                new CreateCategoryRequest(
                        "Groceries",
                        true
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(categoryRepository.existsByUserIdAndNameIgnoreCase(
                user.getId(),
                "Groceries"
        )).thenReturn(true);

        // Act + Assert
        assertThrows(
                DuplicateCategoryException.class,
                () -> categoryService.createCategory(
                        TEST_EMAIL,
                        request
                )
        );

        verify(categoryRepository, never())
                .save(
                        org.mockito.ArgumentMatchers.any(Category.class)
                );
    }

    @Test
    void shouldReturnCategoryWhenOwnedByAuthenticatedUser() {
        // Arrange
        User user = TestDataFactory.createUser();

        Category category =
                TestDataFactory.createCategory(
                        user,
                        "Groceries",
                        true
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(categoryRepository.findByIdAndUserId(
                CATEGORY_ID,
                user.getId()
        )).thenReturn(Optional.of(category));

        // Act
        CategoryResponse response =
                categoryService.getCategoryById(
                        TEST_EMAIL,
                        CATEGORY_ID
                );

        // Assert
        assertEquals(
                "Groceries",
                response.name()
        );

        assertEquals(
                true,
                response.budgetEnabled()
        );

        verify(categoryRepository)
                .findByIdAndUserId(
                        CATEGORY_ID,
                        user.getId()
                );
    }

    @Test
    void shouldThrowCategoryNotFoundWhenCategoryIsNotOwnedByAuthenticatedUser() {
        // Arrange
        User user = TestDataFactory.createUser();

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(categoryRepository.findByIdAndUserId(
                CATEGORY_ID,
                user.getId()
        )).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                CategoryNotFoundException.class,
                () -> categoryService.getCategoryById(
                        TEST_EMAIL,
                        CATEGORY_ID
                )
        );

        verify(categoryRepository)
                .findByIdAndUserId(
                        CATEGORY_ID,
                        user.getId()
                );
    }

    @Test
    void shouldThrowDuplicateCategoryExceptionWhenRenamingToExistingCategory() {
        // Arrange
        User user = TestDataFactory.createUser();

        Category category =
                TestDataFactory.createCategory(
                        user,
                        "Dining",
                        true
                );

        UpdateCategoryRequest request =
                new UpdateCategoryRequest(
                        "Groceries",
                        true
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(categoryRepository.findByIdAndUserId(
                CATEGORY_ID,
                user.getId()
        )).thenReturn(Optional.of(category));

        when(categoryRepository.existsByUserIdAndNameIgnoreCase(
                user.getId(),
                "Groceries"
        )).thenReturn(true);

        // Act + Assert
        assertThrows(
                DuplicateCategoryException.class,
                () -> categoryService.updateCategory(
                        TEST_EMAIL,
                        CATEGORY_ID,
                        request
                )
        );

        verify(categoryRepository, never())
                .save(category);
    }

    @Test
    void shouldDeleteCategoryWhenOwnedByAuthenticatedUser() {
        // Arrange
        User user = TestDataFactory.createUser();

        Category category =
                TestDataFactory.createCategory(
                        user,
                        "Groceries",
                        true
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(categoryRepository.findByIdAndUserId(
                CATEGORY_ID,
                user.getId()
        )).thenReturn(Optional.of(category));

        // Act
        categoryService.deleteCategory(
                TEST_EMAIL,
                CATEGORY_ID
        );

        // Assert
        verify(categoryRepository)
                .delete(category);
    }

    @Test
void shouldReturnAllCategoriesForAuthenticatedUser() {
    // Arrange
    User user = TestDataFactory.createUser();

    Category groceries =
            TestDataFactory.createCategory(
                    user,
                    "Groceries",
                    true
            );

    Category dining =
            TestDataFactory.createCategory(
                    user,
                    "Dining",
                    true
            );

    when(userRepository.findByEmail(TEST_EMAIL))
            .thenReturn(Optional.of(user));

    when(categoryRepository.findAllByUserIdOrderByNameAsc(
            user.getId()
    )).thenReturn(
            List.of(
                    dining,
                    groceries
            )
    );

    // Act
    List<CategoryResponse> responses =
            categoryService.getAllCategories(
                    TEST_EMAIL
            );

    // Assert
    assertEquals(
            2,
            responses.size()
    );

    assertEquals(
            "Dining",
            responses.get(0).name()
    );

    assertEquals(
            "Groceries",
            responses.get(1).name()
    );

    verify(categoryRepository)
            .findAllByUserIdOrderByNameAsc(
                    user.getId()
            );
}

@Test
void shouldUpdateCategoryWhenOwnedByAuthenticatedUser() {
    // Arrange
    User user = TestDataFactory.createUser();

    Category category =
            TestDataFactory.createCategory(
                    user,
                    "Dining",
                    true
            );

    UpdateCategoryRequest request =
            new UpdateCategoryRequest(
                    "  Restaurants  ",
                    false
            );

    when(userRepository.findByEmail(TEST_EMAIL))
            .thenReturn(Optional.of(user));

    when(categoryRepository.findByIdAndUserId(
            CATEGORY_ID,
            user.getId()
    )).thenReturn(Optional.of(category));

    when(categoryRepository.existsByUserIdAndNameIgnoreCase(
            user.getId(),
            "Restaurants"
    )).thenReturn(false);

    when(categoryRepository.save(category))
            .thenReturn(category);

    // Act
    CategoryResponse response =
            categoryService.updateCategory(
                    TEST_EMAIL,
                    CATEGORY_ID,
                    request
            );

    // Assert
    assertEquals(
            "Restaurants",
            response.name()
    );

    assertEquals(
            false,
            response.budgetEnabled()
    );

    verify(categoryRepository)
            .existsByUserIdAndNameIgnoreCase(
                    user.getId(),
                    "Restaurants"
            );

    verify(categoryRepository)
            .save(category);
}

@Test
void shouldThrowCategoryNotFoundWhenUpdatingMissingCategory() {
    // Arrange
    User user = TestDataFactory.createUser();

    UpdateCategoryRequest request =
            new UpdateCategoryRequest(
                    "Groceries",
                    true
            );

    when(userRepository.findByEmail(TEST_EMAIL))
            .thenReturn(Optional.of(user));

    when(categoryRepository.findByIdAndUserId(
            CATEGORY_ID,
            user.getId()
    )).thenReturn(Optional.empty());

    // Act + Assert
    CategoryNotFoundException exception =
            assertThrows(
                    CategoryNotFoundException.class,
                    () -> categoryService.updateCategory(
                            TEST_EMAIL,
                            CATEGORY_ID,
                            request
                    )
            );

    assertEquals(
            "Category not found",
            exception.getMessage()
    );

    verify(categoryRepository, never())
            .save(
                    org.mockito.ArgumentMatchers.any(Category.class)
            );
}

@Test
void shouldThrowCategoryNotFoundWhenDeletingMissingCategory() {
    // Arrange
    User user = TestDataFactory.createUser();

    when(userRepository.findByEmail(TEST_EMAIL))
            .thenReturn(Optional.of(user));

    when(categoryRepository.findByIdAndUserId(
            CATEGORY_ID,
            user.getId()
    )).thenReturn(Optional.empty());

    // Act + Assert
    CategoryNotFoundException exception =
            assertThrows(
                    CategoryNotFoundException.class,
                    () -> categoryService.deleteCategory(
                            TEST_EMAIL,
                            CATEGORY_ID
                    )
            );

    assertEquals(
            "Category not found",
            exception.getMessage()
    );

    verify(categoryRepository, never())
            .delete(
                    org.mockito.ArgumentMatchers.any(Category.class)
            );
}

@Test
void shouldUpdateBudgetEnabledWhenCategoryNameIsUnchanged() {
    // Arrange
    User user = TestDataFactory.createUser();

    Category category =
            TestDataFactory.createCategory(
                    user,
                    "Groceries",
                    true
            );

    UpdateCategoryRequest request =
            new UpdateCategoryRequest(
                    "Groceries",
                    false
            );

    when(userRepository.findByEmail(TEST_EMAIL))
            .thenReturn(Optional.of(user));

    when(categoryRepository.findByIdAndUserId(
            CATEGORY_ID,
            user.getId()
    )).thenReturn(Optional.of(category));

    when(categoryRepository.save(category))
            .thenReturn(category);

    // Act
    CategoryResponse response =
            categoryService.updateCategory(
                    TEST_EMAIL,
                    CATEGORY_ID,
                    request
            );

    // Assert
    assertEquals(
            "Groceries",
            response.name()
    );

    assertEquals(
            false,
            response.budgetEnabled()
    );

    verify(categoryRepository, never())
            .existsByUserIdAndNameIgnoreCase(
                    user.getId(),
                    "Groceries"
            );

    verify(categoryRepository)
            .save(category);
}
}