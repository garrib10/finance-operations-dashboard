import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { API_BASE_URL } from "./apiConfig";
import { getCurrentUser, login, logoutSession, refreshSession, register } from "./authService";
import { clearAccessToken, setAccessToken } from "../utils/authToken";

const fetchMock = vi.fn();

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { "Content-Type": "application/json" },
});

const tokenResponse = { accessToken: "access-token", tokenType: "Bearer", expiresIn: 300 };

const user = {
  id: 1,
  firstName: "Demo",
  lastName: "User",
  displayName: "Demo User",
  preferences: { dateFormat: "MEDIUM" as const, transactionPageSize: 10 as const },
  email: "demo@fintrack.dev",
  createdAt: "2026-09-22T00:00:00",
  profilePhotoUrl: null,
};

function lastInit(): RequestInit {
  return fetchMock.mock.calls.at(-1)?.[1] as RequestInit;
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  clearAccessToken();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("authService", () => {
  it("logs in with credentials, the CSRF header, and JSON only", async () => {
    setAccessToken("stale-token");
    const request = { email: "demo@fintrack.dev", password: "Password123!" };
    fetchMock.mockResolvedValue(json(tokenResponse));

    await expect(login(request)).resolves.toEqual(tokenResponse);

    expect(fetchMock).toHaveBeenCalledExactlyOnceWith(`${API_BASE_URL}/api/auth/login`, {
      method: "POST",
      body: JSON.stringify(request),
      credentials: "include",
      headers: { "Content-Type": "application/json", "X-FinTrack-CSRF": "1" },
    });
  });

  it.each([401, 403, 503])("never refreshes or retries a failed login (%s)", async (status) => {
    fetchMock.mockResolvedValue(json({ message: "Invalid email or password", code: "ACCESS_TOKEN_EXPIRED" }, status));

    await expect(login({ email: "a@b.c", password: "x" })).rejects.toMatchObject({ status });

    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("refreshes with an empty credentialed POST and never sends a token", async () => {
    setAccessToken("expired-access-token");
    fetchMock.mockResolvedValue(json(tokenResponse));

    await expect(refreshSession()).resolves.toEqual(tokenResponse);

    expect(fetchMock).toHaveBeenCalledExactlyOnceWith(`${API_BASE_URL}/api/auth/refresh`, {
      method: "POST",
      credentials: "include",
      headers: { "X-FinTrack-CSRF": "1" },
    });
    expect(JSON.stringify(lastInit())).not.toMatch(/expired-access-token|Authorization|refreshToken|Cookie/);
  });

  it("does not recursively refresh when refresh itself fails", async () => {
    fetchMock.mockResolvedValue(json({ message: "Your session has expired. Please sign in again.", code: "SESSION_EXPIRED" }, 401));

    await expect(refreshSession()).rejects.toMatchObject({ status: 401, code: "SESSION_EXPIRED" });

    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("logs out with a credentialed POST and accepts 204", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(logoutSession()).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledExactlyOnceWith(`${API_BASE_URL}/api/auth/logout`, {
      method: "POST",
      credentials: "include",
      headers: { "X-FinTrack-CSRF": "1" },
    });
  });

  it("reports an unconfirmed logout without retrying it", async () => {
    fetchMock.mockResolvedValue(new Response("<html>Bad Gateway</html>", { status: 503 }));

    await expect(logoutSession()).rejects.toMatchObject({ status: 503, message: "An unexpected error occurred." });

    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("submits registration as a public request that is never refreshed", async () => {
    const request = { firstName: "Demo", lastName: "User", email: "demo@fintrack.dev", password: "Password123!" };
    fetchMock.mockResolvedValue(json({ message: "expired", code: "ACCESS_TOKEN_EXPIRED" }, 401));

    await expect(register(request)).rejects.toMatchObject({ status: 401 });

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(lastInit()).toMatchObject({ method: "POST", body: JSON.stringify(request) });
    expect(lastInit().headers).not.toHaveProperty("Authorization");
    expect(lastInit()).not.toHaveProperty("credentials");
  });

  it("loads the current user with the in-memory bearer token", async () => {
    setAccessToken("access-token");
    fetchMock.mockResolvedValue(json(user));

    await expect(getCurrentUser()).resolves.toEqual(user);

    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE_URL}/api/auth/me`, expect.objectContaining({
      headers: expect.objectContaining({ Authorization: "Bearer access-token" }),
    }));
  });
});
