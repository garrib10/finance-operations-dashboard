import { describe, expect, it } from "vitest";
import {
  availableMonths,
  availableYears,
  clampPeriod,
  isAvailablePeriod,
  isSamePeriod,
  monthName,
  parsePeriodParams,
  withPeriod,
} from "./reportingPeriod";

const OCTOBER_2026 = { month: 10, year: 2026 };
const parse = (query: string) => parsePeriodParams(new URLSearchParams(query));

describe("parsePeriodParams", () => {
  it("means the server's current month without parameters", () => {
    expect(parse("")).toEqual({ kind: "none" });
    expect(parse("view=compact")).toEqual({ kind: "none" });
  });

  it("accepts one plain month and one plain year", () => {
    expect(parse("month=8&year=2026")).toEqual({ kind: "period", period: { month: 8, year: 2026 } });
    expect(parse("year=2000&month=1")).toEqual({ kind: "period", period: { month: 1, year: 2000 } });
  });

  it.each([
    ["only a month", "month=8"],
    ["only a year", "year=2026"],
    ["an empty month", "month=&year=2026"],
    ["an empty year", "month=8&year="],
    ["text", "month=abc&year=2026"],
    ["a decimal", "month=8.5&year=2026"],
    ["a decimal year", "month=8&year=2026.0"],
    ["a sign", "month=+8&year=2026"],
    ["a negative month", "month=-1&year=2026"],
    ["a leading zero", "month=08&year=2026"],
    ["spaces", "month=%208&year=2026"],
    ["an exponent", "month=8&year=2e3"],
    ["month 0", "month=0&year=2026"],
    ["month 13", "month=13&year=2026"],
    ["a year before 2000", "month=8&year=1999"],
    ["a repeated month", "month=8&month=8&year=2026"],
    ["conflicting years", "month=8&year=2025&year=2026"],
  ])("rejects %s", (_case, query) => {
    expect(parse(query)).toEqual({ kind: "invalid" });
  });
});

describe("available periods", () => {
  it("allows January 2000 up to the current month, never later", () => {
    expect(isAvailablePeriod({ month: 1, year: 2000 }, OCTOBER_2026)).toBe(true);
    expect(isAvailablePeriod({ month: 12, year: 2025 }, OCTOBER_2026)).toBe(true);
    expect(isAvailablePeriod(OCTOBER_2026, OCTOBER_2026)).toBe(true);
    expect(isAvailablePeriod({ month: 11, year: 2026 }, OCTOBER_2026)).toBe(false);
    expect(isAvailablePeriod({ month: 1, year: 2027 }, OCTOBER_2026)).toBe(false);
    expect(isAvailablePeriod({ month: 12, year: 1999 }, OCTOBER_2026)).toBe(false);
    expect(isAvailablePeriod({ month: 13, year: 2025 }, OCTOBER_2026)).toBe(false);
  });

  it("offers years from 2000 and only the months up to now in the current year", () => {
    expect(availableYears(OCTOBER_2026)).toEqual(Array.from({ length: 27 }, (_, index) => 2000 + index));
    expect(availableMonths(2026, OCTOBER_2026)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
    expect(availableMonths(2025, OCTOBER_2026)).toHaveLength(12);
  });

  it("moves a future choice back to the current month", () => {
    expect(clampPeriod({ month: 12, year: 2026 }, OCTOBER_2026)).toEqual(OCTOBER_2026);
    expect(clampPeriod({ month: 12, year: 2025 }, OCTOBER_2026)).toEqual({ month: 12, year: 2025 });
  });

  it("compares periods and names months", () => {
    expect(isSamePeriod({ month: 8, year: 2026 }, { month: 8, year: 2026 })).toBe(true);
    expect(isSamePeriod({ month: 8, year: 2026 }, { month: 8, year: 2025 })).toBe(false);
    expect(isSamePeriod(null, OCTOBER_2026)).toBe(false);
    expect(monthName(8)).toBe("August");
  });
});

describe("withPeriod", () => {
  it("sets or removes the period and keeps every other parameter", () => {
    const params = new URLSearchParams("view=compact&month=3&year=2025");

    expect(withPeriod(params, { month: 8, year: 2026 }).toString()).toBe("view=compact&month=8&year=2026");
    expect(withPeriod(params, null).toString()).toBe("view=compact");
    expect(params.toString()).toBe("view=compact&month=3&year=2025"); // Not changed in place.
  });
});
