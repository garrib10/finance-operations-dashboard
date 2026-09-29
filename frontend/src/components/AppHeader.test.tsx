import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as AuthContextModule from "../context/AuthContext";
import AppHeader from "./AppHeader";
import { photoUrl, replacementPhotoUrl } from "../test/accountFixtures";

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
      profilePhotoUrl: null,
    },
    isAuthenticated: true,
    isLoading: false,
    restorationError: null,
    login: vi.fn(),
    logout,
    updateProfile: vi.fn(),
    updatePreferences: vi.fn(),
    uploadProfilePhoto: vi.fn(),
    removeProfilePhoto: vi.fn(),
    retrySessionRestore: vi.fn(async () => undefined), sessionNotice: null, completePasswordChange: vi.fn(),
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

  it("waits for server confirmation, blocks duplicate logout, and navigates afterwards", async () => {
    const user = userEvent.setup();
    let confirm!: () => void;
    const logout = vi.fn(() => new Promise<void>((resolve) => { confirm = resolve; }));
    mockAuthenticatedUser(logout);
    renderHeader();

    await user.click(screen.getByRole("button", { name: "Open account menu for Demo User" }));
    await user.click(screen.getByRole("button", { name: "Logout" }));

    const pending = screen.getByRole("button", { name: "Signing out..." });
    expect(pending).toBeDisabled();
    fireEvent.click(pending);
    expect(logout).toHaveBeenCalledOnce();
    expect(screen.getByText("demo@fintrack.dev")).toBeInTheDocument();

    await act(async () => confirm());

    expect(screen.queryByText("demo@fintrack.dev")).not.toBeInTheDocument();
  });

  it("keeps the user signed in with a retryable alert when logout is not confirmed", async () => {
    const user = userEvent.setup();
    const logout = vi.fn()
      .mockRejectedValueOnce(new Error("503 from /api/auth/logout with internal detail"))
      .mockResolvedValueOnce(undefined);
    mockAuthenticatedUser(logout);
    renderHeader();

    await user.click(screen.getByRole("button", { name: "Open account menu for Demo User" }));
    await user.click(screen.getByRole("button", { name: "Logout" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("We couldn’t sign you out. Check your connection and try again.");
    expect(alert).not.toHaveTextContent(/503|internal/);
    expect(screen.getByText("demo@fintrack.dev")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Logout" }));

    expect(logout).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
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
      uploadProfilePhoto: vi.fn(),
      removeProfilePhoto: vi.fn(),
      retrySessionRestore: vi.fn(async () => undefined), sessionNotice: null, completePasswordChange: vi.fn(),
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


  describe("profile photo", () => {
    function mockPhoto(profilePhotoUrl: string | null) {
      mockAuthenticatedUser();
      const context = mockedUseAuth();
      mockedUseAuth.mockReturnValue({ ...context, user: { ...context.user!, profilePhotoUrl } });
    }
    const trigger = () => screen.getByRole("button", { name: /account menu for Demo User/ });

    it("renders the photo as a decorative part of the trigger without changing its name", () => {
      mockPhoto(photoUrl);
      renderHeader();
      const image = trigger().querySelector("img");
      expect(image).toHaveAttribute("src", photoUrl);
      expect(image).toHaveAttribute("alt", "");
      expect(image?.parentElement).toHaveClass("avatar", "account-menu__avatar");
      expect(image?.parentElement).toHaveAttribute("aria-hidden", "true");
      expect(trigger()).toHaveAccessibleName("Open account menu for Demo User");
      expect(trigger()).not.toHaveTextContent("DU");
      expect(trigger()).toHaveTextContent("Demo User");
      expect(screen.getAllByRole("button")).toHaveLength(1);
      expect(document.body).not.toHaveTextContent(photoUrl);
    });

    it("renders initials when there is no photo", () => {
      mockPhoto(null);
      renderHeader();
      expect(trigger().querySelector("img")).toBeNull();
      expect(trigger().querySelector(".account-menu__avatar")).toHaveTextContent("DU");
    });

    it("falls back to initials when the photo fails and retries a replacement", () => {
      mockPhoto(photoUrl);
      const { rerender } = render(<MemoryRouter><AppHeader /></MemoryRouter>);
      fireEvent.error(trigger().querySelector("img")!);
      expect(trigger().querySelector("img")).toBeNull();
      expect(trigger()).toHaveTextContent("DU");
      mockPhoto(replacementPhotoUrl);
      rerender(<MemoryRouter><AppHeader /></MemoryRouter>);
      expect(trigger().querySelector("img")).toHaveAttribute("src", replacementPhotoUrl);
    });

    it("keeps the avatar, name, and chevron structure used by the responsive layout", async () => {
      mockPhoto(photoUrl);
      renderHeader();
      expect([...trigger().children].map(child => child.className)).toEqual([
        "avatar account-menu__avatar", "account-menu__name", "account-menu__chevron",
      ]);
      await userEvent.click(trigger());
      expect(screen.getByText("demo@fintrack.dev")).toBeInTheDocument();
      await userEvent.keyboard("{Escape}");
      expect(trigger()).toHaveFocus();
      expect(trigger()).toHaveAttribute("aria-expanded", "false");
    });
  });
});
