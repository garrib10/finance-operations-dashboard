# FinTrack Application Testing Contract

This document defines the branch implementation contract. Confirm the deployed revision before applying these expectations to a hosted environment; issue #16 is not deployed by this documentation update.

The Selenium automation suite is maintained in a separate repository. FinTrack does not include Selenium dependencies, WebDriver configuration, or Selenium test code.

## Deployed environments

| Service                 | URL                                                                       |
| ----------------------- | ------------------------------------------------------------------------- |
| Production frontend     | https://finance-operations-dashboard.vercel.app/                          |
| Production backend      | https://finance-operations-dashboard-production.up.railway.app            |
| Production health check | https://finance-operations-dashboard-production.up.railway.app/api/health |
| Local frontend          | http://localhost:5173                                                     |
| Local backend           | http://localhost:8080                                                     |
| Local Swagger UI        | http://localhost:8080/swagger-ui/index.html                               |

Swagger/OpenAPI is intentionally disabled in production.

Automation must use environment variables for deployed URLs rather than hard-coding them throughout the test suite.

## Frontend routes

| Route             | Access      | Expected behavior                                    |
| ----------------- | ----------- | ---------------------------------------------------- |
| `/login`          | Public      | Displays the Login page                              |
| `/register`       | Public      | Displays the Registration page                       |
| `/`               | Protected   | Displays the Dashboard                               |
| `/transactions`   | Protected   | Displays transaction management                      |
| `/budgets`        | Protected   | Displays budget management                           |
| `/profile`        | Protected   | Edit own names; view read-only email                 |
| `/settings`       | Protected   | Edit preferences and change password                 |
| Any unknown route | Conditional | Redirects to `/`, which then requires authentication |

The Dashboard route is `/`, not `/dashboard`.

When an unauthenticated user opens a protected route, FinTrack redirects the user to `/login`.

Successful registration redirects to `/login`. Successful login returns to the attempted protected route (including query/hash), or `/` when no destination was saved.

## Authentication behavior

FinTrack uses stateless JWT access-token authentication.

The frontend stores the access token in browser `localStorage` using `fintrack_access_token`.

On application startup:

1. The frontend checks for a stored access token.
2. If no token exists, session restoration finishes without authentication.
3. If a token exists, the frontend calls `GET /api/auth/me`.
4. If the request succeeds, the authenticated user is restored.
5. An authenticated 401 for the current token removes it and clears the user.
6. A network/server restoration failure preserves the token, shows a recovery message, and offers Retry or Sign in again; protected content stays hidden.

Logging out removes the stored token and clears the authenticated user.

Authenticated API 401 responses invalidate the current session centrally; protected
routes redirect to Login. A late 401 belonging to a different, previous token does
not invalidate the newer session. Account responses from a previous session and
restoration responses predating a successful save cannot replace newer user state.
Incorrect current-password errors use 400 and keep the session active.

Invalid credentials submitted through the Login form remain a Login form error.

## Public API endpoints

| Method | Endpoint             | Purpose                                 |
| ------ | -------------------- | --------------------------------------- |
| `GET`  | `/api/health`        | Check backend availability              |
| `POST` | `/api/auth/register` | Register a user                         |
| `POST` | `/api/auth/login`    | Authenticate and obtain an access token |

All other application endpoints require a valid JWT.

## Registration validation

| Field      | Rule                                                              |
| ---------- | ----------------------------------------------------------------- |
| First name | Required; maximum 100 characters                                  |
| Last name  | Required; maximum 100 characters                                  |
| Email      | Required; valid email; maximum 255 characters                     |
| Password   | Required; at least 15 Unicode code points, at most 72 UTF-8 bytes |

Email addresses must be unique. Successful-registration automation must use a unique fictional email address.

FinTrack does not currently provide account deletion. Accounts created in deployed environments remain stored unless removed manually from the database.

Successful-registration tests should therefore be executed deliberately rather than creating a new account during every routine smoke-test run.

## Login validation

| Field    | Rule                                       |
| -------- | ------------------------------------------ |
| Email    | Required and must be a valid email address |
| Password | Required                                   |

