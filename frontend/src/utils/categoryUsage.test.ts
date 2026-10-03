import { describe, expect, it } from "vitest";
import { categoryCardIds, deleteBlockedReason } from "./categoryUsage";

describe("deleteBlockedReason", () => {
  it.each([
    [1, 0, "Used by 1 transaction."],
    [12, 0, "Used by 12 transactions."],
    [0, 1, "Used by 1 budget."],
    [0, 2, "Used by 2 budgets."],
    [12, 1, "Used by 12 transactions and 1 budget."],
    [1, 3, "Used by 1 transaction and 3 budgets."],
  ])("describes %i transactions and %i budgets", (transactions, budgets, reason) => {
    expect(deleteBlockedReason(transactions, budgets)).toBe(reason);
  });

  it("falls back to a general reason when no counts are known", () => {
    expect(deleteBlockedReason(0, 0)).toBe("Used by transactions or budgets.");
  });
});

describe("categoryCardIds", () => {
  it("gives each card stable, distinct element IDs", () => {
    expect(categoryCardIds(7)).toEqual({
      heading: "category-7-heading",
      edit: "category-7-edit",
      delete: "category-7-delete",
      deleteReason: "category-7-delete-reason",
    });
  });
});
