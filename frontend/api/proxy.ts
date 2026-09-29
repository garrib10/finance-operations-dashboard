/// <reference types="node" />

/**
 * Vercel Function behind the `/api/:fintrackPath*` rewrite in vercel.json.
 * BACKEND_ORIGIN is a server-only project variable (never VITE_-prefixed) naming
 * this environment's backend origin, e.g. https://api-staging.example.com.
 *
 * Self-contained on purpose: Vercel compiles each file in /api on its own, so this
 * file imports nothing relative. Tests live in ../tests, never under /api (every file
 * here would be deployed as a function).
 *
 * Same-origin API proxy: the browser calls /api/* on the frontend host, and this
 * server-side code forwards the request to the one backend named by BACKEND_ORIGIN.
 * The refresh cookie therefore stays first-party to the frontend host.
 *
 * Safety rules:
 * - The upstream origin comes only from BACKEND_ORIGIN (validated); nothing in the
 *   request (path, query, headers, cookies, body) can change the host or scheme.
 * - Only headers the application needs are forwarded; hop-by-hop and host headers
 *   are never forwarded. Nothing about requests is logged except fixed categories.
 * - Responses are never cached, and every Set-Cookie value is passed through
 *   unchanged (no Domain is added, no attribute is removed).
 */

/** Rewrite parameter carrying the original path below /api (see vercel.json). */
export const PATH_PARAM = "fintrackPath";

/** Above the backend's 3 MB multipart limit, below Vercel's 4.5 MB request limit. */
export const MAX_REQUEST_BYTES = 4 * 1024 * 1024;

/** Below the function's maxDuration so a slow backend gets a clean 504. */
export const UPSTREAM_TIMEOUT_MS = 25_000;

const FORWARDED_REQUEST_HEADERS = [
  "accept",
  "accept-language",
  "authorization",
  "content-type",
  "cookie",
  "origin",
  "referer",
  "x-fintrack-csrf",
];

const HOP_BY_HOP_HEADERS = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "proxy-connection",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
]);

/**
 * Response headers never passed back. The body is re-sent decoded, so its original
 * length and encoding no longer apply; the rest would only reveal the upstream.
 */
const DROPPED_RESPONSE_HEADERS = new Set([
  "content-encoding",
  "content-length",
  "location",
  "server",
  "set-cookie",
  "via",
  "x-powered-by",
]);

const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]"]);

export type BackendOrigin = { ok: true; origin: string } | { ok: false };

/**
 * Accepts only an absolute origin: https (http only for localhost), no credentials,
 * no path other than "/", no query or fragment. Never echoes the configured value.
 */
export function parseBackendOrigin(value: string | undefined): BackendOrigin {
  if (!value || value.trim() !== value) {
    return { ok: false };
  }
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return { ok: false };
  }
  const secure = url.protocol === "https:";
  const localHttp = url.protocol === "http:" && LOCAL_HOSTS.has(url.hostname);
  const bareOrigin = url.username === "" && url.password === ""
    && (url.pathname === "/" || url.pathname === "") && url.search === "" && url.hash === ""
    && !value.includes("?") && !value.includes("#");
  if (!(secure || localHttp) || !bareOrigin) {
    return { ok: false };
  }
  return { ok: true, origin: url.origin };
}

function errorResponse(status: number, error: string, message: string, code: string): Response {
  return Response.json(
    { timestamp: new Date().toISOString(), status, error, message, code },
    { status, headers: { "Cache-Control": "no-store" } },
  );
}

const notFound = () => errorResponse(404, "Not Found", "The requested resource was not found.", "NOT_FOUND");

/** Segments below /api, taken from the rewrite parameter or, failing that, the pathname. */
function apiPathSegments(url: URL): string[] | null {
  const fromRewrite = url.searchParams.getAll(PATH_PARAM);
  let path: string;
  if (fromRewrite.length > 0) {
    path = fromRewrite.join("/");
  } else if (url.pathname.startsWith("/api/") && url.pathname !== "/api/proxy") {
    try {
      path = decodeURIComponent(url.pathname.slice("/api/".length));
    } catch {
      return null;
    }
  } else {
    return null;
  }
  const segments = path.split("/");
  const safe = segments.length > 0 && segments.every((segment) =>
    segment !== "" && segment !== "." && segment !== ".." && !segment.includes("\\")
    && ![...segment].some((character) => character.charCodeAt(0) < 0x20 || character.charCodeAt(0) === 0x7f));
  return safe ? segments : null;
}

