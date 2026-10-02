import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest, ApiError } from "./api";
import {
  getSessionGeneration,
  invalidateAuthSession,
  subscribeToSessionInvalidation,
  type SessionEndEvent,
} from "./authSession";
import * as authService from "./authService";
import { getBudgets } from "./budgetService";
import { getDashboard } from "./dashboardService";
import { login, register } from "./authService";
import { getTransactions } from "./transactionService";
import { clearAccessToken, getAccessToken, setAccessToken } from "../utils/authToken";

const fetchMock = vi.fn();

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function errorResponse(status: number, message: string, code?: string): Response {
  return json({
    timestamp: "2026-09-21T00:00:00Z",
    status,
    error: status === 401 ? "Unauthorized" : "Request failed",
    message,
    ...(code ? { code } : {}),
  }, status);
}

const expired = () => errorResponse(401, "Access token has expired", "ACCESS_TOKEN_EXPIRED");
const tokens = (accessToken: string) => ({ accessToken, tokenType: "Bearer", expiresIn: 300 });

function bearerOf(call: number): string | undefined {
  const init = fetchMock.mock.calls[call]?.[1] as RequestInit;
  return (init.headers as Record<string, string>).Authorization;
}

let refreshSession: ReturnType<typeof vi.spyOn>;
let events: SessionEndEvent[];
let unsubscribe: () => void;

beforeEach(() => {
  window.localStorage.clear();
  clearAccessToken();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  refreshSession = vi.spyOn(authService, "refreshSession");
  events = [];
  unsubscribe = subscribeToSessionInvalidation((event) => events.push(event));
});

afterEach(() => {
  unsubscribe();
  refreshSession.mockRestore();
  vi.unstubAllGlobals();
});

describe("authenticated API requests", () => {
  const authenticatedRequests: Array<[string, () => Promise<unknown>]> = [
    ["dashboard", () => getDashboard()],
    ["transactions", () => getTransactions()],
    ["budgets", () => getBudgets()],
  ];

  it.each(authenticatedRequests)(
    "invalidates the session when the %s request returns a generic 401",
    async (_name, request) => {
      setAccessToken("rejected-token");
      fetchMock.mockResolvedValueOnce(errorResponse(401, "Authentication is required.", "AUTHENTICATION_REQUIRED"));

      await expect(request()).rejects.toMatchObject({ status: 401 });

      expect(events).toEqual([{ reason: "INVALID", origin: "local" }]);
      expect(getAccessToken()).toBeNull();
      expect(refreshSession).not.toHaveBeenCalled();
    },
  );

  it("does not invalidate a newer session when an old request returns 401", async () => {
    setAccessToken("old-token");
    let finish!: (response: Response) => void;
    fetchMock.mockReturnValue(new Promise<Response>((resolve) => { finish = resolve; }));
    const request = apiRequest("/api/account/profile");
    setAccessToken("new-token");
    finish(errorResponse(401, "Rejected"));
    await expect(request).rejects.toMatchObject({ status: 401 });
    expect(getAccessToken()).toBe("new-token");
    expect(events).toEqual([]);
  });

  it.each([403, 409, 429, 500, 503])("does not invalidate or refresh for a %s error", async (status) => {
    setAccessToken("valid-token");
    fetchMock.mockResolvedValueOnce(errorResponse(status, "Request failed.", "ACCESS_TOKEN_EXPIRED"));

    await expect(apiRequest("/api/dashboard")).rejects.toMatchObject({ status });

    expect(events).toEqual([]);
    expect(refreshSession).not.toHaveBeenCalled();
    expect(getAccessToken()).toBe("valid-token");
  });

  it("does not refresh on a network failure", async () => {
    setAccessToken("valid-token");
    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));

    await expect(apiRequest("/api/dashboard")).rejects.toBeInstanceOf(TypeError);

    expect(refreshSession).not.toHaveBeenCalled();
    expect(getAccessToken()).toBe("valid-token");
  });

  it("includes the bearer token with authenticated requests", async () => {
    setAccessToken("valid-token");
    fetchMock.mockResolvedValueOnce(json({ income: 100, expenses: 50 }));

    await apiRequest("/api/dashboard");

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/dashboard"),
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer valid-token" }),
      }),
    );
    expect(fetchMock.mock.calls[0][1]).not.toHaveProperty("credentials");
  });
});

