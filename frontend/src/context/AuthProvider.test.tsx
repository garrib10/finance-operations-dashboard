import { useState } from "react";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../services/api";
import * as AuthService from "../services/authService";
import { invalidateAuthSession } from "../services/authSession";
import { getAuthToken, setAuthToken } from "../utils/authToken";
import { useAuth } from "./AuthContext";
import { AuthProvider } from "./AuthProvider";

vi.mock("../services/authService", async () => {
  const actual = await vi.importActual<typeof AuthService>(
    "../services/authService",
  );

  return {
    ...actual,
    getCurrentUser: vi.fn(),
    login: vi.fn(),
  };
});

const mockedGetCurrentUser = vi.mocked(AuthService.getCurrentUser);
const mockedLogin = vi.mocked(AuthService.login);

const currentUser = {
  id: 1,
  firstName: "Demo",
  lastName: "User",
  displayName: "Demo User",
  preferences: { dateFormat: "MEDIUM" as const, transactionPageSize: 10 as const },
  email: "demo@fintrack.dev",
  createdAt: "2026-09-09T00:00:00",
};

function AuthStateProbe() {
  const {
    user,
    isAuthenticated,
    isLoading,
    restorationError,
    retrySessionRestore,
  } = useAuth();

  if (restorationError) {
    return (
      <>
        <p role="alert">{restorationError}</p>

        <button
          type="button"
          disabled={isLoading}
          onClick={() => void retrySessionRestore()}
        >
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
    </>
  );
}

function AuthActionsProbe() {
  const { user, isAuthenticated, login, logout } = useAuth();
  const [loginFailed, setLoginFailed] = useState(false);

  async function handleLogin(): Promise<void> {
    setLoginFailed(false);

    try {
      await login({
        email: "demo@fintrack.dev",
        password: "Password123!",
      });
    } catch {
      setLoginFailed(true);
    }
  }

  return (
    <>
      <p>{isAuthenticated ? "Authenticated" : "Unauthenticated"}</p>
      <p>{user?.email ?? "No user"}</p>

      {loginFailed && <p role="alert">Login initialization failed</p>}

      <button type="button" onClick={() => void handleLogin()}>
        Login through provider
      </button>

      <button type="button" onClick={logout}>
        Logout through provider
      </button>
    </>
  );
}

beforeEach(() => {
  window.localStorage.clear();
  vi.clearAllMocks();
});

describe("AuthProvider", () => {
  it("restores an existing session and clears it after invalidation", async () => {
    setAuthToken("valid-token");
    mockedGetCurrentUser.mockResolvedValue(currentUser);

    render(
      <AuthProvider>
        <AuthStateProbe />
      </AuthProvider>,
    );

    expect(await screen.findByText("demo@fintrack.dev")).toBeInTheDocument();
    expect(screen.getByText("Authenticated")).toBeInTheDocument();
    expect(getAuthToken()).toBe("valid-token");

    act(() => {
      invalidateAuthSession();
    });

    await waitFor(() => {
      expect(screen.getByText("Unauthenticated")).toBeInTheDocument();
    });

    expect(screen.getByText("No user")).toBeInTheDocument();
    expect(screen.queryByText("demo@fintrack.dev")).not.toBeInTheDocument();
    expect(getAuthToken()).toBeNull();
  });

  it("starts unauthenticated when no token exists", async () => {
    render(
      <AuthProvider>
        <AuthStateProbe />
      </AuthProvider>,
    );

    expect(await screen.findByText("Unauthenticated")).toBeInTheDocument();
    expect(screen.getByText("No user")).toBeInTheDocument();
    expect(mockedGetCurrentUser).not.toHaveBeenCalled();
  });

  it("does not restore the session after the provider unmounts", () => {
    let queuedRestore: VoidFunction | undefined;

    const queueMicrotaskSpy = vi
      .spyOn(globalThis, "queueMicrotask")
      .mockImplementationOnce((callback) => {
        queuedRestore = callback;
      });

    setAuthToken("valid-token");

    const { unmount } = render(
      <AuthProvider>
        <AuthStateProbe />
      </AuthProvider>,
    );

    unmount();
    queuedRestore?.();

    expect(mockedGetCurrentUser).not.toHaveBeenCalled();

    queueMicrotaskSpy.mockRestore();
  });

  it("preserves the token when restoration fails temporarily", async () => {
    setAuthToken("recoverable-token");

    mockedGetCurrentUser.mockRejectedValue(
      new TypeError("Unable to reach the server"),
    );

    render(
      <AuthProvider>
        <AuthStateProbe />
      </AuthProvider>,
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "We couldn’t restore your session. Check your connection and try again.",
    );

    expect(getAuthToken()).toBe("recoverable-token");
    expect(
      screen.getByRole("button", {
        name: "Retry restoration",
      }),
    ).toBeEnabled();
  });

  it("restores the user after retrying a temporary failure", async () => {
    const user = userEvent.setup();

    setAuthToken("recoverable-token");

    mockedGetCurrentUser
      .mockRejectedValueOnce(new TypeError("Unable to reach the server"))
      .mockResolvedValueOnce(currentUser);

    render(
      <AuthProvider>
        <AuthStateProbe />
      </AuthProvider>,
    );

    await screen.findByRole("alert");

    await user.click(
      screen.getByRole("button", {
        name: "Retry restoration",
      }),
    );

    expect(await screen.findByText("Authenticated")).toBeInTheDocument();
    expect(screen.getByText("demo@fintrack.dev")).toBeInTheDocument();
    expect(mockedGetCurrentUser).toHaveBeenCalledTimes(2);
    expect(getAuthToken()).toBe("recoverable-token");
  });

  it("remains recoverable when another retry fails temporarily", async () => {
    const user = userEvent.setup();

    setAuthToken("recoverable-token");

    mockedGetCurrentUser
      .mockRejectedValueOnce(new TypeError("Initial network failure"))
      .mockRejectedValueOnce(new ApiError("Service unavailable.", 503));

    render(
      <AuthProvider>
        <AuthStateProbe />
      </AuthProvider>,
    );

    await screen.findByRole("alert");

    await user.click(
      screen.getByRole("button", {
        name: "Retry restoration",
      }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "We couldn’t restore your session. Check your connection and try again.",
    );

    expect(mockedGetCurrentUser).toHaveBeenCalledTimes(2);
    expect(getAuthToken()).toBe("recoverable-token");

    expect(
      screen.getByRole("button", {
        name: "Retry restoration",
      }),
    ).toBeEnabled();
  });

  it("clears an expired session when restoration returns 401", async () => {
    setAuthToken("expired-token");

    mockedGetCurrentUser.mockImplementation(async () => {
      invalidateAuthSession();
      throw new ApiError("Unauthorized.", 401);
    });

    render(
      <AuthProvider>
        <AuthStateProbe />
      </AuthProvider>,
    );

    expect(await screen.findByText("Unauthenticated")).toBeInTheDocument();

    expect(screen.getByText("No user")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(getAuthToken()).toBeNull();
  });

  it("logs in, loads the current user, and logs out", async () => {
    const user = userEvent.setup();

    mockedLogin.mockResolvedValue({
      accessToken: "new-access-token",
      tokenType: "Bearer",
      expiresIn: 3600,
    });
    mockedGetCurrentUser.mockResolvedValue(currentUser);

    render(
      <AuthProvider>
        <AuthActionsProbe />
      </AuthProvider>,
    );

    await screen.findByText("Unauthenticated");

    await user.click(
      screen.getByRole("button", { name: "Login through provider" }),
    );

    expect(await screen.findByText("Authenticated")).toBeInTheDocument();
    expect(screen.getByText("demo@fintrack.dev")).toBeInTheDocument();
    expect(getAuthToken()).toBe("new-access-token");
    expect(mockedLogin).toHaveBeenCalledWith({
      email: "demo@fintrack.dev",
      password: "Password123!",
    });
    expect(mockedGetCurrentUser).toHaveBeenCalledOnce();

    await user.click(
      screen.getByRole("button", { name: "Logout through provider" }),
    );

    expect(screen.getByText("Unauthenticated")).toBeInTheDocument();
    expect(screen.getByText("No user")).toBeInTheDocument();
    expect(getAuthToken()).toBeNull();
  });

  it("removes the new token when loading the user after login fails", async () => {
    const user = userEvent.setup();

    mockedLogin.mockResolvedValue({
      accessToken: "invalid-access-token",
      tokenType: "Bearer",
      expiresIn: 3600,
    });
    mockedGetCurrentUser.mockRejectedValue(
      new TypeError("Unable to load the current user"),
    );

    render(
      <AuthProvider>
        <AuthActionsProbe />
      </AuthProvider>,
    );

    await screen.findByText("Unauthenticated");

    await user.click(
      screen.getByRole("button", { name: "Login through provider" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Login initialization failed",
    );
    expect(screen.getByText("Unauthenticated")).toBeInTheDocument();
    expect(screen.getByText("No user")).toBeInTheDocument();
    expect(getAuthToken()).toBeNull();
  });
});
