import { render, screen } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import App from "./App";
import { AuthContext, type AuthContextValue } from "./context/AuthContext";
import { accountUser } from "./test/accountFixtures";

vi.mock("./pages/DashboardPage", () => ({ default: () => <h1>Dashboard destination</h1> }));
vi.mock("./pages/TransactionPage", () => ({ default: () => <h1>Transactions destination</h1> }));
vi.mock("./pages/BudgetPage", () => ({ default: () => <h1>Budgets destination</h1> }));
vi.mock("./pages/LoginPage", () => ({ default: () => <h1>Login destination</h1> }));

function DestinationProbe() {
  const location = useLocation();
  return <output data-testid="destination">{location.pathname}|{location.state?.from?.pathname}</output>;
}

function renderApp(path: string, authenticated = true) {
  const context: AuthContextValue = {
    user: authenticated ? accountUser : null,
    isAuthenticated: authenticated,
    isLoading: false,
    restorationError: null,
    login: vi.fn(), logout: vi.fn(), retrySessionRestore: vi.fn(),
    updateProfile: vi.fn(), updatePreferences: vi.fn(),
  };
  return render(<AuthContext.Provider value={context}>
    <MemoryRouter initialEntries={[path]}><App /><DestinationProbe /></MemoryRouter>
  </AuthContext.Provider>);
}

describe("application routing and account shells", () => {
  it.each([["/profile", "Profile"], ["/settings", "Account Settings"]])("protects and renders %s without account forms", (path, title) => {
    renderApp(path);
    expect(screen.getByRole("heading", { name: title, level: 1 })).toBeInTheDocument();
    expect(screen.getAllByRole("banner")).toHaveLength(1);
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(document.querySelector("form")).toBeNull();
    expect(screen.getByRole("link", { name: "Skip to main content" })).toHaveAttribute("href", "#main-content");
  });

  it.each(["/profile", "/settings", "/transactions", "/budgets", "/"])("redirects signed-out access to %s and preserves its destination", (path) => {
    renderApp(path, false);
    expect(screen.getByRole("heading", { name: "Login destination" })).toBeInTheDocument();
    expect(screen.getByTestId("destination")).toHaveTextContent(`/login|${path}`);
  });

  it.each([["/", "Dashboard"], ["/transactions", "Transactions"], ["/budgets", "Budgets"], ["/unknown", "Dashboard"]])("preserves existing route behavior for %s", (path, title) => {
    renderApp(path);
    expect(screen.getByRole("heading", { name: `${title} destination` })).toBeInTheDocument();
  });

  it("protects the fallback destination for an unknown signed-out route", () => {
    renderApp("/unknown", false);
    expect(screen.getByTestId("destination")).toHaveTextContent("/login|/");
  });
});