describe("expired access tokens", () => {
  it("refreshes once and retries the original request once with the new token", async () => {
    setAccessToken("expired-token");
    refreshSession.mockResolvedValue(tokens("fresh-token"));
    fetchMock.mockResolvedValueOnce(expired()).mockResolvedValueOnce(json({ ok: true }));

    await expect(apiRequest("/api/dashboard")).resolves.toEqual({ ok: true });

    expect(refreshSession).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(bearerOf(0)).toBe("Bearer expired-token");
    expect(bearerOf(1)).toBe("Bearer fresh-token");
    expect(getAccessToken()).toBe("fresh-token");
    expect(events).toEqual([]);
  });

  it("shares one refresh among concurrent expired requests in this tab", async () => {
    setAccessToken("expired-token");
    let finishRefresh!: (value: ReturnType<typeof tokens>) => void;
    refreshSession.mockReturnValue(new Promise((resolve) => { finishRefresh = resolve; }));
    fetchMock.mockImplementation(async (_url: string, init: RequestInit) => {
      const auth = (init.headers as Record<string, string>).Authorization;
      return auth === "Bearer fresh-token" ? json({ ok: true }) : expired();
    });

    const requests = [apiRequest("/api/dashboard"), apiRequest("/api/budgets"), apiRequest("/api/transactions")];
    await vi.waitFor(() => expect(refreshSession).toHaveBeenCalledOnce());
    finishRefresh(tokens("fresh-token"));

    await expect(Promise.all(requests)).resolves.toEqual([{ ok: true }, { ok: true }, { ok: true }]);
    expect(refreshSession).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledTimes(6);
  });

  it("reuses a newer token already installed instead of rotating again", async () => {
    setAccessToken("old-token");
    let finish!: (response: Response) => void;
    fetchMock
      .mockReturnValueOnce(new Promise<Response>((resolve) => { finish = resolve; }))
      .mockResolvedValueOnce(json({ ok: true }));

    const request = apiRequest("/api/dashboard");
    setAccessToken("newer-token");
    finish(expired());

    await expect(request).resolves.toEqual({ ok: true });
    expect(refreshSession).not.toHaveBeenCalled();
    expect(bearerOf(1)).toBe("Bearer newer-token");
  });

  it("does not refresh again when the retry is also rejected", async () => {
    setAccessToken("expired-token");
    refreshSession.mockResolvedValue(tokens("fresh-token"));
    fetchMock.mockResolvedValueOnce(expired()).mockResolvedValueOnce(expired());

    await expect(apiRequest("/api/dashboard")).rejects.toMatchObject({ status: 401, code: "ACCESS_TOKEN_EXPIRED" });

    expect(refreshSession).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(getAccessToken()).toBeNull();
    expect(events).toEqual([{ reason: "INVALID", origin: "local" }]);
  });

  it("keeps a newer session when a retry fails after another login", async () => {
    setAccessToken("expired-token");
    refreshSession.mockResolvedValue(tokens("fresh-token"));
    let finishRetry!: (response: Response) => void;
    fetchMock.mockResolvedValueOnce(expired())
      .mockReturnValueOnce(new Promise<Response>((resolve) => { finishRetry = resolve; }));

    const request = apiRequest("/api/dashboard");
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    setAccessToken("newest-token");
    finishRetry(expired());

    await expect(request).rejects.toMatchObject({ status: 401 });
    expect(getAccessToken()).toBe("newest-token");
  });

  it.each([
    ["generic unauthorized", "AUTHENTICATION_REQUIRED"],
    ["message-only expiry", undefined],
  ])("does not refresh for a %s 401", async (_name, code) => {
    setAccessToken("token");
    fetchMock.mockResolvedValueOnce(errorResponse(401, "Access token has expired", code));

    await expect(apiRequest("/api/dashboard")).rejects.toMatchObject({ status: 401 });

    expect(refreshSession).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("does not refresh when no access token was sent", async () => {
    fetchMock.mockResolvedValueOnce(expired());

    await expect(apiRequest("/api/dashboard")).rejects.toMatchObject({ status: 401 });

    expect(refreshSession).not.toHaveBeenCalled();
  });

  it("does not refresh a request that opts out of retry", async () => {
    setAccessToken("token");
    fetchMock.mockResolvedValueOnce(expired());

    await expect(apiRequest("/api/dashboard", { retryOnExpiredToken: false })).rejects.toMatchObject({ status: 401 });

    expect(refreshSession).not.toHaveBeenCalled();
    // An expired token is not an invalid session: nothing is signed out.
    expect(events).toEqual([]);
    expect(getAccessToken()).toBe("token");
  });

  it("does not replay a streamed body that the first send consumed", async () => {
    setAccessToken("token");
    fetchMock.mockResolvedValueOnce(expired());
    const body = new ReadableStream({ start(controller) { controller.close(); } });

    await expect(apiRequest("/api/transactions", { method: "POST", body })).rejects.toMatchObject({ status: 401 });

    expect(refreshSession).not.toHaveBeenCalled();
    // An expired token is not an invalid session: nothing is signed out.
    expect(events).toEqual([]);
    expect(getAccessToken()).toBe("token");
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("does not refresh an aborted request", async () => {
    setAccessToken("token");
    const controller = new AbortController();
    fetchMock.mockImplementationOnce(async () => {
      controller.abort();
      return expired();
    });

    await expect(apiRequest("/api/dashboard", { signal: controller.signal })).rejects.toMatchObject({ status: 401 });

    expect(refreshSession).not.toHaveBeenCalled();
    // An expired token is not an invalid session: nothing is signed out.
    expect(events).toEqual([]);
    expect(getAccessToken()).toBe("token");
  });

  it("replays the same JSON mutation body and signal once", async () => {
    setAccessToken("expired-token");
    refreshSession.mockResolvedValue(tokens("fresh-token"));
    fetchMock.mockResolvedValueOnce(expired()).mockResolvedValueOnce(json({ id: 7 }, 201));
    const controller = new AbortController();
    const body = JSON.stringify({ amount: 12.5, description: "Lunch" });

    await expect(apiRequest("/api/transactions", { method: "POST", body, signal: controller.signal }))
      .resolves.toEqual({ id: 7 });

    const [first, retry] = fetchMock.mock.calls.map(call => call[1] as RequestInit);
    expect(retry).toMatchObject({ method: "POST", body, signal: controller.signal });
    expect(retry.body).toBe(first.body);
    expect(retry.headers).toEqual({ "Content-Type": "application/json", Authorization: "Bearer fresh-token" });
  });

  it("ends the session everywhere when refresh reports it expired", async () => {
    setAccessToken("expired-token");
    refreshSession.mockRejectedValue(new ApiError("Your session has expired. Please sign in again.", 401, undefined, "SESSION_EXPIRED"));
    fetchMock.mockResolvedValueOnce(expired());

    await expect(apiRequest("/api/dashboard")).rejects.toMatchObject({
      status: 401, code: "SESSION_EXPIRED", message: "Your session has expired. Please sign in again.",
    });

    expect(events).toEqual([{ reason: "SESSION_TERMINATED", origin: "local" }]);
    expect(getAccessToken()).toBeNull();
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it.each([
    ["a 503", new ApiError("Sign-in is temporarily unavailable. Please try again.", 503, undefined, "SESSION_UNAVAILABLE")],
    ["a network failure", new TypeError("Failed to fetch")],
    ["a 403 from request protection", new ApiError("This request could not be verified", 403, undefined, "REQUEST_FORBIDDEN")],
  ])("reports a temporary failure without claiming the session ended for %s", async (_name, failure) => {
    setAccessToken("expired-token");
    refreshSession.mockRejectedValue(failure);
    fetchMock.mockResolvedValueOnce(expired());

    await expect(apiRequest("/api/dashboard")).rejects.toBe(failure);

    expect(events).toEqual([{ reason: "TEMPORARY_FAILURE", origin: "local" }]);
    expect(getAccessToken()).toBeNull();
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("clears the shared refresh after failure so a later refresh can run", async () => {
    setAccessToken("expired-token");
    refreshSession.mockRejectedValueOnce(new TypeError("offline")).mockResolvedValueOnce(tokens("fresh-token"));
    fetchMock.mockResolvedValueOnce(expired());
    await expect(apiRequest("/api/dashboard")).rejects.toBeInstanceOf(TypeError);

    setAccessToken("expired-again");
    fetchMock.mockResolvedValueOnce(expired()).mockResolvedValueOnce(json({ ok: true }));
    await expect(apiRequest("/api/dashboard")).resolves.toEqual({ ok: true });

    expect(refreshSession).toHaveBeenCalledTimes(2);
  });

  it("discards a refresh that finishes after the session changed", async () => {
    setAccessToken("expired-token");
    let finishRefresh!: (value: ReturnType<typeof tokens>) => void;
    refreshSession.mockReturnValue(new Promise((resolve) => { finishRefresh = resolve; }));
    fetchMock.mockResolvedValueOnce(expired());

    const request = apiRequest("/api/dashboard");
    await vi.waitFor(() => expect(refreshSession).toHaveBeenCalledOnce());
    const generation = getSessionGeneration();
    invalidateAuthSession("LOGOUT");
    finishRefresh(tokens("late-token"));

    await expect(request).rejects.toMatchObject({ code: "SESSION_SUPERSEDED" });
    expect(getSessionGeneration()).toBe(generation + 1);
    expect(getAccessToken()).toBeNull();
    expect(events).toEqual([{ reason: "LOGOUT", origin: "local" }]);
  });
});

describe("public API requests", () => {
  it("keeps an invalid login response as an ordinary form error", async () => {
    setAccessToken("existing-token");
    fetchMock.mockResolvedValueOnce(errorResponse(401, "Invalid email or password."));

    await expect(login({ email: "wrong@example.com", password: "wrong-password" })).rejects.toMatchObject({
      status: 401,
      message: "Invalid email or password.",
    });

    expect(events).toEqual([]);
    expect(getAccessToken()).toBe("existing-token");
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/auth/login"),
      expect.objectContaining({
        headers: expect.not.objectContaining({ Authorization: expect.anything() }),
      }),
    );
  });

  it("never refreshes a public registration request", async () => {
    setAccessToken("token");
    fetchMock.mockResolvedValueOnce(expired());

    await expect(register({ firstName: "A", lastName: "B", email: "a@b.c", password: "x" }))
      .rejects.toMatchObject({ status: 401 });

    expect(refreshSession).not.toHaveBeenCalled();
    expect(events).toEqual([]);
    expect(getAccessToken()).toBe("token");
  });
});

describe("API response handling", () => {
  it("returns field errors from a validation response", async () => {
    fetchMock.mockResolvedValueOnce(json({ fields: { email: "Email must be valid." } }, 400));

    await expect(apiRequest("/api/auth/register", { authenticated: false })).rejects.toMatchObject({
      status: 400,
      message: "Validation failed.",
      validationErrors: { email: "Email must be valid." },
    });
  });

  it("keeps nested category field keys and stable codes from financial validation errors", async () => {
    fetchMock.mockResolvedValueOnce(json({
      timestamp: "2026-10-01T10:00:00", status: 400, error: "Validation Failed",
      fields: {
        amount: "Amount can have at most 10 whole digits and 2 decimal places",
        "newCategory.name": "Category name is required",
        "newCategory.iconKey": "Icon must be one of the approved category icons",
      },
    }, 400));
    fetchMock.mockResolvedValueOnce(json({
      status: 409, error: "Conflict", message: "Category already exists", code: "CATEGORY_DUPLICATE",
    }, 409));

    await expect(apiRequest("/api/transactions", { method: "POST", body: "{}" })).rejects.toMatchObject({
      status: 400,
      validationErrors: {
        amount: "Amount can have at most 10 whole digits and 2 decimal places",
        "newCategory.name": "Category name is required",
        "newCategory.iconKey": "Icon must be one of the approved category icons",
      },
    });
    await expect(apiRequest("/api/budgets", { method: "POST", body: "{}" })).rejects.toMatchObject({
      status: 409, message: "Category already exists", code: "CATEGORY_DUPLICATE",
    });
  });

  it("uses a fallback message when an error response omits one", async () => {
    fetchMock.mockResolvedValueOnce(json({}, 500));

    await expect(apiRequest("/api/dashboard")).rejects.toMatchObject({
      status: 500,
      message: "An unexpected error occurred.",
    });
  });

  it.each([
    ["an HTML error page", new Response("<html><body>502 Bad Gateway</body></html>", { status: 502 })],
    ["plain text", new Response("upstream timeout", { status: 504 })],
    ["an empty body", new Response(null, { status: 500 })],
    ["malformed JSON", new Response("{\"message\":", { status: 500, headers: { "Content-Type": "application/json" } })],
    ["a non-object JSON body", json("just a string", 500)],
  ])("never surfaces %s as a message", async (_name, response) => {
    fetchMock.mockResolvedValueOnce(response);

    const error = await apiRequest("/api/dashboard", { authenticated: false }).catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).message).toBe("An unexpected error occurred.");
  });

  it("returns undefined for a successful no-content response", async () => {
    const response = new Response(null, { status: 204 });
    const parse = vi.spyOn(response, "json");
    fetchMock.mockResolvedValueOnce(response);

    await expect(apiRequest("/api/transactions/1")).resolves.toBeUndefined();
    expect(parse).not.toHaveBeenCalled();
  });

  it("returns undefined for a successful empty body", async () => {
    fetchMock.mockResolvedValueOnce(new Response("", { status: 200 }));

    await expect(apiRequest("/api/transactions/1")).resolves.toBeUndefined();
  });

  it("rejects a malformed success body with a safe error", async () => {
    fetchMock.mockResolvedValueOnce(new Response("<html>ok</html>", { status: 200 }));

    await expect(apiRequest("/api/dashboard")).rejects.toMatchObject({
      name: "ApiError", status: 200, message: "The server returned an unexpected response.",
    });
  });

  it("ignores a body that cannot be read", async () => {
    const response = new Response("x", { status: 500 });
    vi.spyOn(response, "text").mockRejectedValue(new TypeError("stream error"));
    fetchMock.mockResolvedValueOnce(response);

    await expect(apiRequest("/api/dashboard", { authenticated: false })).rejects.toMatchObject({
      status: 500, message: "An unexpected error occurred.",
    });
  });
});

describe("session invalidation", () => {
  it("removes the in-memory token", () => {
    setAccessToken("expired-token");

    invalidateAuthSession();

    expect(getAccessToken()).toBeNull();
  });
});
