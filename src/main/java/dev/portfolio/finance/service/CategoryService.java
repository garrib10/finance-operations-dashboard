package dev.portfolio.finance.service;

import java.util.List;
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

@Service
public class CategoryService {

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

        String normalizedName = request.name().trim();

        if (categoryRepository.existsByUserIdAndNameIgnoreCase(
                user.getId(),
                normalizedName
        )) {
            throw new DuplicateCategoryException(
                    "Category already exists"
            );
        }

        Category category = new Category(
                user,
                normalizedName,
                request.budgetEnabled()
        );

        Category savedCategory =
                categoryRepository.save(category);

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
                .findAllByUserIdOrderByNameAsc(user.getId())
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

        String normalizedName = request.name().trim();

        boolean nameChanged =
                !category.getName().equalsIgnoreCase(normalizedName);

        if (nameChanged && categoryRepository.existsByUserIdAndNameIgnoreCase(
                user.getId(),
                normalizedName
        )) {
            throw new DuplicateCategoryException(
                    "Category already exists"
            );
        }

        category.update(
                normalizedName, 
                request.budgetEnabled()
       );

        Category savedCategory = 
                 categoryRepository.save(category);

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
}