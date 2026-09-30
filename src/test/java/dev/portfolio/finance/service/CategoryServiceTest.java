package dev.portfolio.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.portfolio.finance.dto.category.CategoryResponse;
import dev.portfolio.finance.dto.category.CreateCategoryRequest;
import dev.portfolio.finance.dto.category.UpdateCategoryRequest;
import dev.portfolio.finance.entity.BuiltInCategory;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.CategoryIcon;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.category.CategoryNotFoundException;
import dev.portfolio.finance.exception.category.DuplicateCategoryException;
import dev.portfolio.finance.exception.category.InvalidCategoryNameException;
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

        when(categoryRepository.existsByUserIdAndNormalizedName(
                user.getId(),
                "groceries"
        )).thenReturn(false);

        when(categoryRepository.saveAndFlush(
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
                .existsByUserIdAndNormalizedName(
                        user.getId(),
                        "groceries"
                );

        verify(categoryRepository)
                .saveAndFlush(
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

        when(categoryRepository.existsByUserIdAndNormalizedName(
                user.getId(),
                "groceries"
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
                .saveAndFlush(
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

        when(categoryRepository.existsByUserIdAndNormalizedNameAndIdNot(
                user.getId(),
                "groceries",
                category.getId()
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
                .saveAndFlush(category);
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

    when(categoryRepository.findAllByUserIdOrderByNameAscIdAsc(
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
            .findAllByUserIdOrderByNameAscIdAsc(
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

    when(categoryRepository.existsByUserIdAndNormalizedNameAndIdNot(
            user.getId(),
            "restaurants",
            category.getId()
    )).thenReturn(false);

    when(categoryRepository.saveAndFlush(category))
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
            .existsByUserIdAndNormalizedNameAndIdNot(
                    user.getId(),
                    "restaurants",
                    category.getId()
            );

    verify(categoryRepository)
            .saveAndFlush(category);
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
            .saveAndFlush(
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

    when(categoryRepository.saveAndFlush(category))
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
            .existsByUserIdAndNormalizedNameAndIdNot(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any()
            );

    verify(categoryRepository)
            .saveAndFlush(category);
}

@Test
void shouldCreateCustomTaggedCategoryWithCollapsedWhitespace() {
    User user = TestDataFactory.createUser();

    when(userRepository.findByEmail(TEST_EMAIL))
            .thenReturn(Optional.of(user));
    when(categoryRepository.saveAndFlush(
            org.mockito.ArgumentMatchers.any(Category.class)
    )).thenAnswer(invocation -> invocation.getArgument(0));

    CategoryResponse response = categoryService.createCategory(
            TEST_EMAIL,
            new CreateCategoryRequest(" Eating \t\u00A0 Out ", false)
    );

    ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
    verify(categoryRepository).saveAndFlush(saved.capture());
    assertEquals("Eating Out", response.name());
    assertEquals("Eating Out", saved.getValue().getName());
    assertEquals("eating out", saved.getValue().getNormalizedName());
    assertFalse(saved.getValue().isBuiltIn());
    assertEquals(CategoryIcon.TAG, saved.getValue().getIcon());
    verify(categoryRepository).existsByUserIdAndNormalizedName(user.getId(), "eating out");
}

@Test
void shouldRejectInvalidCategoryNameBeforeQueryingOrSaving() {
    User user = TestDataFactory.createUser();

    when(userRepository.findByEmail(TEST_EMAIL))
            .thenReturn(Optional.of(user));

    InvalidCategoryNameException exception = assertThrows(
            InvalidCategoryNameException.class,
            () -> categoryService.createCategory(
                    TEST_EMAIL,
                    new CreateCategoryRequest("Bad\u0000Name", true)
            )
    );

    assertEquals(InvalidCategoryNameException.Reason.CONTROL_CHARACTER, exception.getReason());
    verify(categoryRepository, never()).existsByUserIdAndNormalizedName(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    verify(categoryRepository, never()).saveAndFlush(
            org.mockito.ArgumentMatchers.any(Category.class));
}

@Test
void shouldMapConcurrentNormalizedNameViolationToDuplicate() {
    User user = TestDataFactory.createUser();

    when(userRepository.findByEmail(TEST_EMAIL))
            .thenReturn(Optional.of(user));
    when(categoryRepository.saveAndFlush(
            org.mockito.ArgumentMatchers.any(Category.class)
    )).thenThrow(new DataIntegrityViolationException(
            "could not execute statement",
            new RuntimeException(null, new RuntimeException(
                    "Duplicate entry for key 'categories.UK_CATEGORIES_USER_NORMALIZED_NAME'"))
    ));

    DuplicateCategoryException exception = assertThrows(
            DuplicateCategoryException.class,
            () -> categoryService.createCategory(
                    TEST_EMAIL,
                    new CreateCategoryRequest("Groceries", true)
            )
    );

    assertEquals("Category already exists", exception.getMessage());
}

@Test
void shouldRethrowUnrelatedIntegrityViolation() {
    User user = TestDataFactory.createUser();
    DataIntegrityViolationException unrelated =
            new DataIntegrityViolationException("violates fk_categories_user");

    when(userRepository.findByEmail(TEST_EMAIL))
            .thenReturn(Optional.of(user));
    when(categoryRepository.saveAndFlush(
            org.mockito.ArgumentMatchers.any(Category.class)
    )).thenThrow(unrelated);

    DataIntegrityViolationException thrown = assertThrows(
            DataIntegrityViolationException.class,
            () -> categoryService.createCategory(
                    TEST_EMAIL,
                    new CreateCategoryRequest("Groceries", true)
            )
    );

    assertSame(unrelated, thrown);
}

@Test
void shouldChangeDisplayCasingWithoutDuplicateCheckAndKeepBuiltInMetadata() {
    User user = TestDataFactory.createUser();
    Category category = Category.builtIn(user, BuiltInCategory.GROCERIES);

    when(userRepository.findByEmail(TEST_EMAIL))
            .thenReturn(Optional.of(user));
    when(categoryRepository.findByIdAndUserId(CATEGORY_ID, user.getId()))
            .thenReturn(Optional.of(category));
    when(categoryRepository.saveAndFlush(category))
            .thenReturn(category);

    CategoryResponse response = categoryService.updateCategory(
            TEST_EMAIL,
            CATEGORY_ID,
            new UpdateCategoryRequest("GROCERIES", true)
    );

    assertEquals("GROCERIES", response.name());
    assertEquals("groceries", category.getNormalizedName());
    assertTrue(category.isBuiltIn());
    assertEquals(CategoryIcon.SHOPPING_CART, category.getIcon());
    verify(categoryRepository, never()).existsByUserIdAndNormalizedNameAndIdNot(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
}

@Test
void shouldRecomputeNormalizedNameOnRenameAndKeepBuiltInMetadata() {
    User user = TestDataFactory.createUser();
    Category category = Category.builtIn(user, BuiltInCategory.HOUSING);

    when(userRepository.findByEmail(TEST_EMAIL))
            .thenReturn(Optional.of(user));
    when(categoryRepository.findByIdAndUserId(CATEGORY_ID, user.getId()))
            .thenReturn(Optional.of(category));
    when(categoryRepository.saveAndFlush(category))
            .thenReturn(category);

    categoryService.updateCategory(
            TEST_EMAIL,
            CATEGORY_ID,
            new UpdateCategoryRequest("  Home   Costs ", false)
    );

    assertEquals("Home Costs", category.getName());
    assertEquals("home costs", category.getNormalizedName());
    assertFalse(category.isBudgetEnabled());
    assertTrue(category.isBuiltIn());
    assertEquals(CategoryIcon.HOUSE, category.getIcon());
    verify(categoryRepository).existsByUserIdAndNormalizedNameAndIdNot(
            user.getId(), "home costs", category.getId());
}
}
