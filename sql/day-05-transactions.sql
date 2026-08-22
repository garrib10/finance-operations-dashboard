/*
==========================================================
Day 5 - Transactions
Finance Operations Dashboard

Purpose:
Verify transaction persistence, ownership,
and database relationships.
==========================================================
*/

USE finance_operations_dashboard;

----------------------------------------------------------
-- Show all transactions
----------------------------------------------------------

SELECT
    id,
    user_id,
    type,
    amount,
    description,
    transaction_date,
    created_at
FROM transactions;

----------------------------------------------------------
-- Count transactions per user
----------------------------------------------------------

SELECT
    u.email,
    COUNT(t.id) AS total_transactions
FROM users u
LEFT JOIN transactions t
    ON u.id = t.user_id
GROUP BY
    u.id,
    u.email;

----------------------------------------------------------
-- Verify foreign key relationship
----------------------------------------------------------

SHOW CREATE TABLE transactions;