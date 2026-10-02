# Deployment Guide

This guide covers the topology and variables (unchanged since v1.2.0), the v1.2.0
refresh-session rollout, and the [v1.3.0 custom-categories release](#v130-custom-categories-issue-19).

## v1.2.0: refresh sessions

FinTrack v1.2.0 adds refresh-token sessions (issue #18). The browser must reach the
backend **through the frontend's own origin** so the `HttpOnly` refresh cookie stays
first-party. This guide covers the topology, the variables per environment, the
staging rollout, rollback, and the smoke checklist.

v1.2.0 is deployed to staging and production. The v1.2.0 sections below record that
rollout; the topology, proxy, and variables still apply.

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

## v1.3.0: custom categories (issue #19)

### What changes

- **Database:** Flyway V6 (a Java migration) adds `normalized_name`, `built_in`, and
  `icon_key` to `categories`, backfills every existing row, replaces the `(user_id, name)`
  unique key with `(user_id, normalized_name)`, and adds composite ownership foreign keys
  from transactions and budgets. See
  [database migrations](database-migrations.md#v6-category-normalization-built-in-metadata-icons-and-ownership).
- **API:** category responses gain `builtIn` and `iconKey`; transaction and budget writes
  accept `categoryId` or `newCategory`; `GET /api/transactions` accepts `categoryId`;
  financial and dashboard responses gain `categoryIconKey`. Existing `categoryId` clients
  keep working. See [categories API](categories-api.md).
- **Frontend:** custom-category creation, icon picker, category management, category
  filters, and icons (`lucide-react`).
- **Variables:** none. No new Railway or Vercel variables or secrets are needed for
  categories. `FLYWAY_BASELINE_ON_MIGRATE` stays unset or `false` (both databases already
  have Flyway history).

### Rollout steps (manual)

1. Merge the feature into `develop`, then promote to `staging` with the usual branch flow.
2. **Back up the staging database.** Optionally rehearse V6 on a restored copy first: if
   any user has duplicate or invalid legacy names, the preflight lists their IDs and stops
   before changing anything.
3. Deploy **Railway first**. Confirm in the logs: `Successfully applied 1 migration … now at
   version v6` (or "up to date"), no Hibernate validation errors, and the app started.
4. Check `/api/health` on the Railway origin, then deploy **Vercel** and check
   `/api/health` through the Vercel host.
5. Run the [v1.3.0 staging smoke checklist](#v130-staging-smoke-checklist).
6. For production: take a **fresh** backup, rehearse V6 on a restored copy, then repeat
   steps 3–5 at a quiet time. While V6 runs, the old backend instance may still serve; once
   the new columns are required it cannot insert categories (registration included) until
   the new instance is live.

### Failure and rollback

- If the V6 preflight fails, nothing was changed. On MySQL, Flyway still records a failed
  V6 row: fix the reported rows by hand, run `flyway repair`, and restart.
- **Never run `flyway clean`** against staging or production. Policy is roll-forward.
- Builds before v1.3.0 cannot run on a V6 schema. Rolling back therefore means restoring
  the pre-V6 backup together with the previous backend and frontend.
- If only the frontend misbehaves, redeploy the previous Vercel build; v1.2.0 frontends
  still work with the v1.3.0 API (they send `categoryId` and ignore the new fields), but
  they cannot create or manage custom categories.

### v1.3.0 staging smoke checklist

**Not yet performed.** Run after the feature reaches `staging`; record pass/fail, browser,
and evidence for each item. Nothing here was run locally as part of the feature work.

- [ ] 1. Register a fresh user; the 13 built-in categories appear with their icons.
- [ ] 2. Sign in as an existing (migrated) user; their categories, transactions, and
      budgets are unchanged.
- [ ] 3. Create a transaction with an existing category.
- [ ] 4. Create a transaction with **Create a custom category…**, a name, and an icon.
- [ ] 5. Reuse that category in another transaction without reloading the page.
- [ ] 6. Create a budget with the same custom category.
- [ ] 7. Create another custom category from the budget form.
- [ ] 8. Use the budget-created category in a transaction.
- [ ] 9. Edit a transaction to use a custom category (existing and new).
- [ ] 10. Edit a budget to use a custom category (existing and new).
- [ ] 11. Rename a custom category; transactions, budgets, filters, and the dashboard show
      the new name, and amounts are unchanged.
- [ ] 12. Change a custom category's icon; the new icon shows everywhere.
- [ ] 13. Delete an unused custom category (after the confirmation step).
- [ ] 14. Try to delete a category used by a transaction or a past budget: refused with the
      "used by transactions or budgets" message, nothing removed.
- [ ] 15. Built-in categories show no edit or delete controls.
- [ ] 16. Filter transactions by a custom category, combined with type and dates; paging
      restarts at page 1; Reset clears it.
- [ ] 17. Filter budgets by a custom category within a month; the empty message differs
      between "no budgets this month" and "none in this category".
- [ ] 18. Dashboard: totals, recent transactions (with icons), spending by category, and
      budget statuses are correct.
- [ ] 19. Server field errors appear beside their inputs: an amount over 10 whole digits
      (`amount`), a budget limit over 10 whole digits (`monthlyLimit`), a duplicate name
      (`newCategory.name`). `newCategory.iconKey` cannot be produced from the UI; check it
      with an API client sending an unknown icon (`400`, `fields["newCategory.iconKey"]`).
- [ ] 20. Keyboard only: create a transaction and a budget with a new category, choose an
      icon with arrow keys, rename and delete from **Manage categories**; focus returns
      sensibly after cancel, save, and delete.
- [ ] 21. Phone width (about 375 px), tablet, and 200% browser zoom: no horizontal page
      scrolling, icon grid wraps, long names wrap, management controls reachable.
- [ ] 22. Sign out and sign in as another user.
- [ ] 23. The second user sees none of the first user's custom categories; a first-user
      category ID in `/api/categories/{id}` or `?categoryId=` returns `404`.
- [ ] 24. Backend startup logs show Flyway at V6 with no pending migration.
- [ ] 25. The v1.2.0 smoke items (login, refresh, logout, password change, profile photos,
      transactions, budgets) still pass.
