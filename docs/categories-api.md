# Categories API

Categories belong to exactly one user. Every endpoint below requires a bearer access
token (`Authorization: Bearer <token>`); the refresh cookie alone does not authorize
category requests. Missing, invalid, or expired tokens get the standard
`401 Unauthorized` response. The owner always comes from the token, never from the
request body. For the underlying policy (normalization, built-in defaults, icons, and
database safeguards) see [categories](categories.md).

## Response

Every endpoint returns categories in the same shape:

```json
{
  "id": 42,
  "name": "Pet Care",
  "budgetEnabled": true,
  "builtIn": false,
  "iconKey": "paw-print",
  "createdAt": "2026-09-30T09:00:00",
  "updatedAt": "2026-09-30T09:00:00"
}
```

| Field | Notes |
| --- | --- |
| `id` | Stable; never changes on rename |
| `name` | Display name, normalized (NFC, whitespace collapsed and trimmed) |
| `budgetEnabled` | Whether the category can have budgets |
| `builtIn` | `true` for the 13 seeded defaults; set by the server only |
| `iconKey` | Approved semantic icon key (catalog listed in [categories](categories.md#icons)). A stored key outside the current catalog is returned as `tag` (the row is not changed) |
| `createdAt`, `updatedAt` | Local date-times, as elsewhere in the API |

Responses never include the internal comparison name, the owner, or any other
internal field.

## Endpoints

### `GET /api/categories`

Lists the authenticated user's built-in and custom categories, ordered by name, then ID.

### `GET /api/categories/{id}`

Returns one of the user's categories. `404 CATEGORY_NOT_FOUND` if it does not exist or
belongs to someone else.

### `POST /api/categories`

```json
{ "name": "Pet Care", "budgetEnabled": true, "iconKey": "paw-print" }
```

- Creates a **custom** category (`builtIn: false`) owned by the caller. Returns
  `201 Created` with the category (no `Location` header, like the other create
  endpoints).
- `iconKey` is optional; omitted or blank means `tag`. A supplied key must be an exact
  key from the approved catalog.
- Extra properties such as `builtIn`, `userId`, `id`, or `normalizedName` are ignored.
- `409 CATEGORY_DUPLICATE` if the name matches one of the user's existing categories,
  built-in or custom, after normalization (for example `pet care` or `HOUSING`).

### `PUT /api/categories/{id}`

```json
{ "name": "Pet Supplies", "budgetEnabled": false, "iconKey": "piggy-bank" }
```

Full update of a **custom** category:

- Renaming is allowed while the category is in use. The ID and every transaction and
  budget reference stay the same, so those records show the new name the next time they
  load. Nothing is copied, merged, or rewritten.
- Changing only case or spacing (`Pet Care` → `pet care`) is allowed; a category never
  conflicts with itself.
- `iconKey` is optional; omitted or blank **keeps the current icon**, so older clients
  never reset it. A supplied key must be approved.
- `409 CATEGORY_DUPLICATE` if the new name matches another of the user's categories.

Any `PUT` to a **built-in** category returns `403 CATEGORY_BUILT_IN`, even when every
submitted value matches the current ones. Built-in names, icons, and budget flags are
fixed.

### `DELETE /api/categories/{id}`

| Category | Result |
| --- | --- |
| Custom, not referenced by any transaction or budget | Permanently deleted: `204 No Content`, empty body |
| Custom, referenced by any transaction or any budget (any month or year) | `409 CATEGORY_IN_USE`; nothing is deleted, reassigned, or renamed |
| Built-in | `403 CATEGORY_BUILT_IN` |
| Missing or another user's | `404 CATEGORY_NOT_FOUND` |

To delete a category that is in use, first move or delete its transactions and budgets.

### `GET /api/categories/summary`

Usage of every category the authenticated user owns, for the Categories page (issue #100),
for the server's current month or an earlier one (issue #103). Read-only; it never accepts
a user or category ID, so ownership always comes from the access token.

| Request | Month described |
| --- | --- |
| `GET /api/categories/summary` | The server's current reporting month (the default) |
| `GET /api/categories/summary?month=8&year=2026` | August 2026 |

**Optional parameters.** `month` and `year` are given together or not at all, and are
validated before any data is read:

| Rule | Field | Message |
| --- | --- | --- |
| Only one of the two given | the missing one | `Month and year must be given together` |
| `month` outside 1–12 | `month` | `Month must be between 1 and 12` |
| `year` before 2000 (the earliest year budgets accept) | `year` | `Year must be 2000 or later` |
| `year` after the server's current year | `year` | `Year must be {current year} or earlier` |
| A later month of the current year (no future months) | `month` | `Choose {Month Year} or an earlier month` |
| `month` is not a whole number (`month=abc`) | `month` | `Month must be a whole number between 1 and 12` |
| `year` is not a whole number (`year=abcd`) | `year` | `Year must be a whole number` |

Invalid periods return `400` with the standard field-validation body, for example
`GET /api/categories/summary?month=8`:

```json
{
  "timestamp": "2026-10-04T18:30:00",
  "status": 400,
  "error": "Validation Failed",
  "fields": { "year": "Month and year must be given together" }
}
```

`?month=13&year=1999` reports both fields at once (`month` and `year`).

**Number parsing is Spring's.** A value that converts to the same whole number is
accepted as that number: `month= 8` (padded), `month=+8`, and `month=08` all mean August,
while `8.0`, `2e3`, and text are rejected as above. A parameter given twice
(`month=8&month=9`) uses the first value. These spellings are not part of the contract:
the Categories page treats them as invalid URLs, removes them, and only ever sends one
plain `month` and one plain `year`. The range rules above always apply.

Response for
`GET /api/categories/summary?month=10&year=2026` (or no parameters in October 2026):

```json
{
  "month": 10,
  "year": 2026,
  "serverCurrentMonth": 10,
  "serverCurrentYear": 2026,
  "categories": [
    {
      "id": 42,
      "name": "Pet Care",
      "iconKey": "paw-print",
      "builtIn": false,
      "budgetEnabled": true,
      "transactionCount": 5,
      "currentMonthTransactionCount": 2,
      "budgetCount": 3,
      "lastTransactionDate": "2026-10-20",
      "currentMonthSpent": 60.00,
      "allTimeSpent": 140.00,
      "currentMonthBudget": {
        "budgetId": 11,
        "monthlyLimit": 80.00,
        "amountSpent": 60.00,
        "amountRemaining": 20.00,
        "percentageUsed": 75.00,
        "status": "WARNING"
      },
      "canDelete": false
    },
    {
      "id": 3,
      "name": "Income",
      "iconKey": "circle-dollar-sign",
      "builtIn": true,
      "budgetEnabled": false,
      "transactionCount": 2,
      "currentMonthTransactionCount": 0,
      "budgetCount": 0,
      "lastTransactionDate": "2026-09-30",
      "currentMonthSpent": 0.00,
      "allTimeSpent": 0.00,
      "currentMonthBudget": null,
      "canDelete": false
    },
    {
      "id": 57,
      "name": "Weekend Trips",
      "iconKey": "plane",
      "builtIn": false,
      "budgetEnabled": true,
      "transactionCount": 0,
      "currentMonthTransactionCount": 0,
      "budgetCount": 0,
      "lastTransactionDate": null,
      "currentMonthSpent": 0.00,
      "allTimeSpent": 0.00,
      "currentMonthBudget": null,
      "canDelete": true
    }
  ]
}
```

The rows show a used custom category with this month's budget and activity, a built-in
income category used before but not this month (`currentMonthTransactionCount` is `0`;
income counts as a transaction but never as spending, and built-ins can never be deleted),
and a never-used custom category (`null` date and budget, deletable).

| Field | Meaning |
| --- | --- |
| `month`, `year` | The month the figures describe: the requested month, or by default the server's reporting month (the whole calendar month containing today in the server's default time zone, the same month the dashboard uses). Every `currentMonth*` row field refers to this month. |
| `serverCurrentMonth`, `serverCurrentYear` | Always the server's own current reporting month, whatever was requested. Equal to `month`/`year` for a default request (both come from one clock reading, so even a request running across midnight at a month end reports one month); a client compares them to tell whether it is showing history, and uses them as the latest month it may request. |
| `categories` | Every category the user owns, ordered by name, then ID. Empty only if the user has no categories. |
| `transactionCount` | All of the category's transactions, income and expense, in any month. With `budgetCount` it decides `canDelete`. |
| `currentMonthTransactionCount` | The category's transactions, income and expense, in the reporting month given by the top-level `month` and `year` (inclusive of the 1st and last day, including future-dated ones). Always present, `0` when none. It is for display only and never affects `canDelete`; spending still counts expenses only. |
| `budgetCount` | The category's budgets in any month or year. |
| `lastTransactionDate` | The latest transaction date (`yyyy-MM-dd`), including future-dated ones; `null` when never used. |
| `currentMonthSpent`, `allTimeSpent` | Sums of **expense** transactions only, in the reporting month and in total. Income never counts as spending. Always present, two decimal places (`0.00` when none). |
| `currentMonthBudget` | The category's budget for the reporting month, or `null`. `amountSpent` equals `currentMonthSpent`; `amountRemaining`, `percentageUsed`, and `status` use the same rules as budget analytics and the dashboard (`ON_TRACK` below 50%, `CAUTION` from 50%, `WARNING` from 75%, `OVER_BUDGET` from 100%). Reported whenever such a budget exists, regardless of `budgetEnabled`. |
| `canDelete` | `true` only for a custom category with no transactions and no budgets. It reflects the moment of the request: `DELETE` still re-checks and returns `409 CATEGORY_IN_USE` if a reference was added since. |

Money values are JSON numbers with two decimal places, computed with exact decimal
arithmetic. Unauthenticated requests receive `401`. Whichever month is requested, the
all-time fields (`transactionCount`, `budgetCount`, `lastTransactionDate`, `allTimeSpent`)
and `canDelete` are the same; only `currentMonthSpent`, `currentMonthTransactionCount`,
`currentMonthBudget`, `month`, and `year` follow the requested month. A category with no
activity that month is still listed, with zeros and a `null` budget. For example,
`?month=8&year=2026` in October 2026 returns `"month": 8, "year": 2026,
"serverCurrentMonth": 10, "serverCurrentYear": 2026`.

**Queries.** The summary runs a fixed five statements however many categories exist, and
exactly as many for an earlier month as for the default request (both tested), for
the current month or an earlier one (the requested month only changes the date range and
month passed to the same queries): the
user, the categories, one grouped transaction aggregate (counts, latest date, and both
expense sums by conditional aggregation), one grouped budget count, and the reporting
month's budgets. The transaction aggregate computes both counts, the latest date, and both
expense sums in that one grouped statement (`COUNT(CASE …)` for this month's count), so
adding `currentMonthTransactionCount` added no query. Transactions and budgets are aggregated separately rather than joined
together, so no count or sum is multiplied. `CategorySummaryQueryCountTest` asserts the
same statement count for 2 and 12 categories, both for the current month and for an
explicitly requested earlier month.

## Categories in transactions and budgets

`POST`/`PUT /api/transactions` and `POST`/`PUT /api/budgets` choose their category with
**exactly one** of:

```json
{ "categoryId": 42, "type": "EXPENSE", "amount": 40.00, "description": "Vet", "transactionDate": "2026-09-30" }
```

```json
{ "newCategory": { "name": "Pet Care", "iconKey": "paw-print" },
  "type": "EXPENSE", "amount": 40.00, "description": "Vet", "transactionDate": "2026-09-30" }
```

(Budgets use the same `categoryId` / `newCategory` fields alongside `monthlyLimit`,
`month`, and `year`.) Existing clients that send only `categoryId` work unchanged.

| `categoryId` | `newCategory` | Result |
| --- | --- | --- |
| set | absent | Uses that category; it must be the caller's (`404 CATEGORY_NOT_FOUND` otherwise, identical for missing and foreign IDs) |
| absent | set | Creates a custom category for the caller, then the record |
| set | set | `400`, `fields.newCategory`: `Choose an existing category or a new category, not both` |
| absent | absent | `400`, `fields.categoryId`: `Choose an existing category or create a new one` |

`newCategory` accepts only `name` (required, same normalization and limits as the category
API) and `iconKey` (optional; omitted or blank means `tag`; must be an approved key).
Anything else (`builtIn`, `budgetEnabled`, `userId`, IDs) is ignored. A category created this
way is custom (`builtIn: false`), owned by the caller, and always `budgetEnabled: true`, so
it can be used by both transactions and budgets afterwards. Its errors use the nested field
names: `fields["newCategory.name"]`, `fields["newCategory.iconKey"]`.

**Atomic.** The category and the transaction or budget are written in one database
transaction. If the financial record fails for any reason (validation, a duplicate budget
period, a database error), the new category is not kept. On updates, the transaction or
budget is found and authorized first, so a `404` never creates a category.

**Duplicates.** If the name matches one of the caller's categories (built-in or custom,
after normalization), the request returns `409 CATEGORY_DUPLICATE` and nothing is
created. The existing category is never reused silently; a client can reload categories
and offer the existing one. Under concurrency, two equivalent new-category requests produce
exactly one success and one `409`, with no record created for the loser. Requests are
never retried automatically.

### Transaction category filter

`GET /api/transactions?categoryId=42` limits results to one of the caller's categories by
ID, and combines with `type`, `search`, `startDate`/`endDate`, `minAmount`/`maxAmount`,
sorting (ties still broken by ID), and paging. Omitted means all categories. A zero or
negative value returns `400`; a missing or another user's category returns the same
`404 CATEGORY_NOT_FOUND` as above (not an empty page).

Budgets have no server-side category filter: `GET /api/budgets` already returns all of the
caller's budgets with `categoryId`, so the frontend filters by category on the client
(Phase 4), as it does for month and year.

### Icons in financial and dashboard responses

These responses add `categoryIconKey` next to the existing `categoryId` and
`categoryName` (nothing is removed or renamed):

- Transactions (`TransactionResponse`, including search pages)
- Budgets (`BudgetResponse`) and budget analytics (`BudgetAnalyticsResponse`)
- Dashboard recent transactions, budget summaries, and spending by category

```json
{ "id": 7, "categoryId": 42, "categoryName": "Pet Care", "categoryIconKey": "paw-print", "type": "EXPENSE", "…": "…" }
```

A stored key outside the catalog is returned as `tag`. Spending by category is grouped by
category ID, so renaming a category or changing its icon never changes totals.

## Order of checks and enumeration safety

1. Request shape: body fields and a positive numeric ID (`400`).
2. Ownership: the category is looked up by ID **and** the caller's user ID. Another
   user's category gives exactly the same `404` as a missing one (same status, message,
   and code), before any built-in, name, or in-use check runs.
3. Built-in protection (`403`).
4. Name and icon rules, duplicates (`400` / `409`), or references for deletes (`409`).

## Security and ownership summary

- Every endpoint requires authentication; the user is taken from the access token,
  never from the request body. There is no way to read or change another user's
  categories, and their IDs are indistinguishable from missing ones.
- Composite foreign keys (`user_id`, `category_id`) on transactions and budgets make a
  cross-user reference impossible at the database level, independent of service checks.
- The display name is stored in Unicode NFC with whitespace runs collapsed and trimmed;
  uniqueness compares that name lowercased with `Locale.ROOT` (see
  [Name normalization](categories.md#name-normalization)). Control and other unsupported
  characters are rejected, never stripped or truncated. Icon keys must come from the
  approved catalog in the API; the database also checks the key format
  (`ck_categories_icon_key_format`).
- Names are rendered as text by React (never as HTML); icons are drawn from a fixed
  frontend registry, so a stored key can never load arbitrary content.
- Error bodies never echo submitted values, SQL, constraint names, or other users' data.

## Errors

Business errors use the standard error body with a stable `code`:

```json
{
  "timestamp": "2026-09-30T09:00:00",
  "status": 409,
  "error": "Conflict",
  "message": "This category is used by transactions or budgets and cannot be deleted.",
  "code": "CATEGORY_IN_USE"
}
```

| Condition | Status | `code` | `message` |
| --- | --- | --- | --- |
| Name matches another of the user's categories | 409 | `CATEGORY_DUPLICATE` | `Category already exists` |
| Missing or another user's category | 404 | `CATEGORY_NOT_FOUND` | `Category not found` |
| Change or delete a built-in category | 403 | `CATEGORY_BUILT_IN` | `Built-in categories cannot be changed or deleted.` |
| Delete a referenced category | 409 | `CATEGORY_IN_USE` | `This category is used by transactions or budgets and cannot be deleted.` |

Input errors use the standard validation body (`400`, `"error": "Validation Failed"`)
with per-field messages:

```json
{ "timestamp": "…", "status": 400, "error": "Validation Failed",
  "fields": { "iconKey": "Icon must be one of the approved category icons" } }
```

| Field | Messages |
| --- | --- |
| `name` | `Category name is required`, `Category name must be 100 characters or fewer`, `Category name contains unsupported characters`, `Category name must be text` |
| `iconKey` | `Icon must be one of the approved category icons`, `Icon must be text` |
| `budgetEnabled` | `Budget enabled must be true or false` |
| `id` (path) | `Category ID must be a positive whole number` (zero, negative, non-numeric, or out of range) |

A body that is missing or not valid JSON returns `400` with
`Request body is missing or malformed; check field names and types`. A non-JSON content
type keeps Spring's `415`. Any other failure returns a generic `500` (`Unable to complete
the category request. Please try again.`). No error ever contains SQL, constraint names,
submitted values, tokens, or another user's data.

## Concurrency

The duplicate and in-use checks give clear answers in normal use; the database decides
under concurrency:

- Two requests creating or renaming to the same normalized name: one succeeds, the other
  gets `409 CATEGORY_DUPLICATE` from the `(user_id, normalized_name)` unique key, and its
  transaction rolls back.
- A delete racing with a new transaction or budget for that category: either the
  reference commits first and the delete gets `409 CATEGORY_IN_USE` from the restrictive
  foreign key, or the delete commits first and the new reference is rejected. No
  record is ever left pointing at a deleted category.

Database errors are translated only in context: a unique-key violation on save becomes
`CATEGORY_DUPLICATE` only when it is the normalized-name key (matched case-insensitively,
since MySQL and H2 report the name differently), and a foreign-key violation while
deleting becomes `CATEGORY_IN_USE`. Anything else is the generic `500`. These paths are
verified on MySQL 8.4 with real row locks (`CategoryMutationRaceMySqlIT`,
`CategoryV6MySqlIT`).

## Test coverage

Run with `./mvnw clean verify` (MySQL tests need Docker). Category API tests: `CategoryControllerTest`, `CategoryServiceTest`, `CategoryApiIntegrationTest`, `CategoryMutationRaceMySqlIT`.

Summary tests: `CategorySummaryServiceTest` (merging, zeros, `canDelete`, budget metrics),
`CategorySummaryQueryTest` (aggregate accuracy, month boundaries, income, ownership),
`CategorySummaryQueryCountTest` (fixed statement count), `CategorySummaryIntegrationTest`
(contract, `401`, user isolation with same-named categories, and parity with the dashboard's
spending and budget metrics), `CategorySummaryMySqlIT` (exact decimals on MySQL), plus
`BudgetMetricsTest` and `ReportingPeriodProviderTest` for the shared calculations.

Financial-write tests:

- **Atomic rollback:** `FinancialCategoryIntegrationTest` creates transactions and budgets
  with `newCategory`, then forces failures (invalid fields, an amount too large for the
  column, a duplicate category, a duplicate budget period, a missing record on update) and
  checks the final database state: no category, transaction, or budget is left behind.
- **Ownership:** another user's built-in or custom category ID returns the same `404` as a
  missing one for transaction writes, budget writes, and the transaction category filter
  (`FinancialCategoryIntegrationTest`, `CategorySelectionServiceTest`).
- **Filter combinations:** `categoryId` with type, search, date range, amount range,
  sorting, ties, and paging, isolated per user (`FinancialCategoryIntegrationTest`).
- **Dashboard:** custom expense and income categories, Unicode and 100-character names,
  rename and icon changes leaving totals unchanged, unknown stored icons shown as `tag`,
  and per-user isolation (`FinancialCategoryIntegrationTest`).
- **Query count:** listing transactions and budgets loads categories in the same select
  (`CategoryFetchQueryCountTest`).
- **MySQL concurrency** (`FinancialCategoryRaceMySqlIT`, real InnoDB row locks): equivalent
  new categories from competing transaction and budget writes, a new category racing a
  rename, a transaction racing a category delete, and two budgets for the same period.
  Each loser gets a controlled error and leaves nothing behind.