Routine automation should use dedicated accounts containing fictional data. Credentials must be stored in CI secrets or local environment variables and must never be committed.

## Transaction behavior

Authenticated users can create, view, edit, delete, search, filter, sort, and paginate their own transactions.

### Transaction validation

| Field            | Rule                                               |
| ---------------- | -------------------------------------------------- |
| Category         | Required and must belong to the authenticated user |
| Type             | Required                                           |
| Amount           | Required and must be at least `0.01`               |
| Description      | Required; maximum 255 characters                   |
| Transaction date | Required                                           |

Supported transaction types are `INCOME` and `EXPENSE`.

Transaction records are user-owned. A user must not be able to access another user's transactions through record IDs.

### Transaction test data

Automation should use unique descriptions such as `selenium-<run-id>-income` and `selenium-<run-id>-expense`.

Before retrying a failed create action, automation must confirm whether the record was already persisted. This prevents duplicate data when persistence succeeds but the UI refresh fails.

Transactions with tied sort values use transaction ID as a secondary key in the same direction.

## Budget behavior

Authenticated users can create, view, edit, and delete their own monthly budgets.

### Budget validation

| Field         | Rule                                               |
| ------------- | -------------------------------------------------- |
| Category      | Required and must belong to the authenticated user |
| Monthly limit | Required and must be at least `0.01`               |
| Month         | Must be between `1` and `12`                       |
| Year          | Must be `2000` or later                            |

### Budget uniqueness

A user can have only one budget for each unique combination of user, category, month, and year.

Creating or updating a budget to an existing combination returns `Budget already exists for this category and month`.

Automation should reserve category, month, and year combinations for individual tests or test runs. Before retrying budget creation, automation must confirm whether the combination already exists.

### Budget period selector

The Budget Period selector includes:

- The default supported year range
- Every year represented by the user's saved budgets
- No duplicate year options
- Years sorted numerically

A saved budget outside the default range remains reachable by selecting its saved year and month.

### Budget analytics and progress

Budget analytics include monthly limit, amount spent, amount remaining, percentage used, and status.

Budget progress bars expose:

- `role="progressbar"`
- An accessible category-specific name
- `aria-valuemin="0"`
- `aria-valuemax="100"`
- A bounded `aria-valuenow`
- An `aria-valuetext` containing the actual percentage used

Usage above 100% remains visible and available through `aria-valuetext`, while the visual width and `aria-valuenow` are capped at 100.

## Dashboard calculations

Dashboard data is scoped to the authenticated user.

| Value                | Calculation                                                                                   |
| -------------------- | --------------------------------------------------------------------------------------------- |
| Total Income         | Sum of all `INCOME` transactions                                                              |
| Total Expenses       | Sum of all `EXPENSE` transactions                                                             |
| Current Balance      | Total Income minus Total Expenses                                                             |
| Monthly Income       | Sum of `INCOME` transactions in the server's current calendar month                           |
| Monthly Expenses     | Sum of `EXPENSE` transactions in the server's current calendar month                          |
| Recent Transactions  | Up to five transactions ordered by transaction date descending, then creation time descending |
| Spending by Category | Current-month `EXPENSE` totals grouped by category                                            |
| Monthly Budgets      | Budgets matching the server's current month and year                                          |

## Stable automation selectors

Accessible roles, labels, input IDs, link names, and button names should be preferred when they are stable and unambiguous.

| Element                | Selector                   |
| ---------------------- | -------------------------- |
| Transaction record     | `transaction-row-{id}`     |
| Budget record          | `budget-card-{id}`         |
| Budget period controls | `budget-period-filter`     |
| Current Balance        | `summary-current-balance`  |
| Total Income           | `summary-total-income`     |
| Total Expenses         | `summary-total-expenses`   |
| Monthly Income         | `summary-monthly-income`   |
| Monthly Expenses       | `summary-monthly-expenses` |

These values are exposed through `data-testid`.

Tests should not depend on generated CSS class names, DOM position, or visual layout when a role, label, ID, button name, or documented selector is available.

## Timezone and date assumptions

FinTrack uses Java `LocalDate.now()` for server-side current-month calculations. The resulting current date depends on the Railway runtime's configured timezone, which may differ from the machine executing Selenium.

