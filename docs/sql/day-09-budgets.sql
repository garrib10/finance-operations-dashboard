/*
===============================================================================
 Day 9 - Monthly Budgets Verification
 Project: Finance Operations Dashboard

 Purpose:
 Verify budget persistence, ownership, category relationships,
 monthly uniqueness, and database constraints.
===============================================================================
*/

USE finance_operations_dashboard;

-- View all budgets
SELECT
    id,
    user_id,
    category_id,
    monthly_limit,
    month,
    year,
    created_at,
    updated_at
FROM budgets
ORDER BY user_id, year DESC, month DESC;

-- View budgets with user and category details
SELECT
    b.id,
    u.email,
    c.name AS category,
    b.monthly_limit,
    b.month,
    b.year
FROM budgets b
JOIN users u
    ON b.user_id = u.id
JOIN categories c
    ON b.category_id = c.id
ORDER BY u.email, b.year DESC, b.month DESC;

-- Verify structure and constraints
DESCRIBE budgets;

SHOW CREATE TABLE budgets;