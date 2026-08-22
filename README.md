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
- Custom CSS

### Testing

- JUnit 5
- Mockito
- Spring Boot Test

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
- User ownership enforcement for all transaction data

### Backend

- Spring Data JPA persistence
- Bean Validation
- Duplicate email prevention
- Global exception handling
- RESTful API architecture
- Swagger / OpenAPI documentation
- Health endpoint

---

## Current API Endpoints

| Method | Endpoint                 | Description                                          |
| ------ | ------------------------ | ---------------------------------------------------- |
| GET    | `/api/health`            | Application health check                             |
| POST   | `/api/auth/register`     | Register a new user                                  |
| POST   | `/api/auth/login`        | Authenticate user and return a JWT                   |
| GET    | `/api/auth/me`           | Return the currently authenticated user              |
| POST   | `/api/transactions`      | Create a new transaction                             |
| GET    | `/api/transactions`      | Retrieve all authenticated user's transactions       |
| GET    | `/api/transactions/{id}` | Retrieve a specific authenticated user's transaction |

---

## Project Structure

```text
src
├── config
├── controller
├── dto
│   ├── auth
│   ├── error
│   └── transaction
├── entity
├── exception
│   ├── auth
│   └── transaction
├── repository
├── security
├── service
└── FinanceOperationsDashboardApplication
```

---

## Prerequisites

Before running the project, make sure you have installed:

- Java 21
- Maven
- MySQL 9+
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

## Planned Features

- Categories
- Monthly Budgets
- Dashboard Analytics
- Search, Filtering & Sorting
- Responsive React Frontend
- Automated Backend Testing
- Production Deployment

---

## Project Progress

- ✅ Day 1 – Project Setup & Architecture
- ✅ Day 2 – MySQL & Database Foundation
- ✅ Day 3 – User Registration
- ✅ Day 4 – Authentication & JWT
- ✅ Day 5 – Transactions
- ⬜ Day 6 – Transaction CRUD
- ⬜ Day 7 – Categories
- ⬜ Day 8 – Search & Filtering
- ⬜ Day 9 – Budgets
- ⬜ Day 10 – Budget Business Logic
- ⬜ Day 11 – Dashboard API
- ⬜ Day 12 – Testing Foundation
- ⬜ Day 13 – Complete Backend Testing
- ⬜ Day 14 – React + TypeScript
- ⬜ Day 15 – Frontend Authentication
- ⬜ Day 16 – Dashboard UI
- ⬜ Day 17 – Transaction Management
- ⬜ Day 18 – Budget UI
- ⬜ Day 19 – Deployment
- ⬜ Day 20 – Documentation & Portfolio Polish

---

## Screenshots

Coming soon...

---

## License

This project is licensed under the MIT License.
