package dev.portfolio.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import dev.portfolio.finance.exception.category.CategoryValidationException;
import dev.portfolio.finance.repository.BudgetRepository;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.repository.projection.CategoryBudgetCountProjection;
import dev.portfolio.finance.repository.projection.CategoryTransactionUsageProjection;
import dev.portfolio.finance.repository.projection.CurrentMonthBudgetProjection;

/**
 * Category usage for the Categories page, for the server's current month or a requested
 * earlier one. Five statements per request regardless of how
 * many categories exist: the user, the categories, one grouped transaction aggregate, one
 * grouped budget count, and the reporting month's budgets. Transactions and budgets are
 * aggregated separately, never joined together, so no count or sum is multiplied.
 */
@Service
public class CategorySummaryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final int EARLIEST_YEAR = 2000;
    private static final String PAIRED_MESSAGE = "Month and year must be given together";

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

    /** The server's current reporting month. */
    @Transactional(readOnly = true)
    public CategorySummaryListResponse getSummary(String authenticatedEmail) {
        return getSummary(authenticatedEmail, null, null);
    }

    /**
     * The requested month, or the server's current month when both values are absent. The
     * period is validated before any query, so a rejected request touches no data; the
     * request never names a user, so ownership always comes from the authenticated email.
     */
    @Transactional(readOnly = true)
    public CategorySummaryListResponse getSummary(String authenticatedEmail, Integer month, Integer year) {
        ReportingPeriod current = reportingPeriodProvider.currentMonth();
        validatePeriod(month, year, current);
        ReportingPeriod period = reportingPeriodProvider.forMonth(month, year);

        User user = userRepository.findByEmail(authenticatedEmail).orElseThrow();
        Long userId = user.getId();

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

        return new CategorySummaryListResponse(period.month(), period.year(), current.month(), current.year(), rows);
    }

    /**
     * Both or neither; month 1–12; year from 2000 (the earliest budgets accept) to the
     * current year; never a month after the current one, so this stays a history view.
     */
    private static void validatePeriod(Integer month, Integer year, ReportingPeriod current) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (month == null && year == null) {
            return;
        }
        if (month == null) {
            errors.put("month", PAIRED_MESSAGE);
        } else if (month < 1 || month > 12) {
            errors.put("month", "Month must be between 1 and 12");
        }
        if (year == null) {
            errors.put("year", PAIRED_MESSAGE);
        } else if (year < EARLIEST_YEAR) {
            errors.put("year", "Year must be " + EARLIEST_YEAR + " or later");
        } else if (year > current.year()) {
            errors.put("year", "Year must be " + current.year() + " or earlier");
        }
        if (errors.isEmpty() && year == current.year() && month > current.month()) {
            errors.put("month", "Choose " + monthLabel(current) + " or an earlier month");
        }
        if (!errors.isEmpty()) {
            throw new CategoryValidationException(errors);
        }
    }

    private static String monthLabel(ReportingPeriod period) {
        return Month.of(period.month()).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + period.year();
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
