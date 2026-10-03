import { describe, expect, it } from "vitest";
import { groceriesRow, petCareRow, salaryRow, summaryRow, unusedRow } from "../test/categorySummaryFixtures";
import {
  compareByName,
  formatReportingMonth,
  formatShare,
  monthSpendingTotal,
  overBudgetCount,
  spendingShare,
  topCategory,
  withSpendingCount,
} from "./categorySummary";

describe("category summary helpers", () => {
  const rows = [groceriesRow, petCareRow, salaryRow, unusedRow];

  it("orders by name ignoring case, then by ID", () => {
    const sorted = [
      summaryRow({ id: 5, name: "beta" }),
      summaryRow({ id: 2, name: "Alpha" }),
      summaryRow({ id: 1, name: "alpha" }),
    ].sort(compareByName);

    expect(sorted.map((row) => row.id)).toEqual([1, 2, 5]);
  });

  it("totals the month's spending from every row", () => {
    expect(monthSpendingTotal(rows)).toBe(400);
    expect(monthSpendingTotal([])).toBe(0);
  });

  it("calculates shares to one decimal place and never divides by zero", () => {
    expect(spendingShare(300, 400)).toBe(75);
    expect(spendingShare(1, 3)).toBe(33.3);
    expect(spendingShare(0, 0)).toBe(0);
  });

  it("shows a tiny non-zero share as under 0.1% rather than 0%", () => {
    expect(formatShare(spendingShare(0.01, 10000), 0.01)).toBe("<0.1%");
    expect(formatShare(0, 0)).toBe("0.0%");
    expect(formatShare(33.3, 1)).toBe("33.3%");
  });

  it("picks the top category by spending, breaking ties by name then ID", () => {
    expect(topCategory(rows)).toBe(groceriesRow);

    const tieA = summaryRow({ id: 4, name: "Zoo", currentMonthSpent: 50 });
    const tieB = summaryRow({ id: 8, name: "art", currentMonthSpent: 50 });
    expect(topCategory([tieA, tieB])).toBe(tieB);
  });

  it("has no top category when nothing was spent", () => {
    expect(topCategory([salaryRow, unusedRow])).toBeNull();
  });

  it("counts categories over budget and with spending (income is not spending)", () => {
    expect(overBudgetCount(rows)).toBe(1);
    expect(withSpendingCount(rows)).toBe(2);
  });

  it("formats the server's reporting month", () => {
    expect(formatReportingMonth(10, 2026)).toBe("October 2026");
    expect(formatReportingMonth(1, 2027)).toBe("January 2027");
  });
});
