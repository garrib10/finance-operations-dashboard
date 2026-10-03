import { useCallback, useEffect, useRef, useState } from "react";
import { getCategorySummary } from "../services/categoryService";
import type { CategorySummaryList } from "../types/category";

export type CategorySummaryStatus = "loading" | "ready" | "error";

/** Always this fixed text: server or network details are never shown on the page. */
export const SUMMARY_LOAD_ERROR = "Unable to load categories. Please try again.";

/**
 * Loads the Categories page's usage summary. Kept out of CategoryContext, which holds the
 * plain category list used by every category dropdown. Responses from an older request
 * are ignored, so a slow reload can never overwrite newer data.
 */
export function useCategorySummary() {
  const [summary, setSummary] = useState<CategorySummaryList | null>(null);
  const [status, setStatus] = useState<CategorySummaryStatus>("loading");
  const [error, setError] = useState("");
  const latestRequest = useRef(0);

  /**
   * Requests the summary; state changes only once the response arrives. Resolves false
   * when this request failed (a superseded request defers to the newer one: true).
   */
  const load = useCallback(async (request: number): Promise<boolean> => {
    try {
      const response = await getCategorySummary();
      if (request !== latestRequest.current) return true;
      setSummary(response);
      setError("");
      setStatus("ready");
      return true;
    } catch {
      if (request !== latestRequest.current) return true;
      setError(SUMMARY_LOAD_ERROR);
      setStatus("error");
      return false;
    }
  }, []);

  /**
   * Shows the loading state, then refreshes (for Retry and after changes). Resolves
   * whether fresh data arrived, so callers can report a change honestly.
   */
  const reload = useCallback(async (): Promise<boolean> => {
    setStatus("loading");
    setError("");
    return load(++latestRequest.current);
  }, [load]);

  useEffect(() => {
    void load(++latestRequest.current);
    return () => {
      // Ignore a response that arrives after the page has gone.
      latestRequest.current += 1;
    };
  }, [load]);

  return { summary, status, error, reload };
}
