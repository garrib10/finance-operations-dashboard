/**
 * Browser code always calls relative /api/... URLs on its own origin. In production
 * the Vercel function in api/proxy.ts forwards them to the backend; locally the Vite
 * dev server proxies them (see devProxy.ts). Same-origin requests keep the HttpOnly
 * refresh cookie first-party, so VITE_API_BASE_URL is no longer used.
 */
export const API_BASE_URL = "";
