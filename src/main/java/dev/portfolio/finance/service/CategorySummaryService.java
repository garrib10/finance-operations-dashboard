package dev.portfolio.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.portfolio.finance.dto.category.CategorySummaryListResponse;
import dev.portfolio.finance.dto.category.CategorySummaryResponse;
import dev.portfolio.finance.dto.category.CurrentMonthBudgetResponse;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.BudgetRepository;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.repository.projection.CategoryBudgetCountProjection;
import dev.portfolio.finance.repository.projection.CategoryTransactionUsageProjection;
import dev.portfolio.finance.repository.projection.CurrentMonthBudgetProjection;

/**
 * Category usage for the Categories page. Five statements per request regardless of how
 * many categories exist: the user, the categories, one grouped transaction aggregate, one
 * grouped budget count, and the reporting month's budgets. Transactions and budgets are
 * aggregated separately, never joined together, so no count or sum is multiplied.
 */
@Service
public class CategorySummaryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final ReportingPeriodProvider reportingPeriodProvider;

    public CategorySummaryService(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository,
            BudgetRepository budgetRepository,
            ReportingPeriodProvider reportingPeriodProvider
    ) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.reportingPeriodProvider = reportingPeriodProvider;
    }

    @Transactional(readOnly = true)
    public CategorySummaryListResponse getSummary(String authenticatedEmail) {
        User user = userRepository.findByEmail(authenticatedEmail).orElseThrow();
        Long userId = user.getId();
        ReportingPeriod period = reportingPeriodProvider.currentMonth();

        List<Category> categories = categoryRepository.findAllByUserIdOrderByNameAscIdAsc(userId);

        Map<Long, CategoryTransactionUsageProjection> usage = byCategory(
                transactionRepository.summarizeUsageByCategory(
                        userId, TransactionType.EXPENSE, period.start(), period.end()),
                CategoryTransactionUsageProjection::getCategoryId);

        Map<Long, CategoryBudgetCountProjection> budgetCounts = byCategory(
                budgetRepository.countBudgetsByCategory(userId),
                CategoryBudgetCountProjection::getCategoryId);

        Map<Long, CurrentMonthBudgetProjection> monthBudgets = byCategory(
                budgetRepository.findMonthBudgets(userId, period.month(), period.year()),
                CurrentMonthBudgetProjection::getCategoryId);

        List<CategorySummaryResponse> rows = categories.stream()
                .map(category -> toRow(
                        category,
                        usage.get(category.getId()),
                        budgetCounts.get(category.getId()),
                        monthBudgets.get(category.getId())))
                .toList();

        return new CategorySummaryListResponse(period.month(), period.year(), rows);
    }

    private static CategorySummaryResponse toRow(
            Category category,
            CategoryTransactionUsageProjection usage,
            CategoryBudgetCountProjection budgetCount,
            CurrentMonthBudgetProjection monthBudget
    ) {
        long transactionCount = usage == null ? 0 : usage.getTransactionCount();
        long currentMonthTransactionCount = usage == null ? 0 : count(usage.getCurrentMonthTransactionCount());
        long budgets = budgetCount == null ? 0 : budgetCount.getBudgetCount();
        BigDecimal currentMonthSpent = usage == null ? ZERO : money(usage.getCurrentMonthSpent());

        return new CategorySummaryResponse(
                category.getId(),
                category.getName(),
                category.getIcon().key(),
                category.isBuiltIn(),
                category.isBudgetEnabled(),
                transactionCount,
                currentMonthTransactionCount,
                budgets,
                usage == null ? null : usage.getLastTransactionDate(),
                currentMonthSpent,
                usage == null ? ZERO : money(usage.getAllTimeSpent()),
                monthBudget == null ? null : toBudget(monthBudget, currentMonthSpent),
                !category.isBuiltIn() && transactionCount == 0 && budgets == 0
        );
    }

    /** Same metrics as budget analytics: the month's expense spending against the limit. */
    private static CurrentMonthBudgetResponse toBudget(
            CurrentMonthBudgetProjection budget,
            BigDecimal amountSpent
    ) {
        BigDecimal monthlyLimit = money(budget.getMonthlyLimit());
        BudgetMetrics metrics = BudgetMetrics.calculate(monthlyLimit, amountSpent);
        return new CurrentMonthBudgetResponse(
                budget.getBudgetId(),
                monthlyLimit,
                amountSpent,
                metrics.amountRemaining(),
                metrics.percentageUsed(),
                metrics.status()
        );
    }

    /** COUNT never returns null for a group; guarded anyway so the public field is never null. */
    private static long count(Long value) {
        return value == null ? 0 : value;
    }

    /** Never null (COALESCE sums, NOT NULL limits); COALESCE(..., 0) can come back with scale 0. */
    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static <T> Map<Long, T> byCategory(List<T> rows, Function<T, Long> categoryId) {
        return rows.stream().collect(Collectors.toMap(categoryId, Function.identity()));
    }
}
