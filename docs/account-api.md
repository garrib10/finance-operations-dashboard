# Account API

All account endpoints require `Authorization: Bearer <access token>`. The backend
resolves the user exclusively from the authenticated principal's email. Request
body properties, query parameters, and headers cannot select another account.
Extra JSON properties are ignored under the existing mapper configuration; only
the explicitly defined DTO fields are applied. Email cannot be changed.

| Method | Endpoint | Request fields | Success |
| --- | --- | --- | --- |
| GET | `/api/auth/me` | None | 200, canonical user response |
| PUT | `/api/account/profile` | `firstName`, `lastName`, `displayName` | 200, canonical user response |
| PUT | `/api/account/preferences` | `dateFormat`, `transactionPageSize` | 200, canonical user response |
| POST | `/api/account/password` | `currentPassword`, `newPassword` | 204, empty body |

The canonical user response contains `id`, `firstName`, `lastName`, `displayName`,
`email`, `createdAt`, and `preferences` (`dateFormat`, `transactionPageSize`).
Password values and hashes are never included.

Both PUT operations require all fields in their editable subset. Names are
trimmed, required, and limited to 100 characters. Preferences support `MEDIUM`
or `ISO` for date format, and 10, 25, or 50 for transaction page size. Updates
preserve other account fields and relationships. The existing entity lifecycle
preserves `createdAt` and advances `updatedAt` on writes.

Password changes verify the exact current password with BCrypt and reject reuse
of the current password. New passwords use the shared registration policy:
minimum 15 Unicode code points, maximum 72 UTF-8 bytes, spaces and Unicode
allowed, and a local common-password blocklist. Passwords are not trimmed or
normalized. There are no uppercase, digit, or symbol composition requirements.
Existing shorter passwords remain eligible for login and current-password checks.

## Errors

Validation errors return HTTP 400 with the existing envelope:

```json
{
  "timestamp": "2026-09-25T12:00:00",
  "status": 400,
  "error": "Validation Failed",
  "fields": { "currentPassword": "Current password is incorrect" }
}
```

Password reuse produces `fields.newPassword`. Neither error returns 401, so a
frontend session must remain active. Invalid fields return field errors; malformed
JSON returns a safe 400 message. Unexpected account failures return a generic 500
message. Missing, malformed, invalid, or expired authentication returns the normal
401 envelope. A token referring to a user who no longer exists also returns 401.

Do not enable DEBUG logging for Spring's ExceptionHandlerExceptionResolver: its
exception messages can contain rejected credentials. Credential request DTOs and
the login response redact secrets in `toString()`; no account request-body logging
is introduced.

## Token and concurrency limitations

Existing JWTs remain valid until their normal expiration after a password change.
Removing a token from a browser is not server-side revocation. Token revocation and
invalidation belong to issue #18; Phase 3 must communicate this limitation.

Hibernate updates only dirty user columns, preventing a profile save from
rewriting unrelated credentials. No optimistic locking or token-version field is
introduced. Concurrent edits to the same fields can still use last-writer-wins
semantics, including overlapping password changes; stronger concurrency controls
remain a separate decision.
