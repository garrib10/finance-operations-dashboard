// @vitest-environment node
import { createServer, type IncomingHttpHeaders, type Server } from "node:http";
import type { AddressInfo } from "node:net";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import proxyFunction, {
  config,
  MAX_REQUEST_BYTES,
  parseBackendOrigin,
  proxyApiRequest,
  type ProxyOptions,
} from "../api/proxy";

/** What the stand-in backend saw for one request. */
interface Seen {
  method: string;
  url: string;
  headers: IncomingHttpHeaders;
  body: Buffer;
}

type Reply = (seen: Seen, res: import("node:http").ServerResponse) => void;

const REFRESH_COOKIE = "__Secure-fintrack_refresh=abcDEF123_-abcDEF123_-abcDEF123_-abcDEF123_; Max-Age=2592000; Expires=Tue, 27 Oct 2026 12:00:00 GMT; Path=/api/auth; Secure; HttpOnly; SameSite=Lax";
const CLEARED_COOKIE = "__Secure-fintrack_refresh=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/api/auth; Secure; HttpOnly; SameSite=Lax";
const FRONTEND = "https://fintrack.example.com";

let server: Server;
let backend: string;
let seen: Seen[];
let reply: Reply;
let logged: string[];

function json(res: import("node:http").ServerResponse, status: number, body: unknown, headers: Record<string, string | string[]> = {}) {
  res.writeHead(status, { "Content-Type": "application/json", ...headers });
  res.end(JSON.stringify(body));
}

