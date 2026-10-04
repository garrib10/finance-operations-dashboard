import { describe, expect, it } from "vitest";
import { groceriesRow, petCareRow, salaryRow, summaryRow, unusedRow } from "../test/categorySummaryFixtures";
import {
  compareByName,
  formatReportingMonth,
  formatShare,
  isActiveThisMonth,
  partitionByActivity,
  needsBudgetCount,
  needsCurrentMonthBudget,
  monthSpendingTotal,
  overBudgetCount,
  shareBarWidth,
  spendingDistribution,
  spendingShare,
  validSpend,
  topCategory,
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

  it("counts categories over budget", () => {
    expect(overBudgetCount(rows)).toBe(1);
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

describe("needsCurrentMonthBudget", () => {
  const spending = (overrides: Partial<Parameters<typeof summaryRow>[0]> = {}) =>
    summaryRow({ id: 50, name: "Gifts", currentMonthSpent: 25, ...overrides });

  it("needs a budget for spending this month with no budget, in a category that takes budgets", () => {
    expect(needsCurrentMonthBudget(spending())).toBe(true);
  });

  it("still needs one when only earlier months had budgets", () => {
    expect(needsCurrentMonthBudget(spending({ budgetCount: 3 }))).toBe(true);
  });

  it.each([
    ["no spending this month", { currentMonthSpent: 0 }],
    ["a negative amount", { currentMonthSpent: -5 }],
    ["an invalid amount", { currentMonthSpent: Number.NaN }],
    ["a budget this month", { currentMonthBudget: groceriesRow.currentMonthBudget }],
    ["budgets switched off", { budgetEnabled: false }],
    ["income only this month", { currentMonthSpent: 0, currentMonthTransactionCount: 2 }],
    ["spending only in earlier months", { currentMonthSpent: 0, allTimeSpent: 500, transactionCount: 9 }],
  ])("does not for %s", (_case, overrides) => {
    expect(needsCurrentMonthBudget(spending(overrides))).toBe(false);
  });

  it("does not change the category it checks", () => {
    const row = spending();
    const before = structuredClone(row);
    needsCurrentMonthBudget(row);

    expect(row).toEqual(before);
  });

  it("counts every qualifying category, and zero for none", () => {
    const qualifying = [spending({ id: 51 }), spending({ id: 52, name: "Books" })];

    expect(needsBudgetCount([...qualifying, groceriesRow, salaryRow, unusedRow])).toBe(2);
    expect(needsBudgetCount([groceriesRow, salaryRow, unusedRow])).toBe(0);
    expect(needsBudgetCount([])).toBe(0);
  });
});

describe("active this month", () => {
  const row = (overrides: Partial<Parameters<typeof summaryRow>[0]>) => summaryRow({ id: 70, name: "Row", ...overrides });
  const budget = groceriesRow.currentMonthBudget;

  it.each([
    ["spending this month", { currentMonthSpent: 5 }],
    ["a budget this month", { currentMonthBudget: budget }],
    ["both", { currentMonthSpent: 5, currentMonthBudget: budget }],
    ["a built-in with spending", { builtIn: true, currentMonthSpent: 5 }],
  ])("is active with %s", (_case, overrides) => {
    expect(isActiveThisMonth(row(overrides))).toBe(true);
  });

  it.each([
    ["nothing this month", {}],
    ["a negative amount", { currentMonthSpent: -3 }],
    ["only this month's income", { currentMonthTransactionCount: 3 }],
    ["only earlier transactions", { transactionCount: 9, allTimeSpent: 400 }],
    ["only earlier budgets", { budgetCount: 4 }],
    ["a built-in with nothing this month", { builtIn: true }],
    ["budgets switched off and no spending", { budgetEnabled: false }],
  ])("is other with %s", (_case, overrides) => {
    expect(isActiveThisMonth(row(overrides))).toBe(false);
  });

  it("puts every category in exactly one section, in order, without changing the input", () => {
    const source = [groceriesRow, unusedRow, petCareRow, salaryRow];
    const before = [...source];
    const { activeCategories, otherCategories } = partitionByActivity(source);

    expect(activeCategories).toEqual([groceriesRow, petCareRow]);
    expect(otherCategories).toEqual([unusedRow, salaryRow]);
    expect(activeCategories[0]).toBe(groceriesRow); // Same objects, not copies.
    expect([...activeCategories, ...otherCategories]).toHaveLength(source.length);
    expect(new Set([...activeCategories, ...otherCategories])).toEqual(new Set(source));
    expect(source).toEqual(before);
  });
});
