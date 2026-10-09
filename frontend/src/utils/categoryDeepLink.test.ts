import { describe, expect, it } from "vitest";
import { groceries, petCare, sampleCategories } from "../test/categoryFixtures";
import { budgetShortcutPath, categoryLinkKey, resolveCategoryLink } from "./categoryDeepLink";

describe("resolveCategoryLink", () => {
  it("has no link without the parameter", () => {
    expect(resolveCategoryLink(null, "ready", sampleCategories)).toEqual({ kind: "none" });
  });

  it.each(["idle", "loading"] as const)("waits while the category list is %s", (status) => {
    expect(resolveCategoryLink("40", status, [])).toEqual({ kind: "pending" });
  });

  it.each([["custom", petCare], ["built-in", groceries]])("accepts an owned %s category", (_kind, category) => {
    expect(resolveCategoryLink(String(category.id), "ready", sampleCategories))
      .toEqual({ kind: "valid", id: String(category.id), category });
  });

  it.each(["", "abc", "1.5", "0", "-3", "007", "1e3", " 40", "999"])("rejects %j", (value) => {
    expect(resolveCategoryLink(value, "ready", sampleCategories)).toEqual({ kind: "invalid" });
  });

  it("rejects every link when the category list could not load", () => {
    expect(resolveCategoryLink("40", "error", [])).toEqual({ kind: "invalid" });
  });

  it("gives effects a key that changes only with the link's meaning", () => {
    expect(categoryLinkKey({ kind: "valid", id: "40", category: petCare })).toBe("valid:40");
    expect(categoryLinkKey({ kind: "invalid" })).toBe("invalid");
  });
});

describe("budgetShortcutPath", () => {
  it("names the category and the month once each, with no empty values", () => {
    const path = budgetShortcutPath(12, { month: 9, year: 2026 });
    const params = new URL(path, "http://localhost").searchParams;

    expect(path).toBe("/budgets?category=12&month=9&year=2026");
    expect([...params.keys()]).toEqual(["category", "month", "year"]);
  });

  it("writes the current month out like any other month", () => {
    expect(budgetShortcutPath(3, { month: 10, year: 2026 })).toBe("/budgets?category=3&month=10&year=2026");
  });
});
