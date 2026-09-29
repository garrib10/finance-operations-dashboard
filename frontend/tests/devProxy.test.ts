// @vitest-environment node
import { createServer as createHttpServer, type IncomingHttpHeaders, type Server } from "node:http";
import type { AddressInfo } from "node:net";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { createServer, type ViteDevServer } from "vite";
import { apiDevProxy, DEFAULT_DEV_API_TARGET } from "../devProxy";

const COOKIE = "fintrack_refresh=abc; Max-Age=60; Expires=Tue, 27 Oct 2026 12:00:00 GMT; Path=/api/auth; HttpOnly; SameSite=Lax";

let backend: Server;
let vite: ViteDevServer;
let viteOrigin: string;
const seen: Array<{ url: string; headers: IncomingHttpHeaders; body: string }> = [];

beforeAll(async () => {
  backend = createHttpServer((req, res) => {
    let body = "";
    req.on("data", (chunk) => { body += chunk; });
    req.on("end", () => {
      seen.push({ url: req.url ?? "", headers: req.headers, body });
      res.writeHead(200, { "Content-Type": "application/json", "Set-Cookie": [COOKIE, "other=1; Path=/"] });
      res.end(JSON.stringify({ accessToken: "t" }));
    });
  });
  await new Promise<void>((resolve) => backend.listen(0, "127.0.0.1", resolve));
  const target = `http://127.0.0.1:${(backend.address() as AddressInfo).port}`;

  vite = await createServer({
    configFile: false,
    logLevel: "silent",
    server: { port: 0, host: "127.0.0.1", strictPort: false, proxy: apiDevProxy(target), hmr: false, ws: false },
  });
  await vite.listen();
  viteOrigin = `http://127.0.0.1:${(vite.httpServer!.address() as AddressInfo).port}`;
}, 60_000);

afterAll(async () => {
  await vite?.close();
  backend.closeAllConnections();
  await new Promise<void>((resolve) => backend.close(() => resolve()));
});

describe("Vite dev proxy", () => {
  it("defaults to the local Spring Boot backend and keeps the /api prefix", () => {
    expect(DEFAULT_DEV_API_TARGET).toBe("http://localhost:8080");
    expect(apiDevProxy()).toEqual({
      "/api": { target: "http://localhost:8080", changeOrigin: false, secure: false, ws: false },
    });
  });

  it("forwards the browser Origin, custom header, and cookie, and returns Set-Cookie", async () => {
    const response = await fetch(`${viteOrigin}/api/auth/refresh?probe=1`, {
      method: "POST",
      headers: {
        Origin: "http://localhost:5173",
        "X-FinTrack-CSRF": "1",
        Cookie: "fintrack_refresh=abc",
      },
    });

    expect(response.status).toBe(200);
    expect(response.headers.getSetCookie()).toEqual([COOKIE, "other=1; Path=/"]);
    const request = seen.at(-1)!;
    expect(request.url).toBe("/api/auth/refresh?probe=1");
    // changeOrigin: false keeps what request protection checks unchanged.
    expect(request.headers.origin).toBe("http://localhost:5173");
    expect(request.headers.host).toBe(new URL(viteOrigin).host);
    expect(request.headers["x-fintrack-csrf"]).toBe("1");
    expect(request.headers.cookie).toBe("fintrack_refresh=abc");
  });

  it("forwards JSON bodies and leaves non-API paths to Vite", async () => {
    const before = seen.length;
    await fetch(`${viteOrigin}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Origin: "http://localhost:5173", "X-FinTrack-CSRF": "1" },
      body: JSON.stringify({ email: "a@b.c", password: "x" }),
    });
    await fetch(`${viteOrigin}/settings`);

    expect(seen.length).toBe(before + 1);
    expect(seen.at(-1)!.body).toBe(JSON.stringify({ email: "a@b.c", password: "x" }));
  });
});
