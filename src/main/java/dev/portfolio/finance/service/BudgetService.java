package dev.portfolio.finance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.portfolio.finance.dto.budget.BudgetAnalyticsResponse;
import dev.portfolio.finance.dto.budget.BudgetMonthAnalyticsResponse;
import dev.portfolio.finance.dto.budget.BudgetResponse;
import dev.portfolio.finance.dto.budget.CreateBudgetRequest;
import dev.portfolio.finance.dto.budget.UpdateBudgetRequest;
import dev.portfolio.finance.entity.Budget;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.budget.BudgetNotFoundException;
import dev.portfolio.finance.exception.budget.BudgetValidationException;
import dev.portfolio.finance.exception.budget.DuplicateBudgetException;
import dev.portfolio.finance.repository.BudgetRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.repository.projection.CategorySpendingProjection;

@Service
public class BudgetService {

    private static final String BUDGET_PERIOD_CONSTRAINT =
            "uk_budget_user_category_month_year";

    /** The earliest year a budget can have (the same rule as creating one). */
    private static final int EARLIEST_YEAR = 2000;
    /** Digits only (no sign, spaces, or decimals); nine at most, so it always fits an int. */
    private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d{1,9}");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

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

    /**
     * Every budget of the user for one month, with analytics, in exactly three statements
     * however many budgets there are (none included): the user, the month's budgets with their categories, and the
     * month's expense spending grouped by category. The figures come from the same
     * {@link BudgetMetrics} as the single-budget analytics. Month is 1–12 and year is 2000 or
     * later, with no upper bound, so future months can be planned like when creating one.
     */
    @Transactional(readOnly = true)
    public BudgetMonthAnalyticsResponse getMonthAnalytics(
            String authenticatedEmail,
            String monthValue,
            String yearValue
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        Integer month = parseMonth(monthValue, errors);
        Integer year = parseYear(yearValue, errors);
        if (!errors.isEmpty()) {
            throw new BudgetValidationException(errors);
        }

        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();
        ReportingPeriod period = ReportingPeriod.of(year, month);

        List<Budget> budgets = budgetRepository
                .findAllByUserIdAndMonthAndYearOrderByCategoryNameAscCategoryIdAsc(
                        user.getId(),
                        month,
                        year
                );

        Map<Long, BigDecimal> spendingByCategory = transactionRepository
                .findSpendingByCategory(
                        user.getId(),
                        TransactionType.EXPENSE,
                        period.start(),
                        period.end()
                )
                .stream()
                .collect(Collectors.toMap(
                        CategorySpendingProjection::getCategoryId,
                        CategorySpendingProjection::getAmountSpent
                ));

        List<BudgetAnalyticsResponse> rows = budgets.stream()
                .map(budget -> toAnalyticsResponse(
                        budget,
                        spendingByCategory.getOrDefault(budget.getCategory().getId(), ZERO)
                ))
                .toList();

        return new BudgetMonthAnalyticsResponse(month, year, rows);
    }

    private static Integer parseMonth(String value, Map<String, String> errors) {
        if (value == null || value.isEmpty()) {
            errors.put("month", "Month is required");
            return null;
        }
        if (!WHOLE_NUMBER.matcher(value).matches()) {
            errors.put("month", "Month must be a whole number between 1 and 12");
            return null;
        }
        int month = Integer.parseInt(value);
        if (month < 1 || month > 12) {
            errors.put("month", "Month must be between 1 and 12");
            return null;
        }
        return month;
    }

    private static Integer parseYear(String value, Map<String, String> errors) {
        if (value == null || value.isEmpty()) {
            errors.put("year", "Year is required");
            return null;
        }
        if (!WHOLE_NUMBER.matcher(value).matches()) {
            errors.put("year", "Year must be a whole number");
            return null;
        }
        int year = Integer.parseInt(value);
        if (year < EARLIEST_YEAR) {
            errors.put("year", "Year must be " + EARLIEST_YEAR + " or later");
            return null;
        }
        return year;
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

        return toAnalyticsResponse(budget, amountSpent);
    }

    /** One budget's analytics from its month's expense spending; shared by both endpoints. */
    private static BudgetAnalyticsResponse toAnalyticsResponse(
            Budget budget,
            BigDecimal amountSpent
    ) {
        BudgetMetrics metrics =
                BudgetMetrics.calculate(budget.getMonthlyLimit(), amountSpent);

        return new BudgetAnalyticsResponse(
                budget.getId(),
                budget.getCategory().getId(),
                budget.getCategory().getName(),
                budget.getCategory().getIcon().key(),
                budget.getMonthlyLimit(),
                amountSpent,
                metrics.amountRemaining(),
                metrics.percentageUsed(),
                metrics.status(),
                budget.getMonth(),
                budget.getYear()
        );
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
