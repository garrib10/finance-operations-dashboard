import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { PrimaryNav } from "./PrimaryNav";

function renderAt(path: string): HTMLElement {
  render(<MemoryRouter initialEntries={[path]}><PrimaryNav /></MemoryRouter>);
  return screen.getByRole("navigation", { name: "Primary navigation" });
}

function currentLinks(nav: HTMLElement): HTMLElement[] {
  return within(nav).getAllByRole("link").filter((link) => link.hasAttribute("aria-current"));
}

describe("PrimaryNav", () => {
  it("lists only the approved destinations, in order", () => {
    const nav = renderAt("/");

    const label = (link: HTMLElement) => link.querySelector(".primary-nav__label")?.textContent;
    expect(within(nav).getAllByRole("link").map((link) => [label(link), link.getAttribute("href")]))
      .toEqual([["Dashboard", "/"], ["Transactions", "/transactions"], ["Budgets", "/budgets"],
        ["Categories", "/categories"]]);
    expect(within(nav).queryByRole("link", { name: /profile|settings/i })).not.toBeInTheDocument();
  });

  it.each([
    ["/", "Dashboard"],
    ["/transactions", "Transactions"],
    ["/budgets", "Budgets"],
    ["/transactions?page=2#history", "Transactions"],
    ["/budgets/", "Budgets"],
    ["/categories", "Categories"],
  ])("marks only the current destination on %s", (path, label) => {
    const nav = renderAt(path);

    expect(within(nav).getByRole("link", { name: label })).toHaveAttribute("aria-current", "page");
    expect(currentLinks(nav)).toHaveLength(1);
  });

  it.each(["/profile", "/settings"])("marks no primary destination on %s", (path) => {
    expect(currentLinks(renderAt(path))).toHaveLength(0);
  });

  it("names links by their labels and hides the icons and tooltips", () => {
    const nav = renderAt("/");

    for (const link of within(nav).getAllByRole("link")) {
      const label = link.querySelector(".primary-nav__label")?.textContent ?? "";
      expect(label).not.toBe("");
      expect(link).toHaveAccessibleName(label);
      expect(link.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
      // The collapsed-mode tooltip repeats the label but stays out of the accessible name.
      expect(link.querySelector(".sidebar-tooltip")).toHaveAttribute("aria-hidden", "true");
      expect(link.querySelector(".sidebar-tooltip")).toHaveTextContent(label);
    }
  });
});
