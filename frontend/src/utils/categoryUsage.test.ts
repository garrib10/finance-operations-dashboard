import { describe, expect, it } from "vitest";
import { categoryCardIds, deleteBlockedReason, monthActivity } from "./categoryUsage";

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
      actions: "category-7-actions",
      actionsPanel: "category-7-actions-panel",
      edit: "category-7-edit",
      delete: "category-7-delete",
      deleteReason: "category-7-delete-reason",
    });
  });
});

describe("monthActivity", () => {
  it.each([
    [0, "0 transactions in October"],
    [1, "1 transaction in October"],
    [2, "2 transactions in October"],
  ])("describes %i this month", (count, text) => {
    expect(monthActivity(count, "October")).toBe(text);
  });
});
