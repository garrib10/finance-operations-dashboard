/**
 * Device-level preference for the Categories page's "Other categories" section. It holds
 * only "true" or "false" (never account data) and is written only when the user presses
 * the Show/Hide button; automatic opening never changes it.
 */
export const CATEGORIES_OTHERS_EXPANDED_KEY = "fintrack:categories-others-expanded";

/** Missing, invalid, or unreadable values mean collapsed. */
export function readCategoriesOthersExpanded(): boolean {
  try {
    return window.localStorage.getItem(CATEGORIES_OTHERS_EXPANDED_KEY) === "true";
  } catch {
    return false;
  }
}

/** Best effort: the section still works when storage is unavailable or full. */
export function saveCategoriesOthersExpanded(expanded: boolean): void {
  try {
    window.localStorage.setItem(CATEGORIES_OTHERS_EXPANDED_KEY, String(expanded));
  } catch {
    // The preference is a convenience only; ignore storage failures.
  }
}
