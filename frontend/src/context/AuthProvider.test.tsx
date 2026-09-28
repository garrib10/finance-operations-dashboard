import { StrictMode, useState } from "react";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../services/api";
import * as AuthService from "../services/authService";
import { invalidateAuthSession } from "../services/authSession";
import { SESSION_CHANNEL_NAME } from "../services/sessionBroadcast";
import { clearAccessToken, getAccessToken, setAccessToken } from "../utils/authToken";
import { deferred } from "../test/accountFixtures";
import { FakeBroadcastChannel } from "../test/crossTab";
import type { LoginResponse, UserResponse } from "../types/auth";
import { useAuth } from "./AuthContext";
import { AuthProvider } from "./AuthProvider";

vi.mock("../services/authService", async () => {
  const actual = await vi.importActual<typeof AuthService>("../services/authService");

  return {
    ...actual,
    getCurrentUser: vi.fn(),
    login: vi.fn(),
    refreshSession: vi.fn(),
    logoutSession: vi.fn(),
  };
});

const mockedGetCurrentUser = vi.mocked(AuthService.getCurrentUser);
const mockedLogin = vi.mocked(AuthService.login);
const mockedRefresh = vi.mocked(AuthService.refreshSession);
const mockedLogout = vi.mocked(AuthService.logoutSession);

const RESTORATION_ERROR = "We couldn’t restore your session. Check your connection and try again.";
const sessionExpired = () => new ApiError("Your session has expired. Please sign in again.", 401, undefined, "SESSION_EXPIRED");
const tokens = (accessToken: string): LoginResponse => ({ accessToken, tokenType: "Bearer", expiresIn: 300 });

const currentUser: UserResponse = {
  id: 1,
  firstName: "Demo",
  lastName: "User",
  displayName: "Demo User",
  preferences: { dateFormat: "MEDIUM" as const, transactionPageSize: 10 as const },
  email: "demo@fintrack.dev",
  createdAt: "2026-09-09T00:00:00",
  profilePhotoUrl: null,
};

function AuthStateProbe() {
  const { user, isAuthenticated, isLoading, restorationError, sessionNotice, retrySessionRestore } = useAuth();

  if (restorationError) {
    return (
      <>
        <p role="alert">{restorationError}</p>
        <button type="button" disabled={isLoading} onClick={() => void retrySessionRestore()}>
          {isLoading ? "Retrying restoration" : "Retry restoration"}
        </button>
      </>
    );
  }

  if (isLoading) {
    return <p>Loading authentication</p>;
  }

  return (
    <>
      <p>{isAuthenticated ? "Authenticated" : "Unauthenticated"}</p>
      <p>{user?.email ?? "No user"}</p>
      {sessionNotice && <p role="status">{sessionNotice}</p>}
    </>
  );
}

function AuthActionsProbe() {
  const { user, isAuthenticated, login, logout, completePasswordChange, sessionNotice } = useAuth();
  const [loginFailed, setLoginFailed] = useState(false);
  const [logoutFailed, setLogoutFailed] = useState(false);

  async function handleLogin(): Promise<void> {
    setLoginFailed(false);
    try {
      await login({ email: "demo@fintrack.dev", password: "Password123!" });
    } catch {
      setLoginFailed(true);
    }
  }

  async function handleLogout(): Promise<void> {
    setLogoutFailed(false);
    try {
      await logout();
    } catch {
      setLogoutFailed(true);
    }
  }

  return (
    <>
      <p>{isAuthenticated ? "Authenticated" : "Unauthenticated"}</p>
      <p>{user?.email ?? "No user"}</p>
      {sessionNotice && <p role="status">{sessionNotice}</p>}
      {loginFailed && <p role="alert">Login initialization failed</p>}
      {logoutFailed && <p role="alert">Logout not confirmed</p>}
      <button type="button" onClick={() => void handleLogin()}>Login through provider</button>
      <button type="button" onClick={() => void handleLogout()}>Logout through provider</button>
      <button type="button" onClick={completePasswordChange}>Finish password change</button>
    </>
  );
}

