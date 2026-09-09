import { beforeEach, describe, expect, it } from "vitest";

import { getAuthToken, removeAuthToken, setAuthToken } from "./authToken";

describe("authToken", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it("stores an authentication token", () => {
    setAuthToken("test-token");

    expect(window.localStorage.getItem("fintrack_access_token")).toBe(
      "test-token",
    );
  });

  it("returns a stored authentication token", () => {
    window.localStorage.setItem("fintrack_access_token", "saved-token");

    expect(getAuthToken()).toBe("saved-token");
  });

  it("returns null when no token exists", () => {
    expect(getAuthToken()).toBeNull();
  });

  it("removes an authentication token", () => {
    window.localStorage.setItem("fintrack_access_token", "saved-token");

    removeAuthToken();

    expect(getAuthToken()).toBeNull();
  });
});
