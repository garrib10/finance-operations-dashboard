import { afterEach, describe, expect, it, vi } from "vitest";
import {
  CATEGORIES_OTHERS_EXPANDED_KEY,
  readCategoriesOthersExpanded,
  saveCategoriesOthersExpanded,
} from "./categoriesSectionPreference";

describe("categories section preference", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("uses a namespaced device-level key", () => {
    expect(CATEGORIES_OTHERS_EXPANDED_KEY).toBe("fintrack:categories-others-expanded");
  });

  it("defaults to collapsed when nothing is saved", () => {
    expect(readCategoriesOthersExpanded()).toBe(false);
  });

  it.each([["true", true], ["false", false]])("reads a saved %s value", (stored, expanded) => {
    window.localStorage.setItem(CATEGORIES_OTHERS_EXPANDED_KEY, stored);
    expect(readCategoriesOthersExpanded()).toBe(expanded);
  });

  it.each(["TRUE", "1", "yes", "{\"expanded\":true}", ""])("treats the invalid value %j as collapsed", (stored) => {
    window.localStorage.setItem(CATEGORIES_OTHERS_EXPANDED_KEY, stored);
    expect(readCategoriesOthersExpanded()).toBe(false);
  });

  it("defaults to collapsed when storage cannot be read", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new DOMException("Access denied", "SecurityError");
    });

    expect(readCategoriesOthersExpanded()).toBe(false);
  });

  it.each([[true, "true"], [false, "false"]])("saves %s as %j under its own key only", (expanded, stored) => {
    window.localStorage.setItem("fintrack:sidebar-collapsed", "true");
    saveCategoriesOthersExpanded(expanded);

    expect(window.localStorage.getItem(CATEGORIES_OTHERS_EXPANDED_KEY)).toBe(stored);
    expect(window.localStorage.getItem("fintrack:sidebar-collapsed")).toBe("true");
    expect(window.localStorage).toHaveLength(2);
  });

  it("ignores a storage failure when saving", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("Quota exceeded", "QuotaExceededError");
    });

    expect(() => saveCategoriesOthersExpanded(true)).not.toThrow();
  });
});
