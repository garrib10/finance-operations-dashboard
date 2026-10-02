vi.mock("../context/AuthContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../context/AuthContext")>()),
  useAuth: vi.fn(),
}));

import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../context/AuthContext";
import { accountContext } from "../test/accountFixtures";
import { AppLayout } from "./AppLayout";

function renderShell(path = "/transactions"): void {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<section><h1>Dashboard page</h1></section>} />
          <Route path="/transactions" element={<section><h1>Transactions page</h1></section>} />
          <Route path="/budgets" element={<section><h1>Budgets page</h1></section>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("AppLayout", () => {
  beforeEach(() => {
    vi.mocked(useAuth).mockReturnValue(accountContext());
  });

  it("renders the page as a direct child of a single main landmark", () => {
    renderShell();

    const main = screen.getByRole("main");
    expect(main).toHaveAttribute("id", "main-content");
    expect(main).toHaveAttribute("tabindex", "-1");
    // Page CSS (.app-main > section) depends on this structure.
    expect(main.firstElementChild).toBe(screen.getByRole("heading", { name: "Transactions page" }).parentElement);
    expect(screen.getAllByRole("main")).toHaveLength(1);
  });

  it("shows the brand and primary navigation in the sidebar", () => {
    renderShell();

    expect(screen.getByRole("link", { name: "FinTrack" })).toHaveAttribute("href", "/");
    const navs = screen.getAllByRole("navigation");
    expect(navs).toHaveLength(1);
    expect(navs[0]).toHaveAccessibleName("Primary navigation");
    expect(within(navs[0]).getByRole("link", { name: "Transactions" })).toHaveAttribute("aria-current", "page");
    expect(screen.queryByRole("navigation", { name: "Authentication navigation" })).not.toBeInTheDocument();
  });

  it("keeps the account menu in the single top bar", () => {
    renderShell();

    const banners = screen.getAllByRole("banner");
    expect(banners).toHaveLength(1);
    expect(within(banners[0]).getByRole("button", { name: "Open account menu for River Walker" }))
      .toHaveAttribute("aria-expanded", "false");
  });

  it("swaps only the page when navigating between destinations", async () => {
    const user = userEvent.setup();
    renderShell("/");
    const accountTrigger = screen.getByRole("button", { name: /account menu/i });

    await user.click(screen.getByRole("link", { name: "Budgets" }));

    expect(screen.getByRole("heading", { name: "Budgets page" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Budgets" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Dashboard" })).not.toHaveAttribute("aria-current");
    // The shell itself is not remounted.
    expect(screen.getByRole("button", { name: /account menu/i })).toBe(accountTrigger);
  });
});
