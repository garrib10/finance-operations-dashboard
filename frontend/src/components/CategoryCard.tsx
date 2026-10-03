import { useId } from "react";
import type { DateFormatPreference } from "../types/account";
import type { CategorySummary } from "../types/category";
import { clampProgressPercentage, formatBudgetStatus } from "../utils/budgetStatus";
import { formatShare, spendingShare } from "../utils/categorySummary";
import { formatCurrency, formatDate } from "../utils/formatters";
import { CategoryIcon } from "./CategoryIcon";

interface CategoryCardProps {
  category: CategorySummary;
  /** The month's total expense spending, for the share figure. */
  monthTotal: number;
  /** For example "October" (the server's reporting month). */
  monthName: string;
  dateFormat: DateFormatPreference;
}

function plural(count: number, word: string): string {
  return `${count} ${word}${count === 1 ? "" : "s"}`;
}

/**
 * One category's month at a glance. A budget that exists is always shown; "No budget" is
 * offered only for categories that take budgets (budgetEnabled), never for the others.
 */
export function CategoryCard({ category, monthTotal, monthName, dateFormat }: CategoryCardProps) {
  const headingId = useId();
  const budget = category.currentMonthBudget;
  const unused = category.transactionCount === 0 && category.budgetCount === 0;

  return (
    <article className="category-card" aria-labelledby={headingId}>
      <div className="category-card__header">
        <span className="category-card__icon">
          <CategoryIcon iconKey={category.iconKey} className="category-card__icon-svg" />
        </span>
        <h3 id={headingId} className="category-card__name">{category.name}</h3>
        <span className="category-badge">{category.builtIn ? "Built-in" : "Custom"}</span>
      </div>

      <dl className="category-card__figures">
        <div>
          <dt>Spent in {monthName}</dt>
          <dd>{formatCurrency(category.currentMonthSpent)}</dd>
        </div>
        {/* A share only means something for a category that has spending. */}
        {monthTotal > 0 && category.currentMonthSpent > 0 && (
          <div>
            <dt>Share of spending</dt>
            <dd>{formatShare(spendingShare(category.currentMonthSpent, monthTotal), category.currentMonthSpent)}</dd>
          </div>
        )}
      </dl>

      {budget ? (
        <div className="category-card__budget">
          <div className="category-card__budget-row">
            <span>
              {formatCurrency(budget.amountSpent)} of {formatCurrency(budget.monthlyLimit)}
            </span>
            <span className={`budget-status budget-status--${budget.status.toLowerCase()}`}>
              {formatBudgetStatus(budget.status)}
            </span>
          </div>
          <div
            className="budget-progress"
            role="progressbar"
            aria-label={`${category.name} budget used`}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={clampProgressPercentage(budget.percentageUsed)}
            aria-valuetext={`${budget.percentageUsed.toFixed(1)}% used`}
          >
            <div
              className="budget-progress__bar"
              style={{ width: `${clampProgressPercentage(budget.percentageUsed)}%` }}
            />
          </div>
          <p className="category-card__note">
            {budget.amountRemaining >= 0
              ? `${formatCurrency(budget.amountRemaining)} left`
              : `${formatCurrency(-budget.amountRemaining)} over budget`}
          </p>
        </div>
      ) : (
        category.budgetEnabled && (
          <p className="category-card__note">No budget for {monthName}</p>
        )
      )}

      <p className="category-card__usage">
        {unused ? (
          "Not used yet"
        ) : (
          <>
            {plural(category.transactionCount, "transaction")} · {plural(category.budgetCount, "budget")}
            {category.lastTransactionDate && (
              <>
                {" · "}
                <span className="category-card__last-used">
                  Last used {formatDate(category.lastTransactionDate, dateFormat)}
                </span>
              </>
            )}
          </>
        )}
      </p>
    </article>
  );
}
