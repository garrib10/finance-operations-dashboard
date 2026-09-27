# Authentication Session Lifecycle

## Status

In progress for FinTrack v1.2.0 (issue #18). **Phase 1 (foundation) only.**

Phase 1 adds the schema, entities, repositories, configuration, UTC clock, and
refresh-token primitives. The application does not issue or accept refresh tokens
yet. Login, the refresh and logout endpoints, cookies, rotation, reuse handling,
password-change revocation, cleanup, frontend changes, and the Vercel proxy come in
later phases. Do not deploy or merge Phase 1 on its own.

Current behavior is unchanged: login returns a stateless JWT access token, and the
frontend sends it as `Authorization: Bearer <token>`. See
[CSRF security decision](security-csrf.md); it must be revisited when refresh
cookies are introduced.

## Approved policy

| Area                      | Decision                                                                |
| ------------------------- | ----------------------------------------------------------------------- |
| Access token              | Existing JWT mechanism, target lifetime **5 minutes**                   |
| Refresh token             | Opaque, 32 bytes from `SecureRandom`                                    |
| Transport encoding        | Unpadded Base64url, exactly 43 characters                               |
| Storage                   | SHA-256 of the decoded 32 bytes (`BINARY(32)`); raw tokens never stored |
| Refresh session lifetime  | **30 days absolute** from login; never extended by refresh              |
| Rotation                  | New token on every successful refresh (Phase 2)                         |
| Reuse of a consumed token | Revoke the whole session family (Phase 2)                               |
| Multiple devices          | Independent session family per login/device                             |
| Logout                    | Revoke the current family only (later phase)                                |
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
Lookup outcomes are handled in later phases and must not reveal existence.

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

## Refresh cookie (later phase)

Phase 1 defines constants only. Nothing writes `Set-Cookie`, reads request cookies,
or changes controllers.

| Attribute | Local development   | Staging and production        |
| --------- | ------------------- | ----------------------------- |
| Name      | `fintrack_refresh`  | `__Secure-fintrack_refresh`   |
| HttpOnly  | `true`              | `true`                        |
| Secure    | `false`             | `true`                        |
| SameSite  | `Lax`               | `Lax`                         |
| Path      | `/api/auth`         | `/api/auth`                   |
| Domain    | omitted             | omitted                       |

The name is derived from the Secure flag, because the `__Secure-` prefix is only
valid on Secure cookies. At the cookie boundary, a request must carry exactly one
refresh-token value; this is enforced when cookie parsing is implemented.

## Planned phases

1. **Foundation** (this phase): schema, entities, repositories, configuration, clock,
   and token primitives.
2. Refresh issuance and rotation, including family-wide reuse revocation.
3. Password-change revoke-all, and real-MySQL locking/concurrency tests.
4. Remaining work: logout, cookies, cleanup scheduling, frontend, the same-origin
   proxy, and final deployment (`JWT_EXPIRATION_MS=300000` in Phase 5). Assigned to
   phases when each is planned.
