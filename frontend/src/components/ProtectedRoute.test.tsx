import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
  type Location,
} from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import * as AuthContextModule from "../context/AuthContext";
import ProtectedRoute from "./ProtectedRoute";

vi.mock("../context/AuthContext", async () => {
  const actual = await vi.importActual<typeof AuthContextModule>(
    "../context/AuthContext",
  );

  return {
    ...actual,
    useAuth: vi.fn(),
  };
});

const mockedUseAuth = vi.mocked(AuthContextModule.useAuth);

interface LoginLocationState {
  from?: Location;
}

function LoginPageProbe() {
  const location = useLocation();
  const state = location.state as LoginLocationState | null;
  const from = state?.from;

  const attemptedRoute = from
    ? `${from.pathname}${from.search}${from.hash}`
    : "No attempted route";

  return (
    <>
      <p>Login Page</p>
      <p data-testid="attempted-route">{attemptedRoute}</p>
    </>
  );
}

function renderProtectedRoute() {
  return render(
    <MemoryRouter initialEntries={["/protected"]}>
      <Routes>
        <Route element={<ProtectedRoute />}>
          <Route path="/protected" element={<p>Protected Content</p>} />
        </Route>

        <Route path="/login" element={<LoginPageProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ProtectedRoute", () => {
  it("shows loading state while authentication is loading", () => {
    mockedUseAuth.mockReturnValue({
      user: null,
      isAuthenticated: false,
      isLoading: true,
      restorationError: null,
      login: vi.fn(),
      logout: vi.fn(),
      updateProfile: vi.fn(),
      updatePreferences: vi.fn(),
      retrySessionRestore: vi.fn(async () => undefined),
    });

    renderProtectedRoute();

    expect(screen.getByRole("status")).toHaveTextContent("Loading...");
    expect(screen.queryByText("Protected Content")).not.toBeInTheDocument();
  });

  it("redirects unauthenticated users and preserves the attempted route", () => {
    mockedUseAuth.mockReturnValue({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      restorationError: null,
      login: vi.fn(),
      logout: vi.fn(),
      updateProfile: vi.fn(),
      updatePreferences: vi.fn(),
      retrySessionRestore: vi.fn(async () => undefined),
    });

    render(
      <MemoryRouter initialEntries={["/protected?view=summary#details"]}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/protected" element={<p>Protected Content</p>} />
          </Route>

          <Route path="/login" element={<LoginPageProbe />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText("Login Page")).toBeInTheDocument();

    expect(screen.getByTestId("attempted-route")).toHaveTextContent(
      "/protected?view=summary#details",
    );

    expect(screen.queryByText("Protected Content")).not.toBeInTheDocument();
  });

  it("renders protected content for authenticated users", () => {
    mockedUseAuth.mockReturnValue({
      user: {
        id: 1,
        firstName: "Demo",
        lastName: "User",
        displayName: "Demo User",
        preferences: { dateFormat: "MEDIUM" as const, transactionPageSize: 10 as const },
        email: "demo@fintrack.dev",
        createdAt: "2026-09-09T00:00:00",
      },
      isAuthenticated: true,
      isLoading: false,
      restorationError: null,
      login: vi.fn(),
      logout: vi.fn(),
      updateProfile: vi.fn(),
      updatePreferences: vi.fn(),
      retrySessionRestore: vi.fn(async () => undefined),
    });

    renderProtectedRoute();

    expect(screen.getByText("Protected Content")).toBeInTheDocument();
  });

  it("shows an accessible recovery screen after a temporary failure", () => {
    mockedUseAuth.mockReturnValue({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      restorationError:
        "We couldn’t restore your session. Check your connection and try again.",
      login: vi.fn(),
      logout: vi.fn(),
      updateProfile: vi.fn(),
      updatePreferences: vi.fn(),
      retrySessionRestore: vi.fn(async () => undefined),
    });

    renderProtectedRoute();

    expect(
      screen.getByRole("heading", {
        name: "Unable to restore session",
      }),
    ).toBeInTheDocument();

    expect(screen.getByRole("alert")).toHaveTextContent(
      "We couldn’t restore your session. Check your connection and try again.",
    );

    expect(
      screen.getByRole("button", {
        name: "Retry",
      }),
    ).toBeEnabled();

    expect(screen.queryByText("Protected Content")).not.toBeInTheDocument();
    expect(screen.queryByText("Login Page")).not.toBeInTheDocument();
  });

  it("retries session restoration when Retry is clicked", async () => {
    const user = userEvent.setup();
    const retrySessionRestore = vi.fn(async () => undefined);

    mockedUseAuth.mockReturnValue({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      restorationError:
        "We couldn’t restore your session. Check your connection and try again.",
      login: vi.fn(),
      logout: vi.fn(),
      updateProfile: vi.fn(),
      updatePreferences: vi.fn(),
      retrySessionRestore,
    });

    renderProtectedRoute();

    await user.click(
      screen.getByRole("button", {
        name: "Retry",
      }),
    );

    expect(retrySessionRestore).toHaveBeenCalledOnce();
  });

  it("disables the recovery action while restoration is retrying", () => {
    mockedUseAuth.mockReturnValue({
      user: null,
      isAuthenticated: false,
      isLoading: true,
      restorationError:
        "We couldn’t restore your session. Check your connection and try again.",
      login: vi.fn(),
      logout: vi.fn(),
      updateProfile: vi.fn(),
      updatePreferences: vi.fn(),
      retrySessionRestore: vi.fn(async () => undefined),
    });

    renderProtectedRoute();

    expect(
      screen.getByRole("button", {
        name: "Retrying...",
      }),
    ).toBeDisabled();

    expect(screen.getByRole("alert")).toBeInTheDocument();
  });
});
