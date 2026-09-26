import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./api";
import { getCurrentUser, login, register } from "./authService";

vi.mock("./api", () => ({
  apiRequest: vi.fn(),
}));

const mockApiRequest = vi.mocked(apiRequest);

describe("authService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("submits login credentials as a public request", async () => {
    const request = {
      email: "demo@fintrack.dev",
      password: "Password123!",
    };
    const response = {
      accessToken: "access-token",
      tokenType: "Bearer",
      expiresIn: 3600,
    };

    mockApiRequest.mockResolvedValue(response);

    await expect(login(request)).resolves.toEqual(response);
    expect(mockApiRequest).toHaveBeenCalledWith("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(request),
      authenticated: false,
    });
  });

  it("submits registration details as a public request", async () => {
    const request = {
      firstName: "Demo",
      lastName: "User",
      email: "demo@fintrack.dev",
      password: "Password123!",
    };
    const response = {
      id: 1,
      firstName: "Demo",
      lastName: "User",
      displayName: "Demo User",
      preferences: { dateFormat: "MEDIUM" as const, transactionPageSize: 10 as const },
      email: "demo@fintrack.dev",
      createdAt: "2026-09-22T00:00:00",
    };

    mockApiRequest.mockResolvedValue(response);

    await expect(register(request)).resolves.toEqual(response);
    expect(mockApiRequest).toHaveBeenCalledWith("/api/auth/register", {
      method: "POST",
      body: JSON.stringify(request),
      authenticated: false,
    });
  });

  it("loads the current authenticated user", async () => {
    const response = {
      id: 1,
      firstName: "Demo",
      lastName: "User",
      displayName: "Demo User",
      preferences: { dateFormat: "MEDIUM" as const, transactionPageSize: 10 as const },
      email: "demo@fintrack.dev",
      createdAt: "2026-09-22T00:00:00",
    };

    mockApiRequest.mockResolvedValue(response);

    await expect(getCurrentUser()).resolves.toEqual(response);
    expect(mockApiRequest).toHaveBeenCalledWith("/api/auth/me");
  });
});
