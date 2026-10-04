import type { CategorySummary } from "../types/category";

/**
 * Tie-break for any ordering: name (locale-aware, ignoring case but not accents), then ID,
 * so results never shuffle.
 */
export function compareByName(a: CategorySummary, b: CategorySummary): number {
  return a.name.localeCompare(b.name, undefined, { sensitivity: "accent" }) || a.id - b.id;
}

/** A spending amount that can be added up: finite and positive, anything else counts as 0. */
export function validSpend(value: number): number {
  return Number.isFinite(value) && value > 0 ? value : 0;
}

/** All of the month's expense spending; every expense belongs to exactly one category. */
export function monthSpendingTotal(rows: CategorySummary[]): number {
  return rows.reduce((total, row) => total + validSpend(row.currentMonthSpent), 0);
}

/** Percentage of the month's spending, to one decimal place; 0 when nothing was spent. */
export function spendingShare(spent: number, total: number): number {
  return validSpend(total) > 0 ? Math.round((validSpend(spent) / total) * 1000) / 10 : 0;
}

/** A bar's width in percent: the unrounded share, clamped to 0–100. */
export function shareBarWidth(spent: number, total: number): number {
  if (validSpend(total) === 0) return 0;
  return Math.min(100, Math.max(0, (validSpend(spent) / total) * 100));
}

export interface SpendingDistributionRow {
  category: CategorySummary;
  spent: number;
  /** Rounded to one decimal place, as displayed. */
  share: number;
  barWidth: number;
}

export interface SpendingDistribution {
  rows: SpendingDistributionRow[];
  total: number;
  /** True when the displayed (rounded) shares do not add up to exactly 100%. */
  rounded: boolean;
}

/**
 * The month's spending by category: only categories with spending, largest first (ties
 * by name, then ID). Always built from every category, never from a filtered view.
 */
export function spendingDistribution(categories: CategorySummary[]): SpendingDistribution {
  const total = monthSpendingTotal(categories);
  const rows = categories
    .filter((category) => validSpend(category.currentMonthSpent) > 0)
    .sort((a, b) => validSpend(b.currentMonthSpent) - validSpend(a.currentMonthSpent) || compareByName(a, b))
    .map((category) => ({
      category,
      spent: validSpend(category.currentMonthSpent),
      share: spendingShare(category.currentMonthSpent, total),
      barWidth: shareBarWidth(category.currentMonthSpent, total),
    }));
  // Compare in tenths of a percent so floating-point sums like 99.99999 are not "rounded".
  const shownTotal = Math.round(rows.reduce((sum, row) => sum + row.share, 0) * 10);
  return { rows, total, rounded: rows.length > 0 && shownTotal !== 1000 };
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
