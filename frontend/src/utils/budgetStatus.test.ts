import { describe, expect, it } from "vitest";
import { clampProgressPercentage, formatBudgetStatus } from "./budgetStatus";

describe("budget status helpers", () => {
  it.each([
    ["ON_TRACK", "On Track"],
    ["CAUTION", "Caution"],
    ["WARNING", "Warning"],
    ["OVER_BUDGET", "Over Budget"],
  ] as const)("labels %s as %s", (status, label) => {
    expect(formatBudgetStatus(status)).toBe(label);
  });

  it("shows an unrecognised status as its raw value", () => {
    expect(formatBudgetStatus("PAUSED" as never)).toBe("PAUSED");
  });

  it.each([[-5, 0], [0, 0], [42.5, 42.5], [100, 100], [125, 100]])("clamps %s to %s", (input, expected) => {
    expect(clampProgressPercentage(input)).toBe(expected);
  });
});
