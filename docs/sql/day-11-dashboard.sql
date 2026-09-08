-- ============================================================
-- Day 11 - Dashboard API
-- Finance Operations Dashboard / FinTrack
--
-- Purpose:
-- Documents the SQL queries used to verify the dashboard
-- aggregation behavior implemented in the Spring Boot API.
--
-- The dashboard summarizes:
--   - Total income
--   - Total expenses
--   - Current balance
--   - Current-month income
--   - Current-month expenses
--   - Spending grouped by category
--   - Recent transactions
--
-- These queries are intended for development and verification.
-- ============================================================


-- ------------------------------------------------------------
-- 1. All-time income and expense totals
-- ------------------------------------------------------------
-- Used to verify the values returned as:
--   totalIncome
--   totalExpenses
--
-- The Spring Boot application performs equivalent aggregate
-- queries through TransactionRepository.

SELECT
    t.type,
    SUM(t.amount) AS total_amount
FROM transactions t
WHERE t.user_id = (
    SELECT id
    FROM users
    WHERE email = 'jamie@example.com'
)
GROUP BY t.type;


-- ------------------------------------------------------------
-- 2. Current balance
-- ------------------------------------------------------------
-- The application calculates:
--
-- currentBalance = totalIncome - totalExpenses
--
-- This SQL query performs the same calculation directly
-- in MySQL for verification.

SELECT
    COALESCE(SUM(
        CASE
            WHEN t.type = 'INCOME' THEN t.amount
            ELSE 0
        END
    ), 0)
    -
    COALESCE(SUM(
        CASE
            WHEN t.type = 'EXPENSE' THEN t.amount
            ELSE 0
        END
    ), 0) AS current_balance
FROM transactions t
WHERE t.user_id = (
    SELECT id
    FROM users
    WHERE email = 'jamie@example.com'
);


-- ------------------------------------------------------------
-- 3. Current-month income and expenses
-- ------------------------------------------------------------
-- Dashboard monthly values are based on the current calendar
-- month.
--
-- During Day 11 testing, September 2026 was used.

SELECT
    t.type,
    SUM(t.amount) AS monthly_total
FROM transactions t
WHERE t.user_id = (
    SELECT id
    FROM users
    WHERE email = 'jamie@example.com'
)
AND t.transaction_date BETWEEN '2026-09-01' AND '2026-09-30'
GROUP BY t.type;


-- ------------------------------------------------------------
-- 4. Monthly expense totals grouped by category
-- ------------------------------------------------------------
-- Used to verify the dashboard categorySpending collection.
--
-- Only EXPENSE transactions are included because category
-- spending should not include income.

SELECT
    c.id AS category_id,
    c.name AS category_name,
    SUM(t.amount) AS amount_spent
FROM transactions t
JOIN categories c
    ON t.category_id = c.id
WHERE t.user_id = (
    SELECT id
    FROM users
    WHERE email = 'jamie@example.com'
)
AND t.type = 'EXPENSE'
AND t.transaction_date BETWEEN '2026-09-01' AND '2026-09-30'
GROUP BY
    c.id,
    c.name
ORDER BY amount_spent DESC;


-- ------------------------------------------------------------
-- 5. Five most recent transactions
-- ------------------------------------------------------------
-- Used to verify the dashboard recentTransactions collection.
--
-- transaction_date is the primary sort field.
-- created_at is used as a secondary sort field when multiple
-- transactions occur on the same date.

SELECT
    t.id,
    c.id AS category_id,
    c.name AS category_name,
    t.type,
    t.amount,
    t.description,
    t.transaction_date,
    t.created_at
FROM transactions t
JOIN categories c
    ON t.category_id = c.id
WHERE t.user_id = (
    SELECT id
    FROM users
    WHERE email = 'jamie@example.com'
)
ORDER BY
    t.transaction_date DESC,
    t.created_at DESC
LIMIT 5;


-- ------------------------------------------------------------
-- 6. Current-month budgets
-- ------------------------------------------------------------
-- The dashboard only displays budget summaries that belong
-- to the authenticated user and match the current month/year.
--
-- In Day 11 testing, Jamie did not have a September 2026
-- budget, so the API correctly returned:
--
-- "budgetSummaries": []

SELECT
    b.id,
    c.name AS category_name,
    b.monthly_limit,
    b.month,
    b.year
FROM budgets b
JOIN categories c
    ON b.category_id = c.id
WHERE b.user_id = (
    SELECT id
    FROM users
    WHERE email = 'jamie@example.com'
)
AND b.month = 9
AND b.year = 2026
ORDER BY c.name;