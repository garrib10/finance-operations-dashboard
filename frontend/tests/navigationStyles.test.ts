// @vitest-environment node
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/** Style contracts that jsdom cannot see (it does not apply App.css), read from source. */
const src = fileURLToPath(new URL("../src/", import.meta.url));
const appCss = readFileSync(`${src}App.css`, "utf8");
const navigation = readFileSync(`${src}navigation.ts`, "utf8");
const desktopQuery = /DESKTOP_NAV_QUERY = "([^"]+)"/.exec(navigation)?.[1];

describe("navigation style contract", () => {
  it("switches between sidebar and drawer at the breakpoint the code uses", () => {
    expect(desktopQuery).toBe("(width > 1100px)");
    expect(appCss).toContain(`@media ${desktopQuery} {`);
    // The page layouts change at the same boundary, so they describe the content area.
    expect(appCss).toContain("@media (max-width: 1100px) {");
  });

  it("never animates the sidebar width", () => {
    expect(appCss).toContain(".app-sidebar {");
    expect(appCss).not.toMatch(/transition:[^;]*\b(all|width|grid-template-columns)\b/);
  });

  it("turns off the drawer animation for reduced motion", () => {
    expect(appCss).toMatch(/\.mobile-nav\[open\] \{\s*animation: mobile-nav-in/);
    const reduced = appCss.slice(appCss.indexOf("@media (prefers-reduced-motion: reduce) {"));
    expect(reduced).toMatch(/\.mobile-nav\[open\],\s*\.mobile-nav\[open\]::backdrop\s*\{\s*animation: none;/);
  });

  it("scopes collapsed-sidebar label hiding to the sidebar, not the drawer", () => {
    expect(appCss).toContain('.app-layout[data-sidebar-collapsed="true"] .app-sidebar .primary-nav__label {');
    expect(appCss).not.toMatch(/data-sidebar-collapsed="true"\] \.primary-nav/);
  });
});
