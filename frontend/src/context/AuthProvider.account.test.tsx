import { act, render, renderHook, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import userEvent from "@testing-library/user-event";
import AppHeader from "../components/AppHeader";
import { AuthProvider } from "./AuthProvider";
import { useAuth } from "./AuthContext";
import * as authService from "../services/authService";
import * as accountService from "../services/accountService";
import { ApiError } from "../services/api";
import { invalidateAuthSession } from "../services/authSession";
import { getAuthToken, setAuthToken } from "../utils/authToken";
import { accountUser, deferred } from "../test/accountFixtures";
import type { LoginResponse, UserResponse } from "../types/auth";

vi.mock("../services/authService");
vi.mock("../services/accountService");
const getUser = vi.mocked(authService.getCurrentUser);
const login = vi.mocked(authService.login);
const profile = vi.mocked(accountService.updateProfile);
const preferences = vi.mocked(accountService.updatePreferences);
const profileRequest = { firstName: "New", lastName: "Name", displayName: "New Identity" };
const savedUser = { ...accountUser, ...profileRequest };

beforeEach(() => {
  vi.resetAllMocks();
  window.localStorage.clear();
  setAuthToken("original-token");
  getUser.mockResolvedValue(accountUser);
});

async function ready() {
  const hook = renderHook(() => useAuth(), { wrapper: AuthProvider });
  await waitFor(() => expect(hook.result.current.user).toEqual(accountUser));
  return hook;
}

describe("AuthProvider account synchronization", () => {
  it("applies canonical profile responses immediately without reloading or changing the token", async () => {
    const { result } = await ready();
    const pending = deferred<UserResponse>();
    profile.mockReturnValue(pending.promise);
    let request!: Promise<UserResponse>;
    act(() => { request = result.current.updateProfile(profileRequest); });
    expect(result.current.isLoading).toBe(false);
    expect(result.current.user).toEqual(accountUser);
    await act(async () => { pending.resolve(savedUser); expect(await request).toEqual(savedUser); });
    expect(result.current.user).toEqual(savedUser);
    expect(profile).toHaveBeenCalledWith(profileRequest);
    expect(getUser).toHaveBeenCalledTimes(1);
    expect(getAuthToken()).toBe("original-token");
    expect(result.current.isLoading).toBe(false);
  });

  it("applies canonical preferences and returns the updated user", async () => {
    const { result } = await ready();
    const next = { ...accountUser, preferences: { dateFormat: "ISO" as const, transactionPageSize: 50 as const } };
    preferences.mockResolvedValue(next);
    await act(async () => { expect(await result.current.updatePreferences(next.preferences)).toEqual(next); });
    expect(result.current.user).toEqual(next);
    expect(preferences).toHaveBeenCalledWith(next.preferences);
    expect(getUser).toHaveBeenCalledTimes(1);
    expect(getAuthToken()).toBe("original-token");
  });

  it.each(["updateProfile", "updatePreferences"] as const)("preserves the user and propagates failed %s calls", async (action) => {
    const { result } = await ready();
    const error = new ApiError("Validation failed", 400, { displayName: "Required" });
    profile.mockRejectedValue(error);
    preferences.mockRejectedValue(error);
    await act(async () => {
      const request = action === "updateProfile" ? result.current.updateProfile(profileRequest) : result.current.updatePreferences(accountUser.preferences);
      await expect(request).rejects.toBe(error);
    });
    expect(result.current.user).toEqual(accountUser);
    expect(result.current.isLoading).toBe(false);
    expect(getAuthToken()).toBe("original-token");
  });

  it.each(["logout", "expiration"])("cannot restore a user after %s", async (event) => {
    const { result } = await ready();
    const pending = deferred<UserResponse>();
    profile.mockReturnValue(pending.promise);
    let request!: Promise<UserResponse>;
    act(() => { request = result.current.updateProfile(profileRequest); });
    act(() => { if (event === "logout") result.current.logout(); else invalidateAuthSession(); });
    await act(async () => { pending.resolve(savedUser); await request; });
    expect(result.current.user).toBeNull();
    expect(getAuthToken()).toBeNull();
  });

  it("does not replace another account with a late update", async () => {
    const { result } = await ready();
    const pending = deferred<UserResponse>();
    preferences.mockReturnValue(pending.promise);
    let request!: Promise<UserResponse>;
    act(() => { request = result.current.updatePreferences(accountUser.preferences); });
    const other = { ...accountUser, id: 2, displayName: "Other Account", email: "other@example.com" };
    login.mockResolvedValue({ accessToken: "other-token", tokenType: "Bearer", expiresIn: 3600 });
    getUser.mockResolvedValue(other);
    await act(async () => { await result.current.login({ email: other.email, password: "password" }); });
    await act(async () => { pending.resolve(savedUser); await request; });
    expect(result.current.user).toEqual(other);
    expect(getAuthToken()).toBe("other-token");
  });

  it.each(["resolve", "reject"])("ignores an older restoration that later %ss after a save", async (completion) => {
    const { result } = await ready();
    const pending = deferred<UserResponse>();
    getUser.mockReturnValue(pending.promise);
    let restore!: Promise<void>;
    act(() => { restore = result.current.retrySessionRestore(); });
    profile.mockResolvedValue(savedUser);
    await act(async () => { await result.current.updateProfile(profileRequest); });
    await act(async () => {
      if (completion === "resolve") pending.resolve(accountUser); else pending.reject(new Error("Unavailable"));
      await restore;
    });
    expect(result.current.user).toEqual(savedUser);
    expect(result.current.restorationError).toBeNull();
    expect(result.current.isLoading).toBe(false);
  });

  it("ignores an initial restoration response after logout", async () => {
    const pending = deferred<UserResponse>();
    getUser.mockReturnValue(pending.promise);
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(getUser).toHaveBeenCalledOnce());
    act(() => result.current.logout());
    await act(async () => pending.resolve(accountUser));
    expect(result.current.user).toBeNull();
    expect(result.current.isLoading).toBe(false);
  });

  it("cannot store a late login token after logout", async () => {
    const { result } = await ready();
    const pending = deferred<LoginResponse>();
    login.mockReturnValue(pending.promise);
    let request!: Promise<void>;
    act(() => { request = result.current.login({ email: "a@example.com", password: "password" }); });
    act(() => result.current.logout());
    await act(async () => { pending.resolve({ accessToken: "stale", tokenType: "Bearer", expiresIn: 3600 }); await request; });
    expect(getAuthToken()).toBeNull();
    expect(result.current.user).toBeNull();
  });

  it("treats a restoration 401 as signed out even without an invalidation event", async () => {
    getUser.mockRejectedValueOnce(new ApiError("Unauthorized", 401));
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.user).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.restorationError).toBeNull();
  });

  it.each(["resolve", "reject"] as const)(
    "preserves the newer account when an older login user lookup later %ss",
    async (completion) => {
      const { result } = await ready();
      const pendingUser = deferred<UserResponse>();
      const otherUser = {
        ...accountUser,
        id: 2,
        email: "other@example.com",
        displayName: "Other Account",
      };
      login
        .mockResolvedValueOnce({ accessToken: "first-token", tokenType: "Bearer", expiresIn: 3600 })
        .mockResolvedValueOnce({ accessToken: "other-token", tokenType: "Bearer", expiresIn: 3600 });
      getUser
        .mockReturnValueOnce(pendingUser.promise)
        .mockResolvedValueOnce(otherUser);

      let firstLogin!: Promise<void>;
      act(() => {
        firstLogin = result.current.login({ email: accountUser.email, password: "password" });
      });
      await waitFor(() => expect(getUser).toHaveBeenCalledTimes(2));

      await act(async () => {
        await result.current.login({ email: otherUser.email, password: "password" });
      });
      expect(result.current.user).toEqual(otherUser);

      await act(async () => {
        if (completion === "resolve") {
          pendingUser.resolve(accountUser);
          await firstLogin;
        } else {
          const error = new TypeError("Old login lookup failed");
          pendingUser.reject(error);
          await expect(firstLogin).rejects.toBe(error);
        }
      });

      expect(result.current.user).toEqual(otherUser);
      expect(result.current.isAuthenticated).toBe(true);
      expect(result.current.restorationError).toBeNull();
      expect(getAuthToken()).toBe("other-token");
    },
  );

  it("rejects updates when signed out", async () => {
    window.localStorage.clear();
    const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    await expect(result.current.updateProfile(profileRequest)).rejects.toThrow("Sign in");
    expect(profile).not.toHaveBeenCalled();
  });
});

function SaveProfileProbe() {
  const { updateProfile } = useAuth();
  return <button onClick={() => void updateProfile(profileRequest)}>Save test profile</button>;
}

it("updates the real AppHeader immediately after a profile save", async () => {
  profile.mockResolvedValue(savedUser);
  render(<MemoryRouter><AuthProvider><AppHeader /><SaveProfileProbe /></AuthProvider></MemoryRouter>);
  await screen.findByRole("button", { name: "Open account menu for River Walker" });
  await userEvent.click(screen.getByRole("button", { name: "Save test profile" }));
  expect(await screen.findByRole("button", { name: "Open account menu for New Identity" })).toHaveTextContent("NI");
  expect(getUser).toHaveBeenCalledOnce();
});
