import { describe, expect, it } from "vitest";
import { groceriesRow, petCareRow, salaryRow, summaryRow, unusedRow } from "../test/categorySummaryFixtures";
import type { CategorySummary } from "../types/category";
import {
  DEFAULT_DISCOVERY,
  discoverCategories,
  isDefaultDiscovery,
  matchesFilter,
  matchesSearch,
  normalizeSearch,
  type CategoryDiscovery,
} from "./categoryDiscovery";

const rows = [groceriesRow, salaryRow, petCareRow, unusedRow];
const names = (list: CategorySummary[]) => list.map((row) => row.name);
const discover = (overrides: Partial<CategoryDiscovery>, source: CategorySummary[] = rows) =>
  discoverCategories(source, { ...DEFAULT_DISCOVERY, ...overrides });

describe("category search", () => {
  it("normalizes by trimming, NFC, and lower case", () => {
    expect(normalizeSearch("  Pet CARE ")).toBe("pet care");
    expect(normalizeSearch("Café")).toBe("café");
  });

  it.each([
    ["case-insensitive", "GROCERIES", true],
    ["a partial name", "roc", true],
    ["trimmed input", "  pet  ", true],
    ["no match", "travel", false],
  ])("matches %s", (_kind, query, expected) => {
    const target = query.includes("pet") ? petCareRow : groceriesRow;
    expect(matchesSearch(target, query)).toBe(expected);
  });

  it("matches Unicode-equivalent spellings both ways", () => {
    const composed = summaryRow({ id: 40, name: "Café visits" });
    const decomposed = summaryRow({ id: 41, name: "Café runs" });

    expect(matchesSearch(composed, "café")).toBe(true);
    expect(matchesSearch(decomposed, "CAFÉ")).toBe(true);
    expect(matchesSearch(composed, "cafe")).toBe(false); // Accents still matter.
  });

  it("treats an empty or whitespace-only query as no search", () => {
    expect(names(discover({ query: "" }))).toHaveLength(4);
    expect(names(discover({ query: "   " }))).toHaveLength(4);
  });

  it("returns nothing when nothing matches", () => {
    expect(discover({ query: "zzz" })).toEqual([]);
  });
});

describe("category filters", () => {
  it.each([
    ["all", ["Groceries", "Hobbies", "Income", "Pet Care"]],
    ["custom", ["Hobbies", "Pet Care"]],
    ["builtIn", ["Groceries", "Income"]],
    ["unused", ["Hobbies"]],
    ["noBudget", ["Hobbies", "Income"]],
  ] as const)("%s", (filter, expected) => {
    expect(names(discover({ filter }))).toEqual(expected);
  });

  it("calls a category unused only when both all-time counters are zero", () => {
    expect(matchesFilter(summaryRow({ id: 1, name: "A", transactionCount: 0, budgetCount: 1 }), "unused")).toBe(false);
    expect(matchesFilter(summaryRow({ id: 2, name: "B", transactionCount: 1, budgetCount: 0 }), "unused")).toBe(false);
    // A built-in can be unused, even though it can never be deleted.
    expect(matchesFilter(summaryRow({ id: 3, name: "C", builtIn: true, canDelete: false }), "unused")).toBe(true);
  });

  it("counts any category without a budget this month as no budget, whether or not it takes budgets", () => {
    expect(matchesFilter({ ...salaryRow, budgetEnabled: false }, "noBudget")).toBe(true);
    expect(matchesFilter(groceriesRow, "noBudget")).toBe(false);
  });

  it("combines search and filter", () => {
    expect(names(discover({ query: "e", filter: "custom" }))).toEqual(["Hobbies", "Pet Care"]);
    expect(names(discover({ query: "groc", filter: "custom" }))).toEqual([]);
  });
});

describe("category sorting", () => {
  const tied = [
    summaryRow({ id: 8, name: "beta", transactionCount: 2, currentMonthSpent: 50 }),
    summaryRow({ id: 5, name: "Alpha", transactionCount: 2, currentMonthSpent: 50 }),
    summaryRow({ id: 2, name: "alpha", transactionCount: 2, currentMonthSpent: 50 }),
    summaryRow({ id: 4, name: "Gamma", transactionCount: 2, currentMonthSpent: 80 }),
    summaryRow({ id: 6, name: "Delta", transactionCount: 9, currentMonthSpent: 0 }),
  ];
  const ids = (list: CategorySummary[]) => list.map((row) => row.id);

  it("sorts by name, ignoring case, then by ID", () => {
    expect(ids(discover({ sort: "name" }, tied))).toEqual([2, 5, 8, 6, 4]);
  });

  it("sorts by this month's spending, then name, then ID", () => {
    expect(ids(discover({ sort: "monthSpending" }, tied))).toEqual([4, 2, 5, 8, 6]);
  });

  it("sorts by most used, then this month's spending, then name, then ID", () => {
    expect(ids(discover({ sort: "mostUsed" }, tied))).toEqual([6, 4, 2, 5, 8]);
  });

  it("never mutates the source array", () => {
    const source = [...tied];
    const before = ids(source);
    discover({ sort: "monthSpending", filter: "custom", query: "a" }, source);

    expect(ids(source)).toEqual(before);
  });
});

describe("discovery state", () => {
  it("keeps listed IDs visible even when they no longer match", () => {
    expect(names(discoverCategories(rows, { ...DEFAULT_DISCOVERY, query: "groc" }, [petCareRow.id, null])))
      .toEqual(["Groceries", "Pet Care"]);
  });

  it("knows when the controls are at their defaults", () => {
    expect(isDefaultDiscovery(DEFAULT_DISCOVERY)).toBe(true);
    expect(isDefaultDiscovery({ ...DEFAULT_DISCOVERY, query: "  " })).toBe(true);
    expect(isDefaultDiscovery({ ...DEFAULT_DISCOVERY, query: "x" })).toBe(false);
    expect(isDefaultDiscovery({ ...DEFAULT_DISCOVERY, filter: "unused" })).toBe(false);
    expect(isDefaultDiscovery({ ...DEFAULT_DISCOVERY, sort: "mostUsed" })).toBe(false);
  });
});
