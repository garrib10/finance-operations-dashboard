/*
===============================================================================
 Day 6 - Transaction CRUD Verification
 Project: Finance Operations Dashboard

 Purpose:
 Verify Transaction CRUD functionality and ownership in MySQL after
 implementing Create, Read, Update, and Delete operations.

 Author: Brian Garrity
===============================================================================
*/

USE finance_operations_dashboard;

-- ============================================================================
-- View all transactions
-- ============================================================================

SELECT
    id,
    user_id,
    type,
    amount,
    description,
    transaction_date,
    created_at,
    updated_at
FROM transactions
ORDER BY id;

-- ============================================================================
-- Verify total number of transactions
-- ============================================================================

SELECT COUNT(*) AS total_transactions
FROM transactions;

-- ============================================================================
-- View all registered users
-- Useful for verifying transaction ownership
-- ============================================================================

SELECT
    id,
    first_name,
    last_name,
    email
FROM users
ORDER BY id;

-- ============================================================================
-- Verify transactions grouped by user
-- ============================================================================

SELECT
    u.email,
    COUNT(t.id) AS total_transactions
FROM users u
LEFT JOIN transactions t
    ON u.id = t.user_id
GROUP BY u.id, u.email
ORDER BY u.id;

-- ============================================================================
-- View all income transactions
-- ============================================================================

SELECT *
FROM transactions
WHERE type = 'INCOME';

-- ============================================================================
-- View all expense transactions
-- ============================================================================

SELECT *
FROM transactions
WHERE type = 'EXPENSE';

-- ============================================================================
-- Verify transaction amounts (highest to lowest)
-- ============================================================================

SELECT
    id,
    amount,
    description
FROM transactions
ORDER BY amount DESC;

-- ============================================================================
-- Verify transactions by date
-- ============================================================================

SELECT
    id,
    transaction_date,
    description,
    amount
FROM transactions
ORDER BY transaction_date DESC;

-- ============================================================================
-- End of Day 6 Verification
-- ============================================================================