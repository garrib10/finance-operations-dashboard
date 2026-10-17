import { afterEach, describe, expect, it, vi } from "vitest";
import {
  claimAutomaticReload,
  isPageLoadError,
  PAGE_RELOAD_KEY,
  PAGE_RELOAD_WINDOW_MS,
} from "./pageLoadRecovery";

describe("page load recovery", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it.each([
    "Failed to fetch dynamically imported module: https://app.example/assets/BudgetPage-abc123.js",
    "error loading dynamically imported module: https://app.example/assets/BudgetPage-abc123.js",
    "Importing a module script failed.",
    "Unable to preload CSS for /assets/BudgetPage-abc123.css",
  ])("recognizes the browser's failed page download %j", (message) => {
    expect(isPageLoadError(new TypeError(message))).toBe(true);
  });

  it.each([
    new TypeError("Cannot read properties of undefined (reading 'map')"),
    new Error("Request failed with status 500"),
    "Failed to fetch dynamically imported module",
    undefined,
  ])("does not treat %j as a failed page download", (error) => {
    expect(isPageLoadError(error)).toBe(false);
  });

  it("allows the first automatic reload in a tab and records when it happened", () => {
    expect(claimAutomaticReload(1_000_000)).toBe(true);
    expect(window.sessionStorage.getItem(PAGE_RELOAD_KEY)).toBe("1000000");
  });

  it("refuses a second automatic reload within the window, so an outage cannot loop", () => {
    expect(claimAutomaticReload(1_000_000)).toBe(true);
    expect(claimAutomaticReload(1_000_000 + PAGE_RELOAD_WINDOW_MS - 1)).toBe(false);
    // The refusal leaves the original attempt in place.
    expect(window.sessionStorage.getItem(PAGE_RELOAD_KEY)).toBe("1000000");
  });

  it("allows another automatic reload once the window has passed, for a later deployment", () => {
    expect(claimAutomaticReload(1_000_000)).toBe(true);
    expect(claimAutomaticReload(1_000_000 + PAGE_RELOAD_WINDOW_MS)).toBe(true);
  });

  it.each(["", "not-a-number", "-5"])("ignores the unusable stored value %j", (stored) => {
    window.sessionStorage.setItem(PAGE_RELOAD_KEY, stored);
    expect(claimAutomaticReload(1_000_000)).toBe(true);
  });

  it("uses the current time by default", () => {
    vi.spyOn(Date, "now").mockReturnValue(42_000);
    expect(claimAutomaticReload()).toBe(true);
    expect(window.sessionStorage.getItem(PAGE_RELOAD_KEY)).toBe("42000");
  });

  it("refuses to reload when storage cannot be read, because the loop guard would not work", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new DOMException("Access denied", "SecurityError");
    });
    expect(claimAutomaticReload(1_000_000)).toBe(false);
  });

  it("refuses to reload when the attempt cannot be recorded", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("Quota exceeded", "QuotaExceededError");
    });
    expect(claimAutomaticReload(1_000_000)).toBe(false);
  });
});
