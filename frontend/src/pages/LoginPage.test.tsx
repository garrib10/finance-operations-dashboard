import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { AuthContextValue } from "../context/AuthContext";
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
  type Location,
} from "react-router-dom";

import { describe, expect, it, vi } from "vitest";
import * as AuthContextModule from "../context/AuthContext";
import { ApiError } from "../services/api";
import LoginPage from "./LoginPage";

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

function TransactionDestination() {
  const location = useLocation();

  return (
    <p>
      Transaction Page
      {`${location.search}${location.hash}`}
    </p>
  );
}

async function submitLoginForm(
  email = "demo@fintrack.dev",
  password = "FinTrackDemo123!",
) {
  const user = userEvent.setup();

  await user.type(screen.getByLabelText("Email"), email);
  await user.type(screen.getByLabelText("Password"), password);

  await user.click(
    screen.getByRole("button", {
      name: "Sign In",
    }),
  );
}

function mockLoggedOutContext(login: AuthContextValue["login"]) {
  mockedUseAuth.mockReturnValue({
    user: null,
    isAuthenticated: false,
    isLoading: false,
    restorationError: null,
    login,
    logout: vi.fn(),
    retrySessionRestore: vi.fn(async () => undefined),
  });
}

describe("LoginPage", () => {
  it("submits credentials and navigates to the dashboard by default", async () => {
    const login = vi.fn().mockResolvedValue(undefined);
    mockLoggedOutContext(login);

    render(
      <MemoryRouter initialEntries={["/login"]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<p>Dashboard Page</p>} />
        </Routes>
      </MemoryRouter>,
    );

    await submitLoginForm();

    expect(login).toHaveBeenCalledWith({
      email: "demo@fintrack.dev",
      password: "FinTrackDemo123!",
    });

    expect(await screen.findByText("Dashboard Page")).toBeInTheDocument();
  });

  it("returns to the preserved route after a successful login", async () => {
    const login = vi.fn().mockResolvedValue(undefined);
    mockLoggedOutContext(login);

    const attemptedRoute: Location = {
      pathname: "/transactions",
      search: "?page=2",
      hash: "#recent",
      state: null,
      key: "transactions",
    };

    const loginState: LoginLocationState = {
      from: attemptedRoute,
    };

    render(
      <MemoryRouter
        initialEntries={[
          {
            pathname: "/login",
            state: loginState,
          },
        ]}
      >
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/transactions" element={<TransactionDestination />} />
        </Routes>
      </MemoryRouter>,
    );

    await submitLoginForm();

    expect(
      await screen.findByText("Transaction Page?page=2#recent"),
    ).toBeInTheDocument();
  });

  it("keeps invalid credentials as an inline form error", async () => {
    const login = vi
      .fn()
      .mockRejectedValue(new ApiError("Invalid email or password.", 401));

    mockLoggedOutContext(login);

    render(
      <MemoryRouter initialEntries={["/login"]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await submitLoginForm("wrong@example.com", "wrong-password");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Invalid email or password.",
    );

    expect(
      screen.getByRole("heading", {
        name: "Login",
      }),
    ).toBeInTheDocument();
  });

  it("shows a fallback error when login fails unexpectedly", async () => {
    const login = vi.fn().mockRejectedValue(new TypeError("Network failed"));

    mockLoggedOutContext(login);

    render(
      <MemoryRouter initialEntries={["/login"]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await submitLoginForm();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Unable to log in. Please try again.",
    );
  });

  it("does not redirect back to Login after a successful login", async () => {
    const login = vi.fn().mockResolvedValue(undefined);
    mockLoggedOutContext(login);

    const loginRoute: Location = {
      pathname: "/login",
      search: "",
      hash: "",
      state: null,
      key: "login",
    };

    render(
      <MemoryRouter
        initialEntries={[
          {
            pathname: "/login",
            state: {
              from: loginRoute,
            } satisfies LoginLocationState,
          },
        ]}
      >
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<p>Dashboard Page</p>} />
        </Routes>
      </MemoryRouter>,
    );

    await submitLoginForm();

    expect(await screen.findByText("Dashboard Page")).toBeInTheDocument();

    expect(
      screen.queryByRole("heading", {
        name: "Login",
      }),
    ).not.toBeInTheDocument();
  });
});
