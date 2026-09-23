import { afterEach, describe, expect, it, vi } from "vitest";

describe("apiConfig", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it("uses the configured API base URL", async () => {
    vi.stubEnv("VITE_API_BASE_URL", "https://api.fintrack.example");
    vi.resetModules();

    const { API_BASE_URL } = await import("./apiConfig");

    expect(API_BASE_URL).toBe("https://api.fintrack.example");
  });

  it("uses the local API URL when no configured URL exists", async () => {
    vi.stubEnv("VITE_API_BASE_URL", undefined);
    vi.resetModules();

    const { API_BASE_URL } = await import("./apiConfig");

    expect(API_BASE_URL).toBe("http://localhost:8080");
  });
});
