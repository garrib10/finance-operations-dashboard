vi.mock("../context/AuthContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../context/AuthContext")>()),
  useAuth: vi.fn(),
}));

import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { useAuth } from "../context/AuthContext";
import { accountContext } from "../test/accountFixtures";
import { PublicLayout } from "./PublicLayout";

const signedOut = { ...accountContext(), user: null, isAuthenticated: false };

function renderLogin(): void {
  render(
    <MemoryRouter initialEntries={["/login"]}>
      <Routes>
        <Route element={<PublicLayout />}>
          <Route path="/login" element={<section><h1>Login page</h1></section>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("PublicLayout", () => {
  it("renders the brand, sign-in links, and page inside a single main landmark", () => {
    vi.mocked(useAuth).mockReturnValue(signedOut);
    renderLogin();

    const banner = screen.getByRole("banner");
    expect(within(banner).getByRole("link", { name: "FinTrack" })).toHaveAttribute("href", "/");
    const nav = within(banner).getByRole("navigation", { name: "Authentication navigation" });
    expect(within(nav).getByRole("link", { name: "Login" })).toHaveAttribute("href", "/login");
    expect(within(nav).getByRole("link", { name: "Register" })).toHaveAttribute("href", "/register");

    const main = screen.getByRole("main");
    expect(main).toHaveAttribute("id", "main-content");
    expect(within(main).getByRole("heading", { name: "Login page" })).toBeInTheDocument();
    expect(screen.getAllByRole("banner")).toHaveLength(1);
    expect(screen.getAllByRole("main")).toHaveLength(1);
  });

  it("has no application navigation or account menu", () => {
    vi.mocked(useAuth).mockReturnValue(signedOut);
    renderLogin();

    expect(screen.queryByRole("navigation", { name: "Primary navigation" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /account menu/i })).not.toBeInTheDocument();
  });

  it.each([
    ["a session is being restored", { ...signedOut, isLoading: true }],
    ["the user is signed in", accountContext()],
  ])("hides the sign-in links while %s", (_state, context) => {
    vi.mocked(useAuth).mockReturnValue(context);
    renderLogin();

    expect(screen.getByRole("link", { name: "FinTrack" })).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Authentication navigation" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Login" })).not.toBeInTheDocument();
  });
});
