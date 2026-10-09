import type { CategorySummary } from "../types/category";
import {
  monthSpendingTotal,
  overBudgetCount,
  topCategory,
  needsBudgetCount,
} from "../utils/categorySummary";
import { formatCurrency } from "../utils/formatters";
import { CategoryIcon } from "./CategoryIcon";

interface CategorySummaryStripProps {
  rows: CategorySummary[];
  /** For example "October 2026" (the summary response's month). */
  monthLabel: string;
  /** An earlier month than the server's current one: say so instead of "this month". */
  historical?: boolean;
}

/** Headline figures, all derived from the summary rows so they always agree with the cards. */
export function CategorySummaryStrip({ rows, monthLabel, historical = false }: CategorySummaryStripProps) {
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
        <dt>{historical ? `Top in ${monthLabel}` : "Top this month"}</dt>
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
          <dd className="category-stat__detail">
            {historical ? `No spending in ${monthLabel}` : `No spending yet in ${monthLabel}`}
          </dd>
        )}
      </div>

      <div className="category-stat">
        <dt>Over budget</dt>
        <dd className="category-stat__value">{overBudgetCount(rows)}</dd>
        <dd className="category-stat__detail">
          of {budgets} {budgets === 1 ? "budget" : "budgets"} {historical ? `in ${monthLabel}` : "this month"}
        </dd>
      </div>

      {/* Every category, never the searched or filtered cards. */}
      <div className="category-stat">
        <dt>No budget</dt>
        <dd className="category-stat__value">{needsBudgetCount(rows)}</dd>
        <dd className="category-stat__detail">categories spending in {monthLabel} without a budget</dd>
      </div>
    </dl>
  );
}
