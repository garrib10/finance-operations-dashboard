import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  clearAccessToken,
  getAccessToken,
  removeLegacyAccessToken,
  setAccessToken,
} from "./authToken";

const SECRET = "eyJhbGciOiJIUzI1NiJ9.memory-only-token.signature";

describe("authToken", () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
    clearAccessToken();
  });

  it("keeps the access token in memory only", () => {
    setAccessToken(SECRET);

    expect(getAccessToken()).toBe(SECRET);
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain(SECRET);
    expect(window.location.href).not.toContain(SECRET);
  });

  it("returns null when no token exists and after clearing", () => {
    expect(getAccessToken()).toBeNull();

    setAccessToken(SECRET);
    clearAccessToken();

    expect(getAccessToken()).toBeNull();
  });

  it("loses the token when the module is reloaded, as on a page reload", async () => {
    setAccessToken(SECRET);
    vi.resetModules();

    const reloaded = await import("./authToken");

    expect(reloaded.getAccessToken()).toBeNull();
  });

  it("deletes a legacy localStorage token without restoring or reading it", () => {
    window.localStorage.setItem("fintrack_access_token", "legacy-placeholder");
    window.localStorage.setItem("unrelated", "kept");
    const getItem = vi.spyOn(Storage.prototype, "getItem");
    const log = vi.spyOn(console, "log");

    removeLegacyAccessToken();

    // The legacy value is deleted, never read, restored, or logged.
    expect(getItem).not.toHaveBeenCalled();
    expect(log).not.toHaveBeenCalled();
    getItem.mockRestore();
    log.mockRestore();
    expect(window.localStorage.getItem("fintrack_access_token")).toBeNull();
    expect(window.localStorage.getItem("unrelated")).toBe("kept");
    expect(getAccessToken()).toBeNull();
  });

  it("tolerates unavailable storage when removing the legacy key", () => {
    const removeItem = vi.spyOn(Storage.prototype, "removeItem").mockImplementation(() => {
      throw new DOMException("denied", "SecurityError");
    });

    expect(() => removeLegacyAccessToken()).not.toThrow();
    removeItem.mockRestore();
  });
});
