# Budgets API

Budgets are monthly spending limits, one per category per month for each user. Every
endpoint requires a bearer access token; the user always comes from that token, and no
endpoint accepts a user ID, email, or other ownership identifier. Another user's budget
behaves exactly like a missing one.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/budgets` | Create a budget (`201`) |
| `GET` | `/api/budgets` | All of the user's budgets, newest month first |
| `GET` | `/api/budgets/{id}` | One budget |
| `GET` | `/api/budgets/analytics?month=&year=` | One month's budgets with analytics, in one request (issue #105) |
| `GET` | `/api/budgets/{id}/analytics` | One budget's analytics |
| `PUT` | `/api/budgets/{id}` | Update a budget |
| `DELETE` | `/api/budgets/{id}` | Delete a budget (`204`) |

Creating and updating take `monthlyLimit` (0.01 or more, at most 10 whole digits and 2
decimal places), `month` (1–12), `year` (2000 or later, no upper limit, so future months
can be planned), and a category: an existing `categoryId` or a `newCategory` created in
the same transaction. See [categories-api.md](categories-api.md) for the category rules.

## `GET /api/budgets/analytics`

Every budget the signed-in user has for one month, each with its analytics, so a client
needs one request instead of one per budget (issue #105). `GET /api/budgets/{id}/analytics`
stays available and returns the same figures for a single budget.

**Parameters** (both required):

| Parameter | Accepted | Errors (`400`, field: message) |
| --- | --- | --- |
| `month` | `1`–`12`, digits only | missing or empty: `month`: `Month is required`; not plain digits (`abc`, `8.5`, `-1`, `+8`, ` 8`): `Month must be a whole number between 1 and 12`; out of range: `Month must be between 1 and 12` |
| `year` | `2000` or later, digits only, no upper limit | missing or empty: `year`: `Year is required`; not plain digits: `Year must be a whole number`; before 2000: `Year must be 2000 or later` |

The range is the same as creating a budget, so past and future months both work. Both
fields are reported at once when both are wrong. Errors use the standard validation body:

```json
{
  "timestamp": "2026-10-10T09:15:00",
  "status": 400,
  "error": "Validation Failed",
  "fields": { "month": "Month must be between 1 and 12" }
}
```

`GET /api/budgets/analytics?year=2026` (month missing):

```json
{
  "timestamp": "2026-10-10T09:15:00",
  "status": 400,
  "error": "Validation Failed",
  "fields": { "month": "Month is required" }
}
```

Unauthenticated requests receive `401`, as on every budget endpoint.

**Response.** `GET /api/budgets/analytics?month=3&year=2024`:

```json
{
  "month": 3,
  "year": 2024,
  "budgets": [
    {
      "budgetId": 21,
      "categoryId": 57,
      "categoryName": "Apartment Rent",
      "categoryIconKey": "house",
      "monthlyLimit": 1200.00,
      "amountSpent": 0.00,
      "amountRemaining": 1200.00,
      "percentageUsed": 0.00,
      "status": "ON_TRACK",
      "month": 3,
      "year": 2024
    },
    {
      "budgetId": 19,
      "categoryId": 42,
      "categoryName": "Pet Care",
      "categoryIconKey": "paw-print",
      "monthlyLimit": 80.00,
      "amountSpent": 50.00,
      "amountRemaining": 30.00,
      "percentageUsed": 62.50,
      "status": "CAUTION",
      "month": 3,
      "year": 2024
    }
  ]
}
```

A month without budgets, `GET /api/budgets/analytics?month=1&year=2001`:

```json
{ "month": 1, "year": 2001, "budgets": [] }
```

| Field | Meaning |
| --- | --- |
| `month`, `year` | The requested month. |
| `budgets` | The user's budgets for that month only, ordered by category name (the database's collation), then category ID. Each item has exactly the fields of `GET /api/budgets/{id}/analytics`. |
| `amountSpent` | **Expense** transactions in the budget's category during the month, inclusive of the 1st and the last day (including future-dated ones). Income never counts. `0.00` when there were none. |
| `amountRemaining`, `percentageUsed`, `status` | From `BudgetMetrics`, the single definition shared with the single-budget analytics, the dashboard, and the Categories summary: remaining is limit minus spent (negative when over), the percentage has two decimal places, and the status is `ON_TRACK` below 50%, `CAUTION` from 50%, `WARNING` from 75%, `OVER_BUDGET` from 100%. |

Money values are JSON numbers with two decimal places, computed with exact decimal
arithmetic. Spending in a category without a budget that month never creates a row, and
categories are matched by ID, so two users' same-named categories never mix.

**Queries.** Exactly three statements per request, however many budgets the month has
(none included): the user, the month's budgets with their categories (one join), and the
month's expense spending grouped by category. Budgets from other months and older
transactions are never loaded. `BudgetMonthAnalyticsQueryCountTest` holds this at 3 for 0,
2, 8, and 15 budgets, with and without spending.
