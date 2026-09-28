import { afterEach, describe, expect, it, vi } from "vitest";

describe("apiConfig", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it("uses same-origin relative URLs", async () => {
    const { API_BASE_URL } = await import("./apiConfig");

    expect(API_BASE_URL).toBe("");
    expect(`${API_BASE_URL}/api/auth/login`).toBe("/api/auth/login");
  });

  it("ignores the obsolete cross-origin VITE_API_BASE_URL", async () => {
    vi.stubEnv("VITE_API_BASE_URL", "https://backend.example.com");
    vi.resetModules();

    const { API_BASE_URL } = await import("./apiConfig");

    expect(API_BASE_URL).toBe("");
  });

  it("never produces a doubled slash or doubled /api prefix", async () => {
    const { API_BASE_URL } = await import("./apiConfig");

    for (const path of ["/api/auth/refresh", "/api/account/photo", "/api/transactions?page=0"]) {
      const url = `${API_BASE_URL}${path}`;
      expect(url.startsWith("/api/")).toBe(true);
      expect(url).not.toMatch(/\/\/|\/api\/api/);
    }
  });
});
