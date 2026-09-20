# CSRF Security Decision for Stateless JWT Authentication

## Status

Accepted for FinTrack v1.1.0.

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
- Public login and registration requests are not rejected for missing CSRF tokens
- CORS credentials are disabled
- Only configured origins, methods, and headers are allowed

## References

- [Spring Security CSRF documentation](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [OWASP Cross-Site Request Forgery Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
