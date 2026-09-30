package dev.portfolio.finance.service;

import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.portfolio.finance.dto.category.CategoryResponse;
import dev.portfolio.finance.dto.category.CreateCategoryRequest;
import dev.portfolio.finance.dto.category.UpdateCategoryRequest;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.category.CategoryNotFoundException;
import dev.portfolio.finance.exception.category.DuplicateCategoryException;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.validation.CategoryNameNormalizer;
import dev.portfolio.finance.validation.NormalizedCategoryName;

@Service
public class CategoryService {

    private static final String NORMALIZED_NAME_CONSTRAINT =
            "uk_categories_user_normalized_name";

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public CategoryService(
            CategoryRepository categoryRepository,
            UserRepository userRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CategoryResponse createCategory(
            String authenticatedEmail,
            CreateCategoryRequest request
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        Category category = Category.custom(
                user,
                CategoryNameNormalizer.normalize(request.name()),
                request.budgetEnabled()
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
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        return categoryRepository
                .findAllByUserIdOrderByNameAscIdAsc(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private CategoryResponse mapToResponse(
            Category category
    ) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.isBudgetEnabled(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(
            String authenticatedEmail,
            Long categoryId
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        Category category = categoryRepository
                .findByIdAndUserId(categoryId, user.getId())
                .orElseThrow(() ->
                        new CategoryNotFoundException(
                                "Category not found"
                        )
                );

        return mapToResponse(category);
    }

    @Transactional
    public CategoryResponse updateCategory(
            String authenticatedEmail,
            Long categoryId,
            UpdateCategoryRequest request
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        Category category = categoryRepository
                .findByIdAndUserId(categoryId, user.getId())
                .orElseThrow(() ->
                        new CategoryNotFoundException(
                                "Category not found"
                        )
                );

        NormalizedCategoryName name =
                CategoryNameNormalizer.normalize(request.name());

        boolean nameChanged = !category.getNormalizedName()
                .equals(name.comparisonName());

        if (nameChanged && categoryRepository.existsByUserIdAndNormalizedNameAndIdNot(
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

        Category savedCategory =
                saveEnforcingUniqueName(category);

        return mapToResponse(savedCategory);
    }

    @Transactional
    public void deleteCategory(
            String authenticatedEmail,
            Long categoryId
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        Category category = categoryRepository
                .findByIdAndUserId(categoryId, user.getId())
                .orElseThrow(() ->
                        new CategoryNotFoundException(
                                "Category not found"
                        )
                );

        categoryRepository.delete(category);
    }

    /**
     * The existence checks are only a friendly first pass; a concurrent request can still
     * win the race, so the unique constraint's violation is mapped to the same conflict.
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
