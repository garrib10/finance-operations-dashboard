import type { CategoryLoadStatus } from "../context/CategoryContext";
import type { CategoryResponse } from "../types/category";
import { MONTH_PARAM, YEAR_PARAM, type ReportingPeriod } from "./reportingPeriod";

export const CATEGORY_PARAM = "category";
/** On the Transactions page: start a new transaction with this category chosen. */
export const ADD_TRANSACTION_PARAM = "addCategory";

/**
 * The state of a ?category= link: no parameter, still waiting for the user's categories,
 * invalid (malformed, unknown, deleted, or another user's), or a category the user owns.
 */
export type CategoryLink =
  | { kind: "none" }
  | { kind: "pending" }
  | { kind: "invalid" }
  | { kind: "valid"; id: string; category: CategoryResponse };

/**
 * Only a positive whole number that is in the signed-in user's own category list is
 * valid, so an invalid ID is never sent to the API and another user's ID is
 * indistinguishable from a missing one. If the list cannot load, the link is ignored.
 */
export function resolveCategoryLink(
  value: string | null,
  status: CategoryLoadStatus,
  categories: CategoryResponse[],
): CategoryLink {
  if (value === null) return { kind: "none" };
  if (status === "idle" || status === "loading") return { kind: "pending" };
  if (!/^[1-9]\d*$/.test(value)) return { kind: "invalid" };

  const category = categories.find((item) => String(item.id) === value);
  return category ? { kind: "valid", id: value, category } : { kind: "invalid" };
}

/** A stable key for effects: changes only when the link's meaning changes. */
export function categoryLinkKey(link: CategoryLink): string {
  return link.kind === "valid" ? `valid:${link.id}` : link.kind;
}

/**
 * The Budgets page for one category in one reporting month, for example
 * /budgets?category=12&month=9&year=2026. The month is always written out (the Categories
 * page's server month, never the browser's clock), so Budgets opens the month that was on
 * screen; Budgets then edits that month's budget or starts a new one.
 */
export function budgetShortcutPath(categoryId: number, period: ReportingPeriod): string {
  const params = new URLSearchParams({
    [CATEGORY_PARAM]: String(categoryId),
    [MONTH_PARAM]: String(period.month),
    [YEAR_PARAM]: String(period.year),
  });
  return `/budgets?${params}`;
}
