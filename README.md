# FinTrack — Finance Operations Dashboard

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-brightgreen?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6.x-3178C6?logo=typescript&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?logo=mysql&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-CC0200?logo=flyway&logoColor=white)
![JUnit 5](https://img.shields.io/badge/JUnit-5-25A162?logo=junit5&logoColor=white)
![Vitest](https://img.shields.io/badge/Vitest-4.1-6E9F18?logo=vitest&logoColor=white)
![Railway](https://img.shields.io/badge/Railway-0B0D0E?logo=railway&logoColor=white)
![Vercel](https://img.shields.io/badge/Vercel-black?logo=vercel)
![Cloudinary](https://img.shields.io/badge/Cloudinary-3448C5?logo=cloudinary&logoColor=white)

FinTrack is a production-deployed full-stack personal finance application built with Java, Spring Boot, React, TypeScript, and MySQL. It provides secure account access, transaction and budget management, financial analytics, and a responsive dashboard.

The project demonstrates layered backend architecture, short-lived JWTs with rotating refresh-token sessions, user-scoped data access, automated testing, and full-stack deployment across Vercel and Railway.

> **Demo project:** FinTrack uses fictional financial data only. It does not connect to banks, process real transactions, or provide financial advice.

---

## Live Demo

| Resource     | URL                                                                                                                                            |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Application  | [finance-operations-dashboard.vercel.app](https://finance-operations-dashboard.vercel.app/)                                                    |
| Health Check | [finance-operations-dashboard-production.up.railway.app/api/health](https://finance-operations-dashboard-production.up.railway.app/api/health) |

---

## Project Highlights

- Full-stack React and Spring Boot application deployed through Vercel and Railway
- Five-minute JWT access tokens held only in memory, renewed through rotating
  refresh tokens in a secure `HttpOnly` cookie, with reuse detection and revocation
- Password changes sign out every device; multi-tab sessions stay in sync
- Same-origin Vercel `/api` proxy, so the refresh cookie is always first-party
- BCrypt password hashing
- User-scoped transactions, categories, budgets, and dashboard data
- Custom categories with approved icons, created together with a transaction or budget in one
  database transaction and reusable everywhere; database-enforced per-user name uniqueness and
  ownership
- Dedicated Categories page: this month's activity and spending distribution, categories
  active this month shown first, no-budget warnings, search, filters, and sorting,
  accessible card actions, and shortcuts into each category's transactions and budget,
  backed by a fixed-query summary API
- Responsive application shell: a collapsible desktop sidebar that remembers its state and
  an accessible mobile navigation drawer, with keyboard and reduced-motion support
- Search, filtering, sorting, pagination, and financial analytics
- Responsive dashboard visualizations built with Recharts
- Production CORS, environment-based secrets, and disabled production API documentation
- **950 passing backend tests** (911 unit + 39 MySQL integration) with **98.86% instruction coverage** and **95.69% branch coverage**
- **927 passing frontend tests** across **58 test files** with **99.58% statement, 98.84% branch, 99.81% function, and 100% line coverage**

---

## Tech Stack

| Area             | Technologies                                                                                                  |
| ---------------- | ------------------------------------------------------------------------------------------------------------- |
| Backend          | Java 21, Spring Boot 4.1, Spring Web MVC, Spring Security, Spring Data JPA, Hibernate, Bean Validation, Maven |
| Frontend         | React 19, TypeScript 6, Vite, React Router, Recharts, Lucide icons, custom CSS                                |
| Database         | MySQL, Flyway                                                                                                 |
| Image Storage    | Cloudinary (optional, backend-only profile photos)                                                            |
| Authentication   | JWT, BCrypt                                                                                                   |
| Backend Testing  | JUnit 5, Mockito, Spring Boot Test, MockMvc, Spring Security Test, H2, Testcontainers (MySQL), JaCoCo        |
| Frontend Testing | Vitest, React Testing Library, jest-dom, jsdom                                                                |
| Deployment       | Vercel, Railway                                                                                               |

---

## Architecture

```text
React + TypeScript Frontend
          |
          | HTTPS / JSON / JWT
          v
Spring Boot REST API
          |
          v
Service Layer
          |
          v
Spring Data JPA / Hibernate
          |
          v
MySQL Database
```

The browser calls relative `/api/...` paths on the Vercel host; a small server-side Vercel function forwards them to the Railway backend, so authentication cookies stay first-party (locally, the Vite dev server does the same). The backend owns authentication, authorization, validation, business logic, and persistence. MySQL credentials and backend secrets are stored only in Railway; they are never exposed to Vercel or browser code.

---

## Features

### Authentication

- User registration and login
- Five-minute access tokens kept only in memory (never in browser storage)
- Rotating refresh tokens in an `HttpOnly`, `Secure`, `SameSite=Lax` cookie; stored
  server-side only as SHA-256 hashes
- Silent renewal: an expired access token triggers one shared refresh and one retry
- Session restoration after reload by refreshing, then loading the canonical user
- Reuse of an old refresh token revokes that whole session family
- Independent sessions per device; logout ends only the current one
- Password changes revoke every session and require signing in again
- Cross-tab coordination with Web Locks and BroadcastChannel (no tokens shared)
- Protected application routes and attempted-route restoration after signing in again
- Temporary refresh failures show a recoverable retry state instead of signing out
- Logout completes only after the server confirms the session was revoked
- Accessible user-account dropdown with account details, keyboard dismissal, focus restoration, and logout

### Profile and Account Settings

- Protected `/profile` and `/settings` pages, available from the account dropdown
- Editable display name, first name, and last name; email remains read-only
- Immediate header identity updates using the canonical saved user response
- Persisted date format: `MEDIUM` (Sep 25, 2026) or `ISO` (2026-09-25)
- Persisted transaction page size: 10, 25, or 50; changes restart pagination at page zero
- Password changes require the correct current password and frontend confirmation
- New passwords require at least 15 Unicode code points and at most 72 UTF-8 bytes;
  spaces and Unicode are allowed without trimming or normalization. Common passwords,
  all-blank passwords, and reuse of the current password are rejected.
- Accessible field errors, focus management, live password-rule checks, visibility
  controls, success messages, and a read-only email hint
- Names and preferences survive refresh and later sign-in; failed saves preserve edits

### Profile Photos

- Optional JPEG or PNG profile photo (up to 2 MB) uploaded from Account Settings
- Local preview before upload, then explicit upload, replace, and confirmed removal
- The backend inspects, re-encodes, and resizes every image to a fresh JPEG, discarding
  EXIF/GPS metadata, before storing it in Cloudinary
- The header avatar and settings page update immediately; photos persist across refresh
  and later sign-in
- Initials remain the fallback when no photo exists, the feature is disabled, or an image
  fails to load
- Failed uploads or removals keep the current photo; the feature is off unless configured

See [Account API](docs/account-api.md) for request fields, response shapes, and errors.

### Transactions

- Create, view, edit, and delete income and expense transactions
- Search transactions by description
- Filter by type, category, amount, and date
- Choose an existing category or create a custom one (name and icon) while adding or editing
- Sort by amount, transaction date, or creation date
- Paginated transaction results
- Deterministic pagination: transactions with equal values in the selected sort field are ordered by transaction ID in the same direction.
- Preserve completed saves and show separate warnings when a follow-up list refresh fails
- Accessible validation errors with field associations and automatic focus management

### Categories

- 13 built-in categories with icons created for every new user; built-ins cannot be renamed or deleted
- Custom categories with a name and an approved icon, created from the transaction or budget form and
  saved in the same database transaction as the record
- New categories are immediately reusable in every form and filter
- Rename or re-icon a custom category everywhere it is used, without changing its ID or any totals
- Delete unused custom categories; categories still used by a transaction or budget are protected
- Names are compared after Unicode normalization and lowercasing, so equivalent duplicates are rejected
  per user (enforced by a database constraint, including under concurrency)
- Strict ownership: another user's category is indistinguishable from a missing one and can never be
  referenced by a transaction or budget, also enforced by composite database foreign keys
- Category icons beside the names on transactions, budgets, filters, and the dashboard; unknown icons fall
  back to a generic tag

**Categories page** (`/categories`, in the sidebar and mobile drawer):

- Summary strip: category counts, this month's top category, categories over budget, and
  categories spending without a budget
- This month's spending distribution as an accessible table with share bars
- "Active this month" first (spending or a budget this month), then a collapsible "Other
  categories" section that remembers whether it is open; each category appears once
- A card per category with its icon, built-in or custom badge, spending and share, budget
  progress and status, this month's transactions, and last-used date
- A warning with a Set budget link on categories spending this month without a budget
- Search (ignores case and surrounding spaces, Unicode-normalized), filters (Custom,
  Built-in, Unused, No budget this month), and sorting (name, this month's spending, most
  used); the summary always covers every category
- Create, rename, re-icon, and delete custom categories through each card's accessible
  "More actions" button; Delete is unavailable, with the reason, when a category is in use
- Shortcuts to the category's transactions (or to add its first one) and to set or edit its
  budget
- Status feedback: success confirmations, and persistent, labelled warnings and errors that
  never rely on colour alone; field errors stay beside their inputs

### Budgets

- Create, edit, and delete monthly category budgets
- Filter budgets by month, year, and category
- Choose an existing category or create a custom one while creating or editing a budget
- Prevent duplicate budgets for the same category and period
- Calculate spending, remaining balance, and utilization
- Display On Track, Caution, Warning, and Over Budget statuses
- Preserve completed saves and show separate warnings when a follow-up list or analytics refresh fails

### Dashboard

- Display all-time and current-month financial totals
- Calculate the current balance
- Show recent transactions with category icons
- Group current-month spending by category (grouped by category ID, so renames never split totals)
- Visualize budget progress and utilization with Recharts
- Support useful loading, error, and empty states

### Responsive Frontend

- Desktop sidebar navigation with icons and a clearly marked current page
- Collapsible icon-only sidebar mode with tooltips, saved per device
- Mobile and tablet navigation drawer (1100px and below) built on the native modal
  `<dialog>`, with focus handling, Escape and backdrop dismissal, and scroll locking
- Skip link, labelled navigation landmarks, and reduced-motion support
- Desktop, tablet, and mobile layouts
- Centralized, type-safe API communication
- Reusable currency and date formatting
- Direct route and browser-refresh support

---

## Security

- Five-minute signed JWT access tokens plus opaque, rotating refresh tokens
  (see the [authentication session lifecycle](docs/auth-session-lifecycle.md))
- Refresh and logout accept the cookie only with an exact `Origin` and a custom
  CSRF header; the cookie can never authenticate business APIs
  (see the [CSRF decision](docs/security-csrf.md))
- Password hashing with BCrypt
- Protected frontend routes and backend API endpoints
- Authenticated-user ownership enforcement for profiles, preferences, password changes, transactions, categories, budgets, and dashboard data
- Account mutation DTOs accept only their editable fields; ownership comes from the principal
- Password changes revoke every refresh session; an already-issued access token can
  still work for at most five minutes (there is no access-token denylist)
- Profile-photo uploads are content-inspected, size-limited, and re-encoded server-side;
  Cloudinary credentials stay on the backend and MySQL stores only an opaque key
  (see [Profile-photo security](docs/profile-photo-security.md))
- Cross-user resource isolation verified through automated tests
- Request validation and consistent API error handling
- Production CORS allowlist for approved Vercel origins
- Explicit support for browser preflight requests
- Secrets and database credentials supplied through environment variables
- Swagger/OpenAPI documentation disabled in production
- Hibernate SQL output disabled in production
- Stack traces and internal error details excluded from API responses

---

## Testing & Quality

| Test Suite        | Results                                                             |
| ----------------- | ------------------------------------------------------------------- |
| Backend           | **950 tests passing** (911 unit, 39 MySQL integration)              |
| Backend Coverage  | **98.86% instruction coverage, 95.69% branch coverage**             |
| Frontend          | **927 tests passing across 58 test files**                          |
| Frontend Coverage | **99.58% statements, 98.84% branches, 99.81% functions, 100% lines** |

For branch behavior, validation rules, stable automation selectors, test-data ownership, and Selenium assumptions, see the [FinTrack Application Testing Contract](docs/application-testing-contract.md).

For schema versioning, migration conventions, existing-database adoption, and backup expectations, see [Database Migrations](docs/database-migrations.md).

See the [frontend and staging testing guide](docs/frontend-testing.md) for commands and the issue #16 smoke checklist.

### Backend Testing

The backend test suite uses JUnit 5, Mockito, Spring Boot Test, MockMvc, Spring Security Test, H2, Flyway, Testcontainers (MySQL 8.4), and JaCoCo.

Coverage includes:

- Service-layer business logic
- Controller responses and request validation
- Repository queries and JPA Specifications
- JWT generation, validation, and authentication
- Spring Security configuration
- User ownership and cross-user data isolation
- Transaction search, filtering, sorting, and pagination
- Category normalization, built-in protection, icons, rename, and in-use deletion rules
- The category usage summary: ownership, month boundaries, expense-only spending, budget
  parity with the dashboard, and a fixed query count
- Atomic category creation with transactions and budgets (rollback verified on the database)
- Flyway V6 on a production-shaped MySQL schema, including preflight failures and recovery
- Budget calculations, analytics, and status behavior
- Dashboard aggregation
- Full authenticated application workflows
- Flyway clean-schema migrations, existing-schema adoption, migration history, and failure handling
- Refresh-token rotation, reuse revocation, logout, and password-change revoke-all
- Real MySQL races (login, refresh, logout, password change) in both lock orders
- Real MySQL category races (duplicate names, rename versus create, delete versus new reference)

Unit and most integration tests use a dedicated `test` profile with H2 in MySQL mode. `*IT` tests run against a real MySQL 8.4 container through Testcontainers during `verify`, so Docker must be running (locally, for example, Colima).

Run the backend suite:

```bash
./mvnw --batch-mode clean verify
```

The JaCoCo HTML report is generated at:

```text
target/site/jacoco/index.html
```

### Frontend Testing

The frontend test suite uses Vitest and React Testing Library.

Coverage includes:

- Authentication and protected routes
- Session restoration
- Dashboard loading, success, empty, and error states
- Transaction CRUD, filtering, sorting, and pagination
- Budget CRUD, analytics, charts, and business rules
- User-visible validation and API errors
- Memory-only token storage, refresh-and-retry-once, single-flight refresh, and
  cross-tab Web Locks/BroadcastChannel coordination
- The Vercel API proxy (header, cookie, multipart, and SSRF handling) and routing rules
- Profile, preference, and password forms; stale session responses and safe 401 handling
- Profile-photo preview, validation, upload/replace/remove states, avatar fallback, and
  header synchronization
- Preference-aware date rendering and all transaction request paths
- Category selection, custom-category creation, icon picker, category filters, server field
  messages beside their inputs, icon fallback, and stale-session category state
- The Categories page: loading, error, and empty states; create, edit, and delete workflows
  with focus management; the card actions disclosure; current-month activity; no-budget
  warnings; Active and Other sections with the saved preference; search, filters, sorting,
  and the spending table; deep links; and status notices
- Responsive navigation: public versus signed-in layouts, current-page state, sidebar collapse
  and saved preference, every mobile-drawer close path, focus return, and scroll-lock cleanup

Run the frontend suite:

```bash
cd frontend
npm run test:coverage
```

---

## Screenshots

### Dashboard

The dashboard summarizes income, expenses, recent activity, category spending, and monthly budget performance.

<img src="assets/screenshots/dashboard.png" alt="FinTrack dashboard showing financial summaries, recent transactions, spending by category, and monthly budgets" width="1000">

### Budget Analytics

Budget comparison and utilization charts show spending progress across budget categories.

<img src="assets/screenshots/budget-analytics.png" alt="FinTrack charts comparing monthly budgets with spending and showing budget utilization percentages" width="1000">

### Budget Management

Users can create monthly budgets, select reporting periods, review analytics, and monitor budget status.

<img src="assets/screenshots/budgets.png" alt="FinTrack budget management page showing analytics and budget status cards" width="1000">

### Transaction Management

Users can record, edit, delete, search, filter, sort, and paginate through income and expense transactions.

<img src="assets/screenshots/transactions.png" alt="FinTrack transaction management page showing entry, filtering, and transaction history features" width="1000">

---

## API Documentation

The health, registration, and login endpoints are public. All other endpoints require a valid JWT access token.

| Area           | Method   | Endpoint                      | Description                                     |
| -------------- | -------- | ----------------------------- | ----------------------------------------------- |
| Health         | `GET`    | `/api/health`                 | Check application health                        |
| Authentication | `POST`   | `/api/auth/register`          | Register a user                                 |
| Authentication | `POST`   | `/api/auth/login`             | Authenticate and receive a JWT                  |
| Authentication | `GET`    | `/api/auth/me`                | Get own canonical user; 200                     |
| Account        | `PUT`    | `/api/account/profile`        | Update own names; 200 canonical user            |
| Account        | `PUT`    | `/api/account/preferences`    | Update own preferences; 200 canonical user      |
| Account        | `POST`   | `/api/account/password`       | Change own password; 204 empty body             |
| Account        | `PUT`    | `/api/account/photo`          | Upload/replace own photo; 200 canonical user    |
| Account        | `DELETE` | `/api/account/photo`          | Remove own photo; 200 canonical user            |
| Transactions   | `POST`   | `/api/transactions`           | Create a transaction                            |
| Transactions   | `GET`    | `/api/transactions`           | Search, filter, sort, and paginate transactions |
| Transactions   | `GET`    | `/api/transactions/{id}`      | Get a transaction                               |
| Transactions   | `PUT`    | `/api/transactions/{id}`      | Update a transaction                            |
| Transactions   | `DELETE` | `/api/transactions/{id}`      | Delete a transaction                            |
| Categories     | `POST`   | `/api/categories`             | Create a custom category                        |
| Categories     | `GET`    | `/api/categories`             | Get all categories                              |
| Categories     | `GET`    | `/api/categories/{id}`        | Get a category                                  |
| Categories     | `PUT`    | `/api/categories/{id}`        | Rename or re-icon a custom category             |
| Categories     | `DELETE` | `/api/categories/{id}`        | Delete an unused custom category                |
| Categories     | `GET`    | `/api/categories/summary`     | Per-category usage and spending summary         |
| Budgets        | `POST`   | `/api/budgets`                | Create a monthly budget                         |
| Budgets        | `GET`    | `/api/budgets`                | Get budgets for a selected period               |
| Budgets        | `GET`    | `/api/budgets/{id}`           | Get a budget                                    |
| Budgets        | `GET`    | `/api/budgets/{id}/analytics` | Get budget analytics                            |
| Budgets        | `PUT`    | `/api/budgets/{id}`           | Update a budget                                 |
| Budgets        | `DELETE` | `/api/budgets/{id}`           | Delete a budget                                 |
| Dashboard      | `GET`    | `/api/dashboard`              | Get the financial dashboard summary             |

Swagger/OpenAPI documentation is available locally at:

```text
http://localhost:8080/swagger-ui/index.html
```

Swagger/OpenAPI is intentionally disabled in production.

Category rules, stable error codes (`CATEGORY_DUPLICATE`, `CATEGORY_NOT_FOUND`, `CATEGORY_BUILT_IN`,
`CATEGORY_IN_USE`), the transaction category filter (`GET /api/transactions?categoryId=`), and the
`categoryId` / `newCategory` contract for transaction and budget writes are documented in
[Categories API](docs/categories-api.md).

### Example Requests

#### Login

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "email": "demo@example.com",
  "password": "TestPassword123!"
}
```

#### Authenticated Request

```http
GET /api/dashboard
Authorization: Bearer <JWT>
```

---

## Project Structure

| Path                                                | Purpose                                                                  |
| --------------------------------------------------- | ------------------------------------------------------------------------ |
| `src/main/java/dev/portfolio/finance/config`        | Spring Security, CORS, and application configuration                     |
| `src/main/java/dev/portfolio/finance/controller`    | REST API controllers                                                     |
| `src/main/java/dev/portfolio/finance/dto`           | Validated request and response models                                    |
| `src/main/java/dev/portfolio/finance/entity`        | JPA entities and database relationships                                  |
| `src/main/java/dev/portfolio/finance/repository`    | Spring Data repositories and queries                                     |
| `src/main/java/dev/portfolio/finance/security`      | JWT authentication and request filtering                                 |
| `src/main/java/dev/portfolio/finance/service`       | Business logic and ownership enforcement                                 |
| `src/main/java/dev/portfolio/finance/specification` | Dynamic transaction filtering                                            |
| `src/main/resources`                                | Application and production configuration                                 |
| `src/main/resources/db/migration`                   | Versioned Flyway database migrations                                     |
| `src/test`                                          | Backend unit and integration tests                                       |
| `frontend/src`                                      | React components, pages, services, contexts, types, hooks, and utilities |
| `docs/sql`                                          | Documented MySQL scripts                                                 |
| `scripts`                                           | Local development scripts                                                |

---

## Deployment

| Component       | Platform |
| --------------- | -------- |
| React frontend  | Vercel   |
| Spring Boot API | Railway  |
| MySQL database  | Railway  |

- Vercel builds and deploys the React application from the `frontend` directory,
  including the `api/proxy.ts` function that forwards `/api/*` to Railway.
- Railway runs the Java 21 API with the `prod` Spring profile.
- The backend connects to MySQL through Railway private networking.
- Production configuration and secrets are supplied through environment variables.
- Vercel holds only the server-side `BACKEND_ORIGIN` (the backend's public origin) and
  no database or provider credentials.
- Profile photos are optional. To enable them, set `PROFILE_PHOTOS_ENABLED=true`,
  `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, and
  `PROFILE_PHOTO_KEY_PREFIX` (for example `fintrack/production/profile-photos`) in
  Railway only. Vercel needs no new variables. Use separate Cloudinary credentials
  for production and nonproduction.
- Direct React routes are supported through a Vercel SPA rewrite that never captures `/api`.
- See the [deployment guide](docs/deployment.md) for per-environment variables, the
  staging rollout, rollback, and the smoke checklist.

---

## Local Development

### Prerequisites

- Java 21
- MySQL
- Node.js and npm
- Git

### Clone the Repository

```bash
git clone https://github.com/garrib10/finance-operations-dashboard.git
cd finance-operations-dashboard
```

### Backend

Create the local environment file from the provided example and supply your own development values:

```bash
cp .env.example .env
```

Required backend variables:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `JWT_EXPIRATION_MS` (`300000`)

Refresh-session variables have safe defaults; set `REFRESH_COOKIE_SECURE=false` for
local HTTP. See `.env.example`.

Optional profile-photo variables (leave `PROFILE_PHOTOS_ENABLED=false` to run without
Cloudinary; uploads then return 503 and initials are shown):

- `PROFILE_PHOTOS_ENABLED`
- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`
- `PROFILE_PHOTO_KEY_PREFIX` (for example `fintrack/development/profile-photos`)

Start the Spring Boot API:

```bash
./scripts/run-local.sh
```

Flyway applies pending database migrations during application startup. V3 adds display names and persisted account preferences, V5 adds the refresh-session and token-history tables, and V6 (a Java migration) adds category normalization, built-in and icon metadata, and ownership constraints. For setup, adoption, and migration rules, see [Database Migrations](docs/database-migrations.md).

The backend runs at `http://localhost:8080`.

- Health endpoint: `http://localhost:8080/api/health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

### Frontend

From the project root:

```bash
cd frontend
npm install
```

No frontend environment file is needed: the Vite dev server proxies `/api` to the
backend at `http://localhost:8080`. `VITE_API_BASE_URL` is no longer used.

Start the Vite development server:

```bash
npm run dev
```

The frontend runs at `http://localhost:5173`.

---

## Future Improvements

- Code-split the frontend bundle by route
- Add production monitoring and structured application metrics

---

## License

This project is licensed under the [MIT License](LICENSE).
