import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useMediaQuery } from "./useMediaQuery";

function stubMatchMedia(initial: boolean) {
  const listeners = new Set<() => void>();
  const list = {
    matches: initial,
    addEventListener: (_type: string, listener: () => void) => listeners.add(listener),
    removeEventListener: (_type: string, listener: () => void) => listeners.delete(listener),
  };
  const matchMedia = vi.fn(() => list);
  vi.stubGlobal("matchMedia", matchMedia);
  return {
    matchMedia,
    listeners,
    change(matches: boolean) {
      list.matches = matches;
      listeners.forEach((listener) => listener());
    },
  };
}

describe("useMediaQuery", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("reads the current match on the first render", () => {
    const media = stubMatchMedia(true);

    const { result } = renderHook(() => useMediaQuery("(width > 1100px)"));

    expect(result.current).toBe(true);
    expect(media.matchMedia).toHaveBeenCalledWith("(width > 1100px)");
  });

  it("updates when the query starts or stops matching, and unsubscribes on unmount", () => {
    const media = stubMatchMedia(false);
    const { result, unmount } = renderHook(() => useMediaQuery("(width > 1100px)"));
    expect(result.current).toBe(false);

    act(() => media.change(true));
    expect(result.current).toBe(true);

    act(() => media.change(false));
    expect(result.current).toBe(false);

    unmount();
    expect(media.listeners.size).toBe(0);
  });

  it("reports false when matchMedia is unavailable", () => {
    vi.stubGlobal("matchMedia", undefined);

    const { result, unmount } = renderHook(() => useMediaQuery("(width > 1100px)"));

    expect(result.current).toBe(false);
    unmount();
  });
});