function renderState() {
  return render(<AuthProvider><AuthStateProbe /></AuthProvider>);
}

beforeEach(() => {
  window.localStorage.clear();
  clearAccessToken();
  vi.clearAllMocks();
  FakeBroadcastChannel.reset();
  vi.stubGlobal("BroadcastChannel", FakeBroadcastChannel);
  mockedRefresh.mockRejectedValue(sessionExpired());
  mockedLogout.mockResolvedValue(undefined);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("AuthProvider restoration", () => {
  it("refreshes before loading /me, keeps the token in memory, and clears it after invalidation", async () => {
    mockedRefresh.mockResolvedValue(tokens("restored-token"));
    mockedGetCurrentUser.mockImplementation(async () => {
      expect(getAccessToken()).toBe("restored-token");
      return currentUser;
    });

    renderState();

    expect(screen.getByText("Loading authentication")).toBeInTheDocument();
    expect(await screen.findByText("demo@fintrack.dev")).toBeInTheDocument();
    expect(screen.getByText("Authenticated")).toBeInTheDocument();
    expect(mockedRefresh).toHaveBeenCalledOnce();
    expect(mockedRefresh.mock.invocationCallOrder[0]).toBeLessThan(mockedGetCurrentUser.mock.invocationCallOrder[0]);
    expect(getAccessToken()).toBe("restored-token");
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);

    act(() => {
      invalidateAuthSession();
    });

    await waitFor(() => expect(screen.getByText("Unauthenticated")).toBeInTheDocument());
    expect(screen.getByText("No user")).toBeInTheDocument();
    expect(getAccessToken()).toBeNull();
  });

  it("starts unauthenticated without an alert when there is no refresh session", async () => {
    renderState();

    expect(await screen.findByText("Unauthenticated")).toBeInTheDocument();
    expect(screen.getByText("No user")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(mockedGetCurrentUser).not.toHaveBeenCalled();
    // A page with no session tells other tabs nothing.
    expect(FakeBroadcastChannel.posted).toEqual([]);
  });

  it("removes a legacy localStorage token and never restores it", async () => {
    window.localStorage.setItem("fintrack_access_token", "legacy-token");

    renderState();

    expect(await screen.findByText("Unauthenticated")).toBeInTheDocument();
    expect(window.localStorage.getItem("fintrack_access_token")).toBeNull();
    expect(getAccessToken()).toBeNull();
    expect(JSON.stringify(mockedRefresh.mock.calls)).not.toContain("legacy-token");
  });

  it("does not restore the session after the provider unmounts", () => {
    let queuedRestore: VoidFunction | undefined;
    const queueMicrotaskSpy = vi.spyOn(globalThis, "queueMicrotask").mockImplementationOnce((callback) => {
      queuedRestore = callback;
    });

    const { unmount } = renderState();
    unmount();
    queuedRestore?.();

    expect(mockedRefresh).not.toHaveBeenCalled();
    queueMicrotaskSpy.mockRestore();
  });

  it("rotates the refresh cookie only once under React Strict Mode", async () => {
    mockedRefresh.mockResolvedValue(tokens("restored-token"));
    mockedGetCurrentUser.mockResolvedValue(currentUser);

    render(<StrictMode><AuthProvider><AuthStateProbe /></AuthProvider></StrictMode>);

    expect(await screen.findByText("demo@fintrack.dev")).toBeInTheDocument();
    expect(mockedRefresh).toHaveBeenCalledOnce();
  });

  it.each([
    ["a network failure", new TypeError("Unable to reach the server")],
    ["a 503", new ApiError("Sign-in is temporarily unavailable. Please try again.", 503, undefined, "SESSION_UNAVAILABLE")],
  ])("shows a recoverable state for %s during refresh", async (_name, failure) => {
    mockedRefresh.mockRejectedValue(failure);

    renderState();

    expect(await screen.findByRole("alert")).toHaveTextContent(RESTORATION_ERROR);
    expect(screen.getByRole("button", { name: "Retry restoration" })).toBeEnabled();
    expect(getAccessToken()).toBeNull();
    expect(mockedGetCurrentUser).not.toHaveBeenCalled();
    expect(mockedRefresh).toHaveBeenCalledOnce();
  });

  it("clears the unusable token when /me fails temporarily after refresh", async () => {
    mockedRefresh.mockResolvedValue(tokens("restored-token"));
    mockedGetCurrentUser.mockRejectedValue(new TypeError("Unable to reach the server"));

    renderState();

    expect(await screen.findByRole("alert")).toHaveTextContent(RESTORATION_ERROR);
    expect(getAccessToken()).toBeNull();
  });

  it("restores after an explicit retry, which makes exactly one new attempt", async () => {
    const user = userEvent.setup();
    mockedRefresh
      .mockRejectedValueOnce(new TypeError("Unable to reach the server"))
      .mockResolvedValueOnce(tokens("retried-token"));
    mockedGetCurrentUser.mockResolvedValue(currentUser);

    renderState();
    await screen.findByRole("alert");
    expect(mockedRefresh).toHaveBeenCalledOnce();

    await user.click(screen.getByRole("button", { name: "Retry restoration" }));

    expect(await screen.findByText("Authenticated")).toBeInTheDocument();
    expect(screen.getByText("demo@fintrack.dev")).toBeInTheDocument();
    expect(mockedRefresh).toHaveBeenCalledTimes(2);
    expect(mockedGetCurrentUser).toHaveBeenCalledOnce();
    expect(getAccessToken()).toBe("retried-token");
  });

  it("keeps the retry state visible while retrying and remains recoverable on another failure", async () => {
    const user = userEvent.setup();
    const retry = deferred<LoginResponse>();
    mockedRefresh
      .mockRejectedValueOnce(new TypeError("Initial network failure"))
      .mockReturnValueOnce(retry.promise);

    renderState();
    await screen.findByRole("alert");
    await user.click(screen.getByRole("button", { name: "Retry restoration" }));

    expect(screen.getByRole("button", { name: "Retrying restoration" })).toBeDisabled();

    await act(async () => retry.reject(new ApiError("Service unavailable.", 503)));

    expect(await screen.findByRole("alert")).toHaveTextContent(RESTORATION_ERROR);
    expect(screen.getByRole("button", { name: "Retry restoration" })).toBeEnabled();
    expect(mockedRefresh).toHaveBeenCalledTimes(2);
  });

  it("treats a terminal refresh 401 as signed out, with no retry loop", async () => {
    renderState();

    expect(await screen.findByText("Unauthenticated")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(getAccessToken()).toBeNull();
    expect(mockedRefresh).toHaveBeenCalledOnce();
  });

  it("does not let a late restoration overwrite a newer login", async () => {
    const user = userEvent.setup();
    const staleRefresh = deferred<LoginResponse>();
    const newerUser = { ...currentUser, id: 2, email: "newer@fintrack.dev" };
    mockedRefresh.mockReturnValueOnce(staleRefresh.promise);
    mockedLogin.mockResolvedValue(tokens("login-token"));
    mockedGetCurrentUser.mockResolvedValue(newerUser);

    render(<AuthProvider><AuthActionsProbe /></AuthProvider>);
    await waitFor(() => expect(mockedRefresh).toHaveBeenCalledOnce());
    await user.click(screen.getByRole("button", { name: "Login through provider" }));
    expect(await screen.findByText("newer@fintrack.dev")).toBeInTheDocument();

    await act(async () => staleRefresh.resolve(tokens("stale-restore-token")));

    expect(screen.getByText("newer@fintrack.dev")).toBeInTheDocument();
    expect(getAccessToken()).toBe("login-token");
    expect(mockedGetCurrentUser).toHaveBeenCalledOnce();
  });

  it("does not let restoration sign the user back in after logout", async () => {
    const user = userEvent.setup();
    const pendingUser = deferred<UserResponse>();
    mockedRefresh.mockResolvedValue(tokens("restored-token"));
    mockedGetCurrentUser.mockReturnValue(pendingUser.promise);

    render(<AuthProvider><AuthActionsProbe /></AuthProvider>);
    await waitFor(() => expect(mockedGetCurrentUser).toHaveBeenCalledOnce());
    await user.click(screen.getByRole("button", { name: "Logout through provider" }));
    await act(async () => pendingUser.resolve(currentUser));

    expect(screen.getByText("Unauthenticated")).toBeInTheDocument();
    expect(getAccessToken()).toBeNull();
  });
});

describe("AuthProvider login and logout", () => {
  it("logs in, loads /me, announces the account change, and logs out after server confirmation", async () => {
    const user = userEvent.setup();
    const confirmLogout = deferred<void>();
    mockedLogin.mockResolvedValue(tokens("new-access-token"));
    mockedGetCurrentUser.mockResolvedValue(currentUser);
    mockedLogout.mockReturnValue(confirmLogout.promise);

    render(<AuthProvider><AuthActionsProbe /></AuthProvider>);
    await screen.findByText("Unauthenticated");

    await user.click(screen.getByRole("button", { name: "Login through provider" }));

    expect(await screen.findByText("Authenticated")).toBeInTheDocument();
    expect(getAccessToken()).toBe("new-access-token");
    expect(mockedLogin).toHaveBeenCalledWith({ email: "demo@fintrack.dev", password: "Password123!" });
    expect(mockedGetCurrentUser).toHaveBeenCalledOnce();
    expect(FakeBroadcastChannel.posted).toEqual([expect.objectContaining({ type: "ACCOUNT_CHANGED" })]);

    await user.click(screen.getByRole("button", { name: "Logout through provider" }));
    // Still signed in until the server confirms the revocation.
    expect(screen.getByText("Authenticated")).toBeInTheDocument();
    expect(getAccessToken()).toBe("new-access-token");

    await act(async () => confirmLogout.resolve());

    expect(screen.getByText("Unauthenticated")).toBeInTheDocument();
    expect(screen.getByText("No user")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(getAccessToken()).toBeNull();
    expect(FakeBroadcastChannel.posted.at(-1)).toEqual(expect.objectContaining({ type: "LOGOUT" }));
    expect(JSON.stringify(FakeBroadcastChannel.posted)).not.toMatch(/new-access-token|demo@fintrack|Password/);
  });

  it("sends one logout request for repeated clicks", async () => {
    const user = userEvent.setup();
    const confirmLogout = deferred<void>();
    mockedLogout.mockReturnValue(confirmLogout.promise);
    render(<AuthProvider><AuthActionsProbe /></AuthProvider>);
    await screen.findByText("Unauthenticated");

    await user.click(screen.getByRole("button", { name: "Logout through provider" }));
    await user.click(screen.getByRole("button", { name: "Logout through provider" }));
    await act(async () => confirmLogout.resolve());

    expect(mockedLogout).toHaveBeenCalledOnce();
  });

  it("keeps the session when logout cannot be confirmed, and allows a retry", async () => {
    const user = userEvent.setup();
    mockedRefresh.mockResolvedValue(tokens("restored-token"));
    mockedGetCurrentUser.mockResolvedValue(currentUser);
    mockedLogout.mockRejectedValueOnce(new ApiError("Sign-in is temporarily unavailable.", 503)).mockResolvedValueOnce(undefined);

    render(<AuthProvider><AuthActionsProbe /></AuthProvider>);
    await screen.findByText("Authenticated");

    await user.click(screen.getByRole("button", { name: "Logout through provider" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Logout not confirmed");
    expect(screen.getByText("Authenticated")).toBeInTheDocument();
    expect(getAccessToken()).toBe("restored-token");
    expect(FakeBroadcastChannel.posted).toEqual([]);

    await user.click(screen.getByRole("button", { name: "Logout through provider" }));

    expect(await screen.findByText("Unauthenticated")).toBeInTheDocument();
    expect(mockedLogout).toHaveBeenCalledTimes(2);
  });

  it("removes the new token when loading the user after login fails", async () => {
    const user = userEvent.setup();
    mockedLogin.mockResolvedValue(tokens("invalid-access-token"));
    mockedGetCurrentUser.mockRejectedValue(new TypeError("Unable to load the current user"));

    render(<AuthProvider><AuthActionsProbe /></AuthProvider>);
    await screen.findByText("Unauthenticated");

    await user.click(screen.getByRole("button", { name: "Login through provider" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Login initialization failed");
    expect(screen.getByText("Unauthenticated")).toBeInTheDocument();
    expect(getAccessToken()).toBeNull();
    expect(FakeBroadcastChannel.posted).toEqual([]);
  });

  it("does not disturb other tabs when a login attempt fails", async () => {
    const user = userEvent.setup();
    mockedLogin.mockRejectedValue(new ApiError("Invalid email or password", 401));

    render(<AuthProvider><AuthActionsProbe /></AuthProvider>);
    await screen.findByText("Unauthenticated");
    await user.click(screen.getByRole("button", { name: "Login through provider" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Login initialization failed");
    expect(FakeBroadcastChannel.posted).toEqual([]);
    expect(mockedRefresh).toHaveBeenCalledOnce();
  });
});

describe("AuthProvider password change and session termination", () => {
  it("signs out after a password change without refresh or logout, and tells other tabs", async () => {
    const user = userEvent.setup();
    mockedRefresh.mockResolvedValue(tokens("restored-token"));
    mockedGetCurrentUser.mockResolvedValue(currentUser);

    render(<AuthProvider><AuthActionsProbe /></AuthProvider>);
    await screen.findByText("Authenticated");

    await user.click(screen.getByRole("button", { name: "Finish password change" }));

    expect(screen.getByText("Unauthenticated")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Your password was changed. Please sign in again.");
    expect(getAccessToken()).toBeNull();
    expect(mockedRefresh).toHaveBeenCalledOnce();
    expect(mockedLogout).not.toHaveBeenCalled();
    expect(FakeBroadcastChannel.posted).toEqual([expect.objectContaining({ type: "PASSWORD_CHANGED" })]);
  });

  it("shows the expired-session notice when a request's refresh ends the session", async () => {
    mockedRefresh.mockResolvedValue(tokens("restored-token"));
    mockedGetCurrentUser.mockResolvedValue(currentUser);
    renderState();
    await screen.findByText("Authenticated");

    act(() => invalidateAuthSession("SESSION_TERMINATED"));

    expect(screen.getByText("Unauthenticated")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Your session has expired. Please sign in again.");
    expect(FakeBroadcastChannel.posted).toEqual([expect.objectContaining({ type: "SESSION_TERMINATED" })]);
  });

  it("shows the recoverable state when a request's refresh fails temporarily", async () => {
    mockedRefresh.mockResolvedValue(tokens("restored-token"));
    mockedGetCurrentUser.mockResolvedValue(currentUser);
    renderState();
    await screen.findByText("Authenticated");

    act(() => invalidateAuthSession("TEMPORARY_FAILURE"));

    expect(screen.getByRole("alert")).toHaveTextContent(RESTORATION_ERROR);
    expect(getAccessToken()).toBeNull();
    expect(FakeBroadcastChannel.posted).toEqual([]);
  });

  it("clears the login notice after a successful login", async () => {
    const user = userEvent.setup();
    mockedLogin.mockResolvedValue(tokens("next-token"));
    mockedGetCurrentUser.mockResolvedValue(currentUser);
    render(<AuthProvider><AuthActionsProbe /></AuthProvider>);
    await screen.findByText("Unauthenticated");
    act(() => invalidateAuthSession("SESSION_TERMINATED"));
    expect(screen.getByRole("status")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Login through provider" }));

    expect(await screen.findByText("Authenticated")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});

describe("AuthProvider cross-tab notifications", () => {
  async function signedIn() {
    mockedRefresh.mockResolvedValue(tokens("tab-token"));
    mockedGetCurrentUser.mockResolvedValue(currentUser);
    const view = renderState();
    await screen.findByText("Authenticated");
    return view;
  }

  it.each([
    ["LOGOUT", "You were signed out in another tab."],
    ["PASSWORD_CHANGED", "Your password was changed. Please sign in again."],
    ["SESSION_TERMINATED", "Your session has expired. Please sign in again."],
  ])("clears this tab when another tab reports %s", async (type, notice) => {
    await signedIn();

    act(() => FakeBroadcastChannel.deliverFromAnotherTab({ type, sender: "other-tab" }));

    expect(screen.getByText("Unauthenticated")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(notice);
    expect(getAccessToken()).toBeNull();
    // Received events are never echoed back to other tabs.
    expect(FakeBroadcastChannel.posted).toEqual([]);
  });

  it("restores deliberately with its own token when another tab changes account", async () => {
    await signedIn();
    const otherUser = { ...currentUser, id: 9, email: "other@fintrack.dev" };
    mockedRefresh.mockResolvedValue(tokens("own-new-token"));
    mockedGetCurrentUser.mockResolvedValue(otherUser);

    act(() => FakeBroadcastChannel.deliverFromAnotherTab({ type: "ACCOUNT_CHANGED", sender: "other-tab" }));

    expect(await screen.findByText("other@fintrack.dev")).toBeInTheDocument();
    expect(getAccessToken()).toBe("own-new-token");
    expect(mockedRefresh).toHaveBeenCalledTimes(2);
  });

  it("ignores a late response from the previous account after an account change", async () => {
    const pendingUser = deferred<UserResponse>();
    mockedRefresh.mockResolvedValueOnce(tokens("first-token"));
    mockedGetCurrentUser.mockReturnValueOnce(pendingUser.promise);
    renderState();
    await waitFor(() => expect(mockedGetCurrentUser).toHaveBeenCalledOnce());
    const otherUser = { ...currentUser, id: 9, email: "other@fintrack.dev" };
    mockedRefresh.mockResolvedValueOnce(tokens("second-token"));
    mockedGetCurrentUser.mockResolvedValueOnce(otherUser);

    act(() => FakeBroadcastChannel.deliverFromAnotherTab({ type: "ACCOUNT_CHANGED", sender: "other-tab" }));
    expect(await screen.findByText("other@fintrack.dev")).toBeInTheDocument();
    await act(async () => pendingUser.resolve(currentUser));

    expect(screen.getByText("other@fintrack.dev")).toBeInTheDocument();
    expect(getAccessToken()).toBe("second-token");
  });

  it("closes its channel when the provider unmounts", async () => {
    const { unmount } = await signedIn();
    const channel = FakeBroadcastChannel.channels.find((candidate) => candidate.name === SESSION_CHANNEL_NAME);

    unmount();

    expect(channel?.closed).toBe(true);
  });

  it("works without BroadcastChannel", async () => {
    vi.stubGlobal("BroadcastChannel", undefined);
    mockedLogin.mockResolvedValue(tokens("solo-token"));
    mockedGetCurrentUser.mockResolvedValue(currentUser);
    const user = userEvent.setup();

    render(<AuthProvider><AuthActionsProbe /></AuthProvider>);
    await screen.findByText("Unauthenticated");
    await user.click(screen.getByRole("button", { name: "Login through provider" }));

    expect(await screen.findByText("Authenticated")).toBeInTheDocument();
    setAccessToken("solo-token");
    await user.click(screen.getByRole("button", { name: "Logout through provider" }));
    expect(await screen.findByText("Unauthenticated")).toBeInTheDocument();
  });
});
