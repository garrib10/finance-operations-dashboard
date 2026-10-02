package dev.portfolio.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.portfolio.finance.dto.budget.BudgetAnalyticsResponse;
import dev.portfolio.finance.dto.budget.BudgetResponse;
import dev.portfolio.finance.dto.budget.CreateBudgetRequest;
import dev.portfolio.finance.dto.budget.UpdateBudgetRequest;
import dev.portfolio.finance.entity.Budget;
import dev.portfolio.finance.entity.BudgetStatus;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.budget.BudgetNotFoundException;
import dev.portfolio.finance.exception.budget.DuplicateBudgetException;
import dev.portfolio.finance.repository.BudgetRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;

@Service
public class BudgetService {

    private static final String BUDGET_PERIOD_CONSTRAINT =
            "uk_budget_user_category_month_year";

    private final BudgetRepository budgetRepository;
    private final CategorySelectionService categorySelectionService;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public BudgetService(
            BudgetRepository budgetRepository,
            CategorySelectionService categorySelectionService,
            UserRepository userRepository,
            TransactionRepository transactionRepository
    ) {
        this.budgetRepository = budgetRepository;
        this.categorySelectionService = categorySelectionService;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public BudgetResponse createBudget(
            String authenticatedEmail,
            CreateBudgetRequest request
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        // An existing owned category, or a new one created in this same transaction.
        Category category = categorySelectionService.resolve(
                user,
                request
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
                saveEnforcingUniqueBudget(budget);

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

        // Resolved only after the budget is found and owned, so a 404 never leaves a newly
        // created category behind.
        Category category = categorySelectionService.resolve(
                user,
                request
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
                saveEnforcingUniqueBudget(budget);

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

    @Transactional(readOnly = true)
    public BudgetAnalyticsResponse getBudgetAnalytics(
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

        return mapToAnalyticsResponse(budget);
    }

    private BudgetAnalyticsResponse mapToAnalyticsResponse(
            Budget budget
    ) {
        LocalDate startDate = LocalDate.of(
                budget.getYear(),
                budget.getMonth(),
                1
        );

        LocalDate endDate = startDate.withDayOfMonth(
                startDate.lengthOfMonth()
        );

        BigDecimal amountSpent =
                transactionRepository
                        .sumAmountByUserCategoryTypeAndDateRange(
                                budget.getUser().getId(),
                                budget.getCategory().getId(),
                                TransactionType.EXPENSE,
                                startDate,
                                endDate
                        );

        BigDecimal amountRemaining =
                budget.getMonthlyLimit()
                        .subtract(amountSpent);

        BigDecimal percentageUsed =
                amountSpent
                        .divide(
                                budget.getMonthlyLimit(),
                                4,
                                RoundingMode.HALF_UP
                        )
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        BudgetStatus status =
                determineBudgetStatus(percentageUsed);

        return new BudgetAnalyticsResponse(
                budget.getId(),
                budget.getCategory().getId(),
                budget.getCategory().getName(),
                budget.getCategory().getIcon().key(),
                budget.getMonthlyLimit(),
                amountSpent,
                amountRemaining,
                percentageUsed,
                status,
                budget.getMonth(),
                budget.getYear()
        );
    }

    private BudgetStatus determineBudgetStatus(
            BigDecimal percentageUsed
    ) {
        if (percentageUsed.compareTo(
                BigDecimal.valueOf(100)
        ) >= 0) {
            return BudgetStatus.OVER_BUDGET;
        }

        if (percentageUsed.compareTo(
                BigDecimal.valueOf(75)
        ) >= 0) {
            return BudgetStatus.WARNING;
        }

        if (percentageUsed.compareTo(
                BigDecimal.valueOf(50)
        ) >= 0) {
            return BudgetStatus.CAUTION;
        }

        return BudgetStatus.ON_TRACK;
    }

    private BudgetResponse mapToResponse(
            Budget budget
    ) {
        return new BudgetResponse(
                budget.getId(),
                budget.getCategory().getId(),
                budget.getCategory().getName(),
                budget.getCategory().getIcon().key(),
                budget.getMonthlyLimit(),
                budget.getMonth(),
                budget.getYear(),
                budget.getCreatedAt(),
                budget.getUpdatedAt()
        );
    }

    /**
     * The existence check above is a friendly first pass. A concurrent request for the same
     * category and month can still win, so that unique-key violation becomes the same
     * conflict and the whole transaction, including any category created for it, rolls back.
     */
    private Budget saveEnforcingUniqueBudget(Budget budget) {
        try {
            return budgetRepository.saveAndFlush(budget);
        } catch (DataIntegrityViolationException ex) {
            if (violatesConstraint(ex, BUDGET_PERIOD_CONSTRAINT)) {
                throw new DuplicateBudgetException(
                        "Budget already exists for this category and month"
                );
            }
            throw ex;
        }
    }

    private static boolean violatesConstraint(DataIntegrityViolationException ex, String constraint) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
