# Local demo data

`scripts/seed-local-demo.mjs` creates a fictional demo account with realistic sample data in
a **local** FinTrack, for manual testing, screenshots, and developing the Selenium suite
(issue #125). It talks to the API like the frontend does; it never touches the database
directly.

## What it creates

For the account in `FINTRACK_SEED_EMAIL` (registered if it does not exist yet):

- 2 custom categories: "Pet Care" (paw-print icon) and "Coffee" (coffee icon)
- 22 transactions: salary, rent, groceries, and so on, in the previous and current month
  (never dated after today), using built-in and custom categories
- 6 budgets for the current month with mixed statuses (on track, caution, warning, over
  budget), including both custom categories

All names and amounts are fictional.

## Run it

1. Start the local backend (`./scripts/run-local.sh`). The frontend is optional for seeding.
2. Choose a new email and a password of at least 15 characters. Keep the password in your
   password manager; never commit it or paste it into issues.
3. Run, from the repository root:

   ```bash
   FINTRACK_SEED_EMAIL=demo-seed@example.com \
   FINTRACK_SEED_PASSWORD='<your 15+ character password>' \
   node scripts/seed-local-demo.mjs
   ```

4. Sign in at http://localhost:5173 with that email and password.

Optional variables:

| Variable | Default | Meaning |
| --- | --- | --- |
| `FINTRACK_API_URL` | `http://localhost:8080` | the local backend |
| `FINTRACK_FRONTEND_ORIGIN` | `http://localhost:5173` | sent as `Origin` on sign-in; must be in the backend's `FRONTEND_URLS` |

## Safety rules

- **Local only.** The script refuses any API whose host is not `localhost`, `127.0.0.1`, or
  `::1`, so it can never create data on staging or production.
- **No credentials in the repository.** Email and password come only from environment
  variables, and the password is never printed.
- **Never duplicates data.** If the account already has transactions, it stops without
  adding anything; use a new email for a fresh demo account.
- FinTrack has no account deletion, so seeded local accounts stay in your local database.
  That is harmless; reset the local database if you want a clean slate.
