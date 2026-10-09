import { ArrowLeftRight, PiggyBank, Plus } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import type { DateFormatPreference } from "../types/account";
import type { CategorySummary } from "../types/category";
import { clampProgressPercentage, formatBudgetStatus } from "../utils/budgetStatus";
import { formatReportingMonth, formatShare, needsCurrentMonthBudget, spendingShare } from "../utils/categorySummary";
import { ADD_TRANSACTION_PARAM, CATEGORY_PARAM, budgetShortcutPath } from "../utils/categoryDeepLink";
import { categoryCardIds, deleteBlockedReason, monthActivity } from "../utils/categoryUsage";
import { formatCurrency, formatDate } from "../utils/formatters";
import { monthName as nameOfMonth, type ReportingPeriod } from "../utils/reportingPeriod";
import { CategoryActionsMenu } from "./CategoryActionsMenu";
import { CategoryIcon } from "./CategoryIcon";
import { InlineNotice } from "./InlineNotice";

interface CategoryCardProps {
  category: CategorySummary;
  /** The month's total expense spending, for the share figure. */
  monthTotal: number;
  /** The month the figures describe (the summary response's month, never the browser's). */
  period: ReportingPeriod;
  dateFormat: DateFormatPreference;
  /** Opens the edit form (custom categories only). */
  onEdit?: () => void;
  /** Opens the delete confirmation (custom categories that can be deleted). */
  onDelete?: () => void;
  /** Shown in place of the management actions while editing or confirming a delete. */
  workflow?: ReactNode;
  /** Whether this card's "More actions" disclosure is open (custom categories only). */
  actionsOpen?: boolean;
  onActionsOpenChange?: (open: boolean) => void;
}



/**
 * One category's month at a glance. A budget that exists is always shown; "No budget" is
 * offered only for categories that take budgets (budgetEnabled), never for the others.
 */
export function CategoryCard({
  category,
  monthTotal,
  period,
  dateFormat,
  onEdit,
  onDelete,
  workflow,
  actionsOpen = false,
  onActionsOpenChange,
}: CategoryCardProps) {
  const ids = categoryCardIds(category.id);
  const headingId = ids.heading;
  const budget = category.currentMonthBudget;
  // Spending but no budget this month: the warning carries the card's only Set budget link.
  const needsBudget = needsCurrentMonthBudget(category);
  // The card's lines name just the month (the page names the year); the budget links,
  // which leave the page, name both and open Budgets on that same month.
  const monthName = nameOfMonth(period.month);
  const budgetPath = budgetShortcutPath(category.id, period);
  const budgetLabel = (verb: string) => `${verb} budget for ${category.name} for ${formatReportingMonth(period.month, period.year)}`;

  return (
    <article className="category-card" aria-labelledby={headingId}>
      <div className="category-card__header">
        <span className="category-card__icon">
          <CategoryIcon iconKey={category.iconKey} className="category-card__icon-svg" />
        </span>
        {/* Name and badge wrap together, so a narrow card moves the badge under the name
            instead of squeezing the name to a letter per line. */}
        <div className="category-card__title">
          <h3 id={headingId} className="category-card__name" tabIndex={-1}>{category.name}</h3>
          <span className="category-badge">{category.builtIn ? "Built-in" : "Custom"}</span>
        </div>
        {/* Built-ins cannot be changed, so they get no actions; hidden during a workflow,
            which shows its own controls in the card. */}
        {!category.builtIn && !workflow && (
          <CategoryActionsMenu
            categoryId={category.id}
            categoryName={category.name}
            open={actionsOpen}
            onOpenChange={(open) => onActionsOpenChange?.(open)}
            onEdit={() => onEdit?.()}
            onDelete={() => onDelete?.()}
            deleteBlockedReason={category.canDelete ? null
              : `${deleteBlockedReason(category.transactionCount, category.budgetCount)} Change or remove those first to delete this category.`}
          />
        )}
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
        needsBudget ? (
          // Static advice, not an announcement: several cards may show it at once.
          <div className="category-card__warning">
            <InlineNotice variant="warning" live={false}>
              {formatCurrency(category.currentMonthSpent)} spent in {monthName} with no budget.{" "}
              <Link
                className="category-card__warning-link"
                to={budgetPath}
                aria-label={budgetLabel("Set")}
              >
                Set budget
              </Link>
            </InlineNotice>
          </div>
        ) : (
          category.budgetEnabled && (
            <p className="category-card__note">No budget for {monthName}</p>
          )
        )
      )}

      {/* The month's activity; the all-time counts only decide whether Delete is available. */}
      <p className="category-card__usage">
        {category.lastTransactionDate === null ? (
          "Not used yet"
        ) : (
          <>
            {monthActivity(category.currentMonthTransactionCount, monthName)}
            {" · "}
            <span className="category-card__last-used">
              Last used {formatDate(category.lastTransactionDate, dateFormat)}
            </span>
          </>
        )}
      </p>

      <div className="category-card__links">
        {/* Nothing to view yet, so offer to record the first transaction instead. */}
        {category.transactionCount === 0 ? (
          <Link
            className="category-card__link category-card__link--primary"
            to={`/transactions?${ADD_TRANSACTION_PARAM}=${category.id}`}
            aria-label={`Add a transaction for ${category.name}`}
          >
            <Plus aria-hidden="true" focusable="false" size={15} />
            Add transaction
          </Link>
        ) : (
          <Link
            className="category-card__link category-card__link--primary"
            to={`/transactions?${CATEGORY_PARAM}=${category.id}`}
            aria-label={`View transactions for ${category.name}`}
          >
            <ArrowLeftRight aria-hidden="true" focusable="false" size={15} />
            View transactions
          </Link>
        )}
        {category.budgetEnabled && !needsBudget && (
          <Link
            className="category-card__link"
            to={budgetPath}
            aria-label={budgetLabel(budget ? "Edit" : "Set")}
          >
            <PiggyBank aria-hidden="true" focusable="false" size={15} />
            {budget ? "Edit budget" : "Set budget"}
          </Link>
        )}
      </div>

      {workflow}
    </article>
  );
}
