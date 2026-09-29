# Authentication Session Lifecycle

## Status

Implemented for FinTrack v1.2.0 (issue #18): backend (Phases 1–3), frontend
(Phase 4), and same-origin deployment proxy (Phase 5). Staging verification is
still to be done by following the [deployment guide](deployment.md); do not promote
to production until its smoke checklist passes.

Business APIs authenticate with `Authorization: Bearer <access token>`. See the
[CSRF security decision](security-csrf.md) for the cookie-authorized exception.

## Lifecycle at a glance

1. **Login.** The browser POSTs credentials to `/api/auth/login` (same origin, with
   `X-FinTrack-CSRF: 1`). The backend verifies the password against the locked user row.
2. **Refresh-session creation.** A new family (one per login/device) is created with a
   30-day absolute expiration; the token's SHA-256 hash is stored, never the token.
3. **Access token in memory.** The response carries a five-minute access JWT, which
   the frontend keeps only in JavaScript memory (never in any browser storage).
4. **Refresh cookie.** The raw refresh token is set as `__Secure-fintrack_refresh`
   (`HttpOnly; Secure; SameSite=Lax; Path=/api/auth`, no `Domain`); JavaScript cannot
   read it.
5. **Business request.** Every API call sends `Authorization: Bearer <access token>`.
6. **Expiration.** After five minutes the backend answers `401 ACCESS_TOKEN_EXPIRED`.
7. **Single-flight refresh.** One refresh per tab (Web Lock across tabs) calls
   `/api/auth/refresh` with the cookie.
8. **Rotation.** The presented token is marked consumed and a new one issued in the same
   family; the absolute expiration never moves (no sliding extension).
9. **Retry.** The original request is retried once with the new access token.
10. **Reload.** A reload loses the in-memory token; restoration calls refresh, then `/me`.
11. **Logout.** `/api/auth/logout` revokes the current family and clears the cookie; the
    frontend signs out only after the server confirms.
12. **Password change.** Revokes every family for the user; every tab and device must
    sign in again. Profile, preference, and photo changes never revoke.
13. **Reuse detection.** Presenting an already-consumed token revokes its whole family.
14. **Multiple devices.** Each login is an independent family; logout and reuse affect
    only one family.
15. **Cross-tab coordination.** BroadcastChannel tells other tabs about logout,
    password change, session termination, and account changes, without tokens.
16. **Cleanup.** Families are deleted seven days after their absolute expiration.
17. **Deployment proxy.** In staging/production the browser only talks to the Vercel
    host; `api/proxy.ts` forwards to the backend (see [Deployment proxy](#deployment-proxy)).
18. **Failure handling.** A refresh `401` signs out everywhere; network/`5xx` failures
    show a recoverable Retry state without claiming the session ended.

Access JWTs are stateless and there is no denylist: after logout, reuse revocation, or
a password change, an already-issued access token can still work for **up to five
minutes**. There is no public session-management UI.

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

**Access lifetime.** Every environment uses 5 minutes (`JWT_EXPIRATION_MS=300000`),
including local development: silent refresh keeps users signed in, so there is no
reason for longer access tokens. Hosted values are listed in the
[deployment guide](deployment.md#variables).

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
the second one revokes the family. The frontend prevents this with per-tab single flight and a cross-tab Web Lock.

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

## Password change

`POST /api/account/password` (bearer-authenticated; the refresh cookie is never
accepted) keeps its contract: `204` with an empty body. In one transaction it:

1. Resolves the user ID from the authenticated principal (never from the request).
2. Locks the user row (`SELECT … FOR UPDATE`), the same lock every session flow uses.
3. Verifies the current password against the locked hash and rejects reuse.
4. Stores the new BCrypt hash.
5. Revokes every **active** family for the user with `PASSWORD_CHANGE`, timestamped
   by the UTC clock. Already-revoked families keep their original reason and time;
   expired families are left for cleanup. Token history is not deleted.
6. Commits, then the response clears the refresh cookie.

Wrong current passwords, invalid new passwords, and database failures change
nothing and leave the cookie untouched (validation errors are `400`; unexpected
failures are the account API's generic `500`). Every device must sign in again;
The frontend treats a successful change as the end of the session (see Frontend session lifecycle).
Already-issued access JWTs remain valid until they expire, **at most five minutes**
later; there is no denylist. Profile, preference, and profile-photo changes never
revoke sessions.

The clearing `Set-Cookie` is returned from `/api/account/password` with
`Path=/api/auth`. Because the browser reaches the backend through the same-origin
proxy (Vite locally, Vercel when hosted), it applies that header directly. Even if a
browser ignored it, the family is already revoked server-side, so the stale cookie
fails its next refresh, which clears it.

`RefreshSessionService.revokeAllForUser(userId, reason)` is internal only. It requires
an existing transaction (`Propagation.MANDATORY`), so it always commits or rolls back
with the password update, and returns only a count. No endpoint revokes by user or
session ID.

## Logging

Fixed events with internal IDs, counts, and fixed categories only:

| Event | Fields |
| --- | --- |
| `auth.login.succeeded` | `userId` |
| `auth.login.failed` | `category=invalid_credentials` |
| `auth.session.started` | `userId`, `sessionId` |
| `auth.refresh.rotated` | `userId`, `sessionId` |
| `auth.refresh.rejected` | `category` (`cookie_absent`, `cookie_duplicate`, `malformed`, `unknown`, `expired`, `revoked`) |
| `auth.refresh.reuse_detected` | `userId`, `sessionId`, `action=family_revoked` |
| `auth.logout.revoked` / `auth.logout.noop` | `userId`, `sessionId`, `consumedToken` / `category` |
| `auth.session.revoke_all` | `userId`, `reason`, `count` |
| `auth.session.persistence_failed` | `operation`, `userId`, exception class name |
| `auth.session.cleanup` | `category` (`completed`, `disabled`, `failed`), `deleted`, `batches` |
| `auth.request_protection.rejected` | `category` |

Logs never include raw or encoded tokens, hashes, `Authorization`, `Cookie`, or
`Set-Cookie` headers, passwords, credential DTOs, request bodies, JWT claims beyond
the user ID, SQL bind values, or exception messages (only the exception class name).
Two third-party loggers are turned off because they print such values verbatim:
Tomcat's cookie parser (rejected `Cookie` headers, including the refresh token) and
Hibernate's `SqlExceptionHelper` and `org.hibernate.orm.jdbc.error` (database error text; MySQL's duplicate-key message
contains the key, such as a token hash or an email). Tests assert sentinel passwords,
tokens, and hashes never appear in captured output.
## Locking and race behavior

Every flow that mutates a user's credentials or sessions (login, refresh, reuse,
logout, password change) uses one order:

1. Resolve the user ID without a lock (by email, or by token hash).
2. Lock the user row: `SELECT … FOR UPDATE`.
3. Reload token and session rows (`FOR UPDATE`) after the lock is held.
4. Mutate, then commit.
5. Only then set or clear cookies.

No flow locks a token or session before the user row, so they cannot deadlock one
another. Every read of session state that a decision depends on is itself a locking
read (`FOR UPDATE`): under InnoDB REPEATABLE READ a plain `SELECT` can return the
transaction's earlier snapshot. The MySQL race tests caught exactly that: revoke-all
initially used a plain `SELECT` and missed a family committed by a login that held the
user lock just before the password change acquired it. Transactions are short and make no network calls. Profile-photo updates lock
the same user row and touch no session rows. Cleanup deletes only families expired
for at least seven days without taking the user lock; a rare conflict with a refresh
of such a family is resolved by InnoDB (the refresh returns `503` or cleanup retries
the next day).

Login verifies BCrypt **while holding the user lock** (after an unlocked ID lookup;
unknown emails still pay one BCrypt comparison). This costs one hash per login per
user and is accepted so that password verification and session issuance use the same
committed hash.

| Race | Outcome |
| --- | --- |
| Same token, two refreshes | One rotates. The other sees a consumed token, revokes the family (`REUSE_DETECTED`), and gets `401`; the replacement no longer refreshes. Other families are unaffected. No grace window. |
| Refresh vs logout | Refresh first: logout then revokes the family even though its token was just consumed (`LOGOUT`). Logout first: refresh is `401`. Either way no token in the family refreshes afterwards. |
| Refresh vs password change | Refresh first: the password change revokes the rotated family. Password change first: refresh sees the revoked family and is `401`, creating no successor. |
| Login (old password) vs password change | Login first: its session is revoked by the password change. Password change first: the login is checked against the new hash and fails. No session created with the old password stays active. |

Invariants: after a password change commits, no family for that user is usable and
the old password cannot create one; after logout completes, nothing in that family
refreshes.

### MySQL verification

`*IT` tests run against a pinned `mysql:8.4.6` container (MySQL 8.4 LTS, supported by
Flyway 12) via Testcontainers during `./mvnw clean verify`. They run Flyway V1–V5,
start the application with `ddl-auto=validate`, and verify the populated V4 → V5
upgrade and the V5 constraints on MySQL. Concurrency tests run each operation on its
own thread, transaction, and connection. Ordered races hold the real InnoDB row lock
and wait until `information_schema.innodb_trx` shows the competitor in `LOCK WAIT`
before releasing, so both lock orders are exercised deterministically; barrier-started
races check the invariants for either winner. H2 tests remain for fast unit coverage
but do not prove MySQL locking.

Docker is required for `verify`. Locally that means a running Docker-compatible
runtime (for example Colima with `DOCKER_HOST` and
`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` set); GitHub Actions' Ubuntu runners provide
Docker already. The tests never skip when Docker is missing; the build fails instead.

## Cleanup

`RefreshSessionCleanupService` runs daily at 03:30 UTC (Spring's built-in scheduler)
when `REFRESH_SESSION_CLEANUP_ENABLED=true`. It deletes families whose
`expires_at < now - REFRESH_SESSION_RETENTION_DAYS` (default 7): with the 30-day
lifetime, a family is removed 37 days after login at the earliest. Revocation alone
never makes a family eligible, so reuse evidence stays for the full window. Deletion
cascades to token history.

Each run processes up to 20 batches of 500 families, oldest first, each in its own
short transaction; IDs are selected through `idx_refresh_sessions_expires_at` and
deleted with a bulk statement that re-checks the cutoff. Runs are idempotent and safe
on several instances. Failures are logged as a category and never thrown; cleanup
never runs during startup or on login/refresh.
## Frontend session lifecycle

- **Storage.** The access token lives only in module memory (`utils/authToken.ts`);
  a reload loses it by design. Nothing is written to `localStorage`,
  `sessionStorage`, IndexedDB, cookies, or URLs. The legacy `fintrack_access_token`
  key is deleted on startup without being read. The refresh token is an `HttpOnly`
  cookie; no frontend code can read it.
- **Auth endpoints.** Login, refresh, and logout use `credentials: "include"` and
  `X-FinTrack-CSRF: 1`, carry no bearer token, and are never refreshed or retried
  automatically. Registration is a public request and is never refreshed.
- **Restoration.** On load (and on Retry) the provider calls refresh, then `/me`
  with the new token; `/me` is the only source of user data. A refresh 401 ends
  restoration signed out with no alert. Other failures show the recoverable
  "couldn’t restore your session" state with an explicit Retry; no token is kept.
  React Strict Mode rotates the cookie once (deferred start plus single-flight).
- **Expired requests.** Only a bearer request answered with `401` and code
  `ACCESS_TOKEN_EXPIRED` is refreshed, and it is retried exactly once. The retry
  never refreshes again. If another request already installed a newer token, it is
  reused without rotating. Requests that are aborted, opted out, or have a
  non-replayable (streamed) body are not retried; JSON strings and `FormData`
  (photo uploads, same `File`, browser-generated boundary) replay safely. Retrying
  a mutation is safe because `ACCESS_TOKEN_EXPIRED` is returned by the security
  filter before any controller runs. Messages, `403`, `409`, `429`, `5xx`, and
  network errors never trigger refresh.
- **Single flight.** All refreshes in a tab share one promise, which clears after
  success or failure. A refresh that finishes after the session changed (logout,
  login) is discarded.
- **Terminal vs temporary.** A refresh 401 during a request ends the session in
  every tab ("Your session has expired…") and redirects to login once, keeping the
  intended destination. A network or `503` refresh failure clears this tab's token
  and shows the recoverable state instead; it does not claim the cookie was
  revoked, and it does not loop.
- **Logout.** The server must confirm (`204`) before local state is cleared.
  Duplicate clicks send one request. If logout cannot be confirmed the user stays
  signed in and can retry; there is no local-only fallback.
- **Password change.** After a successful change the tab signs out without calling
  refresh or logout (the backend already revoked every family) and shows "Your
  password was changed. Please sign in again." Failed changes keep the session.
  Profile, preference, and photo changes never sign out.
- **Web Locks.** Login, refresh, and logout run under the `fintrack-auth-session`
  lock, so tabs never rotate the cookie at the same time. A tab that waited
  refreshes for itself with the latest cookie; access tokens are never shared.
  Browsers without Web Locks fall back to per-tab single flight (no storage-based
  lock).
- **BroadcastChannel.** The `fintrack-auth` channel carries only
  `{ type, sender }`, where type is `LOGOUT`, `PASSWORD_CHANGED`,
  `SESSION_TERMINATED`, or `ACCOUNT_CHANGED`. Receiving tabs clear their token and
  user and advance their session guards; on `ACCOUNT_CHANGED` a tab restores
  itself through the cookie. Received events are never re-broadcast. Without
  BroadcastChannel, tabs simply find out at their next refresh.
- **Stale responses.** The provider's `sessionVersion`, `userRevision`, and
  restoration sequence, plus the session generation in `authSession.ts`, keep late
  responses from restoring a signed-out or different account.

Hosted cookie behavior and real-browser checks are covered by the
[staging smoke checklist](deployment.md#staging-smoke-checklist).

## Deployment proxy

The browser never calls the backend host. `vercel.json` sends `/api/*` to the
`api/proxy.ts` function before the SPA fallback, and the function forwards to
`${BACKEND_ORIGIN}/api/<path>` (server-only variable, validated as an https origin).
It forwards only the headers the app needs (`Authorization`, `Cookie`, `Origin`,
`Referer`, `X-FinTrack-CSRF`, `Content-Type`, `Accept`, `Accept-Language`), keeps raw
bodies (multipart boundaries intact), passes every `Set-Cookie` through unchanged, and
marks every response `Cache-Control: no-store`. Upstream failures become sanitized
`502`/`503`/`504` responses, which the frontend treats as temporary. Locally the Vite
dev server proxies `/api` to `http://localhost:8080` the same way. Details, variables,
and the rollout plan: [deployment guide](deployment.md).

## Delivery phases

1. **Foundation** — done.
2. **Issuance, rotation, reuse, logout, request protection** — done.
3. **Password-change revoke-all, locking, MySQL concurrency tests, cleanup** — done.
4. **Frontend session lifecycle** — done.
5. **Same-origin proxy, deployment configuration, final documentation** — done.
   Staging rollout and smoke testing follow the [deployment guide](deployment.md).
