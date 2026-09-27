# CSRF Security Decision for Stateless JWT Authentication

## Status

Accepted for FinTrack v1.1.0. Amended for v1.2.0 (issue #18): business APIs remain
bearer-only, with a narrow cookie-authorized exception for refresh and logout. See
[Issue #18: refresh-cookie exception](#issue-18-refresh-cookie-exception).

This decision applies to the current stateless JWT authentication architecture. It must be reviewed if FinTrack changes how authentication credentials are stored or transmitted.

## Current Authentication Model

FinTrack uses stateless JWT access-token authentication.

- Spring Security uses `SessionCreationPolicy.STATELESS`.
- The backend does not create an authenticated server-side session.
- The frontend stores the access token in browser `localStorage`.
- The frontend explicitly sends the token through the `Authorization: Bearer <token>` header.
- The backend does not use cookies to authenticate API requests.
- Cross-origin requests do not include credentials because CORS credentials are disabled.
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

Because the current access token is stored in `localStorage`, malicious JavaScript executing within the FinTrack frontend origin could potentially access it. This is an XSS risk rather than a traditional CSRF risk.

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

Password changes do not revoke already-issued access tokens. Browser logout also
only removes the local token. Tokens remain usable until expiration; refresh-token
rotation and revocation belong to issue #18. Reassess this CSRF decision if that
work introduces cookie-based credentials.

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
`FRONTEND_URLS`. The same-origin Vercel `/api` proxy planned for Phase 5 will make
frontend auth calls same-origin; these checks remain in place behind it.

### Residual risk

XSS in the frontend origin can still call these endpoints with the correct headers,
though it cannot read the `HttpOnly` cookie. The five-minute access-token lifetime
and family-wide reuse revocation bound that exposure. See the
[authentication session lifecycle](auth-session-lifecycle.md).
