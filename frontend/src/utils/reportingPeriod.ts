/** A calendar month: `month` is 1–12. */
export interface ReportingPeriod {
  month: number;
  year: number;
}

export const MONTH_PARAM = "month";
export const YEAR_PARAM = "year";
/** The earliest year the API accepts (the same as budgets). */
export const EARLIEST_YEAR = 2000;

/**
 * The `?month=&year=` part of a Categories URL, checked for shape only (the server's
 * current month is needed for the range check, see {@link isAvailablePeriod}):
 * - `none`: neither parameter, so the server's current month;
 * - `period`: exactly one of each, written as plain whole numbers (`8`, `2026`; no signs,
 *   decimals, spaces, or leading zeros), month 1–12 and year 2000 or later;
 * - `invalid`: anything else, including only one of the two, an empty value, or a
 *   parameter given more than once (even with the same value), so a URL never has two
 *   competing meanings.
 */
export type PeriodParams = { kind: "none" } | { kind: "period"; period: ReportingPeriod } | { kind: "invalid" };

const WHOLE_NUMBER = /^[1-9]\d*$/;

export function parsePeriodParams(params: URLSearchParams): PeriodParams {
  const months = params.getAll(MONTH_PARAM);
  const years = params.getAll(YEAR_PARAM);
  if (months.length === 0 && years.length === 0) return { kind: "none" };
  if (months.length !== 1 || years.length !== 1) return { kind: "invalid" };
  const [month, year] = [months[0], years[0]];
  if (!WHOLE_NUMBER.test(month) || !WHOLE_NUMBER.test(year)) return { kind: "invalid" };

  const period = { month: Number(month), year: Number(year) };
  if (period.month > 12 || period.year < EARLIEST_YEAR) return { kind: "invalid" };
  return { kind: "period", period };
}

/** From January 2000 up to the server's current month (never a future month). */
export function isAvailablePeriod(period: ReportingPeriod, current: ReportingPeriod): boolean {
  if (period.year < EARLIEST_YEAR || period.month < 1 || period.month > 12) return false;
  return period.year < current.year || (period.year === current.year && period.month <= current.month);
}

export function isSamePeriod(a: ReportingPeriod | null, b: ReportingPeriod | null): boolean {
  return a !== null && b !== null && a.month === b.month && a.year === b.year;
}

/** A copy of the URL parameters with the period set (or removed for `null`), others kept. */
export function withPeriod(params: URLSearchParams, period: ReportingPeriod | null): URLSearchParams {
  const next = new URLSearchParams(params);
  next.delete(MONTH_PARAM);
  next.delete(YEAR_PARAM);
  if (period) {
    next.set(MONTH_PARAM, String(period.month));
    next.set(YEAR_PARAM, String(period.year));
  }
  return next;
}

/** Years to offer: 2000 up to the server's current year. */
export function availableYears(current: ReportingPeriod): number[] {
  return Array.from({ length: current.year - EARLIEST_YEAR + 1 }, (_, index) => EARLIEST_YEAR + index);
}

/** Months to offer in a year: all twelve, or up to the current month in the current year. */
export function availableMonths(year: number, current: ReportingPeriod): number[] {
  const last = year === current.year ? current.month : 12;
  return Array.from({ length: last }, (_, index) => index + 1);
}

/** A chosen month and year, moved back to the current month if they would be in the future. */
export function clampPeriod(period: ReportingPeriod, current: ReportingPeriod): ReportingPeriod {
  return isAvailablePeriod(period, current) ? period : { month: current.month, year: current.year };
}

export function monthName(month: number): string {
  return new Intl.DateTimeFormat("en-US", { month: "long" }).format(new Date(2026, month - 1, 1));
}
