import type { CategorySummary } from "../types/category";
import {
  monthSpendingTotal,
  overBudgetCount,
  topCategory,
  withSpendingCount,
} from "../utils/categorySummary";
import { formatCurrency } from "../utils/formatters";
import { CategoryIcon } from "./CategoryIcon";

interface CategorySummaryStripProps {
  rows: CategorySummary[];
  /** For example "October 2026" (the server's reporting month). */
  monthLabel: string;
}

/** Headline figures, all derived from the summary rows so they always agree with the cards. */
export function CategorySummaryStrip({ rows, monthLabel }: CategorySummaryStripProps) {
  const custom = rows.filter((row) => !row.builtIn).length;
  const top = topCategory(rows);
  const budgets = rows.filter((row) => row.currentMonthBudget !== null).length;

  return (
    <dl className="category-summary-strip">
      <div className="category-stat">
        <dt>Categories</dt>
        <dd className="category-stat__value">{rows.length}</dd>
        <dd className="category-stat__detail">
          {custom} custom · {rows.length - custom} built-in
        </dd>
      </div>

      <div className="category-stat">
        <dt>Top this month</dt>
        {top ? (
          <>
            <dd className="category-stat__value category-stat__value--name">
              <CategoryIcon iconKey={top.iconKey} />
              <span>{top.name}</span>
            </dd>
            <dd className="category-stat__detail">
              {formatCurrency(top.currentMonthSpent)} of {formatCurrency(monthSpendingTotal(rows))}
            </dd>
          </>
        ) : (
          <dd className="category-stat__detail">No spending yet in {monthLabel}</dd>
        )}
      </div>

      <div className="category-stat">
        <dt>Over budget</dt>
        <dd className="category-stat__value">{overBudgetCount(rows)}</dd>
        <dd className="category-stat__detail">
          of {budgets} {budgets === 1 ? "budget" : "budgets"} this month
        </dd>
      </div>

      <div className="category-stat">
        <dt>With spending</dt>
        <dd className="category-stat__value">{withSpendingCount(rows)}</dd>
        <dd className="category-stat__detail">categories in {monthLabel}</dd>
      </div>
    </dl>
  );
}
