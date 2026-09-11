# Finance Operations Dashboard

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-brightgreen?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6.x-3178C6?logo=typescript&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?logo=mysql&logoColor=white)
![JUnit 5](https://img.shields.io/badge/JUnit-5-25A162?logo=junit5&logoColor=white)
![Vitest](https://img.shields.io/badge/Vitest-4.1-6E9F18?logo=vitest&logoColor=white)
![Railway](https://img.shields.io/badge/Railway-0B0D0E?logo=railway&logoColor=white)
![Vercel](https://img.shields.io/badge/Vercel-black?logo=vercel)

A production-deployed, full-stack personal finance application built with **Java, Spring Boot, React, TypeScript, and MySQL**. FinTrack provides secure account access, transaction and budget management, search and filtering, financial analytics, and an aggregated dashboard.

> **Demo project:** This application uses fictional financial data only. It does not connect to banks, process real financial transactions, or provide financial advice.

---

## Live Demo

| Resource               | URL                                                                                                                                            |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Production Application | [finance-operations-dashboard.vercel.app](https://finance-operations-dashboard.vercel.app/)                                                    |
| Production API         | [finance-operations-dashboard-production.up.railway.app](https://finance-operations-dashboard-production.up.railway.app/)                      |
| Health Endpoint        | [finance-operations-dashboard-production.up.railway.app/api/health](https://finance-operations-dashboard-production.up.railway.app/api/health) |

Swagger/OpenAPI documentation is available during local development and intentionally disabled in production.

---

## Highlights

- Full-stack React and Spring Boot application deployed across Vercel and Railway
- Stateless JWT authentication with BCrypt password hashing
- User-scoped transactions, categories, budgets, and dashboard data
- Transaction search, filtering, sorting, and pagination
- Budget analytics with spending, remaining balance, utilization, and status tracking
- Responsive dashboard visualizations built with Recharts
- **152 passing backend tests** with **98% instruction coverage** and **94% branch coverage**
- **37 passing frontend tests** across **7 test files**

---

## Tech Stack

| Area             | Technologies                                                                                                  |
| ---------------- | ------------------------------------------------------------------------------------------------------------- |
| Backend          | Java 21, Spring Boot 4.1, Spring Web MVC, Spring Security, Spring Data JPA, Hibernate, Bean Validation, Maven |
| Frontend         | React 19, TypeScript 6, Vite, React Router, Recharts, custom CSS                                              |
| Database         | MySQL                                                                                                         |
| Authentication   | JWT, BCrypt                                                                                                   |
| Backend testing  | JUnit 5, Mockito, Spring Boot Test, MockMvc, Spring Security Test, H2, JaCoCo                                 |
| Frontend testing | Vitest, React Testing Library, jest-dom, jsdom                                                                |
| Deployment       | Vercel, Railway                                                                                               |

---

## Architecture

```text
Browser
  |
  v
React + TypeScript frontend (Vercel)
  |
  | HTTPS / JSON / JWT
  v
Spring Boot REST API (Railway)
  |
  | Railway private networking
  v
MySQL database (Railway)
```

The frontend communicates with the backend through `VITE_API_BASE_URL`. The backend owns authentication, authorization, validation, business logic, and persistence. MySQL credentials and backend secrets are stored only in Railway; they are never exposed to Vercel or browser code.

---

## Key Features

### Authentication and Security

- User registration, login, logout, and session restoration
- Stateless JWT authentication and protected API endpoints
- BCrypt password hashing
- User ownership enforcement and cross-user resource isolation
- Custom authentication and global API error responses
- Environment-based secrets and configuration
- Explicit production CORS allowlist with browser preflight support
- Swagger/OpenAPI and Hibernate SQL logging disabled under the production profile
- Stack traces and internal error details excluded from API responses

### Transactions

- Create, view, update, and delete income and expense transactions
- Search by description
- Filter by type, amount range, and date range
- Sort by amount, transaction date, or creation date
- Paginated responses
- Validation and authenticated-user ownership enforcement

### Categories

- Create, view, update, and delete custom categories
- Automatic default categories for newly registered users
- Per-user duplicate-name prevention
- Authenticated-user ownership enforcement

### Budgets

- Create, view, update, and delete monthly category budgets
- Prevent duplicate budgets for the same category and month
- Month and year filtering
- Spending and remaining-budget calculations
- Utilization percentage and status tracking: On Track, Caution, Warning, and Over Budget
- Budget vs. Spending and Budget Utilization charts

### Dashboard

- All-time and current-month income and expense totals
- Calculated current balance
- Five most recent transactions
- Current-month spending grouped by category
- Current-month budget analytics and status
- Empty states for users without financial activity

### Frontend Experience

- Protected routes and authenticated navigation
- Centralized, type-safe API communication with automatic bearer-token authentication
- Loading, validation, empty, business-rule, and API error states
- Reusable currency and date formatting
- Responsive desktop, tablet, and mobile layouts
- Direct route and refresh support through a Vercel SPA rewrite

## API Overview

All endpoints except the health check and authentication endpoints require a valid JWT.

| Area           | Method   | Endpoint                      | Description                                         |
| -------------- | -------- | ----------------------------- | --------------------------------------------------- |
| Health         | `GET`    | `/api/health`                 | Check application health                            |
| Authentication | `POST`   | `/api/auth/register`          | Register a user                                     |
| Authentication | `POST`   | `/api/auth/login`             | Authenticate and receive a JWT                      |
| Authentication | `GET`    | `/api/auth/me`                | Get the authenticated user                          |
| Transactions   | `POST`   | `/api/transactions`           | Create a transaction                                |
| Transactions   | `GET`    | `/api/transactions`           | Search, filter, sort, and page through transactions |
| Transactions   | `GET`    | `/api/transactions/{id}`      | Get a transaction                                   |
| Transactions   | `PUT`    | `/api/transactions/{id}`      | Update a transaction                                |
| Transactions   | `DELETE` | `/api/transactions/{id}`      | Delete a transaction                                |
| Categories     | `POST`   | `/api/categories`             | Create a category                                   |
| Categories     | `GET`    | `/api/categories`             | Get all categories                                  |
| Categories     | `GET`    | `/api/categories/{id}`        | Get a category                                      |
| Categories     | `PUT`    | `/api/categories/{id}`        | Update a category                                   |
| Categories     | `DELETE` | `/api/categories/{id}`        | Delete a category                                   |
| Budgets        | `POST`   | `/api/budgets`                | Create a monthly budget                             |
| Budgets        | `GET`    | `/api/budgets`                | Get budgets for a selected period                   |
| Budgets        | `GET`    | `/api/budgets/{id}`           | Get a budget                                        |
| Budgets        | `GET`    | `/api/budgets/{id}/analytics` | Get budget analytics                                |
| Budgets        | `PUT`    | `/api/budgets/{id}`           | Update a budget                                     |
| Budgets        | `DELETE` | `/api/budgets/{id}`           | Delete a budget                                     |
| Dashboard      | `GET`    | `/api/dashboard`              | Get the financial dashboard summary                 |

---

## Example Requests

### Register

```http
POST /api/auth/register
Content-Type: application/json
```

```json
{
  "firstName": "Demo",
  "lastName": "User",
  "email": "demo@example.com",
  "password": "TestPassword123!"
}
```

### Login

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

### Authenticated Request

```http
GET /api/dashboard
Authorization: Bearer <JWT>
```

## Project Structure

```text
finance-operations-dashboard/
├── src/
│   ├── main/
│   │   └── java/dev/portfolio/finance/
│   │       ├── config/
│   │       ├── controller/
│   │       ├── dto/
│   │       │   ├── auth/
│   │       │   ├── budget/
│   │       │   ├── category/
│   │       │   ├── dashboard/
│   │       │   ├── error/
│   │       │   └── transaction/
│   │       ├── entity/
│   │       ├── exception/
│   │       ├── repository/
│   │       ├── security/
│   │       ├── service/
│   │       └── specification/
│   └── test/
├── frontend/
│   ├── public/
│   └── src/
│       ├── assets/
│       ├── components/
│       ├── context/
│       ├── hooks/
│       ├── pages/
│       ├── services/
│       ├── types/
│       ├── utils/
│       ├── App.css
│       ├── App.tsx
│       ├── index.css
│       └── main.tsx
├── docs/
│   └── sql/
├── scripts/
├── pom.xml
└── README.md
```

## Local Development

### Prerequisites

- Java 21
- Maven, or the included Maven wrapper
- MySQL 9+
- Node.js and npm
- Git

### 1. Clone the Repository

```bash
git clone https://github.com/garrib10/finance-operations-dashboard.git
cd finance-operations-dashboard
```

### 2. Configure and Run the Backend

Create `.env` from the tracked example:

```bash
cp .env.example .env
```

Configure the following local values without committing the completed `.env` file:

```env
DB_URL=jdbc:mysql://localhost:3306/finance_operations_dashboard
DB_USERNAME=finance_user
DB_PASSWORD=your_local_database_password
JWT_SECRET=your_base64_encoded_jwt_secret
JWT_EXPIRATION_MS=3600000
```

Start the backend:

```bash
./scripts/run-local.sh
```

The backend runs at `http://localhost:8080`.

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Health check: `http://localhost:8080/api/health`

To test protected endpoints in Swagger, log in through `POST /api/auth/login`, select **Authorize**, and supply the returned JWT.

### 3. Configure and Run the Frontend

```bash
cd frontend
npm install
```

Create `frontend/.env.local`:

```env
VITE_API_BASE_URL=http://localhost:8080
```

Start the Vite development server:

```bash
npm run dev
```

The frontend runs at `http://localhost:5173`. The default backend CORS configuration allows this local origin.

### Production Build

```bash
cd frontend
npm run build
```

Vite writes the production bundle to `frontend/dist/`.

---

## Testing

### Test Results

| Suite    | Result                          | Coverage                                            |
| -------- | ------------------------------- | --------------------------------------------------- |
| Backend  | 152 tests passing               | 98% instruction, 94% branch                         |
| Frontend | 37 tests passing across 7 files | Behavior-focused component and integration coverage |

### Backend Testing

The backend suite uses JUnit 5, Mockito, Spring Boot Test, MockMvc, Spring Security Test, H2, and JaCoCo. It covers:

- Service, controller, validation, repository, and JPA Specification behavior
- Transaction CRUD, search, filtering, sorting, and pagination
- JWT generation, validation, authentication, and Spring Security configuration
- User ownership and cross-user resource isolation
- Category defaults and duplicate prevention
- Budget CRUD, calculations, analytics, and status behavior
- Dashboard aggregation and full authenticated workflows

Tests use a dedicated `test` profile and an H2 in-memory database in MySQL compatibility mode.

Run the suite:

```bash
./mvnw clean test
```

The JaCoCo HTML report is generated at `target/site/jacoco/index.html`.

### Frontend Testing

The frontend suite uses Vitest and React Testing Library. It covers authentication, protected routes, dashboard states, transaction and budget workflows, filtering, pagination, analytics, business rules, empty states, errors, and local-storage token utilities.

Run the suite:

```bash
cd frontend
npm test -- --run
```

---

## Production Deployment

### Vercel Frontend

- Deploys the React/Vite application from the `frontend` directory
- Uses `VITE_API_BASE_URL` to reach the Railway API
- Uses `frontend/vercel.json` to route direct requests and refreshes to the React SPA
- Hosts the production frontend at [finance-operations-dashboard.vercel.app](https://finance-operations-dashboard.vercel.app/)

### Railway Backend and Database

- Runs the Java 21 Spring Boot API with `SPRING_PROFILES_ACTIVE=prod`
- Connects to MySQL through Railway environment variables and private networking
- Uses Railway's `PORT` variable at runtime
- Hosts the API at [finance-operations-dashboard-production.up.railway.app](https://finance-operations-dashboard-production.up.railway.app/)

---

## Branch and Deployment Workflow

| Branch    | Purpose                                                 | Deployment                               |
| --------- | ------------------------------------------------------- | ---------------------------------------- |
| `staging` | Persistent integration and pre-production review branch | Vercel preview deployment                |
| `main`    | Production-ready code                                   | Vercel and Railway production deployment |

Changes are completed and verified on `staging`, submitted through a pull request to `main`, reviewed, and then merged for production deployment.

---

## Screenshots and Demo

Screenshots will be added during Day 20 portfolio polish.

## License

This project is licensed under the MIT License.