beforeAll(async () => {
  server = createServer((req, res) => {
    const chunks: Buffer[] = [];
    req.on("data", (chunk: Buffer) => chunks.push(chunk));
    req.on("end", () => {
      const entry = { method: req.method ?? "", url: req.url ?? "", headers: req.headers, body: Buffer.concat(chunks) };
      seen.push(entry);
      reply(entry, res);
    });
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  backend = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
});

afterAll(async () => {
  server.closeAllConnections();
  await new Promise<void>((resolve) => server.close(() => resolve()));
});

beforeEach(() => {
  seen = [];
  logged = [];
  reply = (_seen, res) => json(res, 200, { ok: true });
});

afterEach(() => {
  vi.unstubAllEnvs();
});

/** A browser request as Vercel delivers it after the /api/:fintrackPath* rewrite. */
function rewritten(path: string, init: RequestInit = {}, query = ""): Request {
  const params = new URLSearchParams(query);
  params.set("fintrackPath", path);
  return new Request(`${FRONTEND}/api/proxy?${params}`, init);
}

function proxy(request: Request, options: Partial<ProxyOptions> = {}) {
  return proxyApiRequest(request, { backendOrigin: backend, log: (message) => logged.push(message), ...options });
}

describe("request forwarding", () => {
  it("forwards the path and query under /api to the fixed backend", async () => {
    const response = await proxy(rewritten("transactions", {}, "page=2&size=25&type=EXPENSE&type=INCOME"));

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ ok: true });
    expect(seen).toHaveLength(1);
    expect(seen[0].method).toBe("GET");
    expect(seen[0].url).toBe("/api/transactions?page=2&size=25&type=EXPENSE&type=INCOME");
    expect(seen[0].body).toHaveLength(0);
  });

  it("accepts the path as one value or as repeated segments", async () => {
    await proxy(rewritten("transactions/42"));
    const repeated = new URLSearchParams([["fintrackPath", "account"], ["fintrackPath", "photo"]]);
    await proxy(new Request(`${FRONTEND}/api/proxy?${repeated}`));

    expect(seen.map((entry) => entry.url)).toEqual(["/api/transactions/42", "/api/account/photo"]);
  });

  it("falls back to the original pathname when no rewrite parameter is present", async () => {
    await proxy(new Request(`${FRONTEND}/api/auth/me?x=1`));

    expect(seen[0].url).toBe("/api/auth/me?x=1");
  });

  it("forwards application headers, cookies, and the browser Origin", async () => {
    await proxy(rewritten("auth/refresh", {
      method: "POST",
      headers: {
        Authorization: "Bearer access-token-value",
        Accept: "application/json",
        "Accept-Language": "en-US",
        Origin: FRONTEND,
        Referer: `${FRONTEND}/settings`,
        "X-FinTrack-CSRF": "1",
        Cookie: "__Secure-fintrack_refresh=refresh-value; other=1",
      },
    }));

    const headers = seen[0].headers;
    expect(seen[0].method).toBe("POST");
    expect(seen[0].body).toHaveLength(0);
    expect(headers.authorization).toBe("Bearer access-token-value");
    expect(headers.accept).toBe("application/json");
    expect(headers["accept-language"]).toBe("en-US");
    expect(headers.origin).toBe(FRONTEND);
    expect(headers.referer).toBe(`${FRONTEND}/settings`);
    expect(headers["x-fintrack-csrf"]).toBe("1");
    expect(headers.cookie).toBe("__Secure-fintrack_refresh=refresh-value; other=1");
  });

  it("forwards a JSON body unchanged", async () => {
    const body = JSON.stringify({ email: "demo@fintrack.dev", password: "Password123!" });

    await proxy(rewritten("auth/login", { method: "POST", headers: { "Content-Type": "application/json" }, body }));

    expect(seen[0].headers["content-type"]).toBe("application/json");
    expect(seen[0].body.toString("utf8")).toBe(body);
  });

  it("forwards multipart photo bytes and the browser's boundary untouched", async () => {
    const bytes = new Uint8Array(256 * 1024);
    for (let i = 0; i < bytes.length; i++) bytes[i] = (i * 31 + 7) % 256;
    const form = new FormData();
    form.append("photo", new File([bytes], "https://evil.example/../upstream.png", { type: "image/png" }));
    const request = rewritten("account/photo", { method: "PUT", body: form });
    const contentType = request.headers.get("content-type") ?? "";

    const response = await proxy(request);

    expect(response.status).toBe(200);
    expect(contentType).toMatch(/^multipart\/form-data; boundary=/);
    expect(seen[0].url).toBe("/api/account/photo");
    expect(seen[0].headers["content-type"]).toBe(contentType);
    const boundary = contentType.split("boundary=")[1];
    expect(seen[0].body.toString("latin1")).toContain(`--${boundary}`);
    expect(seen[0].body.indexOf(Buffer.from(bytes))).toBeGreaterThan(0);
  });

  it("sends GET and HEAD without a body", async () => {
    await proxy(rewritten("health", { method: "HEAD" }));
    await proxy(rewritten("health"));

    expect(seen.map((entry) => [entry.method, entry.body.length])).toEqual([["HEAD", 0], ["GET", 0]]);
  });

  it("never forwards Host, hop-by-hop, or headers named by Connection", async () => {
    await proxy(rewritten("auth/login", {
      method: "POST",
      headers: {
        Connection: "x-fintrack-csrf",
        "X-FinTrack-CSRF": "1",
        "Proxy-Authorization": "Basic secret",
        "X-Forwarded-Host": "evil.example",
        "X-Backend-Origin": "https://evil.example",
        "Content-Length": "999",
      },
      body: "{}",
    }));

    const headers = seen[0].headers;
    expect(headers.host).toBe(new URL(backend).host);
    expect(headers["x-fintrack-csrf"]).toBeUndefined();
    expect(headers["proxy-authorization"]).toBeUndefined();
    expect(headers["x-forwarded-host"]).toBeUndefined();
    expect(headers["x-backend-origin"]).toBeUndefined();
    expect(headers["content-length"]).toBe("2");
  });
});

