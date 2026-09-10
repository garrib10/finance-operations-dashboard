# Finance Operations Dashboard

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-brightgreen?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?logo=mysql&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.9-C71A36?logo=apachemaven&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-Authentication-black?logo=jsonwebtokens)
![JUnit 5](https://img.shields.io/badge/JUnit-5-25A162?logo=junit5&logoColor=white)
![Mockito](https://img.shields.io/badge/Mockito-Testing-78A641)
![Swagger](https://img.shields.io/badge/Swagger-OpenAPI-85EA2D?logo=swagger&logoColor=black)

A production-quality full-stack personal finance management application built with **Java, Spring Boot, React, TypeScript, and MySQL**.

The application includes secure JWT authentication, transaction and budget management, search and filtering, financial analytics, and an aggregated dashboard that combines transaction totals, monthly spending, recent activity, category breakdowns, and budget analytics.

> **Note:** This project uses fictional/demo financial data only. It does **not** connect to real banks, process real financial transactions, or provide financial advice.

---

## Tech Stack

### Backend

- Java 21
- Spring Boot
- Spring Web MVC
- Spring Data JPA
- Hibernate
- Bean Validation
- BCrypt Password Hashing
- Spring Security
- JWT Authentication
- Maven

### Database

- MySQL

### Frontend

- React
- TypeScript
- Vite
- Recharts

### Testing

**Backend**

- JUnit 5
- Mockito
- Spring Boot Test
- MockMvc
- Spring Security Test
- H2 in-memory test database
- JaCoCo

**Frontend**

- Vitest
- React Testing Library
- jest-dom
- jsdom

### Deployment

- Railway (Backend + MySQL)
- Vercel (Frontend)

---

## Current Features

### Authentication

- User registration API
- User login
- JWT-based authentication
- Stateless Spring Security configuration
- Protected API endpoints
- Secure password hashing with BCrypt
- Custom authentication error responses

### Transactions

- Create income and expense transactions
- Retrieve all transactions for the authenticated user
- Retrieve an individual transaction
- Update existing transactions
- Delete transactions
- Search transactions by description
- Filter transactions by type
- Filter transactions by amount range
- Filter transactions by date range
- Sort transactions by amount, transaction date, or creation date
- Paginated transaction responses
- JWT-protected transaction endpoints
- User ownership enforcement for all transaction data
- Transaction filter validation

### Categories

- Create custom categories
- Retrieve all categories for the authenticated user
- Retrieve an individual category
- Update existing categories
- Delete categories
- Automatic default category creation for newly registered users
- Duplicate category name prevention (per user)
- JWT-protected category endpoints
- User ownership enforcement for all category data

### Budgets

- Create monthly budgets by category
- Retrieve all budgets for the authenticated user
- Retrieve an individual budget
- Update existing budgets
- Delete budgets
- Prevent duplicate budgets for the same category and month
- Validate monthly limits, month, and year
- JWT-protected budget endpoints
- User and category ownership enforcement
- Budget analytics
- Spending calculations by category
- Remaining budget calculation
- Budget utilization percentage
- Budget status tracking (On Track, Caution, Warning, Over Budget)

### Backend

- Spring Data JPA persistence
- Bean Validation
- Duplicate email prevention
- Global exception handling
- RESTful API architecture
- Swagger / OpenAPI documentation
- Health endpoint

### Dashboard

- Authenticated dashboard summary endpoint
- All-time income and expense totals
- Calculated current balance
- Current-month income and expense totals
- Five most recent transactions
- Current-month spending grouped by category
- Current-month budget summaries with analytics and status
- User-scoped dashboard data based on JWT authentication
- Graceful empty-state responses for users with no financial activity

### Frontend

- React 19 + TypeScript frontend built with Vite
- Organized component, page, service, type, context, and utility architecture
- Type-safe API integration with the Spring Boot backend
- JWT authentication with login, registration, session restoration, protected routes, and logout
- Authenticated navigation for Dashboard, Transactions, and Budgets
- Centralized API request handling with automatic Bearer token authentication
- Responsive UI built with reusable custom CSS and design variables
- Financial dashboard with summary cards, recent transactions, category spending, and monthly budget analytics
- Transaction management with create, edit, delete, and category integration
- Backend-powered transaction search, filtering, sorting, and pagination
- Budget management with create, edit, delete, and category integration
- Month and year budget filtering with period-specific empty states
- Budget analytics with spending, remaining balance, utilization percentage, and status tracking
- Budget vs. Spending and Budget Utilization visualizations built with Recharts
- Reusable currency and date formatting utilities
- Loading, empty-state, validation, business-rule, and API error handling
- Responsive layouts for desktop, tablet, and mobile
- Automated frontend testing with Vitest and React Testing Library

---

## Current API Endpoints

| Method | Endpoint                      | Description                                          |
| ------ | ----------------------------- | ---------------------------------------------------- |
| GET    | `/api/health`                 | Application health check                             |
|        |                               |                                                      |
|        | **Authentication**            |                                                      |
| POST   | `/api/auth/register`          | Register a new user                                  |
| POST   | `/api/auth/login`             | Authenticate user and return a JWT                   |
| GET    | `/api/auth/me`                | Return the currently authenticated user              |
|        |                               |                                                      |
|        | **Transactions**              |                                                      |
| POST   | `/api/transactions`           | Create a new transaction                             |
| GET    | `/api/transactions`           | Retrieve all authenticated user's transactions       |
| GET    | `/api/transactions/{id}`      | Retrieve a specific authenticated user's transaction |
| PUT    | `/api/transactions/{id}`      | Update a specific authenticated user's transaction   |
| DELETE | `/api/transactions/{id}`      | Delete a specific authenticated user's transaction   |
|        |                               |                                                      |
|        | **Categories**                |                                                      |
| POST   | `/api/categories`             | Create a new category                                |
| GET    | `/api/categories`             | Retrieve all authenticated user's categories         |
| GET    | `/api/categories/{id}`        | Retrieve a specific authenticated user's category    |
| PUT    | `/api/categories/{id}`        | Update a specific authenticated user's category      |
| DELETE | `/api/categories/{id}`        | Delete a specific authenticated user's category      |
|        |                               |                                                      |
|        | **Budgets**                   |                                                      |
| POST   | `/api/budgets`                | Create a monthly budget                              |
| GET    | `/api/budgets`                | Retrieve all authenticated user's budgets            |
| GET    | `/api/budgets/{id}`           | Retrieve a specific authenticated user's budget      |
| GET    | `/api/budgets/{id}/analytics` | Retrieve spending analytics for a specific budget    |
| PUT    | `/api/budgets/{id}`           | Update a specific authenticated user's budget        |
| DELETE | `/api/budgets/{id}`           | Delete a specific authenticated user's budget        |

### Dashboard

| Method | Endpoint         | Description                                                  |
| ------ | ---------------- | ------------------------------------------------------------ |
| GET    | `/api/dashboard` | Returns the authenticated user's financial dashboard summary |

---

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

---

## Prerequisites

Before running the project, make sure you have installed:

- Java 21
- Maven
- MySQL 9+
- Node.js
- npm
- Git

---

## Running the Application

Clone the repository:

```bash
git clone https://github.com/garrib10/finance-operations-dashboard.git
cd finance-operations-dashboard
```

Configure your environment variables by creating a `.env` file:

```env
DB_URL=jdbc:mysql://localhost:3306/finance_operations_dashboard
DB_USERNAME=finance_user
DB_PASSWORD=your_password
```

Run the application:

```bash
./scripts/run-local.sh
```

Or manually:

```bash
mvn spring-boot:run
```

Application:

```
http://localhost:8080
```

Swagger UI:

```
http://localhost:8080/swagger-ui/index.html
```

> **Authentication:** Use `POST /api/auth/login` to obtain a JWT, then click the **Authorize** button in Swagger and paste the token to test protected endpoints.

Health Check:

```
http://localhost:8080/api/health
```

---

### Run the Frontend

From the project root:

```bash
cd frontend
npm install
npm run dev
```

The Vite development server runs at:

```text
http://localhost:5173
```

The frontend uses the following environment variable:

```env
VITE_API_BASE_URL=http://localhost:8080
```

The local Spring Boot CORS configuration allows requests from the Vite development server at `http://localhost:5173`.

### Build the Frontend

```bash
cd frontend
npm run build
```

Vite generates the production build in:

```text
frontend/dist/
```

## Frontend Testing

The React + TypeScript frontend is tested with Vitest and React Testing Library.

Current frontend test coverage includes:

- Authentication and protected-route behavior
- Dashboard loading, success, empty, and error states
- Budget status rendering for On Track, Caution, Warning, and Over Budget
- Transaction loading, CRUD interactions, filtering, pagination, and error handling
- Budget creation, editing, deletion, filtering, analytics, chart rendering, duplicate-budget errors, and empty states
- Local storage token utilities

**Current frontend suite: 37 tests across 7 test files.**

Frontend tests focus on authentication, protected routing, dashboard data rendering, transaction and budget management, filtering, pagination, analytics, business-rule handling, empty states, and user-visible error handling.

To run the frontend test suite:

```bash
cd frontend
npm test -- --run
```

## Automated Testing

The backend includes a comprehensive automated test suite built with **JUnit 5, Mockito, Spring Boot Test, MockMvc, Spring Security Test, H2, and JaCoCo**.

### Test Results

- **152 automated backend tests**
- **98% instruction coverage**
- **94% branch coverage**

Test coverage is measured with **JaCoCo**.

### Testing Strategy

The test suite focuses on meaningful application behavior and business-critical functionality rather than targeting 100% code coverage for its own sake.

Testing includes:

- Service-layer unit testing with Mockito
- Controller and request-validation testing with MockMvc
- Repository and custom JPQL query testing
- Transaction search, filtering, sorting, and pagination testing
- JPA Specification testing
- JWT generation, validation, and authentication testing
- Spring Security configuration testing
- User ownership and cross-user resource isolation testing
- Category initialization and duplicate-prevention testing
- Budget CRUD, analytics, calculations, and status testing
- Dashboard aggregation and financial summary testing
- Full backend integration workflows

Integration tests verify complete application flows including user registration, login, JWT-protected requests, transaction creation, dashboard calculations, and cross-user resource isolation.

Automated tests use a dedicated `test` Spring profile and an **H2 in-memory database configured for MySQL compatibility**, allowing the test suite to run independently from the local MySQL development database.

### Run All Tests

```bash
./mvnw clean test
```

### View the Coverage Report

After running the test suite, JaCoCo generates an HTML coverage report in:

```text
target/site/jacoco/
```

To serve the report locally:

```bash
python3 -m http.server 8000 --directory target/site/jacoco
```

Then open:

```text
http://localhost:8000
```

---

## Planned Features

- Production deployment
- Accessibility and Lighthouse review
- Final responsive UI polish

---

## Project Progress

- ✅ Day 1 – Project Setup & Architecture
- ✅ Day 2 – MySQL & Database Foundation
- ✅ Day 3 – User Registration
- ✅ Day 4 – Authentication & JWT
- ✅ Day 5 – Transactions
- ✅ Day 6 – Transaction CRUD
- ✅ Day 7 – Categories
- ✅ Day 8 – Search, Filtering & Sorting
- ✅ Day 9 – Budgets
- ✅ Day 10 – Budget Business Logic & Analytics
- ✅ Day 11 – Dashboard API
- ✅ Day 12 – Testing Foundation
- ✅ Day 13 – Complete Backend Testing
- ✅ Day 14 – React + TypeScript Foundation
- ✅ Day 15 – Frontend Authentication & Testing
- ✅ Day 16 – Dashboard UI
- ✅ Day 17 – Transaction Management
- ✅ Day 18 – Budget UI
- ⬜ Day 19 – Deployment
- ⬜ Day 20 – Documentation & Portfolio Polish

---

## Screenshots

Coming soon...

---

## License

This project is licensed under the MIT License.
