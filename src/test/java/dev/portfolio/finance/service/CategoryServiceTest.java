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
import dev.portfolio.finance.exception.category.CategoryBuiltInException;
import dev.portfolio.finance.exception.category.CategoryInUseException;
import dev.portfolio.finance.exception.category.CategoryNotFoundException;
import dev.portfolio.finance.exception.category.CategoryValidationException;
import dev.portfolio.finance.exception.category.DuplicateCategoryException;
import dev.portfolio.finance.exception.category.InvalidCategoryNameException;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.repository.BudgetRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.support.TestDataFactory;
import dev.portfolio.finance.validation.CategoryNameNormalizer;


@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final String TEST_EMAIL = "test@example.com";
    private static final Long CATEGORY_ID = 1L;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BudgetRepository budgetRepository;

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
void shouldChangeDisplayCasingWithoutDuplicateCheckAndKeepIcon() {
    User user = TestDataFactory.createUser();
    Category category = Category.custom(user, CategoryNameNormalizer.normalize("Groceries"), true,
            CategoryIcon.SHOPPING_CART);

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
    assertFalse(category.isBuiltIn());
    assertEquals(CategoryIcon.SHOPPING_CART, category.getIcon());
    assertEquals("shopping-cart", response.iconKey());
    verify(categoryRepository, never()).existsByUserIdAndNormalizedNameAndIdNot(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
}

@Test
void shouldRecomputeNormalizedNameOnRenameAndKeepIconWhenOmitted() {
    User user = TestDataFactory.createUser();
    Category category = Category.custom(user, CategoryNameNormalizer.normalize("Housing Costs"), true,
            CategoryIcon.HOUSE);

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
    assertFalse(category.isBuiltIn());
    assertEquals(CategoryIcon.HOUSE, category.getIcon());
    verify(categoryRepository).existsByUserIdAndNormalizedNameAndIdNot(
            user.getId(), "home costs", category.getId());
}

// ---------------------------------------------------------------- Phase 2 policies

private Category owned(Category category, long id) {
    org.springframework.test.util.ReflectionTestUtils.setField(category, "id", id);
    return category;
}

private void givenUserOwns(User user, Category category) {
    when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
    when(categoryRepository.findByIdAndUserId(category.getId(), user.getId())).thenReturn(Optional.of(category));
}

@Test
void createDefaultsToTagWhenIconOmittedOrBlankAndStoresAnApprovedIcon() {
    User user = TestDataFactory.createUser();
    when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
    when(categoryRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Category.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    assertEquals("tag", categoryService.createCategory(TEST_EMAIL,
            new CreateCategoryRequest("Pets", true, null)).iconKey());
    assertEquals("tag", categoryService.createCategory(TEST_EMAIL,
            new CreateCategoryRequest("Gifts", true, "  ")).iconKey());
    CategoryResponse withIcon = categoryService.createCategory(TEST_EMAIL,
            new CreateCategoryRequest("Gym", false, "heart-pulse"));

    assertEquals("heart-pulse", withIcon.iconKey());
    assertFalse(withIcon.builtIn());
    assertFalse(withIcon.budgetEnabled());
}

@Test
void createRejectsAnIconOutsideTheCatalogBeforeSaving() {
    User user = TestDataFactory.createUser();
    when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

    CategoryValidationException exception = assertThrows(CategoryValidationException.class,
            () -> categoryService.createCategory(TEST_EMAIL, new CreateCategoryRequest("Pets", true, "<svg>")));

    assertEquals(java.util.Map.of("iconKey", "Icon must be one of the approved category icons"),
            exception.getFields());
    verify(categoryRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(Category.class));
}

@Test
void createRejectsANameThatMatchesAnExistingBuiltInCategory() {
    User user = TestDataFactory.createUser();
    when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
    when(categoryRepository.existsByUserIdAndNormalizedName(user.getId(), "housing")).thenReturn(true);

    assertThrows(DuplicateCategoryException.class,
            () -> categoryService.createCategory(TEST_EMAIL, new CreateCategoryRequest("  HOUSING ", true)));
}

@Test
void updateChangesACustomIconAndKeepsIdOwnerAndCustomStatus() {
    User user = TestDataFactory.createUser();
    Category category = owned(Category.custom(user, "Pets", true), 7L);
    givenUserOwns(user, category);
    when(categoryRepository.saveAndFlush(category)).thenReturn(category);

    CategoryResponse response = categoryService.updateCategory(TEST_EMAIL, 7L,
            new UpdateCategoryRequest("Pet Care", true, "heart-pulse"));

    assertEquals(7L, response.id());
    assertEquals("Pet Care", response.name());
    assertEquals("heart-pulse", response.iconKey());
    assertFalse(response.builtIn());
    assertSame(user, category.getUser());
}

@Test
void updateWithBlankIconKeepsTheCurrentIcon() {
    User user = TestDataFactory.createUser();
    Category category = owned(Category.custom(user, CategoryNameNormalizer.normalize("Pets"), true,
            CategoryIcon.HEART_PULSE), 7L);
    givenUserOwns(user, category);
    when(categoryRepository.saveAndFlush(category)).thenReturn(category);

    assertEquals("heart-pulse", categoryService.updateCategory(TEST_EMAIL, 7L,
            new UpdateCategoryRequest("Pets", true, " ")).iconKey());
}

@Test
void updateRejectsAnInvalidIconWithoutSaving() {
    User user = TestDataFactory.createUser();
    Category category = owned(Category.custom(user, "Pets", true), 7L);
    givenUserOwns(user, category);

    assertThrows(CategoryValidationException.class, () -> categoryService.updateCategory(TEST_EMAIL, 7L,
            new UpdateCategoryRequest("Pets", true, "https://example.com/icon.svg")));
    verify(categoryRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(Category.class));
}

@Test
void anyUpdateOfABuiltInCategoryIsForbiddenEvenANoOp() {
    User user = TestDataFactory.createUser();
    Category housing = owned(Category.builtIn(user, BuiltInCategory.HOUSING), 1L);
    givenUserOwns(user, housing);

    for (UpdateCategoryRequest request : List.of(
            new UpdateCategoryRequest("Housing", true),
            new UpdateCategoryRequest("Housing", true, "house"),
            new UpdateCategoryRequest("Home", true),
            new UpdateCategoryRequest("Housing", false),
            new UpdateCategoryRequest("Housing", true, "tag"),
            new UpdateCategoryRequest("Bad\u0000", true))) {
        assertThrows(CategoryBuiltInException.class, () -> categoryService.updateCategory(TEST_EMAIL, 1L, request));
    }

    assertEquals("Housing", housing.getName());
    assertTrue(housing.isBudgetEnabled());
    assertEquals(CategoryIcon.HOUSE, housing.getIcon());
    verify(categoryRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(Category.class));
}

@Test
void deletingABuiltInCategoryIsForbiddenBeforeAnyReferenceCheck() {
    User user = TestDataFactory.createUser();
    Category other = owned(Category.builtIn(user, BuiltInCategory.OTHER), 13L);
    givenUserOwns(user, other);

    assertThrows(CategoryBuiltInException.class, () -> categoryService.deleteCategory(TEST_EMAIL, 13L));

    org.mockito.Mockito.verifyNoInteractions(transactionRepository, budgetRepository);
    verify(categoryRepository, never()).delete(org.mockito.ArgumentMatchers.any(Category.class));
}

@Test
void deletingAnUnusedCustomCategoryDeletesAndFlushes() {
    User user = TestDataFactory.createUser();
    Category pets = owned(Category.custom(user, "Pets", true), 7L);
    givenUserOwns(user, pets);

    categoryService.deleteCategory(TEST_EMAIL, 7L);

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(categoryRepository);
    order.verify(categoryRepository).delete(pets);
    order.verify(categoryRepository).flush();
    verify(transactionRepository).existsByCategoryIdAndUserId(7L, user.getId());
    verify(budgetRepository).existsByCategoryIdAndUserId(7L, user.getId());
}

@Test
void deletingACategoryReferencedByTransactionsOrBudgetsIsBlocked() {
    User user = TestDataFactory.createUser();
    Category pets = owned(Category.custom(user, "Pets", true), 7L);
    givenUserOwns(user, pets);

    // transactions only / budgets only (including past months) / both
    boolean[][] references = {{true, false}, {false, true}, {true, true}};
    for (boolean[] reference : references) {
        org.mockito.Mockito.lenient().when(transactionRepository.existsByCategoryIdAndUserId(7L, user.getId()))
                .thenReturn(reference[0]);
        org.mockito.Mockito.lenient().when(budgetRepository.existsByCategoryIdAndUserId(7L, user.getId()))
                .thenReturn(reference[1]);

        CategoryInUseException exception = assertThrows(CategoryInUseException.class,
                () -> categoryService.deleteCategory(TEST_EMAIL, 7L));
        assertEquals("This category is used by transactions or budgets and cannot be deleted.",
                exception.getMessage());
    }
    verify(categoryRepository, never()).delete(org.mockito.ArgumentMatchers.any(Category.class));
}

@Test
void aReferenceCommittedAfterTheCheckMapsToInUse() {
    User user = TestDataFactory.createUser();
    Category pets = owned(Category.custom(user, "Pets", true), 7L);
    givenUserOwns(user, pets);
    org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk"))
            .when(categoryRepository).flush();

    assertThrows(CategoryInUseException.class, () -> categoryService.deleteCategory(TEST_EMAIL, 7L));
}

@Test
void foreignAndMissingCategoriesAreIndistinguishableAndCheckedFirst() {
    User user = TestDataFactory.createUser();
    when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
    when(categoryRepository.findByIdAndUserId(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());

    for (org.junit.jupiter.api.function.Executable call : List.<org.junit.jupiter.api.function.Executable>of(
            () -> categoryService.getCategoryById(TEST_EMAIL, 99L),
            () -> categoryService.updateCategory(TEST_EMAIL, 99L, new UpdateCategoryRequest("Bad\u0000", true, "x")),
            () -> categoryService.deleteCategory(TEST_EMAIL, 99L))) {
        CategoryNotFoundException exception = assertThrows(CategoryNotFoundException.class, call);
        assertEquals("Category not found", exception.getMessage());
    }
    org.mockito.Mockito.verifyNoInteractions(transactionRepository, budgetRepository);
    verify(categoryRepository, never()).existsByUserIdAndNormalizedNameAndIdNot(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
}

@Test
void responsesFallBackToTagForUnknownStoredIconsAndNeverCarryInternalFields() {
    User user = TestDataFactory.createUser();
    Category legacy = owned(Category.custom(user, "Legacy", true), 8L);
    org.springframework.test.util.ReflectionTestUtils.setField(legacy, "iconKey", "retired-icon");
    givenUserOwns(user, legacy);

    CategoryResponse response = categoryService.getCategoryById(TEST_EMAIL, 8L);

    assertEquals("tag", response.iconKey());
    assertEquals("retired-icon", legacy.getIconKey());
    assertEquals(List.of("id", "name", "budgetEnabled", "builtIn", "iconKey", "createdAt", "updatedAt"),
            java.util.Arrays.stream(CategoryResponse.class.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName).toList());
}
}