describe("response forwarding", () => {
  it("passes JSON bodies and statuses through without turning errors into success", async () => {
    for (const status of [401, 403, 409, 413, 415, 429, 500, 503]) {
      reply = (_seen, res) => json(res, status, { status, message: `failure ${status}` });
      const response = await proxy(rewritten("dashboard"));
      expect(response.status).toBe(status);
      expect(await response.json()).toEqual({ status, message: `failure ${status}` });
    }
  });

  it("preserves the ACCESS_TOKEN_EXPIRED body the frontend refreshes on", async () => {
    const expired = { status: 401, error: "Unauthorized", message: "Access token has expired", code: "ACCESS_TOKEN_EXPIRED" };
    reply = (_seen, res) => json(res, 401, expired);

    const response = await proxy(rewritten("budgets"));

    expect(response.status).toBe(401);
    expect(await response.json()).toEqual(expired);
    expect(response.headers.get("content-type")).toBe("application/json");
  });

  it("forwards an empty 204 and a cookie-clearing header", async () => {
    reply = (_seen, res) => {
      res.writeHead(204, { "Set-Cookie": CLEARED_COOKIE });
      res.end();
    };

    const response = await proxy(rewritten("auth/logout", { method: "POST" }));

    expect(response.status).toBe(204);
    expect(await response.text()).toBe("");
    expect(response.headers.getSetCookie()).toEqual([CLEARED_COOKIE]);
  });

  it("keeps every Set-Cookie separate with all refresh-cookie attributes and no Domain", async () => {
    reply = (_seen, res) => json(res, 200, { accessToken: "x" }, { "Set-Cookie": [REFRESH_COOKIE, "second=2; Path=/; HttpOnly"] });

    const response = await proxy(rewritten("auth/login", { method: "POST", body: "{}" }));

    const cookies = response.headers.getSetCookie();
    expect(cookies).toEqual([REFRESH_COOKIE, "second=2; Path=/; HttpOnly"]);
    expect(cookies[0]).toContain("HttpOnly");
    expect(cookies[0]).toContain("Secure");
    expect(cookies[0]).toContain("SameSite=Lax");
    expect(cookies[0]).toContain("Path=/api/auth");
    expect(cookies[0]).toContain("Max-Age=2592000");
    expect(cookies[0]).toContain("Expires=");
    expect(cookies[0].toLowerCase()).not.toContain("domain");
  });

  it("marks every response no-store and drops hop-by-hop and upstream-revealing headers", async () => {
    reply = (_seen, res) => json(res, 200, { ok: true }, {
      "Cache-Control": "public, max-age=600",
      "Keep-Alive": "timeout=5",
      Connection: "keep-alive, x-drop-me",
      "X-Drop-Me": "1",
      "X-Railway-Edge": "railway/us-west",
      "X-Railway-Request-Id": "abc",
      Server: "railway-edge",
      "X-Powered-By": "Spring",
      Location: "https://backend.internal/elsewhere",
      Vary: "Origin",
      "X-Content-Type-Options": "nosniff",
    });

    const response = await proxy(rewritten("auth/me"));

    expect(response.headers.get("cache-control")).toBe("no-store");
    for (const name of ["keep-alive", "connection", "x-drop-me", "x-railway-edge", "x-railway-request-id",
      "server", "x-powered-by", "location", "content-length", "transfer-encoding"]) {
      expect(response.headers.has(name)).toBe(false);
    }
    expect(response.headers.get("vary")).toBe("Origin");
    expect(response.headers.get("x-content-type-options")).toBe("nosniff");
  });

  it("returns no body for HEAD", async () => {
    const response = await proxy(rewritten("health", { method: "HEAD" }));

    expect(response.status).toBe(200);
    expect(await response.text()).toBe("");
  });
});

