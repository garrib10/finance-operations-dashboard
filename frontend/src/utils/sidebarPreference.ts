/**
 * Device-level desktop sidebar preference. It holds only "true" or "false" (never
 * account data), so it is safe to keep after logout and shared by everyone on the device.
 */
export const SIDEBAR_COLLAPSED_KEY = "fintrack:sidebar-collapsed";

/** Missing, invalid, or unreadable values mean expanded. */
export function readSidebarCollapsed(): boolean {
  try {
    return window.localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "true";
  } catch {
    return false;
  }
}

/** Best effort: the sidebar still works when storage is unavailable or full. */
export function saveSidebarCollapsed(collapsed: boolean): void {
  try {
    window.localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(collapsed));
  } catch {
    // The preference is a convenience only; ignore storage failures.
  }
}
