import { useSyncExternalStore } from "react";

function canMatchMedia(): boolean {
  return typeof window.matchMedia === "function";
}

/**
 * Whether a CSS media query matches, updated when it changes. Read synchronously on the
 * first render, so layout chosen from it never flashes. Without matchMedia (older test
 * environments) it reports false.
 */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      if (!canMatchMedia()) return () => {};
      const list = window.matchMedia(query);
      list.addEventListener("change", onChange);
      return () => list.removeEventListener("change", onChange);
    },
    // No server snapshot: FinTrack renders only in the browser.
    () => canMatchMedia() && window.matchMedia(query).matches,
  );
}
