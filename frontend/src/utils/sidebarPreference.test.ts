import { afterEach, describe, expect, it, vi } from "vitest";
import { readSidebarCollapsed, saveSidebarCollapsed, SIDEBAR_COLLAPSED_KEY } from "./sidebarPreference";

describe("sidebar preference", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("uses a namespaced device-level key", () => {
    expect(SIDEBAR_COLLAPSED_KEY).toBe("fintrack:sidebar-collapsed");
  });

  it("defaults to expanded when nothing is saved", () => {
    expect(readSidebarCollapsed()).toBe(false);
  });

  it.each([["true", true], ["false", false]])("reads a saved %s value", (stored, collapsed) => {
    window.localStorage.setItem(SIDEBAR_COLLAPSED_KEY, stored);
    expect(readSidebarCollapsed()).toBe(collapsed);
  });

  it.each(["TRUE", "1", "yes", "{\"collapsed\":true}", ""])("treats the invalid value %j as expanded", (stored) => {
    window.localStorage.setItem(SIDEBAR_COLLAPSED_KEY, stored);
    expect(readSidebarCollapsed()).toBe(false);
  });

  it("defaults to expanded when storage cannot be read", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new DOMException("Access denied", "SecurityError");
    });
    expect(readSidebarCollapsed()).toBe(false);
  });

  it("writes only \"true\" or \"false\"", () => {
    saveSidebarCollapsed(true);
    expect(window.localStorage.getItem(SIDEBAR_COLLAPSED_KEY)).toBe("true");
    saveSidebarCollapsed(false);
    expect(window.localStorage.getItem(SIDEBAR_COLLAPSED_KEY)).toBe("false");
    expect(window.localStorage.length).toBe(1);
  });

  it("ignores a storage write failure", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("Quota exceeded", "QuotaExceededError");
    });
    expect(() => saveSidebarCollapsed(true)).not.toThrow();
  });
});