describe("upstream selection and SSRF protection", () => {
  it.each([
    undefined,
    "",
    " https://api.example.com",
    "not a url",
    "ftp://api.example.com",
    "http://api.example.com",
    "http://10.0.0.5:8080",
    "https://user:pass@api.example.com",
    "https://api.example.com/api",
    "https://api.example.com/path/",
    "https://api.example.com?target=x",
    "https://api.example.com#x",
    "https://api.example.com/?",
    "javascript:alert(1)",
  ])("rejects BACKEND_ORIGIN %s with a sanitized 503", async (value) => {
    const response = await proxy(rewritten("health"), { backendOrigin: value });

    expect(response.status).toBe(503);
    const body = await response.json();
    expect(body).toMatchObject({ status: 503, code: "PROXY_MISCONFIGURED" });
    expect(JSON.stringify(body) + logged.join()).not.toContain("api.example.com");
    expect(logged).toEqual(["api-proxy: BACKEND_ORIGIN is missing or invalid"]);
    expect(seen).toHaveLength(0);
  });

  it.each([
    ["https://api.example.com", "https://api.example.com"],
    ["https://api.example.com/", "https://api.example.com"],
    ["https://api.example.com:8443", "https://api.example.com:8443"],
    ["http://localhost:8080", "http://localhost:8080"],
    ["http://127.0.0.1:8080/", "http://127.0.0.1:8080"],
  ])("accepts %s as origin %s", (value, origin) => {
    expect(parseBackendOrigin(value)).toEqual({ ok: true, origin });
  });

  it.each([
    ["path traversal", "../../admin"],
    ["dot segment", "auth/./me"],
    ["empty segment", "auth//me"],
    ["absolute URL in the path", "https://evil.example/api/x"],
    ["protocol-relative path", "/evil.example/x"],
    ["backslash", "auth\\me"],
    ["control character", "auth\nme"],
  ])("rejects a %s without contacting any upstream", async (_name, path) => {
    const response = await proxy(rewritten(path));

    expect(response.status).toBe(404);
    expect(seen).toHaveLength(0);
  });

  it.each([
    ["an encoded authority", "%2F%2Fevil.example%2Fx"],
    ["an encoded scheme and authority", "https:%2F%2Fevil.example"],
  ])("refuses %s without contacting any upstream", async (_name, path) => {
    const response = await proxy(new Request(`${FRONTEND}/api/${path}`));

    expect(response.status).toBe(404);
    expect(seen).toHaveLength(0);
  });

  it("keeps an encoded query and fragment inside one segment on the fixed backend", async () => {
    await proxy(new Request(`${FRONTEND}/api/auth%3Fx=1%23y`));

    expect(seen).toHaveLength(1);
    expect(seen[0].headers.host).toBe(new URL(backend).host);
    expect(seen[0].url).toBe("/api/auth%3Fx%3D1%23y");
  });

  it("keeps encoded characters inside a single path segment", async () => {
    await proxy(rewritten("transactions/a%2F..%2Fb"));

    expect(seen[0].url).toBe("/api/transactions/a%252F..%252Fb");
  });

  it("ignores upstream overrides in the query, headers, and body", async () => {
    const request = rewritten("auth/me", {
      headers: { Host: "evil.example", "X-Forwarded-Host": "evil.example", "X-Upstream": "https://evil.example" },
    }, "backend=https://evil.example&url=https://evil.example");

    await proxy(request);

    expect(seen).toHaveLength(1);
    expect(seen[0].headers.host).toBe(new URL(backend).host);
    expect(seen[0].url).toBe("/api/auth/me?backend=https%3A%2F%2Fevil.example&url=https%3A%2F%2Fevil.example");
  });

  it.each(["/api/proxy", "/api", "/", "/api/"])("returns 404 for %s without a forwarded path", async (path) => {
    const response = await proxy(new Request(`${FRONTEND}${path}`));

    expect(response.status).toBe(404);
    expect(seen).toHaveLength(0);
  });

  it("returns 404 for a malformed percent-encoded pathname", async () => {
    const response = await proxy(new Request(`${FRONTEND}/api/%E0%A4%A`));

    expect(response.status).toBe(404);
  });
});

