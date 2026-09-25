import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContextModule from "../context/AuthContext";
import AppHeader from "./AppHeader";

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

function mockAuthenticatedUser(logout = vi.fn()): void {
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
    logout,
    updateProfile: vi.fn(),
    updatePreferences: vi.fn(),
    retrySessionRestore: vi.fn(async () => undefined),
  });
}

function LocationProbe() {
  return <p data-testid="location">{useLocation().pathname}</p>;
}

function renderHeader(): void {
  render(
    <MemoryRouter>
      <AppHeader />
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe("AppHeader", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows authenticated navigation and a closed account menu", () => {
    mockAuthenticatedUser();
    renderHeader();

    const accountTrigger = screen.getByRole("button", {
      name: "Open account menu for Demo User",
    });

    expect(accountTrigger).toHaveAttribute("aria-expanded", "false");
    expect(accountTrigger).toHaveAttribute(
      "aria-controls",
      "account-menu-panel",
    );
    expect(accountTrigger).toHaveTextContent("DU");
    expect(accountTrigger).toHaveTextContent("Demo User");

    expect(screen.getByText("Dashboard")).toBeInTheDocument();
    expect(screen.getByText("Transactions")).toBeInTheDocument();
    expect(screen.getByText("Budgets")).toBeInTheDocument();
    expect(screen.queryByText("demo@fintrack.dev")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Logout" }),
    ).not.toBeInTheDocument();
  });

  it("opens the account menu and displays the user identity", async () => {
    const user = userEvent.setup();
    mockAuthenticatedUser();
    renderHeader();

    await user.click(
      screen.getByRole("button", {
        name: "Open account menu for Demo User",
      }),
    );

    expect(
      screen.getByRole("button", {
        name: "Close account menu for Demo User",
      }),
    ).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("demo@fintrack.dev")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Logout" })).toBeInTheDocument();
  });

  it("closes the account menu when the trigger is selected again", async () => {
    const user = userEvent.setup();
    mockAuthenticatedUser();
    renderHeader();

    await user.click(
      screen.getByRole("button", {
        name: "Open account menu for Demo User",
      }),
    );

    await user.click(
      screen.getByRole("button", {
        name: "Close account menu for Demo User",
      }),
    );

    expect(
      screen.getByRole("button", {
        name: "Open account menu for Demo User",
      }),
    ).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("demo@fintrack.dev")).not.toBeInTheDocument();
  });

  it("closes the account menu with Escape and restores trigger focus", async () => {
    const user = userEvent.setup();
    mockAuthenticatedUser();
    renderHeader();

    const accountTrigger = screen.getByRole("button", {
      name: "Open account menu for Demo User",
    });

    await user.click(accountTrigger);

    const logoutButton = screen.getByRole("button", { name: "Logout" });
    logoutButton.focus();
    expect(logoutButton).toHaveFocus();

    await user.keyboard("{Escape}");

    expect(accountTrigger).toHaveFocus();
    expect(accountTrigger).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("demo@fintrack.dev")).not.toBeInTheDocument();
  });

  it("keeps the account menu open for keys other than Escape", async () => {
    const user = userEvent.setup();
    mockAuthenticatedUser();
    renderHeader();

    await user.click(
      screen.getByRole("button", {
        name: "Open account menu for Demo User",
      }),
    );

    fireEvent.keyDown(document, { key: "Enter" });

    expect(screen.getByText("demo@fintrack.dev")).toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: "Close account menu for Demo User",
      }),
    ).toHaveAttribute("aria-expanded", "true");
  });

  it("closes the account menu when a pointer event occurs outside it", async () => {
    const user = userEvent.setup();
    mockAuthenticatedUser();
    renderHeader();

    await user.click(
      screen.getByRole("button", {
        name: "Open account menu for Demo User",
      }),
    );

    fireEvent.pointerDown(document.body);

    expect(screen.queryByText("demo@fintrack.dev")).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: "Open account menu for Demo User",
      }),
    ).toHaveAttribute("aria-expanded", "false");
  });

  it("calls logout and closes the account menu", async () => {
    const user = userEvent.setup();
    const logout = vi.fn();
    mockAuthenticatedUser(logout);
    renderHeader();

    await user.click(
      screen.getByRole("button", {
        name: "Open account menu for Demo User",
      }),
    );
    await user.click(screen.getByRole("button", { name: "Logout" }));

    expect(logout).toHaveBeenCalledOnce();
    expect(screen.queryByText("demo@fintrack.dev")).not.toBeInTheDocument();
  });

  it("shows authentication navigation when signed out", () => {
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

    renderHeader();

    expect(screen.getByRole("link", { name: "Login" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Register" })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /account menu/i }),
    ).not.toBeInTheDocument();
  });
  it("uses display name and its initials in the trigger and dropdown", async () => {
    mockAuthenticatedUser();
    const context = mockedUseAuth();
    mockedUseAuth.mockReturnValue({ ...context, user: { ...context.user!, displayName: "River Quiet Walker" } });
    renderHeader();
    const trigger = screen.getByRole("button", { name: "Open account menu for River Quiet Walker" });
    expect(trigger).toHaveTextContent("RW");
    expect(trigger).not.toHaveAccessibleName(/demo@/);
    await userEvent.click(trigger);
    expect(screen.getAllByText("River Quiet Walker")).toHaveLength(2);
  });

  it.each([
    ["", "Demo", "User", "Demo User", "DU"],
    [" ", "", "", "Account", "A"],
    ["Solo", "Demo", "User", "Solo", "S"],
  ])("supports identity fallback for %s", (displayName, firstName, lastName, name, initials) => {
    mockAuthenticatedUser();
    const context = mockedUseAuth();
    mockedUseAuth.mockReturnValue({ ...context, user: { ...context.user!, displayName, firstName, lastName } });
    renderHeader();
    expect(screen.getByRole("button", { name: `Open account menu for ${name}` })).toHaveTextContent(initials);
  });

  it.each([["Profile", "/profile"], ["Account Settings", "/settings"]])("navigates to %s and closes the dropdown", async (label, path) => {
    mockAuthenticatedUser();
    renderHeader();
    const user = userEvent.setup();
    const trigger = screen.getByRole("button", { name: /Open account menu/ });
    trigger.focus();
    await user.keyboard("{Enter}");
    const link = screen.getByRole("link", { name: label });
    expect(link).toHaveAttribute("href", path);
    link.focus();
    await user.keyboard("{Enter}");
    expect(screen.getByTestId("location")).toHaveTextContent(path);
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("navigation", { name: "Account navigation" })).not.toBeInTheDocument();
  });

});
