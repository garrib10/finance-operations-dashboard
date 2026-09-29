// @vitest-environment node
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const root = fileURLToPath(new URL("..", import.meta.url));
const vercel = JSON.parse(readFileSync(`${root}vercel.json`, "utf8")) as {
  rewrites: Array<{ source: string; destination: string }>;
  routes?: unknown;
};

/** Vercel path-to-regexp style source with a raw regex group, as used by the SPA rule. */
function spaMatches(path: string): boolean {
  const [, spa] = vercel.rewrites;
  return new RegExp(`^${spa.source}$`).test(path);
}

describe("vercel.json routing", () => {
  it("sends /api to the proxy function before the SPA fallback", () => {
    expect(vercel.rewrites).toEqual([
      { source: "/api/:fintrackPath*", destination: "/api/proxy" },
      { source: "/((?!api(?:/|$)).*)", destination: "/" },
    ]);
    expect(vercel.routes).toBeUndefined();
  });

  it.each(["/api", "/api/", "/api/auth/login", "/api/auth/refresh", "/api/account/photo", "/api/proxy"])(
    "never serves index.html for %s",
    (path) => {
      expect(spaMatches(path)).toBe(false);
    },
  );

  it.each(["/", "/login", "/settings", "/budgets", "/transactions?page=2", "/apiary", "/api-docs-page"])(
    "falls back to the SPA for application route %s",
    (path) => {
      expect(spaMatches(path.split("?")[0])).toBe(true);
    },
  );

  it("routes to a same-project function, never to an external or env-interpolated URL", () => {
    for (const { destination } of vercel.rewrites) {
      expect(destination.startsWith("/")).toBe(true);
      expect(destination).not.toMatch(/^\/\/|https?:|\$\{|\$[A-Z_]/);
    }
    expect(existsSync(`${root}api/proxy.ts`)).toBe(true);
  });

  it("deploys only the proxy as a function (no tests or helpers under /api)", () => {
    expect(readdirSync(`${root}api`)).toEqual(["proxy.ts"]);
  });

  it("keeps the proxy self-contained so Vercel can compile it alone", () => {
    const source = readFileSync(`${root}api/proxy.ts`, "utf8");

    expect(source).not.toMatch(/^import .* from ["']\.{1,2}\//m);
    expect(source).toContain("process.env.BACKEND_ORIGIN");
    // Server-only: never read through Vite's client env or a VITE_ variable.
    expect(source).not.toMatch(/import\.meta\.env|process\.env\.VITE_/);
  });
});
