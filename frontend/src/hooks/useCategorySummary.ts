import { useCallback, useEffect, useRef, useState } from "react";
import { getCategorySummary } from "../services/categoryService";
import type { CategorySummaryList } from "../types/category";
import { isAvailablePeriod, isSamePeriod, type ReportingPeriod } from "../utils/reportingPeriod";

export type CategorySummaryStatus = "loading" | "ready" | "error";

/** Always this fixed text: server or network details are never shown on the page. */
export const SUMMARY_LOAD_ERROR = "Unable to load categories. Please try again.";

function keyOf(month: number | null, year: number | null): string {
  return month === null || year === null ? "current" : `${year}-${month}`;
}

/**
 * Loads the Categories page's usage summary for the wanted period (or the server's current
 * month for `null`). Kept out of CategoryContext, which holds the plain category list used
 * by every category dropdown.
 *
 * The server's current month is only known from a response, so a wanted earlier month is
 * requested once that is known and it is available (January 2000 to the current month):
 * the first request is always the default one, which also serves a wanted current month.
 * An unavailable period is never sent; it is the page's job to correct the URL.
 *
 * Only the newest request may change state: a newer request (or unmounting) aborts the
 * previous one, and any response, failure, or abort that arrives late is ignored, so
 * quick period changes can never show one month's figures as another's. `summary` keeps
 * the last good response (with its own `month`/`year`); `status` is `loading` until the
 * requested period's response arrives.
 */
export function useCategorySummary(wanted: ReportingPeriod | null = null) {
  const [summary, setSummary] = useState<CategorySummaryList | null>(null);
  const current: ReportingPeriod | null = summary
    ? { month: summary.serverCurrentMonth, year: summary.serverCurrentYear }
    : null;
  const period = wanted && current && isAvailablePeriod(wanted, current) && !isSamePeriod(wanted, current)
    ? wanted
    : null;
  const month = period?.month ?? null;
  const year = period?.year ?? null;
  const key = keyOf(month, year);

  // The request key that last settled, and how. Loading is "not settled for this key".
  const [settled, setSettled] = useState<{ key: string; ok: boolean } | null>(null);
  const latestRequest = useRef(0);
  const controller = useRef<AbortController | null>(null);
  const requested = useRef<{ key: string; period: ReportingPeriod | null }>({ key: "current", period: null });

  /**
   * Requests one period; state changes only once its response arrives. Resolves false
   * when this request failed (a superseded or aborted request defers to the newer one).
   */
  const load = useCallback(async (request: number, requestKey: string, target: ReportingPeriod | null) => {
    controller.current?.abort();
    const abort = new AbortController();
    controller.current = abort;
    try {
      const response = await getCategorySummary(target, abort.signal);
      if (request !== latestRequest.current) return true;
      setSummary(response);
      setSettled({ key: requestKey, ok: true });
      return true;
    } catch {
      // An abort is never a failure, and only the newest request may report one.
      if (request !== latestRequest.current || abort.signal.aborted) return true;
      setSettled({ key: requestKey, ok: false });
      return false;
    }
  }, []);

  /**
   * Shows the loading state, then refreshes the requested period (for Retry and after
   * changes). Resolves whether fresh data arrived, so callers can report a change honestly.
   */
  const reload = useCallback(async (): Promise<boolean> => {
    setSettled(null);
    const { key: requestKey, period: target } = requested.current;
    return load(++latestRequest.current, requestKey, target);
  }, [load]);

  useEffect(() => {
    const target = month === null || year === null ? null : { month, year };
    requested.current = { key: keyOf(month, year), period: target };
    void load(++latestRequest.current, keyOf(month, year), target);
  }, [load, month, year]);

  useEffect(() => () => {
    // Ignore (and stop) a response that would arrive after the page has gone.
    latestRequest.current += 1;
    controller.current?.abort();
  }, []);

  const status: CategorySummaryStatus = settled?.key !== key ? "loading" : settled.ok ? "ready" : "error";
  return { summary, current, status, error: status === "error" ? SUMMARY_LOAD_ERROR : "", reload };
}
