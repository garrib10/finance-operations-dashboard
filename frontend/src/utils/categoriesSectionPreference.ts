/**
 * Device-level preferences for the Categories page's two card sections, "Active this
 * month" and "Other categories". Each holds only "true" or "false" (never account data)
 * and is written only when the user presses that section's Show/Hide button; automatic
 * opening never changes it.
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

/** "Active this month" is open by default: only a saved "false" closes it. */
export const CATEGORIES_ACTIVE_EXPANDED_KEY = "fintrack:categories-active-expanded";

/** Missing, invalid, or unreadable values mean open. */
export function readCategoriesActiveExpanded(): boolean {
  try {
    return window.localStorage.getItem(CATEGORIES_ACTIVE_EXPANDED_KEY) !== "false";
  } catch {
    return true;
  }
}

/** Best effort: the section still works when storage is unavailable or full. */
export function saveCategoriesActiveExpanded(expanded: boolean): void {
  try {
    window.localStorage.setItem(CATEGORIES_ACTIVE_EXPANDED_KEY, String(expanded));
  } catch {
    // The preference is a convenience only; ignore storage failures.
  }
}
