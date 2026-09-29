# Deployment Guide (v1.2.0)

FinTrack v1.2.0 adds refresh-token sessions (issue #18). The browser must reach the
backend **through the frontend's own origin** so the `HttpOnly` refresh cookie stays
first-party. This guide covers the topology, the variables per environment, the
staging rollout, rollback, and the smoke checklist.

Nothing here has been deployed yet. The staging smoke checklist must pass before
production.

## Topology

```text
Browser ──same-origin /api/*──▶ Vercel (static SPA + api/proxy.ts function)
                                   │ server-to-server HTTPS
                                   ▼
                                Railway (Spring Boot, prod profile) ──▶ MySQL
```

- The browser calls only relative paths (`/api/auth/login`, `/api/transactions`, …)
  on the Vercel host. It never calls the Railway domain directly.
- `vercel.json` rewrites `/api/:fintrackPath*` to the `api/proxy` function **before**
  the SPA fallback, and the SPA fallback explicitly excludes `/api`, so an API path
  can never be served `index.html`.
- `api/proxy.ts` forwards to `${BACKEND_ORIGIN}/api/<path>` with the original query
  string. `BACKEND_ORIGIN` is a server-only Vercel variable; the request cannot change
  the upstream host.
- The backend sets `__Secure-fintrack_refresh` (`HttpOnly; Secure; SameSite=Lax;
  Path=/api/auth`, no `Domain`). Through the proxy it becomes a cookie for the Vercel
  host, so no third-party cookies are needed.

### Why a function instead of an env-interpolated rewrite

Vercel can expand environment variables in legacy `routes` destinations, but that
path was rejected here: it cannot be combined with the current `rewrites` SPA
fallback, an unset variable is left as literal text instead of failing, there is no
way to validate the origin or set `Cache-Control`, and it cannot be tested locally or
in CI. The function is small, validated, and covered by tests.

### Proxy behavior

- **Forwarded request headers (allowlist):** `Accept`, `Accept-Language`,
  `Authorization`, `Content-Type`, `Cookie`, `Origin`, `Referer`, `X-FinTrack-CSRF`.
  Never `Host`, `Content-Length`, hop-by-hop headers, headers named by `Connection`,
  or `X-Forwarded-*`.
- **Bodies:** GET/HEAD send none. Other methods forward the raw bytes; multipart
  uploads keep the browser's boundary. Requests above 4 MB get `413` (the backend
  limit is 3 MB; Vercel's function limit is 4.5 MB).
- **Responses:** status, body, and headers pass through, except hop-by-hop,
  `Content-Length`/`Content-Encoding`, `Location`, `Server`, `Via`, `X-Powered-By`, and
  `X-Railway-*`. Every `Set-Cookie` is forwarded separately and unchanged. Every
  response gets `Cache-Control: no-store`.
- **Failures:** misconfigured `BACKEND_ORIGIN` → `503 PROXY_MISCONFIGURED`;
  unreachable backend → `502 UPSTREAM_UNAVAILABLE`; no answer within 25 s → `504
  UPSTREAM_TIMEOUT` (function `maxDuration` 30 s). Bodies never reveal the upstream.
  The frontend treats these as temporary, not as a signed-out session.
- **Logging:** only fixed messages (`api-proxy: upstream timeout`, …). No headers,
  cookies, tokens, or bodies.
- **Protection:** the path is split into segments; empty, `.`, `..`, backslash, and
  control-character segments are rejected with `404`, and the final URL must stay on
  `BACKEND_ORIGIN` under `/api/`.

### Local development

`npm run dev` (and `npm run preview`) proxy `/api` to `http://localhost:8080`
(override with the server-only `DEV_API_PROXY_TARGET`). `changeOrigin` is off, so
the backend receives the browser's `Origin: http://localhost:5173`, which is the local
`FRONTEND_URLS` default. Locally the cookie is `fintrack_refresh` without `Secure`.

## Variables

Values in angle brackets are placeholders. Never paste real values into the repository.

### Railway (backend service)

| Variable | Secret | Local (`.env`) | Staging | Production |
| --- | --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | no | unset | `prod` | `prod` |
| `DB_URL` / `DB_USERNAME` | no | local MySQL | staging MySQL | production MySQL |
| `DB_PASSWORD` | **yes** | local | staging | production |
| `JWT_SECRET` | **yes** | any local | unique | unique |
| `JWT_EXPIRATION_MS` | no | `300000` | `300000` | `300000` |
| `FRONTEND_URLS` | no | `http://localhost:5173` | `<staging Vercel origin>` | `<production Vercel origin>` |
| `REFRESH_SESSION_TTL_SECONDS` | no | `2592000` | `2592000` | `2592000` |
| `REFRESH_COOKIE_SECURE` | no | `false` | `true` | `true` |
| `REFRESH_COOKIE_SAME_SITE` | no | `Lax` | `Lax` | `Lax` |
| `REFRESH_SESSION_CLEANUP_ENABLED` | no | `true` | `true` | `true` |
| `REFRESH_SESSION_RETENTION_DAYS` | no | `7` | `7` | `7` |
| `PROFILE_PHOTOS_ENABLED` | no | optional | as configured | `true` |
| `CLOUDINARY_CLOUD_NAME` / `CLOUDINARY_API_KEY` | no | optional | staging account/key | production key |
| `CLOUDINARY_API_SECRET` | **yes** | optional | staging secret | production secret |
| `PROFILE_PHOTO_KEY_PREFIX` | no | `fintrack/development/profile-photos` | `fintrack/staging/profile-photos` | `fintrack/production/profile-photos` |

`FRONTEND_URLS` holds exact origins only (scheme + host [+ port]): no path, no trailing
`/`, no wildcard. Startup fails on anything else. There is no refresh-token secret,
hashing pepper, cookie-domain, or raw-token variable, by design.

### Vercel (frontend project)

| Variable | Secret | Preview / development | Staging | Production |
| --- | --- | --- | --- | --- |
| `BACKEND_ORIGIN` | no (but server-only) | `<nonproduction backend origin>` | `<staging Railway origin>` | `<production Railway origin>` |
| `VITE_API_BASE_URL` | — | **remove** | **remove** | **remove** |

- `BACKEND_ORIGIN` is an origin such as `https://<your-staging-service>.up.railway.app`:
  `https`, no path, **no trailing `/api`**, no credentials. It is read only by the
  function and never bundled into browser code. Never prefix it with `VITE_`.
- Scope it per Vercel environment so previews, staging, and production can never share
  the wrong backend.
- `VITE_API_BASE_URL` is obsolete and no longer read; delete it to avoid confusion.
- **Preview deployments:** each preview URL is a different origin, and backend request
  protection only accepts origins listed in `FRONTEND_URLS`. Point previews at a
  nonproduction backend and add the stable branch alias to that backend's
  `FRONTEND_URLS`, or expect login on previews to be rejected with `403`.

## Staging rollout (manual)

1. Open the PR from `feature/v1.2.0-refresh-token-rotation` into `develop`. Wait for
   **Backend CI** (unit + MySQL Testcontainers + JaCoCo) and **Frontend CI** (lint,
   coverage including proxy tests, build) to pass, then merge.
2. Promote `develop` to `staging` with the usual branch workflow.
3. **Back up the staging database** and record the MySQL server version (tests use
   8.4 LTS).
4. Set the staging **Railway** variables from the table above. Confirm
   `SPRING_PROFILES_ACTIVE=prod`, `JWT_EXPIRATION_MS=300000`,
   `REFRESH_COOKIE_SECURE=true`, and `FRONTEND_URLS=<exact staging Vercel origin>`.
5. Set the staging **Vercel** `BACKEND_ORIGIN=<staging Railway origin>` for the staging
   environment only, and delete `VITE_API_BASE_URL`.
6. Double-check that the staging Vercel project points at the staging Railway service,
   not production.
7. Deploy **Railway first**. In the logs, confirm Flyway reports schema version 5
   (`Successfully applied 1 migration … now at version v5`, or "up to date" on
   restart) and that the app starts without Hibernate validation errors.
8. Check `https://<staging Railway origin>/api/health` returns `200`.
9. Deploy **Vercel** once the backend is healthy.
10. Check routing: `https://<staging Vercel origin>/api/health` returns the backend JSON
    (not HTML), and `/settings` loads the app.
11. Run the smoke checklist below and record the evidence.
12. **Do not promote to production** until every critical item passes. Repeat steps
    3–11 for production with production values and a fresh backup.

## Rollback

- Keep the V5 tables. Do not drop `refresh_sessions` or `refresh_tokens`; older builds
  ignore them.
- Roll the frontend and backend back together, because v1.2.0 frontends need the
  refresh endpoints and older frontends expect the old login flow.
- Users may need to sign in again after a rollback or while versions briefly differ.
- Never restore long-lived access tokens in `localStorage` as a rollback shortcut.
- If only the proxy misbehaves, redeploy the previous Vercel build; the backend can
  stay on v1.2.0.

## Staging smoke checklist

Record pass/fail, browser, and evidence (screenshots of DevTools, log excerpts) for
each item.

### Deployment and health
- [ ] Backend starts; Flyway reports schema version 5.
- [ ] `/api/health` works through the Vercel host.
- [ ] Frontend loads with no console errors.
- [ ] No `/api` request returns `index.html` (Network tab: JSON, not HTML).

### Login and cookies
- [ ] Valid login succeeds; invalid login shows the generic error.
- [ ] Browser stores `__Secure-fintrack_refresh` for the Vercel host.
- [ ] The cookie is `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/api/auth`, and has no `Domain`.
- [ ] `document.cookie` in the console does not show it.
- [ ] Login response JSON contains no refresh token.
- [ ] `localStorage` and `sessionStorage` contain no access token.

### Restoration
- [ ] Reload keeps you signed in.
- [ ] Closing and reopening the browser keeps you signed in (while the family is valid).
- [ ] Network tab: `POST /api/auth/refresh` happens before `GET /api/auth/me`.
- [ ] Protected pages never flash before restoration finishes.

### Access expiration and retry
- [ ] Wait more than five minutes, then perform one protected action.
- [ ] Exactly one refresh, then the original request retried once; you stay signed in.
- [ ] No refresh loop; several simultaneous requests still succeed.

### Business regressions
- [ ] Dashboard loads.
- [ ] Create, edit, and delete a budget.
- [ ] Create, edit, and delete a transaction; filters and pagination work.
- [ ] Profile and preference updates work.
- [ ] Photo upload, replacement, and removal work through the proxy.
- [ ] The upload request is `multipart/form-data` with a boundary, and the photo renders.

### Logout
- [ ] Logout succeeds and the refresh cookie is cleared (`Max-Age=0`).
- [ ] Reload after logout does not restore the session.
- [ ] A second open tab signs out too.
- [ ] Within five minutes an old access token may still work (expected); after that,
      business APIs reject it.

### Password change
- [ ] Password change succeeds; the current tab returns to login with "Your password
      was changed. Please sign in again."
- [ ] A second tab returns to login.
- [ ] The old password fails and the new password works.
- [ ] Another browser or device must sign in again.

### Multiple devices and isolation
- [ ] Two browsers hold independent sessions.
- [ ] Logging out one does not log out the other.
- [ ] A password change signs out both.
- [ ] One user cannot see another user's data.

### Failure recovery
- [ ] Block `/api/auth/refresh` in DevTools (request blocking), then reload: a
      recoverable "couldn’t restore your session" message with Retry appears, with no
      automatic retry loop.
- [ ] Unblock and click Retry: the session restores.
- [ ] A revoked or expired session redirects to login once.

### Security inspection
- [ ] Railway and Vercel logs show no tokens, hashes, cookies, passwords, or
      `Authorization`/`Cookie` headers.
- [ ] Browser JavaScript bundles contain no secrets (search DevTools Sources for
      `BACKEND_ORIGIN` values, `JWT_SECRET`, `CLOUDINARY`).
- [ ] The proxy cannot be steered elsewhere: `/api/auth/me?backend=https://example.com`
      and a `Host`/`X-Forwarded-Host` override still hit the staging backend (or 404).
- [ ] A POST to `/api/auth/login` without `X-FinTrack-CSRF`, or from another origin,
      returns `403`.
- [ ] With only the refresh cookie (no `Authorization`), `/api/transactions` returns `401`.
