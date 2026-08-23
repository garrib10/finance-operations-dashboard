/*
===============================================================================
 Day 7 - Categories Verification
 Project: Finance Operations Dashboard

 Purpose:
 Verify category persistence, ownership, default category initialization,
 budgeting support, uniqueness constraints, and the user-category relationship.
===============================================================================
*/

USE finance_operations_dashboard;

-- ============================================================================
-- View all categories
-- ============================================================================

SELECT
    id,
    user_id,
    name,
    budget_enabled,
    created_at,
    updated_at
FROM categories
ORDER BY user_id, name;

-- ============================================================================
-- Count categories per user
-- ============================================================================

SELECT
    u.id AS user_id,
    u.email,
    COUNT(c.id) AS total_categories
FROM users u
LEFT JOIN categories c
    ON u.id = c.user_id
GROUP BY
    u.id,
    u.email
ORDER BY u.id;

-- ============================================================================
-- View categories that support monthly budgeting
-- ============================================================================

SELECT
    user_id,
    name,
    budget_enabled
FROM categories
WHERE budget_enabled = 1
ORDER BY user_id, name;

-- ============================================================================
-- View categories that do not support monthly budgeting
-- ============================================================================

SELECT
    user_id,
    name,
    budget_enabled
FROM categories
WHERE budget_enabled = 0
ORDER BY user_id, name;

-- ============================================================================
-- Verify table structure
-- ============================================================================

DESCRIBE categories;

-- ============================================================================
-- Verify unique constraint and foreign key
-- ============================================================================

SHOW CREATE TABLE categories;

-- ============================================================================
-- End of Day 7 Verification
-- ============================================================================