import type { CategorySummary } from "../types/category";
import { compareByName, validSpend } from "./categorySummary";

export type CategoryFilter = "all" | "custom" | "builtIn" | "unused" | "noBudget";
export type CategorySort = "name" | "monthSpending" | "mostUsed";

export interface CategoryDiscovery {
  query: string;
  filter: CategoryFilter;
  sort: CategorySort;
}

export const DEFAULT_DISCOVERY: CategoryDiscovery = { query: "", filter: "all", sort: "name" };

export const FILTER_OPTIONS: ReadonlyArray<{ value: CategoryFilter; label: string }> = [
  { value: "all", label: "All categories" },
  { value: "custom", label: "Custom" },
  { value: "builtIn", label: "Built-in" },
  { value: "unused", label: "Unused" },
  { value: "noBudget", label: "No budget this month" },
];

export const SORT_OPTIONS: ReadonlyArray<{ value: CategorySort; label: string }> = [
  { value: "name", label: "Name" },
  { value: "monthSpending", label: "This month’s spending" },
  { value: "mostUsed", label: "Most used" },
];

/** Trimmed, NFC-normalized, and lower-cased, so "Café" typed either way finds "café". */
export function normalizeSearch(value: string): string {
  return value.trim().normalize("NFC").toLocaleLowerCase();
}

/** A substring match on the name; an empty or whitespace-only query matches everything. */
export function matchesSearch(category: CategorySummary, query: string): boolean {
  const needle = normalizeSearch(query);
  return needle === "" || normalizeSearch(category.name).includes(needle);
}

export function matchesFilter(category: CategorySummary, filter: CategoryFilter): boolean {
  switch (filter) {
    case "custom":
      return !category.builtIn;
    case "builtIn":
      return category.builtIn;
    case "unused":
      // Never used: both all-time counters are zero (built-ins included, though they
      // still cannot be deleted).
      return category.transactionCount === 0 && category.budgetCount === 0;
    case "noBudget":
      // Every category without a budget this month, whether or not it takes budgets.
      return category.currentMonthBudget === null;
    default:
      return true;
  }
}

const COMPARATORS: Record<CategorySort, (a: CategorySummary, b: CategorySummary) => number> = {
  name: compareByName,
  monthSpending: (a, b) => validSpend(b.currentMonthSpent) - validSpend(a.currentMonthSpent) || compareByName(a, b),
  mostUsed: (a, b) => b.transactionCount - a.transactionCount
    || validSpend(b.currentMonthSpent) - validSpend(a.currentMonthSpent)
    || compareByName(a, b),
};

export function isDefaultDiscovery(discovery: CategoryDiscovery): boolean {
  return normalizeSearch(discovery.query) === ""
    && discovery.filter === DEFAULT_DISCOVERY.filter
    && discovery.sort === DEFAULT_DISCOVERY.sort;
}

/**
 * Search, then filter, then sort, returning a new array (the source is never mutated).
 * IDs in `keepVisible` stay in the result even if they no longer match, so a card being
 * edited (or just changed) never disappears from under the user.
 */
export function discoverCategories(
  categories: readonly CategorySummary[],
  discovery: CategoryDiscovery,
  keepVisible: ReadonlyArray<number | null> = [],
): CategorySummary[] {
  return categories
    .filter((category) => keepVisible.includes(category.id)
      || (matchesSearch(category, discovery.query) && matchesFilter(category, discovery.filter)))
    .sort(COMPARATORS[discovery.sort]);
}
