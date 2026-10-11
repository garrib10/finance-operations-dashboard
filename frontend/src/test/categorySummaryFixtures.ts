import type { CategorySummary, CategorySummaryList } from "../types/category";

/** A summary row with no usage; override what each test needs. */
export function summaryRow(overrides: Partial<CategorySummary> & Pick<CategorySummary, "id" | "name">): CategorySummary {
  return {
    iconKey: "tag",
    builtIn: false,
    budgetEnabled: true,
    transactionCount: 0,
    currentMonthTransactionCount: 0,
    budgetCount: 0,
    lastTransactionDate: null,
    currentMonthSpent: 0,
    allTimeSpent: 0,
    currentMonthBudget: null,
    canDelete: true,
    ...overrides,
  };
}

export const groceriesRow = summaryRow({
  id: 1, name: "Groceries", iconKey: "shopping-cart", builtIn: true, canDelete: false,
  transactionCount: 6, currentMonthTransactionCount: 4, budgetCount: 2, lastTransactionDate: "2026-10-12",
  currentMonthSpent: 300, allTimeSpent: 900,
  currentMonthBudget: {
    budgetId: 10, monthlyLimit: 400, amountSpent: 300, amountRemaining: 100, percentageUsed: 75, status: "WARNING",
  },
});

export const petCareRow = summaryRow({
  id: 7, name: "Pet Care", iconKey: "paw-print", canDelete: false,
  transactionCount: 3, currentMonthTransactionCount: 1, budgetCount: 1, lastTransactionDate: "2026-10-02",
  currentMonthSpent: 100, allTimeSpent: 140,
  currentMonthBudget: {
    budgetId: 11, monthlyLimit: 80, amountSpent: 100, amountRemaining: -20, percentageUsed: 125, status: "OVER_BUDGET",
  },
});

export const salaryRow = summaryRow({
  id: 3, name: "Income", iconKey: "circle-dollar-sign", builtIn: true, budgetEnabled: false, canDelete: false,
  transactionCount: 1, currentMonthTransactionCount: 1, lastTransactionDate: "2026-10-01",
});

export const unusedRow = summaryRow({ id: 9, name: "Hobbies", iconKey: "gamepad-2" });

/** A summary response; the server's current month defaults to the month described. */
export function summaryList(
  categories: CategorySummary[],
  month = 10,
  year = 2026,
  serverCurrentMonth = month,
  serverCurrentYear = year,
): CategorySummaryList {
  return { month, year, serverCurrentMonth, serverCurrentYear, categories };
}
