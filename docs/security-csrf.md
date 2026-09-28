# CSRF Security Decision for Stateless JWT Authentication

## Status

Accepted for FinTrack v1.1.0. Amended for v1.2.0 (issue #18): business APIs remain
bearer-only, with a narrow cookie-authorized exception for refresh and logout. See
[Issue #18: refresh-cookie exception](#issue-18-refresh-cookie-exception).

This decision applies to the current stateless JWT authentication architecture. It must be reviewed if FinTrack changes how authentication credentials are stored or transmitted.

**Summary as of v1.2.0:** FinTrack is *not* cookie-free. Business endpoints are
bearer-only; the `HttpOnly` refresh cookie is accepted only by refresh and logout,
which (with login) require an exact `Origin` and `X-FinTrack-CSRF: 1`. The sections
below record the original v1.1.0 decision, then the v1.2.0 amendment.

## Original Authentication Model (v1.1.0)

FinTrack v1.1.0 used stateless JWT access-token authentication.

- Spring Security uses `SessionCreationPolicy.STATELESS`.
- The backend does not create an authenticated server-side session.
- The frontend stores the access token in browser `localStorage`. *(Superseded in
  v1.2.0: the access token is memory-only; see the issue #18 section.)*
- The frontend explicitly sends the token through the `Authorization: Bearer <token>` header.
- The backend does not use cookies to authenticate business API requests. *(Still
  true in v1.2.0; refresh and logout are the only cookie-authorized endpoints.)*
- Cross-origin requests do not include credentials because CORS credentials are
  disabled. *(v1.2.0: enabled only for login, refresh, and logout.)*
- CORS access is restricted to explicitly configured frontend origins.

## Decision

Spring Security CSRF protection remains disabled while FinTrack uses its current stateless bearer-token authentication model.

Traditional CSRF attacks rely on browsers automatically including authentication credentials, such as session cookies, with forged requests. FinTrack’s JWT is not automatically attached by the browser. The frontend must explicitly read the token and add it to the `Authorization` header.

Enabling `CookieCsrfTokenRepository` would introduce cookie-based CSRF infrastructure that does not match the current authentication architecture. It would also require unnecessary frontend and backend coordination without protecting the primary risk associated with the current token-storage approach.

## Existing Security Controls

FinTrack uses the following controls:

- JWT validation for protected API requests
- Explicit `Authorization` bearer headers
- Stateless Spring Security configuration
- Authentication requirements for state-changing API endpoints
- User-ownership enforcement for protected resources
- Explicit CORS origin allowlists
- Credentialed cross-origin requests disabled
- Restricted allowed request headers
- HTTPS for deployed frontend and backend traffic
- No state-changing operations exposed through `GET` requests

Unauthenticated protected requests return `401 Unauthorized`. They are not accepted because a CSRF token is missing or present.

## Login Requests

Login and registration endpoints are intentionally public.

Successful login returns a JWT in the JSON response. It does not create an authenticated server session or authentication cookie. A different website cannot use a login response to establish an authenticated FinTrack browser session because the FinTrack frontend is responsible for reading and storing the returned token.

CORS restrictions also prevent unapproved origins from reading API responses through browser JavaScript.

## Residual Risk: Cross-Site Scripting

Disabling CSRF protection does not eliminate other browser security risks.

Because the access token was stored in `localStorage` (v1.1.0), malicious JavaScript executing within the FinTrack frontend origin could potentially access it. This is an XSS risk rather than a traditional CSRF risk. Since v1.2.0 the access token is held only in memory and the refresh token is `HttpOnly`, which removes persistent token theft but not in-page XSS abuse.

FinTrack must continue to avoid unsafe HTML rendering, restrict trusted frontend code, validate data, and keep frontend dependencies reviewed and updated.

## Conditions Requiring Reassessment

This decision must be reviewed before introducing any of the following:

- JWT authentication through cookies
- Session-cookie authentication
- Refresh tokens stored in cookies
- `allowCredentials(true)` in the CORS configuration
- Server-side authenticated sessions
- Third-party frontend origins
- State-changing endpoints that do not require bearer authentication

If authentication moves to cookies, FinTrack must implement appropriate CSRF protection and review the cookies’ `HttpOnly`, `Secure`, and `SameSite` settings.

## Automated Verification

`SecurityConfigTest` verifies that:

- Public endpoints remain accessible where intended
- Protected endpoints require authentication
- Protected `POST`, `PUT`, and `DELETE` requests without authentication return `401`
- Registration is not rejected for missing CSRF tokens or protection headers
- Login, refresh, and logout require the exact Origin and custom header (issue #18)
- CORS credentials are enabled only for login, refresh, and logout, and disabled elsewhere
- Only configured origins, methods, and headers are allowed

## References

- [Spring Security CSRF documentation](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [OWASP Cross-Site Request Forgery Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)


## Issue #16 account operations

Profile, preferences, and password changes use the same explicit bearer header,
stateless filter chain, and existing CORS policy. CSRF behavior has not changed.
Ownership is resolved from the principal; account DTOs expose only editable fields.
The password endpoint verifies the current hash with `PasswordEncoder.matches()`
and returns 400 for an incorrect current password, preserving the browser session.

A genuine authenticated 401 clears the current browser session. Temporary session
restoration failures preserve the token for manual retry. Account updates cannot
restore a logged-out user or overwrite a different session with a late response.

Password changes do not revoke already-issued access tokens, which remain usable
until they expire (at most five minutes with issue #18). Since issue #18, a password
change revokes every refresh-session family and clears the refresh cookie; see
[Issue #18: refresh-cookie exception](#issue-18-refresh-cookie-exception). The password
endpoint remains bearer-authenticated and never accepts the refresh cookie.

Credential DTOs redact passwords and login tokens in `toString()`. Account error
responses use safe messages; the exception resolver and HandlerMethod argument-resolution logger are pinned
above DEBUG because validation and malformed-JSON exceptions may contain rejected
credentials. A regression test covers an unquoted secret in a malformed body. Do not enable request-body,
Authorization-header, or SQL bind-value logging. Deployed proxy/APM logging still
requires environment-specific review. The login response intentionally contains its
access token; profile/preference responses never contain credentials or tokens.

## Issue #18: refresh-cookie exception

Business endpoints (account, profile photo, budgets, categories, dashboard,
transactions, and `/api/auth/me`) still authenticate only with an explicit
`Authorization: Bearer` access token. The refresh cookie is never turned into a
Spring Security principal, so a request carrying only the cookie is rejected with
`401` on those endpoints. The analysis above therefore still holds for them.

The refresh cookie is `HttpOnly`, `Secure` outside local development, `SameSite=Lax`,
host-only, and scoped to `Path=/api/auth`. It is read only by two cookie-authorized
endpoints:

- `POST /api/auth/refresh` rotates the cookie and returns a new access token.
- `POST /api/auth/logout` revokes the current session family.

Because the browser attaches this cookie automatically, these endpoints are CSRF
targets. `POST /api/auth/login` is protected as well, against login CSRF (a forged
login into an attacker's account). `SameSite=Lax` alone is not relied on.

### Request protection

`AuthRequestProtectionFilter` applies only to `POST` on those three routes. It runs
before CORS and authentication, and it rejects with `403` and code
`REQUEST_FORBIDDEN` before any controller runs. A rejection never authenticates,
creates a session, rotates, revokes, or clears a cookie.

1. **Custom header.** The request must carry exactly one `X-FinTrack-CSRF: 1`. A
   cross-site form or navigation cannot set custom headers, and a cross-origin
   script cannot send one without a successful preflight.
2. **Exact origin.** `Origin` must be one exact, normalized entry from
   `app.frontend-urls`. Comparison is by set membership (lowercased scheme and host,
   default port omitted), never substring, suffix, or pattern matching. `null`,
   wildcards, paths, credentials, multiple values, and malformed values are rejected.
   So `https://app.example.evil.example` never matches `https://app.example`.
3. **Referer fallback.** Only when no `Origin` header is present, a single absolute
   `Referer` whose origin is exactly allowlisted is accepted. A present but invalid
   `Origin` (including `null`) is never overridden by a valid `Referer`. With neither,
   the request is rejected.

Rejected header values are never echoed in responses or logs; only a fixed category
is logged.

### CORS

CORS is a browser read policy, not the CSRF control; the filter above enforces
protection server-side regardless of CORS. CORS is still kept tight:

- Credentialed CORS (`Access-Control-Allow-Credentials: true`) is registered only for
  the three auth routes, with `POST`/`OPTIONS` and headers `Authorization`,
  `Content-Type`, and `X-FinTrack-CSRF`.
- All other routes keep credentials disabled.
- Origins are the same exact allowlist. Wildcard origins are forbidden, and startup
  fails if `FRONTEND_URLS` contains `*` or any non-origin value. A credentialed
  wildcard would let any site make cookie-bearing requests and read the responses.
- Preflight `OPTIONS` requests reach CORS before authentication; unapproved origins
  or headers are refused.

Development, staging, and production keep separate origin lists through
`FRONTEND_URLS`.

### Same-origin proxy topology

In staging and production the browser calls `/api/*` on the Vercel frontend host;
the `api/proxy.ts` function forwards server-to-server to the Railway backend named by
the server-only `BACKEND_ORIGIN` ([deployment guide](deployment.md)). From the
browser's view every request is same-origin, so the refresh cookie is a first-party
cookie of the frontend host and `SameSite=Lax` works without third-party cookies.

The proxy forwards the browser's `Origin`, `Referer`, `X-FinTrack-CSRF`, `Cookie`, and
`Authorization` unchanged, so every check above still runs on the backend.
`FRONTEND_URLS` therefore lists the Vercel frontend origin, not the Railway origin.
The proxy cannot be steered to another host: the upstream comes only from
`BACKEND_ORIGIN`, and the path may only select a location under `/api/` there.

Locally the Vite dev server proxies `/api` to `http://localhost:8080` with
`changeOrigin` off, so the backend sees `Origin: http://localhost:5173`, the local
`FRONTEND_URLS` default.

### Why the refresh cookie cannot reach business APIs

The cookie's `Path=/api/auth` keeps browsers from sending it to `/api/account`,
`/api/transactions`, `/api/budgets`, or other business paths. Even if it were sent,
Spring Security never turns it into a principal: business endpoints, including
profile, password, and photo changes, require `Authorization: Bearer`. Tests assert
a refresh cookie alone gets `401` on those endpoints.

### Staging and production verification

- POST `/api/auth/login` without `X-FinTrack-CSRF` → `403 REQUEST_FORBIDDEN`.
- POST `/api/auth/refresh` with a different `Origin` → `403`.
- `/api/transactions` with only the refresh cookie → `401`.
- The refresh cookie shows `HttpOnly; Secure; SameSite=Lax; Path=/api/auth` with no
  `Domain` in DevTools, and `document.cookie` does not include it.

See the full [staging smoke checklist](deployment.md#staging-smoke-checklist).

### Residual risk

XSS in the frontend origin can still call these endpoints with the correct headers,
though it cannot read the `HttpOnly` cookie. The five-minute access-token lifetime
and family-wide reuse revocation bound that exposure. See the
[authentication session lifecycle](auth-session-lifecycle.md).
