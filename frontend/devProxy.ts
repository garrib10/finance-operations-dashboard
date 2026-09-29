import type { ProxyOptions } from "vite";

/** Local Spring Boot backend. Never a hosted URL; override with DEV_API_PROXY_TARGET. */
export const DEFAULT_DEV_API_TARGET = "http://localhost:8080";

/**
 * Development/preview proxy for relative /api calls, mirroring the Vercel proxy.
 * The /api prefix is kept, and changeOrigin stays false so the backend sees the
 * browser's own Host and Origin (http://localhost:5173), which request protection
 * and the local FRONTEND_URLS default expect. Cookies and Set-Cookie pass through.
 */
export function apiDevProxy(target: string = DEFAULT_DEV_API_TARGET): Record<string, ProxyOptions> {
  return {
    "/api": {
      target,
      changeOrigin: false,
      secure: false,
      ws: false,
    },
  };
}
