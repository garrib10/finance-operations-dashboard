import type { CategorySummary } from "../types/category";
import { formatShare, spendingDistribution } from "../utils/categorySummary";
import { formatCurrency } from "../utils/formatters";
import { CategoryIcon } from "./CategoryIcon";

interface CategorySpendingTableProps {
  /** Every category in the summary: the distribution never follows the card filters. */
  categories: CategorySummary[];
  /** For example "October 2026" (the summary response's month). */
  monthLabel: string;
  /** An earlier month than the server's current one: say so instead of "this month". */
  historical?: boolean;
}

const HEADING_ID = "category-spending-heading";

/**
 * Where the month's spending went, as a table: every value is text, and each row's bar
 * only repeats its share visually (so the bars and icons are hidden from assistive tech).
 */
export function CategorySpendingTable({ categories, monthLabel, historical = false }: CategorySpendingTableProps) {
  const distribution = spendingDistribution(categories);

  return (
    <section className="category-spending" aria-labelledby={HEADING_ID}>
      <h2 id={HEADING_ID}>Spending in {monthLabel}</h2>

      {distribution.rows.length === 0 ? (
        <p className="empty-state">No spending recorded for {monthLabel}.</p>
      ) : (
        <>
          <p className="category-spending__intro">
            Share of {formatCurrency(distribution.total)} in expenses. Categories with no
            spending {historical ? `in ${monthLabel}` : "this month"} are not listed.
          </p>

          <table className="category-spending__table" aria-labelledby={HEADING_ID}>
            <thead>
              <tr>
                <th scope="col">Category</th>
                <th scope="col">Spent</th>
                <th scope="col">Share</th>
              </tr>
            </thead>
            <tbody>
              {distribution.rows.map(({ category, spent, share, barWidth }) => (
                <tr key={category.id}>
                  <th scope="row">
                    <span className="category-spending__name">
                      <CategoryIcon iconKey={category.iconKey} />
                      <span>{category.name}</span>
                    </span>
                  </th>
                  <td>{formatCurrency(spent)}</td>
                  <td>
                    <span className="category-spending__share">{formatShare(share, spent)}</span>
                    <span className="category-spending__bar" aria-hidden="true">
                      <span style={{ width: `${barWidth}%` }} />
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {distribution.rounded && (
            <p className="category-spending__note">
              Percentages are rounded, so they may not add up to exactly 100%.
            </p>
          )}
        </>
      )}
    </section>
  );
}
