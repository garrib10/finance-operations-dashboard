package dev.portfolio.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import dev.portfolio.finance.dto.budget.CreateBudgetRequest;
import dev.portfolio.finance.dto.category.CategorySelection;
import dev.portfolio.finance.dto.category.NewCategoryRequest;
import dev.portfolio.finance.dto.transaction.CreateTransactionRequest;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.CategoryIcon;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.category.CategoryNotFoundException;
import dev.portfolio.finance.exception.category.CategoryValidationException;
import dev.portfolio.finance.exception.category.DuplicateCategoryException;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.support.TestDataFactory;
import dev.portfolio.finance.validation.NormalizedCategoryName;

@ExtendWith(MockitoExtension.class)
class CategorySelectionServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private CategoryService categoryService;

    private CategorySelectionService resolver;
    private User user;

    @BeforeEach
    void setUp() {
        resolver = new CategorySelectionService(categoryRepository, categoryService);
        user = TestDataFactory.createUser();
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void resolvesAnOwnedExistingCategoryWithoutCreatingAnything() {
        Category owned = Category.custom(user, "Pets", true);
        when(categoryRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(owned));

        assertThat(resolver.resolve(user, transaction(7L, null))).isSameAs(owned);
        verifyNoInteractions(categoryService);
    }

    @Test
    void missingAndForeignIdsAreTheSameNotFound() {
        when(categoryRepository.findByIdAndUserId(any(), eq(1L))).thenReturn(Optional.empty());

        for (long id : new long[] {404L, 9_000L}) {
            assertThatThrownBy(() -> resolver.resolve(user, budget(id, null)))
                    .isInstanceOf(CategoryNotFoundException.class)
                    .hasMessage("Category not found");
        }
        verifyNoInteractions(categoryService);
    }

    @Test
    void createsABudgetEnabledCustomCategoryWithNormalizedNameAndRequestedIcon() {
        Category created = Category.custom(user, "Pet Care", true);
        when(categoryService.createCustomCategory(any(), any(), anyBoolean(), any())).thenReturn(created);

        assertThat(resolver.resolve(user, transaction(null, new NewCategoryRequest("  Pet   Care ", "paw-print"))))
                .isSameAs(created);

        verify(categoryService).createCustomCategory(user, new NormalizedCategoryName("Pet Care", "pet care"),
                true, CategoryIcon.PAW_PRINT);
        verify(categoryRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void omittedOrBlankIconBecomesTagForBothWorkflows() {
        when(categoryService.createCustomCategory(any(), any(), anyBoolean(), any()))
                .thenReturn(Category.custom(user, "Pets", true));

        resolver.resolve(user, transaction(null, new NewCategoryRequest("Pets", null)));
        resolver.resolve(user, budget(null, new NewCategoryRequest("Gifts", " ")));

        verify(categoryService).createCustomCategory(user, new NormalizedCategoryName("Pets", "pets"), true,
                CategoryIcon.TAG);
        verify(categoryService).createCustomCategory(user, new NormalizedCategoryName("Gifts", "gifts"), true,
                CategoryIcon.TAG);
    }

    @Test
    void invalidNestedNameOrIconIsAFieldErrorUnderNewCategory() {
        assertThatThrownBy(() -> resolver.resolve(user, transaction(null, new NewCategoryRequest("Bad\u0000", null))))
                .isInstanceOfSatisfying(CategoryValidationException.class, ex -> assertThat(ex.getFields())
                        .isEqualTo(Map.of("newCategory.name", "Category name contains unsupported characters")));
        assertThatThrownBy(() -> resolver.resolve(user, budget(null, new NewCategoryRequest("Pets", "<svg>"))))
                .isInstanceOfSatisfying(CategoryValidationException.class, ex -> assertThat(ex.getFields())
                        .isEqualTo(Map.of("newCategory.iconKey", "Icon must be one of the approved category icons")));
        verifyNoInteractions(categoryService);
    }

    @Test
    void duplicatesIncludingBuiltInNamesPropagateUnchanged() {
        when(categoryService.createCustomCategory(any(), any(), anyBoolean(), any()))
                .thenThrow(new DuplicateCategoryException("Category already exists"));

        assertThatThrownBy(() -> resolver.resolve(user, budget(null, new NewCategoryRequest("HOUSING", null))))
                .isInstanceOf(DuplicateCategoryException.class);
    }

    @Test
    void rejectsBothOrNeitherEvenWithoutRequestValidation() {
        assertThatThrownBy(() -> resolver.resolve(user, transaction(7L, new NewCategoryRequest("Pets", null))))
                .isInstanceOfSatisfying(CategoryValidationException.class,
                        ex -> assertThat(ex.getFields()).containsOnlyKeys("newCategory"));
        assertThatThrownBy(() -> resolver.resolve(user, budget(null, null)))
                .isInstanceOfSatisfying(CategoryValidationException.class,
                        ex -> assertThat(ex.getFields()).containsOnlyKeys("categoryId"));
        verifyNoInteractions(categoryRepository, categoryService);
    }

    private static CategorySelection transaction(Long categoryId, NewCategoryRequest newCategory) {
        return new CreateTransactionRequest(categoryId, null, null, null, null, newCategory);
    }

    private static CategorySelection budget(Long categoryId, NewCategoryRequest newCategory) {
        return new CreateBudgetRequest(categoryId, null, 9, 2026, newCategory);
    }
}
