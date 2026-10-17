/**
 * Recovery for signed-in pages whose code fails to load (lazy routes, #124).
 *
 * The usual cause is a new deployment: a tab opened on the previous build asks for a page
 * file whose hashed name no longer exists. Reloading picks up the new build, so the app does
 * that automatically, but at most once per minute per tab so a real outage cannot loop.
 */
export const PAGE_RELOAD_KEY = "fintrack:page-load-reload-at";

/** A second failure within this window shows the error message instead of reloading. */
export const PAGE_RELOAD_WINDOW_MS = 60_000;

// Chrome / Edge, Firefox, Safari, and Vite's CSS preload failure, in that order.
const PAGE_LOAD_ERROR_PATTERNS = [
  /Failed to fetch dynamically imported module/i,
  /error loading dynamically imported module/i,
  /Importing a module script failed/i,
  /Unable to preload CSS/i,
];

/** True for errors that mean a page's code could not be downloaded. */
export function isPageLoadError(error: unknown): boolean {
  if (!(error instanceof Error)) return false;
  return PAGE_LOAD_ERROR_PATTERNS.some((pattern) => pattern.test(error.message));
}

/**
 * Records the attempt and returns true when an automatic reload is allowed. Unreadable
 * storage returns false: without the guard a reload could repeat forever.
 */
export function claimAutomaticReload(now: number = Date.now()): boolean {
  try {
    const last = Number(window.sessionStorage.getItem(PAGE_RELOAD_KEY));
    if (Number.isFinite(last) && last > 0 && now - last < PAGE_RELOAD_WINDOW_MS) {
      return false;
    }
    window.sessionStorage.setItem(PAGE_RELOAD_KEY, String(now));
    return true;
  } catch {
    return false;
  }
}
