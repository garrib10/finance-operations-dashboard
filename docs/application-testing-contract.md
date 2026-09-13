# FinTrack Application Testing Contract

This document defines the deployed FinTrack behavior that automated tests may rely on.

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
| Any unknown route | Conditional | Redirects to `/`, which then requires authentication |

The Dashboard route is `/`, not `/dashboard`.

When an unauthenticated user opens a protected route, FinTrack redirects the user to `/login`.

Successful registration redirects to `/login`. Successful login redirects to `/`.

## Authentication behavior

FinTrack uses stateless JWT access-token authentication.

The frontend stores the access token in browser `localStorage` using `fintrack_access_token`.

On application startup:

1. The frontend checks for a stored access token.
2. If no token exists, session restoration finishes without authentication.
3. If a token exists, the frontend calls `GET /api/auth/me`.
4. If the request succeeds, the authenticated user is restored.
5. If the request fails, the current implementation removes the token and clears the user.

Logging out removes the stored token and clears the authenticated user.

The current implementation treats every `/api/auth/me` restoration failure as an invalid session, including temporary network and server failures. Automated tests must follow this behavior until session-restoration resilience is implemented.

Authenticated API 401 responses are not yet handled globally by redirecting every active page to Login. Selenium tests must not assume that every API 401 causes an automatic redirect until the corresponding enhancement is deployed.

Invalid credentials submitted through the Login form remain a Login form error.

## Public API endpoints

| Method | Endpoint             | Purpose                                 |
| ------ | -------------------- | --------------------------------------- |
| `GET`  | `/api/health`        | Check backend availability              |
| `POST` | `/api/auth/register` | Register a user                         |
| `POST` | `/api/auth/login`    | Authenticate and obtain an access token |

All other application endpoints require a valid JWT.

## Registration validation

| Field      | Rule                                          |
| ---------- | --------------------------------------------- |
| First name | Required; maximum 100 characters              |
| Last name  | Required; maximum 100 characters              |
| Email      | Required; valid email; maximum 255 characters |
| Password   | Required; between 8 and 72 characters         |

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

Transaction records are user-owned. A user must not be able to access another userâ€™s transactions through record IDs.

### Transaction test data

Automation should use unique descriptions such as `selenium-<run-id>-income` and `selenium-<run-id>-expense`.

Before retrying a failed create action, automation must confirm whether the record was already persisted. This prevents duplicate data when persistence succeeds but the UI refresh fails.

Until deterministic secondary sorting is deployed, tests should avoid creating transactions with tied values when asserting exact ordering.

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
- Every year represented by the userâ€™s saved budgets
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
| Monthly Income       | Sum of `INCOME` transactions in the serverâ€™s current calendar month                         |
| Monthly Expenses     | Sum of `EXPENSE` transactions in the serverâ€™s current calendar month                        |
| Recent Transactions  | Up to five transactions ordered by transaction date descending, then creation time descending |
| Spending by Category | Current-month `EXPENSE` totals grouped by category                                            |
| Monthly Budgets      | Budgets matching the serverâ€™s current month and year                                        |

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

FinTrack uses Java `LocalDate.now()` for server-side current-month calculations. The resulting current date depends on the Railway runtimeâ€™s configured timezone, which may differ from the machine executing Selenium.

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

- Do not expect every authenticated API 401 to redirect automatically
- Temporary session-restoration failures remove the stored token
- Avoid tied transaction sort values when testing exact ordering
- Check for persisted records before retrying create actions
- Do not expect automatic account cleanup
- Verify current-month and timezone-sensitive behavior against production

When these behaviors change, update this contract and the corresponding Selenium expectations in the same delivery cycle.
