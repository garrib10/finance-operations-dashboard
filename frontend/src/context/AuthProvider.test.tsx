import { act, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "./AuthProvider";
import { useAuth } from "./AuthContext";
import { invalidateAuthSession } from "../services/authSession";
import * as AuthService from "../services/authService";
import { getAuthToken, setAuthToken } from "../utils/authToken";

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

function AuthStateProbe() {
  const { user, isAuthenticated, isLoading } = useAuth();

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

beforeEach(() => {
  window.localStorage.clear();
  vi.clearAllMocks();
});

describe("AuthProvider", () => {
  it("restores an existing session and clears it after invalidation", async () => {
    setAuthToken("valid-token");

    mockedGetCurrentUser.mockResolvedValue({
      id: 1,
      firstName: "Demo",
      lastName: "User",
      email: "demo@fintrack.dev",
      createdAt: "2026-09-09T00:00:00",
    });

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
});
