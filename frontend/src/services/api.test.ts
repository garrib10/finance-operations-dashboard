import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./api";
import {
  invalidateAuthSession,
  subscribeToSessionInvalidation,
} from "./authSession";
import { getBudgets } from "./budgetService";
import { getDashboard } from "./dashboardService";
import { login } from "./authService";
import { getTransactions } from "./transactionService";
import { getAuthToken, setAuthToken } from "../utils/authToken";

const fetchMock = vi.fn();

function errorResponse(status: number, message: string): Response {
  return {
    ok: false,
    status,
    json: vi.fn().mockResolvedValue({
      timestamp: "2026-09-21T00:00:00Z",
      status,
      error: status === 401 ? "Unauthorized" : "Request failed",
      message,
    }),
  } as unknown as Response;
}

function successResponse<T>(body: T): Response {
  return {
    ok: true,
    status: 200,
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response;
}

function noContentResponse(): Response {
  return {
    ok: true,
    status: 204,
    json: vi.fn(),
  } as unknown as Response;
}

beforeEach(() => {
  window.localStorage.clear();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("authenticated API requests", () => {
  const authenticatedRequests: Array<[string, () => Promise<unknown>]> = [
    ["dashboard", () => getDashboard()],
    ["transactions", () => getTransactions()],
    ["budgets", () => getBudgets()],
  ];

  it.each(authenticatedRequests)(
    "invalidates the session when the %s request returns 401",
    async (_name, request) => {
      setAuthToken("expired-token");

      const listener = vi.fn();
      const unsubscribe = subscribeToSessionInvalidation(listener);

      fetchMock.mockResolvedValueOnce(
        errorResponse(401, "Authentication is required."),
      );

      await expect(request()).rejects.toMatchObject({
        status: 401,
      });

      expect(listener).toHaveBeenCalledOnce();
      expect(getAuthToken()).toBeNull();

      unsubscribe();
    },
  );

  it("does not invalidate the session for non-401 errors", async () => {
    setAuthToken("valid-token");

    const listener = vi.fn();
    const unsubscribe = subscribeToSessionInvalidation(listener);

    fetchMock.mockResolvedValueOnce(
      errorResponse(503, "Service temporarily unavailable."),
    );

    await expect(apiRequest("/api/dashboard")).rejects.toMatchObject({
      status: 503,
    });

    expect(listener).not.toHaveBeenCalled();
    expect(getAuthToken()).toBe("valid-token");

    unsubscribe();
  });

  it("includes the bearer token with authenticated requests", async () => {
    setAuthToken("valid-token");

    fetchMock.mockResolvedValueOnce(
      successResponse({
        income: 100,
        expenses: 50,
      }),
    );

    await apiRequest("/api/dashboard");

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/dashboard"),
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer valid-token",
        }),
      }),
    );
  });
});

describe("public API requests", () => {
  it("keeps an invalid login response as an ordinary form error", async () => {
    setAuthToken("existing-token");

    const listener = vi.fn();
    const unsubscribe = subscribeToSessionInvalidation(listener);

    fetchMock.mockResolvedValueOnce(
      errorResponse(401, "Invalid email or password."),
    );

    await expect(
      login({
        email: "wrong@example.com",
        password: "wrong-password",
      }),
    ).rejects.toMatchObject({
      status: 401,
      message: "Invalid email or password.",
    });

    expect(listener).not.toHaveBeenCalled();
    expect(getAuthToken()).toBe("existing-token");

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/auth/login"),
      expect.objectContaining({
        headers: expect.not.objectContaining({
          Authorization: expect.anything(),
        }),
      }),
    );

    unsubscribe();
  });
});

describe("API response handling", () => {
  it("returns field errors from a validation response", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 400,
      json: vi.fn().mockResolvedValue({
        fields: {
          email: "Email must be valid.",
        },
      }),
    } as unknown as Response);

    await expect(
      apiRequest("/api/auth/register", { authenticated: false }),
    ).rejects.toMatchObject({
      status: 400,
      message: "Validation failed.",
      validationErrors: {
        email: "Email must be valid.",
      },
    });
  });

  it("uses a fallback message when an error response omits one", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: vi.fn().mockResolvedValue({}),
    } as unknown as Response);

    await expect(apiRequest("/api/dashboard")).rejects.toMatchObject({
      status: 500,
      message: "An unexpected error occurred.",
    });
  });

  it("returns undefined for a successful no-content response", async () => {
    const response = noContentResponse();
    fetchMock.mockResolvedValueOnce(response);

    await expect(apiRequest("/api/transactions/1")).resolves.toBeUndefined();
    expect(response.json).not.toHaveBeenCalled();
  });
});

describe("session invalidation", () => {
  it("removes the stored token", () => {
    setAuthToken("expired-token");

    invalidateAuthSession();

    expect(getAuthToken()).toBeNull();
  });
});