describe("limits and failures", () => {
  it("rejects a declared or actual body above the limit before forwarding", async () => {
    const declared = await proxy(rewritten("account/photo", {
      method: "PUT", headers: { "Content-Length": String(MAX_REQUEST_BYTES + 1) }, body: "x",
    }));
    const actual = await proxy(rewritten("account/photo", { method: "PUT", body: new Uint8Array(MAX_REQUEST_BYTES + 1) }));

    for (const response of [declared, actual]) {
      expect(response.status).toBe(413);
      expect(await response.json()).toMatchObject({ code: "PAYLOAD_TOO_LARGE" });
    }
    expect(seen).toHaveLength(0);
  });

  it("returns a sanitized 504 when the backend is too slow", async () => {
    reply = () => { /* never answers */ };

    const response = await proxy(rewritten("dashboard"), { timeoutMs: 50 });

    expect(response.status).toBe(504);
    const body = await response.json();
    expect(body).toMatchObject({ code: "UPSTREAM_TIMEOUT" });
    expect(JSON.stringify(body)).not.toContain("127.0.0.1");
    expect(logged).toEqual(["api-proxy: upstream timeout"]);
  });

  it("returns a sanitized 502 when the backend is unreachable", async () => {
    const closed = createServer();
    await new Promise<void>((resolve) => closed.listen(0, "127.0.0.1", resolve));
    const port = (closed.address() as AddressInfo).port;
    await new Promise<void>((resolve) => closed.close(() => resolve()));

    const response = await proxy(rewritten("dashboard"), { backendOrigin: `http://127.0.0.1:${port}` });

    expect(response.status).toBe(502);
    const body = await response.json();
    expect(body).toMatchObject({ status: 502, code: "UPSTREAM_UNAVAILABLE" });
    expect(JSON.stringify(body)).not.toMatch(/127\.0\.0\.1|ECONNREFUSED|fetch failed/);
    expect(response.headers.get("cache-control")).toBe("no-store");
  });

  it("logs only fixed categories, never headers, cookies, tokens, or bodies", async () => {
    const log = vi.spyOn(console, "error").mockImplementation(() => undefined);
    reply = () => { /* never answers */ };

    await proxyApiRequest(rewritten("auth/login", {
      method: "POST",
      headers: { Authorization: "Bearer secret-access", Cookie: "__Secure-fintrack_refresh=secret-refresh" },
      body: JSON.stringify({ password: "secret-password" }),
    }), { backendOrigin: backend, timeoutMs: 50 });
    await proxyApiRequest(rewritten("auth/me"), { backendOrigin: "https://user:secret-origin-password@x.example" });

    const output = JSON.stringify(log.mock.calls);
    expect(log).toHaveBeenCalledTimes(2);
    expect(output).not.toMatch(/secret-access|secret-refresh|secret-password|secret-origin-password|Bearer|Cookie/);
    log.mockRestore();
  });
});

describe("Vercel function entry point", () => {
  it("reads only the server-side BACKEND_ORIGIN and bounds its duration", async () => {
    vi.stubEnv("BACKEND_ORIGIN", backend);
    vi.stubEnv("VITE_BACKEND_ORIGIN", "https://evil.example");

    const response = await proxyFunction.fetch(rewritten("health"));

    expect(response.status).toBe(200);
    expect(seen[0].url).toBe("/api/health");
    expect(config.maxDuration).toBeGreaterThan(25);
  });

  it("fails closed when BACKEND_ORIGIN is not configured", async () => {
    vi.stubEnv("BACKEND_ORIGIN", undefined as unknown as string);
    const log = vi.spyOn(console, "error").mockImplementation(() => undefined);

    const response = await proxyFunction.fetch(rewritten("health"));

    expect(response.status).toBe(503);
    expect(seen).toHaveLength(0);
    log.mockRestore();
  });
});
