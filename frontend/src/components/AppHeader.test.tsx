import { describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AppHeader from "./AppHeader";
import * as AuthContextModule from "../context/AuthContext";

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

describe("AppHeader", () => {
  it("shows authenticated navigation and user name", () => {
    mockedUseAuth.mockReturnValue({
      user: {
        id: 1,
        firstName: "Demo",
        lastName: "User",
        email: "demo@fintrack.dev",
        createdAt: "2026-09-09T00:00:00",
      },
      isAuthenticated: true,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(
      <MemoryRouter>
        <AppHeader />
      </MemoryRouter>,
    );

    expect(screen.getByText("Demo")).toBeInTheDocument();

    expect(screen.getByText("Dashboard")).toBeInTheDocument();

    expect(screen.getByText("Transactions")).toBeInTheDocument();

    expect(screen.getByText("Budgets")).toBeInTheDocument();
  });

  it("calls logout when the logout button is clicked", async () => {
    const logout = vi.fn();

    mockedUseAuth.mockReturnValue({
      user: {
        id: 1,
        firstName: "Demo",
        lastName: "User",
        email: "demo@fintrack.dev",
        createdAt: "2026-09-09T00:00:00",
      },
      isAuthenticated: true,
      isLoading: false,
      login: vi.fn(),
      logout,
    });

    const user = userEvent.setup();

    render(
      <MemoryRouter>
        <AppHeader />
      </MemoryRouter>,
    );

    await user.click(
      screen.getByRole("button", {
        name: "Logout",
      }),
    );

    expect(logout).toHaveBeenCalledOnce();
  });
});
