vi.mock("../context/AuthContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../context/AuthContext")>()),
  useAuth: vi.fn(),
}));

import { render, screen, within } from "@testing-library/react";
import { useState } from "react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../context/AuthContext";
import { accountContext } from "../test/accountFixtures";
import { SIDEBAR_COLLAPSED_KEY } from "../utils/sidebarPreference";
import { AppLayout } from "./AppLayout";

/** A page with local state, to prove the shell never remounts the routed page. */
function CounterPage() {
  const [count, setCount] = useState(0);
  return (
    <section>
      <h1>Transactions page</h1>
      <button type="button" onClick={() => setCount((value) => value + 1)}>Count {count}</button>
    </section>
  );
}

function renderShell(path = "/transactions"): void {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<section><h1>Dashboard page</h1></section>} />
          <Route path="/transactions" element={<CounterPage />} />
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

  afterEach(() => {
    vi.restoreAllMocks();
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

  describe("collapsible sidebar", () => {
    const layout = () => document.querySelector(".app-layout");
    const toggle = (name: "Collapse sidebar" | "Expand sidebar") => screen.getByRole("button", { name });

    it("starts expanded with a labelled toggle that controls the sidebar", () => {
      renderShell();

      const button = toggle("Collapse sidebar");
      expect(button).toHaveAttribute("type", "button");
      expect(button).toHaveAttribute("aria-expanded", "true");
      const sidebar = document.getElementById(button.getAttribute("aria-controls")!);
      expect(sidebar).toContainElement(screen.getByRole("navigation", { name: "Primary navigation" }));
      expect(sidebar).toContainElement(button);
      expect(layout()).toHaveAttribute("data-sidebar-collapsed", "false");
      expect(button.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
    });

    it("starts collapsed when the device preference says so", () => {
      window.localStorage.setItem(SIDEBAR_COLLAPSED_KEY, "true");
      renderShell();

      expect(layout()).toHaveAttribute("data-sidebar-collapsed", "true");
      expect(toggle("Expand sidebar")).toHaveAttribute("aria-expanded", "false");
    });

    it("collapses and expands, keeps focus on the toggle, and saves each change", async () => {
      const user = userEvent.setup();
      renderShell();

      await user.click(toggle("Collapse sidebar"));
      const button = toggle("Expand sidebar");
      expect(button).toHaveFocus();
      expect(button).toHaveAttribute("aria-expanded", "false");
      expect(layout()).toHaveAttribute("data-sidebar-collapsed", "true");
      expect(window.localStorage.getItem(SIDEBAR_COLLAPSED_KEY)).toBe("true");

      await user.keyboard("{Enter}");
      expect(toggle("Collapse sidebar")).toHaveFocus();
      expect(layout()).toHaveAttribute("data-sidebar-collapsed", "false");
      expect(window.localStorage.getItem(SIDEBAR_COLLAPSED_KEY)).toBe("false");
    });

    it("keeps working when the preference cannot be read or saved", async () => {
      const user = userEvent.setup();
      vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => { throw new Error("blocked"); });
      vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("blocked"); });
      renderShell();

      await user.click(toggle("Collapse sidebar"));

      expect(layout()).toHaveAttribute("data-sidebar-collapsed", "true");
      expect(toggle("Expand sidebar")).toHaveFocus();
    });

    it("keeps names, current page, and the brand name while collapsed", async () => {
      const user = userEvent.setup();
      renderShell();
      await user.click(toggle("Collapse sidebar"));

      const nav = screen.getByRole("navigation", { name: "Primary navigation" });
      expect(within(nav).getAllByRole("link").map((link) => link.getAttribute("href")))
        .toEqual(["/", "/transactions", "/budgets"]);
      for (const name of ["Dashboard", "Transactions", "Budgets"]) {
        expect(within(nav).getByRole("link", { name })).toBeInTheDocument();
      }
      expect(within(nav).getByRole("link", { name: "Transactions" })).toHaveAttribute("aria-current", "page");
      expect(within(nav).getByRole("link", { name: "Dashboard" })).not.toHaveAttribute("aria-current");
      expect(screen.getByRole("link", { name: "FinTrack" })).toHaveAttribute("href", "/");
    });

    it("gives every icon-only control a decorative tooltip matching its name", () => {
      window.localStorage.setItem(SIDEBAR_COLLAPSED_KEY, "true");
      renderShell();

      const controls = [
        ...within(screen.getByRole("navigation", { name: "Primary navigation" })).getAllByRole("link"),
        toggle("Expand sidebar"),
      ];
      for (const control of controls) {
        const tooltip = control.querySelector(".sidebar-tooltip");
        expect(tooltip).toHaveAttribute("aria-hidden", "true");
        expect(control).toHaveAccessibleName(tooltip?.textContent ?? "");
      }
    });

    it("does not remount the routed page or the account menu when toggled", async () => {
      const user = userEvent.setup();
      renderShell();
      const accountTrigger = screen.getByRole("button", { name: /account menu/i });
      await user.click(screen.getByRole("button", { name: "Count 0" }));

      await user.click(toggle("Collapse sidebar"));
      await user.click(toggle("Expand sidebar"));

      expect(screen.getByRole("button", { name: "Count 1" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /account menu/i })).toBe(accountTrigger);
    });
  });
});
