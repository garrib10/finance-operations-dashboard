import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import App from "./App";
import { AuthContext, type AuthContextValue } from "./context/AuthContext";
import { accountUser } from "./test/accountFixtures";

vi.mock("./pages/DashboardPage", () => ({ default: () => <h1>Dashboard destination</h1> }));
vi.mock("./pages/TransactionPage", () => ({ default: () => <h1>Transactions destination</h1> }));
vi.mock("./pages/BudgetPage", () => ({ default: () => <h1>Budgets destination</h1> }));
vi.mock("./pages/CategoriesPage", () => ({ default: () => <h1>Categories destination</h1> }));
vi.mock("./pages/LoginPage", () => ({ default: () => <h1>Login destination</h1> }));
vi.mock("./pages/RegisterPage", () => ({ default: () => <h1>Register destination</h1> }));

function DestinationProbe() {
  const location = useLocation();
  return <output data-testid="destination">{location.pathname}|{location.state?.from?.pathname}</output>;
}

function signedInContext(): AuthContextValue {
  return {
    user: accountUser,
    isAuthenticated: true,
    isLoading: false,
    restorationError: null,
    login: vi.fn(), logout: vi.fn(), retrySessionRestore: vi.fn(), sessionNotice: null, completePasswordChange: vi.fn(),
    updateProfile: vi.fn(), updatePreferences: vi.fn(), uploadProfilePhoto: vi.fn(), removeProfilePhoto: vi.fn(),
  };
}

function renderApp(path: string, authenticated = true, overrides: Partial<AuthContextValue> = {}) {
  const context: AuthContextValue = {
    ...signedInContext(),
    user: authenticated ? accountUser : null,
    isAuthenticated: authenticated,
    ...overrides,
  };
  return render(<AuthContext.Provider value={context}>
    <MemoryRouter initialEntries={[path]}><App /><DestinationProbe /></MemoryRouter>
  </AuthContext.Provider>);
}

// Full user-event flows are slow under coverage instrumentation and on a busy machine.
vi.setConfig({ testTimeout: 20_000 });

describe("application routing and account forms", () => {
  it.each([["/profile", "Profile"], ["/settings", "Account Settings"]])("protects and renders %s with account forms", (path, title) => {
    renderApp(path);
    expect(screen.getByRole("heading", { name: title, level: 1 })).toBeInTheDocument();
    expect(screen.getAllByRole("banner")).toHaveLength(1);
    expect(document.querySelector("form")).not.toBeNull();
    expect(screen.getByRole("link", { name: "Skip to main content" })).toHaveAttribute("href", "#main-content");
  });

  it.each(["/profile", "/settings", "/transactions", "/budgets", "/categories", "/"])("redirects signed-out access to %s and preserves its destination", (path) => {
    renderApp(path, false);
    expect(screen.getByRole("heading", { name: "Login destination" })).toBeInTheDocument();
    expect(screen.getByTestId("destination")).toHaveTextContent(`/login|${path}`);
  });

  it.each([["/", "Dashboard"], ["/transactions", "Transactions"], ["/budgets", "Budgets"], ["/categories", "Categories"],
    ["/unknown", "Dashboard"]])("preserves existing route behavior for %s", (path, title) => {
    renderApp(path);
    expect(screen.getByRole("heading", { name: `${title} destination` })).toBeInTheDocument();
  });

  it("protects the fallback destination for an unknown signed-out route", () => {
    renderApp("/unknown", false);
    expect(screen.getByTestId("destination")).toHaveTextContent("/login|/");
  });

  it.each(["/", "/transactions", "/budgets", "/categories", "/profile", "/settings"])("renders %s inside the signed-in shell", (path) => {
    renderApp(path);

    expect(screen.getByRole("navigation", { name: "Primary navigation" })).toBeInTheDocument();
    expect(within(screen.getByRole("banner")).getByRole("button", { name: /account menu/i })).toBeInTheDocument();
    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(screen.queryByRole("navigation", { name: "Authentication navigation" })).not.toBeInTheDocument();
  });

  it.each([["/login", "Login"], ["/register", "Register"]])("renders %s outside the signed-in shell", (path, title) => {
    renderApp(path, false);

    expect(within(screen.getByRole("main")).getByRole("heading", { name: `${title} destination` })).toBeInTheDocument();
    expect(screen.getByRole("navigation", { name: "Authentication navigation" })).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Primary navigation" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /account menu/i })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Skip to main content" })).toHaveAttribute("href", "#main-content");
  });

  it("shows session restoration in the public shell without the sidebar or sign-in links", () => {
    renderApp("/transactions", false, { isLoading: true });

    expect(within(screen.getByRole("main")).getByRole("status")).toHaveTextContent("Loading...");
    expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Login" })).not.toBeInTheDocument();
  });

  it("removes the signed-in shell and returns to Login when the session ends", () => {
    const view = renderApp("/budgets");
    expect(screen.getByRole("navigation", { name: "Primary navigation" })).toBeInTheDocument();

    // Logout, expiry, a password change, and cross-tab logout all clear the user like this.
    view.rerender(<AuthContext.Provider value={{ ...signedInContext(), user: null, isAuthenticated: false }}>
      <MemoryRouter initialEntries={["/budgets"]}><App /><DestinationProbe /></MemoryRouter>
    </AuthContext.Provider>);

    expect(screen.getByRole("heading", { name: "Login destination" })).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Primary navigation" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /account menu|navigation menu/i })).not.toBeInTheDocument();
    expect(screen.getAllByRole("main")).toHaveLength(1);
  });

  it("keeps Profile and Account Settings in the account menu, not the primary navigation", async () => {
    const user = userEvent.setup();
    renderApp("/");
    const primary = screen.getByRole("navigation", { name: "Primary navigation" });
    expect(within(primary).queryByRole("link", { name: /profile|settings/i })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Open account menu/ }));
    const account = screen.getByRole("navigation", { name: "Account navigation" });
    await user.click(within(account).getByRole("link", { name: "Account Settings" }));

    expect(screen.getByRole("heading", { name: "Account Settings", level: 1 })).toBeInTheDocument();
    expect(screen.getByTestId("destination")).toHaveTextContent("/settings");
  });
});
