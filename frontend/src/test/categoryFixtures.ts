import type { CategoryResponse } from "../types/category";

const timestamp = "2026-09-01T10:00:00";

export function category(overrides: Partial<CategoryResponse> & Pick<CategoryResponse, "id" | "name">): CategoryResponse {
  return {
    budgetEnabled: true,
    builtIn: false,
    iconKey: "tag",
    createdAt: timestamp,
    updatedAt: timestamp,
    ...overrides,
  };
}

export const groceries = category({ id: 1, name: "Groceries", builtIn: true, iconKey: "shopping-cart" });
export const housing = category({ id: 2, name: "Housing", builtIn: true, iconKey: "house" });
/** The persisted built-in "Other": an ordinary option, not the create action. */
export const other = category({ id: 13, name: "Other", builtIn: true, iconKey: "tag" });
export const petCare = category({ id: 40, name: "Pet Care", iconKey: "paw-print" });
/** A stored key outside the current catalog. */
export const legacy = category({ id: 41, name: "Legacy", iconKey: "retired-icon" });
export const longUnicode = category({
  id: 42,
  name: `Ünïcödé Café ☕ ${"long name ".repeat(9).trim()}`,
  iconKey: "coffee",
});

export const sampleCategories: CategoryResponse[] = [groceries, housing, other, petCare];
