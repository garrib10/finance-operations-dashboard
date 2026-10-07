vi.mock("../services/categoryService", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../services/categoryService")>()),
  getCategorySummary: vi.fn(),
}));

import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../services/api";
import { getCategorySummary } from "../services/categoryService";
import { deferred } from "../test/accountFixtures";
import { groceriesRow, petCareRow, summaryList } from "../test/categorySummaryFixtures";
import type { CategorySummaryList } from "../types/category";
import { useCategorySummary } from "./useCategorySummary";

describe("useCategorySummary", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads the summary on mount", async () => {
    vi.mocked(getCategorySummary).mockResolvedValue(summaryList([groceriesRow]));
    const { result } = renderHook(() => useCategorySummary());

    expect(result.current.status).toBe("loading");
    await waitFor(() => expect(result.current.status).toBe("ready"));
    expect(result.current.summary?.categories).toEqual([groceriesRow]);
  });

  it("reports one fixed message, never server or network details, when loading fails", async () => {
    vi.mocked(getCategorySummary).mockRejectedValueOnce(new ApiError("SQL timeout on host db-1", 503));
    const { result } = renderHook(() => useCategorySummary());
    await waitFor(() => expect(result.current.status).toBe("error"));
    expect(result.current.error).toBe("Unable to load categories. Please try again.");

    vi.mocked(getCategorySummary).mockRejectedValueOnce(new Error("network"));
    let ok: boolean | undefined;
    await act(async () => { ok = await result.current.reload(); });
    expect(ok).toBe(false);
    expect(result.current.error).toBe("Unable to load categories. Please try again.");
  });

  it("resolves reload with whether fresh data arrived", async () => {
    vi.mocked(getCategorySummary).mockResolvedValue(summaryList([groceriesRow]));
    const { result } = renderHook(() => useCategorySummary());
    await waitFor(() => expect(result.current.status).toBe("ready"));

    let ok: boolean | undefined;
    await act(async () => { ok = await result.current.reload(); });
    expect(ok).toBe(true);
  });

  it("keeps the newest response when an older request finishes last", async () => {
    const first = deferred<CategorySummaryList>();
    vi.mocked(getCategorySummary)
      .mockReturnValueOnce(first.promise)
      .mockResolvedValueOnce(summaryList([petCareRow]));
    const { result } = renderHook(() => useCategorySummary());

    await act(() => result.current.reload());
    await act(async () => first.resolve(summaryList([groceriesRow])));

    expect(result.current.summary?.categories).toEqual([petCareRow]);
    expect(result.current.status).toBe("ready");
  });

  it("ignores a failure from an older request once a newer one has succeeded", async () => {
    const first = deferred<CategorySummaryList>();
    vi.mocked(getCategorySummary)
      .mockReturnValueOnce(first.promise)
      .mockResolvedValueOnce(summaryList([petCareRow]));
    const { result } = renderHook(() => useCategorySummary());

    await act(() => result.current.reload());
    await act(async () => first.reject(new Error("late failure")));

    expect(result.current.status).toBe("ready");
    expect(result.current.error).toBe("");
  });

  it("ignores a response that arrives after unmounting", async () => {
    const pending = deferred<CategorySummaryList>();
    vi.mocked(getCategorySummary).mockReturnValueOnce(pending.promise);
    const { result, unmount } = renderHook(() => useCategorySummary());

    unmount();
    await act(async () => pending.resolve(summaryList([groceriesRow])));

    expect(result.current.summary).toBeNull();
  });

  describe("with a wanted period", () => {
    const AUGUST = { month: 8, year: 2026 };
    const SEPTEMBER = { month: 9, year: 2026 };
    const current = summaryList([groceriesRow], 10, 2026);           // server: October 2026
    const august = summaryList([petCareRow], 8, 2026, 10, 2026);
    const september = summaryList([groceriesRow, petCareRow], 9, 2026, 10, 2026);
    const calls = () => vi.mocked(getCategorySummary).mock.calls.map(([period]) => period ?? null);

    it("learns the server month first, then requests the wanted earlier month", async () => {
      const second = deferred<CategorySummaryList>();
      vi.mocked(getCategorySummary).mockResolvedValueOnce(current).mockReturnValueOnce(second.promise);
      const { result } = renderHook(() => useCategorySummary(AUGUST));

      await waitFor(() => expect(calls()).toEqual([null, AUGUST]));
      expect(result.current.current).toEqual({ month: 10, year: 2026 });
      expect(result.current.status).toBe("loading"); // October is not what was wanted.

      await act(async () => second.resolve(august));
      expect(result.current.status).toBe("ready");
      expect(result.current.summary?.month).toBe(8);
      expect(vi.mocked(getCategorySummary).mock.calls[1][1]).toBeInstanceOf(AbortSignal);
    });

    it("uses the first response when the wanted month is the current one", async () => {
      vi.mocked(getCategorySummary).mockResolvedValue(current);
      const { result } = renderHook(() => useCategorySummary({ month: 10, year: 2026 }));

      await waitFor(() => expect(result.current.status).toBe("ready"));
      expect(calls()).toEqual([null]);
    });

    it("never requests a future or out-of-range month", async () => {
      vi.mocked(getCategorySummary).mockResolvedValue(current);
      const { result } = renderHook(() => useCategorySummary({ month: 12, year: 2026 }));

      await waitFor(() => expect(result.current.status).toBe("ready"));
      expect(calls()).toEqual([null]);
    });

    it("keeps the newer month when an older one answers last, and aborts the older request", async () => {
      const augustRequest = deferred<CategorySummaryList>();
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(current)
        .mockReturnValueOnce(augustRequest.promise)
        .mockResolvedValueOnce(september);
      const { result, rerender } = renderHook(({ wanted }) => useCategorySummary(wanted), {
        initialProps: { wanted: AUGUST as typeof AUGUST | typeof SEPTEMBER },
      });
      await waitFor(() => expect(calls()).toEqual([null, AUGUST]));
      const augustSignal = vi.mocked(getCategorySummary).mock.calls[1][1]!;

      rerender({ wanted: SEPTEMBER });
      await waitFor(() => expect(result.current.summary?.month).toBe(9));
      expect(augustSignal.aborted).toBe(true);

      await act(async () => augustRequest.resolve(august)); // Too late to matter.
      expect(result.current.summary?.month).toBe(9);
      expect(result.current.status).toBe("ready");
    });

    it("never lets an older failure or an abort replace a newer success", async () => {
      const augustRequest = deferred<CategorySummaryList>();
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(current)
        .mockReturnValueOnce(augustRequest.promise)
        .mockResolvedValueOnce(september);
      const { result, rerender } = renderHook(({ wanted }) => useCategorySummary(wanted), {
        initialProps: { wanted: AUGUST as typeof AUGUST | typeof SEPTEMBER },
      });
      await waitFor(() => expect(calls()).toEqual([null, AUGUST]));
      rerender({ wanted: SEPTEMBER });
      await waitFor(() => expect(result.current.summary?.month).toBe(9));

      await act(async () => augustRequest.reject(new DOMException("aborted", "AbortError")));
      expect(result.current.status).toBe("ready");
      expect(result.current.error).toBe("");
    });

    it("stays loading when an older request finishes while a newer one is pending", async () => {
      const augustRequest = deferred<CategorySummaryList>();
      const septemberRequest = deferred<CategorySummaryList>();
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(current)
        .mockReturnValueOnce(augustRequest.promise)
        .mockReturnValueOnce(septemberRequest.promise);
      const { result, rerender } = renderHook(({ wanted }) => useCategorySummary(wanted), {
        initialProps: { wanted: AUGUST as typeof AUGUST | typeof SEPTEMBER },
      });
      await waitFor(() => expect(calls()).toEqual([null, AUGUST]));
      rerender({ wanted: SEPTEMBER });
      await waitFor(() => expect(calls()).toHaveLength(3));

      await act(async () => augustRequest.resolve(august));
      expect(result.current.status).toBe("loading");
      expect(result.current.summary?.month).toBe(10); // August's late answer is ignored.

      await act(async () => septemberRequest.resolve(september));
      expect(result.current.status).toBe("ready");
      expect(result.current.summary?.month).toBe(9);
    });

    it("reloads the wanted month, not the current one", async () => {
      vi.mocked(getCategorySummary).mockResolvedValueOnce(current).mockResolvedValue(august);
      const { result } = renderHook(() => useCategorySummary(AUGUST));
      await waitFor(() => expect(result.current.summary?.month).toBe(8));

      let ok: boolean | undefined;
      await act(async () => { ok = await result.current.reload(); });
      expect(ok).toBe(true);
      expect(calls().at(-1)).toEqual(AUGUST);
    });

    it("aborts the pending request when unmounted", async () => {
      const pending = deferred<CategorySummaryList>();
      vi.mocked(getCategorySummary).mockReturnValueOnce(pending.promise);
      const { unmount } = renderHook(() => useCategorySummary(AUGUST));
      await waitFor(() => expect(getCategorySummary).toHaveBeenCalledTimes(1));
      const signal = vi.mocked(getCategorySummary).mock.calls[0][1]!;

      unmount();
      expect(signal.aborted).toBe(true);
    });
  });
});
