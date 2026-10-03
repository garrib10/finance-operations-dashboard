import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../services/api";
import { getCategorySummary } from "../services/categoryService";
import type { CategorySummaryList } from "../types/category";

export type CategorySummaryStatus = "loading" | "ready" | "error";

const LOAD_ERROR = "Unable to load your categories. Please try again.";

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

  /** Requests the summary; state changes only once the response arrives. */
  const load = useCallback(async (request: number): Promise<void> => {
    try {
      const response = await getCategorySummary();
      if (request !== latestRequest.current) return;
      setSummary(response);
      setError("");
      setStatus("ready");
    } catch (caught) {
      if (request !== latestRequest.current) return;
      setError(caught instanceof ApiError ? caught.message : LOAD_ERROR);
      setStatus("error");
    }
  }, []);

  /** Shows the loading state, then refreshes (for Retry and after changes). */
  const reload = useCallback(async (): Promise<void> => {
    setStatus("loading");
    setError("");
    await load(++latestRequest.current);
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
