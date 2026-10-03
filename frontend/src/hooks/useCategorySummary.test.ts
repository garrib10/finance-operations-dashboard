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
});
