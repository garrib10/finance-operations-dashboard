import { describe, expect, it } from "vitest";
import { formatCurrency, formatDate } from "./formatters";

describe("formatCurrency", () => {
  it("formats positive USD values with two decimal places", () => {
    expect(formatCurrency(1234.56)).toBe("$1,234.56");
  });

  it("formats negative USD values", () => {
    expect(formatCurrency(-42.5)).toBe("-$42.50");
  });
});

describe("formatDate", () => {
  it("formats an ISO date as a readable local date", () => {
    expect(formatDate("2026-09-10")).toBe("Sep 10, 2026");
  });
});
