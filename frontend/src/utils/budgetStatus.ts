import type { BudgetStatus } from "../types/budget";

/** Display label for a budget status; shared by the Dashboard, Budgets, and Categories pages. */
export function formatBudgetStatus(status: BudgetStatus): string {
  switch (status) {
    case "ON_TRACK":
      return "On Track";
    case "CAUTION":
      return "Caution";
    case "WARNING":
      return "Warning";
    case "OVER_BUDGET":
      return "Over Budget";
    default:
      // A status added by a newer API still reads as its raw value rather than blank.
      return status;
  }
}

/** Progress-bar width and aria-valuenow: 0–100 even when a budget is overspent. */
export function clampProgressPercentage(percentage: number): number {
  return Math.min(Math.max(percentage, 0), 100);
}
