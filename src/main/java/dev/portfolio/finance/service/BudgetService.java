package dev.portfolio.finance.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.portfolio.finance.dto.budget.BudgetResponse;
import dev.portfolio.finance.dto.budget.CreateBudgetRequest;
import dev.portfolio.finance.dto.budget.UpdateBudgetRequest;
import dev.portfolio.finance.entity.Budget;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.budget.BudgetNotFoundException;
import dev.portfolio.finance.exception.budget.DuplicateBudgetException;
import dev.portfolio.finance.exception.category.CategoryNotFoundException;
import dev.portfolio.finance.repository.BudgetRepository;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.repository.UserRepository;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public BudgetService(
            BudgetRepository budgetRepository,
            CategoryRepository categoryRepository,
            UserRepository userRepository
    ) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public BudgetResponse createBudget(
            String authenticatedEmail,
            CreateBudgetRequest request
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        Category category = categoryRepository
                .findByIdAndUserId(
                        request.categoryId(),
                        user.getId()
                )
                .orElseThrow(() ->
                        new CategoryNotFoundException(
                                "Category not found"
                        )
                );

        if (budgetRepository.existsByUserIdAndCategoryIdAndMonthAndYear(
                user.getId(),
                category.getId(),
                request.month(),
                request.year()
        )) {
            throw new DuplicateBudgetException(
                    "Budget already exists for this category and month"
            );
        }

        Budget budget = new Budget(
                user,
                category,
                request.monthlyLimit(),
                request.month(),
                request.year()
        );

        Budget savedBudget =
                budgetRepository.save(budget);

        return mapToResponse(savedBudget);
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> getAllBudgets(
            String authenticatedEmail
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        return budgetRepository
                .findAllByUserIdOrderByYearDescMonthDesc(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BudgetResponse getBudgetById(
            String authenticatedEmail,
            Long budgetId
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        Budget budget = budgetRepository
                .findByIdAndUserId(
                        budgetId,
                        user.getId()
                )
                .orElseThrow(() ->
                        new BudgetNotFoundException(
                                "Budget not found"
                        )
                );

        return mapToResponse(budget);
    }

    @Transactional
    public BudgetResponse updateBudget(
            String authenticatedEmail,
            Long budgetId,
            UpdateBudgetRequest request
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        Budget budget = budgetRepository
                .findByIdAndUserId(
                        budgetId,
                        user.getId()
                )
                .orElseThrow(() ->
                        new BudgetNotFoundException(
                                "Budget not found"
                        )
                );

        Category category = categoryRepository
                .findByIdAndUserId(
                        request.categoryId(),
                        user.getId()
                )
                .orElseThrow(() ->
                        new CategoryNotFoundException(
                                "Category not found"
                        )
                );

        boolean budgetIdentityChanged =
                !budget.getCategory().getId().equals(category.getId())
                        || budget.getMonth() != request.month()
                        || budget.getYear() != request.year();

        if (budgetIdentityChanged
                && budgetRepository.existsByUserIdAndCategoryIdAndMonthAndYear(
                        user.getId(),
                        category.getId(),
                        request.month(),
                        request.year()
                )) {

            throw new DuplicateBudgetException(
                    "Budget already exists for this category and month"
            );
        }

        budget.update(
                category,
                request.monthlyLimit(),
                request.month(),
                request.year()
        );

        Budget savedBudget =
                budgetRepository.save(budget);

        return mapToResponse(savedBudget);
    }

    @Transactional
    public void deleteBudget(
            String authenticatedEmail,
            Long budgetId
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        Budget budget = budgetRepository
                .findByIdAndUserId(
                        budgetId,
                        user.getId()
                )
                .orElseThrow(() ->
                        new BudgetNotFoundException(
                                "Budget not found"
                        )
                );

        budgetRepository.delete(budget);
    }

    private BudgetResponse mapToResponse(
            Budget budget
    ) {
        return new BudgetResponse(
                budget.getId(),
                budget.getCategory().getId(),
                budget.getCategory().getName(),
                budget.getMonthlyLimit(),
                budget.getMonth(),
                budget.getYear(),
                budget.getCreatedAt(),
                budget.getUpdatedAt()
        );
    }
}