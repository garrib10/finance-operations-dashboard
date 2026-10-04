import { describe, expect, it } from "vitest";
import { groceriesRow, petCareRow, salaryRow, summaryRow, unusedRow } from "../test/categorySummaryFixtures";
import {
  compareByName,
  formatReportingMonth,
  formatShare,
  monthSpendingTotal,
  overBudgetCount,
  shareBarWidth,
  spendingDistribution,
  spendingShare,
  validSpend,
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

  it("ignores invalid spending values instead of letting them break totals", () => {
    expect(validSpend(Number.NaN)).toBe(0);
    expect(validSpend(Number.POSITIVE_INFINITY)).toBe(0);
    expect(validSpend(-5)).toBe(0);
    expect(validSpend(12.5)).toBe(12.5);
    expect(monthSpendingTotal([summaryRow({ id: 1, name: "A", currentMonthSpent: Number.NaN }), groceriesRow])).toBe(300);
    expect(spendingShare(Number.NaN, 100)).toBe(0);
    expect(spendingShare(10, Number.NaN)).toBe(0);
  });

  it("clamps bar widths between 0% and 100% and never divides by zero", () => {
    expect(shareBarWidth(1, 3)).toBeCloseTo(33.333, 2);
    expect(shareBarWidth(500, 100)).toBe(100);
    expect(shareBarWidth(-10, 100)).toBe(0);
    expect(shareBarWidth(10, 0)).toBe(0);
  });
});

describe("spending distribution", () => {
  it("lists only categories with spending, largest first, ties by name then ID", () => {
    const distribution = spendingDistribution([
      summaryRow({ id: 4, name: "beta", currentMonthSpent: 25 }),
      summaryRow({ id: 9, name: "Zero", currentMonthSpent: 0 }),
      summaryRow({ id: 3, name: "Alpha", currentMonthSpent: 25 }),
      summaryRow({ id: 1, name: "Big", currentMonthSpent: 50 }),
      summaryRow({ id: 2, name: "alpha", currentMonthSpent: 25 }),
    ]);

    expect(distribution.rows.map((row) => row.category.id)).toEqual([1, 2, 3, 4]);
    expect(distribution.total).toBe(125);
    expect(distribution.rows.map((row) => row.share)).toEqual([40, 20, 20, 20]);
    expect(distribution.rows[0].barWidth).toBe(40);
    expect(distribution.rounded).toBe(false);
  });

  it("flags rounded shares that do not add up to 100%", () => {
    const thirds = spendingDistribution([1, 2, 3].map((id) => summaryRow({ id, name: `C${id}`, currentMonthSpent: 10 })));

    expect(thirds.rows.map((row) => row.share)).toEqual([33.3, 33.3, 33.3]);
    expect(thirds.rounded).toBe(true);
  });

  it("is empty, with nothing to round, when nothing was spent", () => {
    expect(spendingDistribution([salaryRow, unusedRow])).toEqual({ rows: [], total: 0, rounded: false });
  });

  it("does not reorder the categories it is given", () => {
    const source = [unusedRow, petCareRow, groceriesRow];
    spendingDistribution(source);

    expect(source.map((row) => row.id)).toEqual([9, 7, 1]);
  });
});
