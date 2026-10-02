package dev.portfolio.finance.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import dev.portfolio.finance.dto.category.CategorySelection;
import dev.portfolio.finance.dto.category.NewCategoryRequest;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.CategoryIcon;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.category.CategoryNotFoundException;
import dev.portfolio.finance.exception.category.CategoryValidationException;
import dev.portfolio.finance.exception.category.InvalidCategoryNameException;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.validation.CategoryNameNormalizer;
import dev.portfolio.finance.validation.ExactlyOneCategorySelectionValidator;
import dev.portfolio.finance.validation.NormalizedCategoryName;

/**
 * Resolves the category of a transaction or budget write: an existing category owned by
 * the user, or a new custom category created through {@link CategoryService}. One
 * implementation serves both workflows.
 *
 * <p>Runs only inside the caller's write transaction ({@code MANDATORY}), so a new category
 * commits or rolls back together with the financial record. Errors are the category API's:
 * a missing or foreign ID gives the same {@code CategoryNotFoundException}, and a duplicate
 * name gives {@code DuplicateCategoryException}. An existing category is never reused in
 * place of a requested new one, and nothing falls back to "Other".
 */
@Service
public class CategorySelectionService {

    static final String INVALID_ICON = "Icon must be one of the approved category icons";

    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;

    public CategorySelectionService(
            CategoryRepository categoryRepository,
            CategoryService categoryService
    ) {
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Category resolve(User user, CategorySelection selection) {
        boolean existing = selection.categoryId() != null;
        boolean created = selection.newCategory() != null;

        // Request validation enforces this too; keep the service safe for any caller.
        if (existing && created) {
            throw new CategoryValidationException("newCategory", ExactlyOneCategorySelectionValidator.BOTH);
        }
        if (!existing && !created) {
            throw new CategoryValidationException("categoryId", ExactlyOneCategorySelectionValidator.MISSING);
        }

        if (existing) {
            return categoryRepository
                    .findByIdAndUserId(selection.categoryId(), user.getId())
                    .orElseThrow(() -> new CategoryNotFoundException("Category not found"));
        }
        return createFor(user, selection.newCategory());
    }

    /** Categories created from a transaction or budget are always budget-enabled. */
    private Category createFor(User user, NewCategoryRequest request) {
        NormalizedCategoryName name;
        try {
            name = CategoryNameNormalizer.normalize(request.name());
        } catch (InvalidCategoryNameException invalid) {
            throw new CategoryValidationException("newCategory.name", invalid.getMessage());
        }

        CategoryIcon icon = request.iconKey() == null || request.iconKey().isBlank()
                ? CategoryIcon.TAG
                : CategoryIcon.fromKey(request.iconKey()).orElseThrow(() ->
                        new CategoryValidationException("newCategory.iconKey", INVALID_ICON));

        return categoryService.createCustomCategory(user, name, true, icon);
    }
}