Therefore:

- Current-month tests must verify behavior against the deployed environment
- Tests near midnight or month boundaries require additional caution
- Transaction dates are date-only values
- Automation should not assume that the local test-runner date always matches the backend date
- CI should perform a health and readiness check before date-sensitive workflows
- Date-sensitive expectations should be based on observed deployed behavior

## Test-data ownership and cleanup

All automation accounts and records must use fictional data.

The initial Selenium suite should run serially. Before enabling parallel execution, each worker must receive a separate automation account.

Automation should:

- Use unique transaction descriptions
- Reserve budget category/month/year combinations
- Delete transactions created by a completed test
- Delete budgets created by a completed test
- Attempt cleanup even when a test fails
- Check whether a record exists before retrying creation
- Avoid modifying unrelated records
- Avoid relying on shared mutable data between tests

FinTrack does not provide account deletion, a production test-data cleanup endpoint, or automatic cleanup of retained registration-test users.

Tests must work within those limitations. No account-deletion feature or production cleanup endpoint is required for the Selenium project.

## Initial Selenium execution expectations

The initial automation suite should:

- Run serially
- Begin with one supported browser
- Use bounded explicit readiness waits
- Avoid fixed sleeps
- Capture screenshots and relevant diagnostics on failure
- Exclude passwords, JWTs, cookies, and other secrets from artifacts
- Confirm authentication before executing protected workflows
- Clean up UI-created transaction and budget data when possible

Browser expansion and parallel workers should be added only after serial execution and cleanup are reliable.

## Health and readiness

Before running deployed UI workflows, automation should request `GET https://finance-operations-dashboard-production.up.railway.app/api/health`.

A bounded retry should be used because Railway may require startup time.

The test run must fail with a clear environment-readiness message if the backend does not become healthy within the configured timeout.

## Known limitations affecting automation

Until corresponding improvements are deployed:

- Password changes and browser logout do not revoke already-issued JWTs (issue #18)
- Temporary restoration failures require explicit retry; they preserve the stored token
- Check for persisted records before retrying create actions
- Do not expect automatic account cleanup
- Verify current-month and timezone-sensitive behavior against production

When these behaviors change, update this contract and the corresponding Selenium expectations in the same delivery cycle.

## Profile and account settings contract

`GET /api/auth/me` and both account PUT responses return the canonical user:
`id`, `firstName`, `lastName`, `displayName`, `email`, `createdAt`, and
`preferences` containing `dateFormat` and numeric `transactionPageSize`.
All account operations require bearer authentication and derive ownership from the
principal. No target user ID or email is accepted as an editable field.

- `PUT /api/account/profile`: all three names are required, trimmed, and at most
  100 Java/JavaScript string units. Email is read-only and excluded from requests.
- `PUT /api/account/preferences`: `MEDIUM` or `ISO`, and numeric 10, 25, or 50.
  Both PUTs return 200 and update AuthContext immediately; failures retain edits.
- Date formatting applies to Dashboard recent transactions and Transaction rows,
  preserving date-only calendar values. Page size applies to all Transaction list
  requests; a change reloads page zero with current filters and sorting.
- `POST /api/account/password`: sends only `currentPassword` and `newPassword`;
  confirmation stays in the browser. Success is 204, clears all password inputs,
  and leaves the session active. The old password then fails login; the new one succeeds.
- New passwords follow registration policy: at least 15 Unicode code points,
  at most 72 UTF-8 bytes, no trimming/normalization or composition requirement.
  All-blank/common passwords and current-password reuse are rejected.
- Incorrect current password and validation failures return safe field-specific 400
  errors. Genuine authentication failures retain centralized 401 handling.
- Logout and password changes do not revoke previously issued JWTs. Refresh-token
  rotation/revocation belongs to issue #18.

Prefer labels such as `Display name`, `Date format`, `Transactions per page`,
`Current password`, `New password`, and `Confirm new password`. The names of save,
reset, and visibility buttons are stable accessible selectors. Do not assert colors
alone: requirement indicators include checkmarks and accessible Met/Not met text.
See [the staging checklist](frontend-testing.md) for manual accessibility coverage.