function connectionTokens(headers: Headers): Set<string> {
  return new Set((headers.get("connection") ?? "")
    .split(",").map((token) => token.trim().toLowerCase()).filter(Boolean));
}

function forwardedRequestHeaders(incoming: Headers): Headers {
  const named = connectionTokens(incoming);
  const outgoing = new Headers();
  for (const name of FORWARDED_REQUEST_HEADERS) {
    const value = incoming.get(name);
    if (value !== null && !named.has(name)) {
      outgoing.set(name, value);
    }
  }
  return outgoing;
}

function forwardedResponseHeaders(upstream: Headers): Headers {
  const named = connectionTokens(upstream);
  const outgoing = new Headers();
  upstream.forEach((value, name) => {
    const lower = name.toLowerCase();
    if (!HOP_BY_HOP_HEADERS.has(lower) && !DROPPED_RESPONSE_HEADERS.has(lower)
      && !named.has(lower) && !lower.startsWith("x-railway")) {
      outgoing.set(name, value);
    }
  });
  // Each cookie stays a separate header, byte for byte (attributes untouched).
  for (const cookie of upstream.getSetCookie()) {
    outgoing.append("set-cookie", cookie);
  }
  // API responses are user-specific or security-sensitive: never cache them.
  outgoing.set("cache-control", "no-store");
  return outgoing;
}

export interface ProxyOptions {
  backendOrigin: string | undefined;
  fetchImpl?: typeof fetch;
  timeoutMs?: number;
  log?: (message: string) => void;
}

export async function proxyApiRequest(request: Request, options: ProxyOptions): Promise<Response> {
  const log = options.log ?? ((message: string) => console.error(message));
  const backend = parseBackendOrigin(options.backendOrigin);
  if (!backend.ok) {
    log("api-proxy: BACKEND_ORIGIN is missing or invalid");
    return errorResponse(503, "Service Unavailable",
      "The service is temporarily unavailable. Please try again.", "PROXY_MISCONFIGURED");
  }

  const incoming = new URL(request.url);
  const segments = apiPathSegments(incoming);
  if (!segments) {
    return notFound();
  }

  // The target cannot leave BACKEND_ORIGIN or /api/: the path always begins with a
  // single "/api/", and every validated segment is encoded, so "/", "?", "#", and ":"
  // inside a segment can never form a new authority, path escape, or query.
  const target = new URL(`/api/${segments.map(encodeURIComponent).join("/")}`, backend.origin);
  incoming.searchParams.forEach((value, name) => {
    if (name !== PATH_PARAM) target.searchParams.append(name, value);
  });

  const method = request.method.toUpperCase();
  let body: ArrayBuffer | undefined;
  if (method !== "GET" && method !== "HEAD") {
    const declared = Number(request.headers.get("content-length") ?? "0");
    if (declared > MAX_REQUEST_BYTES) {
      return errorResponse(413, "Payload Too Large", "The request is too large.", "PAYLOAD_TOO_LARGE");
    }
    const bytes = await request.arrayBuffer();
    if (bytes.byteLength > MAX_REQUEST_BYTES) {
      return errorResponse(413, "Payload Too Large", "The request is too large.", "PAYLOAD_TOO_LARGE");
    }
    // Raw bytes, untouched: multipart bodies keep their boundary from Content-Type.
    body = bytes.byteLength > 0 ? bytes : undefined;
  }

  let upstream: Response;
  try {
    upstream = await (options.fetchImpl ?? fetch)(target, {
      method,
      headers: forwardedRequestHeaders(request.headers),
      body,
      redirect: "manual",
      signal: AbortSignal.timeout(options.timeoutMs ?? UPSTREAM_TIMEOUT_MS),
    });
  } catch (error) {
    const timedOut = error instanceof DOMException && error.name === "TimeoutError";
    log(`api-proxy: upstream ${timedOut ? "timeout" : "unavailable"}`);
    return timedOut
      ? errorResponse(504, "Gateway Timeout", "The service took too long to respond. Please try again.", "UPSTREAM_TIMEOUT")
      : errorResponse(502, "Bad Gateway", "The service is temporarily unavailable. Please try again.", "UPSTREAM_UNAVAILABLE");
  }

  const noBody = method === "HEAD" || upstream.status === 204 || upstream.status === 304;
  return new Response(noBody ? null : await upstream.arrayBuffer(), {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: forwardedResponseHeaders(upstream.headers),
  });
}

export const config = {
  maxDuration: 30,
};

export default {
  fetch(request: Request): Promise<Response> {
    return proxyApiRequest(request, { backendOrigin: process.env.BACKEND_ORIGIN });
  },
};
