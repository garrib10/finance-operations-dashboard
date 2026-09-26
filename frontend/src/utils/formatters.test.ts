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

it("preserves calendar dates in ISO and readable formats", () => {
 expect(formatDate("2026-01-01", "ISO")).toBe("2026-01-01");
 expect(formatDate("2026-01-01", "MEDIUM")).toBe("Jan 1, 2026");
 expect(formatDate("2024-02-29", "ISO")).toBe("2024-02-29");
});
