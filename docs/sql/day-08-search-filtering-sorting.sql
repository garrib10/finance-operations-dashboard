-- =====================================================
-- Day 8 - Search, Filtering & Sorting
-- =====================================================

USE finance_operations_dashboard;

-- View all users
SELECT id, first_name, last_name, email
FROM users;

-- View all transactions
SELECT *
FROM transactions;

-- View all categories
SELECT *
FROM categories;

-- Transactions by user
SELECT
    t.id,
    u.email,
    t.type,
    t.amount,
    t.description,
    t.transaction_date
FROM transactions t
JOIN users u
ON t.user_id = u.id
ORDER BY u.id, t.transaction_date DESC;