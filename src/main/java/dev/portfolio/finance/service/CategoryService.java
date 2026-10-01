package dev.portfolio.finance.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.portfolio.finance.dto.category.CategoryResponse;
import dev.portfolio.finance.dto.category.CreateCategoryRequest;
import dev.portfolio.finance.dto.category.UpdateCategoryRequest;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.CategoryIcon;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.category.CategoryBuiltInException;
import dev.portfolio.finance.exception.category.CategoryInUseException;
import dev.portfolio.finance.exception.category.CategoryNotFoundException;
import dev.portfolio.finance.exception.category.CategoryValidationException;
import dev.portfolio.finance.exception.category.DuplicateCategoryException;
import dev.portfolio.finance.repository.BudgetRepository;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.validation.CategoryNameNormalizer;
import dev.portfolio.finance.validation.NormalizedCategoryName;

/**
 * Category CRUD for the authenticated user. Every lookup is owner-qualified, and ownership
 * is resolved before built-in or in-use checks, so another user's category is
 * indistinguishable from a missing one.
 */
@Service
public class CategoryService {

    private static final String NORMALIZED_NAME_CONSTRAINT =
            "uk_categories_user_normalized_name";

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;

    public CategoryService(
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            BudgetRepository budgetRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
    }

    @Transactional
    public CategoryResponse createCategory(
            String authenticatedEmail,
            CreateCategoryRequest request
    ) {
        User user = findUser(authenticatedEmail);

        Category category = Category.custom(
                user,
                CategoryNameNormalizer.normalize(request.name()),
                request.budgetEnabled(),
                requestedIcon(request.iconKey()).orElse(CategoryIcon.TAG)
        );

        if (categoryRepository.existsByUserIdAndNormalizedName(
                user.getId(),
                category.getNormalizedName()
        )) {
            throw new DuplicateCategoryException(
                    "Category already exists"
            );
        }

        Category savedCategory =
                saveEnforcingUniqueName(category);

        return mapToResponse(savedCategory);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories(
            String authenticatedEmail
    ) {
        User user = findUser(authenticatedEmail);

        return categoryRepository
                .findAllByUserIdOrderByNameAscIdAsc(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(
            String authenticatedEmail,
            Long categoryId
    ) {
        User user = findUser(authenticatedEmail);

        return mapToResponse(findOwnedCategory(categoryId, user));
    }

    /**
     * Full update of a custom category. Renaming is allowed while the category is in use:
     * the ID, owner, and every transaction and budget reference stay the same. Any update
     * of a built-in category, including a no-op, is forbidden.
     */
    @Transactional
    public CategoryResponse updateCategory(
            String authenticatedEmail,
            Long categoryId,
            UpdateCategoryRequest request
    ) {
        User user = findUser(authenticatedEmail);

        Category category = findOwnedCategory(categoryId, user);

        if (category.isBuiltIn()) {
            throw new CategoryBuiltInException();
        }

        NormalizedCategoryName name =
                CategoryNameNormalizer.normalize(request.name());

        // A category never conflicts with itself: case- or spacing-only renames skip the
        // check, and a real rename excludes the category's own ID.
        if (!category.getNormalizedName().equals(name.comparisonName())
                && categoryRepository.existsByUserIdAndNormalizedNameAndIdNot(
                        user.getId(),
                        name.comparisonName(),
                        category.getId()
                )) {
            throw new DuplicateCategoryException(
                    "Category already exists"
            );
        }

        category.update(
                name,
                request.budgetEnabled()
        );

        requestedIcon(request.iconKey())
                .ifPresent(category::changeIcon);

        Category savedCategory =
                saveEnforcingUniqueName(category);

        return mapToResponse(savedCategory);
    }

    /**
     * Hard-deletes an unused custom category. Built-in categories and categories referenced
     * by any transaction or budget (including past months) are never deleted, reassigned,
     * or renamed.
     */
    @Transactional
    public void deleteCategory(
            String authenticatedEmail,
            Long categoryId
    ) {
        User user = findUser(authenticatedEmail);

        Category category = findOwnedCategory(categoryId, user);

        if (category.isBuiltIn()) {
            throw new CategoryBuiltInException();
        }

        if (transactionRepository.existsByCategoryIdAndUserId(category.getId(), user.getId())
                || budgetRepository.existsByCategoryIdAndUserId(category.getId(), user.getId())) {
            throw new CategoryInUseException();
        }

        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // A delete can only violate the restrictive transaction/budget foreign keys,
            // so a reference committed after the check above surfaces here, not as success.
            throw new CategoryInUseException();
        }
    }

    private User findUser(String authenticatedEmail) {
        return userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();
    }

    private Category findOwnedCategory(Long categoryId, User user) {
        return categoryRepository
                .findByIdAndUserId(categoryId, user.getId())
                .orElseThrow(() ->
                        new CategoryNotFoundException(
                                "Category not found"
                        )
                );
    }

    /** Empty when not supplied (null or blank); otherwise an exact approved key. */
    private static Optional<CategoryIcon> requestedIcon(String iconKey) {
        if (iconKey == null || iconKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(CategoryIcon.fromKey(iconKey).orElseThrow(() ->
                new CategoryValidationException(
                        "iconKey",
                        "Icon must be one of the approved category icons"
                )));
    }

    private CategoryResponse mapToResponse(
            Category category
    ) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.isBudgetEnabled(),
                category.isBuiltIn(),
                category.getIcon().key(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    /**
     * The existence checks are only a friendly first pass; a concurrent request can still
     * win the race, so the unique constraint's violation is mapped to the same conflict and
     * the transaction rolls back.
     */
    private Category saveEnforcingUniqueName(Category category) {
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            if (violatesNormalizedNameConstraint(ex)) {
                throw new DuplicateCategoryException(
                        "Category already exists"
                );
            }
            throw ex;
        }
    }

    /**
     * MySQL reports {@code categories.uk_categories_user_normalized_name}; H2 reports the
     * upper-case name. Matching is case-insensitive across the cause chain.
     */
    private static boolean violatesNormalizedNameConstraint(
            DataIntegrityViolationException ex
    ) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT)
                    .contains(NORMALIZED_NAME_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }
}
