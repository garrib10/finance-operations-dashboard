import { describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import LoginPage from "./LoginPage";
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

describe("LoginPage", () => {
  it("submits email and password", async () => {
    const login = vi.fn().mockResolvedValue(undefined);

    mockedUseAuth.mockReturnValue({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      login,
      logout: vi.fn(),
    });

    const user = userEvent.setup();

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );

    await user.type(screen.getByLabelText("Email"), "demo@fintrack.dev");

    await user.type(screen.getByLabelText("Password"), "FinTrackDemo123!");

    await user.click(
      screen.getByRole("button", {
        name: "Sign In",
      }),
    );

    expect(login).toHaveBeenCalledWith({
      email: "demo@fintrack.dev",
      password: "FinTrackDemo123!",
    });
  });

  it("shows an error when login fails", async () => {
    const login = vi.fn().mockRejectedValue(new Error("Login failed"));

    mockedUseAuth.mockReturnValue({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      login,
      logout: vi.fn(),
    });

    const user = userEvent.setup();

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );

    await user.type(screen.getByLabelText("Email"), "wrong@example.com");

    await user.type(screen.getByLabelText("Password"), "wrong-password");

    await user.click(
      screen.getByRole("button", {
        name: "Sign In",
      }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Unable to log in. Please try again.",
    );
  });
});
