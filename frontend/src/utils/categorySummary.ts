import type { CategorySummary } from "../types/category";

/** Tie-break for any ordering: name ignoring case, then ID, so results never shuffle. */
export function compareByName(a: CategorySummary, b: CategorySummary): number {
  return a.name.localeCompare(b.name, undefined, { sensitivity: "base" }) || a.id - b.id;
}

/** All of the month's expense spending; every expense belongs to exactly one category. */
export function monthSpendingTotal(rows: CategorySummary[]): number {
  return rows.reduce((total, row) => total + row.currentMonthSpent, 0);
}

/** Percentage of the month's spending, to one decimal place; 0 when nothing was spent. */
export function spendingShare(spent: number, total: number): number {
  return total > 0 ? Math.round((spent / total) * 1000) / 10 : 0;
}

/** Shows "<0.1%" rather than a misleading 0% for small non-zero shares. */
export function formatShare(share: number, spent: number): string {
  return share === 0 && spent > 0 ? "<0.1%" : `${share.toFixed(1)}%`;
}

/** Highest spending this month, ties by name then ID; null when nothing was spent. */
export function topCategory(rows: CategorySummary[]): CategorySummary | null {
  const spending = rows.filter((row) => row.currentMonthSpent > 0);
  if (spending.length === 0) return null;
  return [...spending].sort((a, b) => b.currentMonthSpent - a.currentMonthSpent || compareByName(a, b))[0];
}

export function overBudgetCount(rows: CategorySummary[]): number {
  return rows.filter((row) => row.currentMonthBudget?.status === "OVER_BUDGET").length;
}

/** Categories with expense spending this month (income does not count as spending). */
export function withSpendingCount(rows: CategorySummary[]): number {
  return rows.filter((row) => row.currentMonthSpent > 0).length;
}

export function formatReportingMonth(month: number, year: number): string {
  return new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric" })
    .format(new Date(year, month - 1, 1));
}
