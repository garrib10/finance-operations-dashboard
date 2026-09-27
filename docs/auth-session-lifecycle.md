# Authentication Session Lifecycle

## Status

In progress for FinTrack v1.2.0 (issue #18). **Phases 1–2 implemented.**

- Phase 1: schema, entities, repositories, configuration, UTC clock, token primitives.
- Phase 2: login session issuance, the refresh cookie, `POST /api/auth/refresh`
  with rotation and reuse revocation, `POST /api/auth/logout`, request protection,
  stable authentication error codes, and access-JWT hardening.

Still pending: password-change revocation, cleanup, and real-MySQL concurrency tests
(Phase 3); frontend renewal (Phase 4); the same-origin proxy and deployment
validation (Phase 5). **Do not deploy or merge before those phases.** In particular,
the current frontend does not send `X-FinTrack-CSRF`, so its login is rejected with
`403` until Phase 4 updates the client.

Business APIs still authenticate with `Authorization: Bearer <access token>`. See the
[CSRF security decision](security-csrf.md) for the cookie-authorized exception.

## Approved policy

| Area                      | Decision                                                                |
| ------------------------- | ----------------------------------------------------------------------- |
| Access token              | Existing JWT mechanism, target lifetime **5 minutes**                   |
| Refresh token             | Opaque, 32 bytes from `SecureRandom`                                    |
| Transport encoding        | Unpadded Base64url, exactly 43 characters                               |
| Storage                   | SHA-256 of the decoded 32 bytes (`BINARY(32)`); raw tokens never stored |
| Refresh session lifetime  | **30 days absolute** from login; never extended by refresh              |
| Rotation                  | New token on every successful refresh                                   |
| Reuse of a consumed token | Revoke the whole session family, no grace window                        |
| Multiple devices          | Independent session family per login/device                             |
| Logout                    | Revoke the current family only; idempotent                              |
| Password change           | Revoke every family for the user; log in again (Phase 3)                |
| Access-token revocation   | None: no denylist; access tokens expire within 5 minutes                |
| Cleanup                   | Delete families 7 days after absolute expiration (later phase)          |
| Time source               | Injectable UTC `Clock`                                                  |

Access tokens remain stateless. Requests authenticated with an access token make no
database session check. A revoked family therefore stops refreshing immediately, but
an access token already issued stays valid until it expires (at most 5 minutes).

## Data model

See [database migrations: V5](database-migrations.md#v5-refresh-sessions-and-token-history).

- **Session family** (`refresh_sessions`, `RefreshSession`): one per login on one
  device. It has a random UUID, an owning user, a creation time, a fixed absolute
  expiration, and an optional revocation time and reason (`LOGOUT`,
  `PASSWORD_CHANGE`, `REUSE_DETECTED`). A family is active only when it is not
  revoked and the current time is before `expires_at`. Revocation is final: the
  original time and reason are never overwritten.
- **Token history** (`refresh_tokens`, `RefreshToken`): one row per issued token,
  holding only its 32-byte hash, creation time, and optional consumption time. A
  token can be consumed once; consuming it again is an error, never a silent
  no-op. Consumed rows are kept so a replayed token can be recognized as reuse
  rather than as unknown. There is no parent/replacement link, because ordered
  history plus `consumed_at` is enough.

Tokens can only be issued into an active family. Deleting a family deletes its token
history. The entities are never serialized, and their `toString()` output is redacted.

## Refresh-token handling

`RefreshTokenGenerator` is the only code that handles raw token values.

- `generate()` returns an `IssuedRefreshToken` holding the raw token (for the future
  cookie writer) and its hash (for persistence). The wrapper is short-lived, not
  serializable, copies the hash defensively, and redacts `toString()`.
- `hashPresentedToken(String)` parses strictly, then returns the SHA-256 hash for
  lookup. It rejects null, blank, whitespace, padding (`=`), standard Base64
  characters (`+`, `/`), any other character, any length other than 43, and
  noncanonical encodings (nonzero spare bits in the final character). Every
  rejection throws the same `InvalidRefreshTokenException`, with a fixed message,
  no cause, and no submitted value.

Parsing a well-formed token says nothing about whether it exists in the database.
Every lookup outcome maps to the same client-facing response (see below).

No refresh-token signing secret, hash pepper, BCrypt, encryption, or JWT is used. The
token carries 256 bits of randomness, so a fast unsalted SHA-256 lookup hash is
appropriate. Raw tokens, encoded tokens, and hashes must never be logged or placed in
exception messages, responses, or entities.

## Time

`AuthSessionConfig` provides a `Clock.systemUTC()` bean. Session and token entities
take the `Clock` for creation, revocation, and consumption, and never call
`Instant.now()` themselves. Instants are truncated to microseconds to match
`DATETIME(6)`, and are persisted as UTC. Hibernate binds them with a UTC calendar on
MySQL, independent of the JVM time zone. Tests use fixed or mutable clocks. Existing
entities keep their current timestamp behavior.

## Configuration

`AuthSessionProperties` (`app.auth-session.*`) is validated at startup. Defaults are
the approved production values.

| Environment variable              | Property                                   | Approved | Allowed                                    |
| --------------------------------- | ------------------------------------------ | -------- | ------------------------------------------ |
| `JWT_EXPIRATION_MS`               | `access-token-lifetime` (from `app.jwt.expiration-ms`) | `300000` | 1–60 minutes                   |
| `REFRESH_SESSION_TTL_SECONDS`     | `refresh-session-ttl`                      | `2592000` | Positive, ≤ 30 days, and longer than the access lifetime |
| `REFRESH_COOKIE_SECURE`           | `refresh-cookie-secure`                    | `true`   | `true` / `false` (local HTTP only)         |
| `REFRESH_COOKIE_SAME_SITE`        | `refresh-cookie-same-site`                 | `Lax`    | `Lax`, `Strict`                            |
| `REFRESH_SESSION_CLEANUP_ENABLED` | `cleanup-enabled`                          | `true`   | `true` / `false`                           |
| `REFRESH_SESSION_RETENTION_DAYS`  | `retention`                                | `7`      | 1–30 days                                  |

Validation messages name the property and variable, but never the rejected value.
Blank durations or SameSite values fall back to the approved defaults. A blank
Secure flag fails startup. `SameSite=None` is rejected because the approved topology
is a same-origin `/api` proxy. `REFRESH_COOKIE_SECURE` defaults to `true`, and the
`prod` profile pins it to `true`. None of these values are secrets, and none may use
a `VITE_` prefix. Cookie name, path, and domain are fixed, not configurable.

**Access lifetime during development.** The approved value is 5 minutes, and
`.env.example` uses it. Until refresh is wired in, local development may temporarily
set `JWT_EXPIRATION_MS=3600000` to avoid frequent re-logins. When refresh-token
support is released, every hosted environment must set `JWT_EXPIRATION_MS=300000`.

## Refresh cookie

`RefreshCookieService` is the only code that builds or reads the cookie.

| Attribute | Local development   | Staging and production        |
| --------- | ------------------- | ----------------------------- |
| Name      | `fintrack_refresh`  | `__Secure-fintrack_refresh`   |
| HttpOnly  | `true`              | `true`                        |
| Secure    | `false`             | `true`                        |
| SameSite  | `Lax`               | `Lax`                         |
| Path      | `/api/auth`         | `/api/auth`                   |
| Domain    | omitted             | omitted                       |

The name is derived from the Secure flag, because the `__Secure-` prefix is only
valid on Secure cookies. `Max-Age` and `Expires` both carry the **remaining**
absolute session lifetime (rounded down to whole seconds), so rotation never grants
another 30 days. Clearing uses the same name, path, Secure, and SameSite with
`Max-Age=0` and `Expires=Thu, 01 Jan 1970 00:00:00 GMT`. Example (production):

```http
Set-Cookie: __Secure-fintrack_refresh=<43 chars>; Max-Age=2592000; Expires=Tue, 27 Oct 2026 12:00:00 GMT; Path=/api/auth; Secure; HttpOnly; SameSite=Lax
```

A request must carry exactly one cookie with the configured name. Duplicates are
never resolved to one value; they are treated as having no usable token. Refresh
tokens are read only from this cookie, never from JSON, query or form parameters,
`Authorization`, other headers, or paths.

## Endpoints

All three require request protection (below). Refresh and logout take an empty body
and need no access token. `/api/auth/me` and every business endpoint still require
a bearer access token; the refresh cookie never authenticates them.

### `POST /api/auth/login`

1. Verify credentials with BCrypt (no lock held; generic `401 Invalid email or password`).
2. In one transaction: lock the user row, start a new family with a 30-day absolute
   expiration from the UTC clock, generate a 32-byte token, and store only its hash.
3. After commit, return the access-token response and set the refresh cookie.

Each login creates an independent family. Failed logins create no rows and set no
cookie. If persistence fails, the response is `503` with no cookie and no token.
Registration still does not sign in.

The response shape is unchanged; `expiresIn` is the access lifetime in seconds:

```json
{ "accessToken": "…", "tokenType": "Bearer", "expiresIn": 300 }
```

### `POST /api/auth/refresh`

1. Read the cookie and strictly decode and hash it.
2. Look up the owning user ID by hash (no secret material), outside the transaction.
3. In one transaction: lock the user row, then reload the token and family with
   `SELECT … FOR UPDATE`.
4. Reject if the family is revoked or at/after `expires_at`.
5. If the token is already consumed, revoke the family with `REUSE_DETECTED`, commit,
   and respond `401`.
6. Otherwise mark it consumed, store the hash of a new token in the same family
   (expiration unchanged), and issue a new access JWT.
7. After commit, return `200` with the access-token response and the rotated cookie.

Terminal outcomes are returned rather than thrown, so reuse revocation always commits
before the `401` is written. The replacement cookie is attached only after commit. At
most one refresh succeeds per token; the previous token then only triggers reuse.
Concurrent refreshes of the same token (for example, two tabs) are serialized, and
the second one revokes the family; Phase 4 adds client-side single-flight handling.

### `POST /api/auth/logout`

Revokes the family identified by the cookie with `LOGOUT`, clears the cookie, and
returns `204`. A consumed (rotated-out) token revokes its family with
`REUSE_DETECTED` instead. Missing, blank, malformed, duplicate, unknown, expired,
or already-revoked cookies, and repeated logouts, also return `204` and clear the
cookie without touching any family. Other families are never revoked. If revoking a
known family fails in the database, the response is `503`, the cookie is kept, and
no revocation is claimed.

### Error contract

| Condition                                                      | Status | Code                  | Cookie   |
| -------------------------------------------------------------- | ------ | --------------------- | -------- |
| Refresh: missing, malformed, duplicate, unknown, expired, revoked, or reused | `401`  | `SESSION_EXPIRED`     | Cleared  |
| Missing/invalid `Origin`, `Referer`, or `X-FinTrack-CSRF`      | `403`  | `REQUEST_FORBIDDEN`   | Untouched |
| Temporary database/session failure                             | `503`  | `SESSION_UNAVAILABLE` | Untouched |
| Expired, correctly signed access token on a protected API      | `401`  | `ACCESS_TOKEN_EXPIRED` | —       |
| Missing, malformed, or otherwise invalid access token          | `401`  | `AUTHENTICATION_REQUIRED` | —   |

Every refresh `401` has the same message: *"Your session has expired. Please sign in
again."* Clients cannot tell unknown, expired, revoked, or reused tokens apart.
Responses never contain SQL, internal IDs, token state, stack traces, or lock details.
Codes appear in the existing error JSON as an optional `code` field.

## Request protection

Login, refresh, and logout require `X-FinTrack-CSRF: 1` and an exact allowlisted
`Origin`, or, only when `Origin` is absent, an exact allowlisted `Referer`. The check
runs before CORS and authentication. See [security-csrf.md](security-csrf.md#issue-18-refresh-cookie-exception).

## Access tokens

`JwtService` issues access JWTs only: subject (email), `userId`, a unique `jti`,
`iat`, and `exp` (5 minutes in the approved configuration). A single parse verifies
signature and expiration, then requires the subject, a numeric `userId`, and `jti`.
`ACCESS_TOKEN_EXPIRED` is reported only for a correctly signed, complete token past
`exp`; anything else is `AUTHENTICATION_REQUIRED`. Controller-level `401`s never
carry the expired code. There is no access-token denylist and no database lookup per
request, so after logout or reuse revocation an already-issued access token remains
valid until it expires (at most 5 minutes).

## Logging

Fixed events with internal IDs only: `auth.login.succeeded`, `auth.login.failed`,
`auth.session.started`, `auth.refresh.rotated`, `auth.refresh.rejected category=…`,
`auth.refresh.reuse_detected`, `auth.logout.revoked`, `auth.logout.noop category=…`,
`auth.session.persistence_failed`, and `auth.request_protection.rejected category=…`.
Tomcat's cookie-parser logger is turned off because it would otherwise log rejected
`Cookie` headers verbatim, including the refresh token. Logs never include raw or encoded tokens, hashes, `Authorization`, `Cookie`, or
`Set-Cookie` headers, passwords, request bodies, JWT claims beyond the user ID, SQL
bind values, or exception messages (only the exception class name).

## Locking and databases

Every mutating session transaction takes the user row lock first, then the token and
session rows. Phase 3 revoke-all (password change) must use the same order. Tests
run on H2 in MySQL mode; they verify transactional outcomes but **do not prove MySQL
InnoDB locking or isolation behavior**. Phase 3 adds real-MySQL concurrency tests.

## Planned phases

1. **Foundation** — done.
2. **Issuance, rotation, reuse, logout, request protection** — done.
3. Password-change revoke-all (replacing today's behavior, where the current session
   stays signed in after a password change), scheduled cleanup 7 days after
   expiration, and real-MySQL concurrency tests.
4. Frontend: in-memory access tokens, one controlled refresh on
   `ACCESS_TOKEN_EXPIRED`, single-flight and cross-tab coordination, and logout UX.
5. Same-origin Vercel `/api` proxy, hosted configuration (`JWT_EXPIRATION_MS=300000`,
   Secure cookies), and deployment validation.
