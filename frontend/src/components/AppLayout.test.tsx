vi.mock("../context/AuthContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../context/AuthContext")>()),
  useAuth: vi.fn(),
}));

import { fireEvent, render, screen, within } from "@testing-library/react";
import { useState } from "react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useNavigate } from "react-router-dom";
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

/** Navigates without the drawer, standing in for browser back/forward. */
function DashboardPage() {
  const navigate = useNavigate();
  return (
    <section>
      <h1>Dashboard page</h1>
      <button type="button" onClick={() => navigate("/budgets")}>Go to budgets</button>
    </section>
  );
}

/** Steps through history, standing in for the browser's Back and Forward buttons. */
function BudgetsPage() {
  const navigate = useNavigate();
  return (
    <section>
      <h1>Budgets page</h1>
      <button type="button" onClick={() => navigate(-1)}>History back</button>
    </section>
  );
}

function TransactionsForwardProbe() {
  const navigate = useNavigate();
  return <button type="button" onClick={() => navigate(1)}>History forward</button>;
}

const sidebar = () => document.getElementById("app-sidebar")!;

function renderShell(path = "/transactions") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/transactions" element={<CounterPage />} />
          <Route path="/budgets" element={<BudgetsPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

// Full user-event flows are slow under coverage instrumentation and on a busy machine.
vi.setConfig({ testTimeout: 20_000 });

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

    expect(within(sidebar()).getByRole("link", { name: "FinTrack" })).toHaveAttribute("href", "/");
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

  it("moves the current-page marker with browser back and forward", async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/transactions"]}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/transactions" element={<section><h1>Transactions page</h1><TransactionsForwardProbe /></section>} />
            <Route path="/budgets" element={<BudgetsPage />} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
    const current = () => within(sidebar()).getByRole("link", { current: "page" });

    await user.click(within(sidebar()).getByRole("link", { name: "Budgets" }));
    expect(current()).toHaveAccessibleName("Budgets");

    await user.click(screen.getByRole("button", { name: "History back" }));
    expect(screen.getByRole("heading", { name: "Transactions page" })).toBeInTheDocument();
    expect(current()).toHaveAccessibleName("Transactions");

    await user.click(screen.getByRole("button", { name: "History forward" }));
    expect(screen.getByRole("heading", { name: "Budgets page" })).toBeInTheDocument();
    expect(current()).toHaveAccessibleName("Budgets");
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
        .toEqual(["/", "/transactions", "/budgets", "/categories"]);
      for (const name of ["Dashboard", "Transactions", "Budgets", "Categories"]) {
        expect(within(nav).getByRole("link", { name })).toBeInTheDocument();
      }
      expect(within(nav).getByRole("link", { name: "Transactions" })).toHaveAttribute("aria-current", "page");
      expect(within(nav).getByRole("link", { name: "Dashboard" })).not.toHaveAttribute("aria-current");
      expect(within(sidebar()).getByRole("link", { name: "FinTrack" })).toHaveAttribute("href", "/");
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

  describe("mobile navigation drawer", () => {
    const menuButton = () => screen.getByRole("button", { name: "Open navigation menu" });
    const drawer = () => document.getElementById("mobile-navigation") as HTMLDialogElement;
    const isScrollLocked = () => document.documentElement.classList.contains("scroll-locked");

    async function openDrawer(path?: string) {
      const user = userEvent.setup();
      const view = renderShell(path);
      await user.click(menuButton());
      return { user, view };
    }

    it("has a labelled menu button that controls a closed drawer", () => {
      renderShell();

      expect(menuButton()).toHaveAttribute("type", "button");
      expect(menuButton()).toHaveAttribute("aria-haspopup", "dialog");
      expect(menuButton()).toHaveAttribute("aria-expanded", "false");
      expect(menuButton()).toHaveAttribute("aria-controls", "mobile-navigation");
      expect(drawer().tagName).toBe("DIALOG");
      expect(drawer()).toHaveAttribute("aria-label", "Navigation menu");
      expect(drawer().open).toBe(false);
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      // While closed, the drawer adds no second navigation.
      expect(screen.getAllByRole("navigation")).toHaveLength(1);
      expect(within(screen.getByRole("banner")).getByRole("link", { name: "FinTrack" })).toHaveAttribute("href", "/");
    });

    it("opens as a modal, focuses the close button, and locks page scrolling", async () => {
      const showModal = vi.spyOn(HTMLDialogElement.prototype, "showModal");
      await openDrawer();

      expect(showModal).toHaveBeenCalledOnce();
      expect(screen.getByRole("dialog", { name: "Navigation menu" })).toBe(drawer());
      expect(drawer().open).toBe(true);
      expect(menuButton()).toHaveAttribute("aria-expanded", "true");
      expect(within(drawer()).getByRole("button", { name: "Close navigation menu" })).toHaveFocus();
      expect(isScrollLocked()).toBe(true);
      const nav = within(drawer()).getByRole("navigation", { name: "Primary navigation" });
      expect(within(nav).getAllByRole("link").map((link) => link.getAttribute("href")))
        .toEqual(["/", "/transactions", "/budgets", "/categories"]);
      expect(within(nav).getByRole("link", { name: "Transactions" })).toHaveAttribute("aria-current", "page");
    });

    it("closes with the close button and returns focus to the menu button", async () => {
      const { user } = await openDrawer();

      await user.click(within(drawer()).getByRole("button", { name: "Close navigation menu" }));

      expect(drawer().open).toBe(false);
      expect(menuButton()).toHaveAttribute("aria-expanded", "false");
      expect(menuButton()).toHaveFocus();
      expect(isScrollLocked()).toBe(false);
    });

    it("closes on Escape (the dialog cancel event)", async () => {
      await openDrawer();
      const cancel = new Event("cancel", { cancelable: true });

      fireEvent(drawer(), cancel);

      expect(cancel.defaultPrevented).toBe(true);
      expect(drawer().open).toBe(false);
      expect(menuButton()).toHaveFocus();
    });

    it("closes on a backdrop click but not on a click inside the panel", async () => {
      const { user } = await openDrawer();

      await user.click(within(drawer()).getByRole("navigation"));
      expect(drawer().open).toBe(true);

      fireEvent.click(drawer());
      expect(drawer().open).toBe(false);
      expect(isScrollLocked()).toBe(false);
    });

    it("closes after following a link and shows the new page", async () => {
      const { user } = await openDrawer();

      await user.click(within(drawer()).getByRole("link", { name: "Budgets" }));

      expect(screen.getByRole("heading", { name: "Budgets page" })).toBeInTheDocument();
      expect(drawer().open).toBe(false);
      expect(menuButton()).toHaveFocus();
      expect(isScrollLocked()).toBe(false);
    });

    it("closes when following the brand link or the current page's link", async () => {
      const { user } = await openDrawer();
      await user.click(within(drawer()).getByRole("link", { name: "Transactions" }));
      expect(drawer().open).toBe(false);

      await user.click(menuButton());
      await user.click(within(drawer()).getByRole("link", { name: "FinTrack" }));
      expect(drawer().open).toBe(false);
      expect(screen.getByRole("heading", { name: "Dashboard page" })).toBeInTheDocument();
    });

    it("closes when the route changes another way, such as browser back or forward", async () => {
      const { user } = await openDrawer("/");

      // jsdom does not make the page inert, so a page control can stand in for history.
      await user.click(screen.getByRole("button", { name: "Go to budgets" }));

      expect(screen.getByRole("heading", { name: "Budgets page" })).toBeInTheDocument();
      expect(drawer().open).toBe(false);
      expect(isScrollLocked()).toBe(false);
    });

    it("closes when the window widens to the desktop layout", async () => {
      const listeners = new Set<() => void>();
      const query = {
        matches: false,
        addEventListener: (_type: string, listener: () => void) => listeners.add(listener),
        removeEventListener: (_type: string, listener: () => void) => listeners.delete(listener),
      };
      const matchMedia = vi.fn(() => query);
      vi.stubGlobal("matchMedia", matchMedia);
      try {
        await openDrawer();
        expect(matchMedia).toHaveBeenCalledWith("(width > 1100px)");
        expect(drawer().open).toBe(true);

        query.matches = true;
        listeners.forEach((listener) => listener());

        await vi.waitFor(() => expect(drawer().open).toBe(false));
        expect(listeners.size).toBe(0);
      } finally {
        vi.unstubAllGlobals();
      }
    });

    it("stays in sync when the browser closes the dialog itself", async () => {
      await openDrawer();

      drawer().close();

      await vi.waitFor(() => expect(menuButton()).toHaveAttribute("aria-expanded", "false"));
      expect(isScrollLocked()).toBe(false);
      expect(menuButton()).toHaveFocus();
    });

    it("is unaffected by the desktop collapse preference", async () => {
      window.localStorage.setItem(SIDEBAR_COLLAPSED_KEY, "true");
      await openDrawer();

      // Collapsed styles are scoped to the sidebar, which never contains the drawer.
      expect(sidebar()).not.toContainElement(drawer());
      const nav = within(drawer()).getByRole("navigation", { name: "Primary navigation" });
      for (const name of ["Dashboard", "Transactions", "Budgets", "Categories"]) {
        expect(within(nav).getByRole("link", { name })).toBeInTheDocument();
      }
    });

    it("cleans up when the shell unmounts, such as after logout", async () => {
      const { view } = await openDrawer();
      expect(isScrollLocked()).toBe(true);

      view.unmount();

      expect(isScrollLocked()).toBe(false);
    });
  });
});
