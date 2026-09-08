-- =====================================================
-- Day 10 - Budget Analytics Verification
-- =====================================================

-- View all budgets

SELECT *
FROM budgets;

-- View all categorized transactions

SELECT
    id,
    user_id,
    category_id,
    type,
    amount,
    transaction_date,
    description
FROM transactions
ORDER BY id;

-- Monthly spending by category (Jamie)

SELECT
    c.name AS category,
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
AND t.transaction_date BETWEEN '2026-08-01' AND '2026-08-31'
GROUP BY c.name;

-- Budget ownership verification

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
ORDER BY u.email;

-- Verify transaction-category relationships

SELECT
    t.id,
    u.email,
    c.name AS category,
    t.type,
    t.amount,
    t.transaction_date
FROM transactions t
JOIN users u
    ON t.user_id = u.id
JOIN categories c
    ON t.category_id = c.id
ORDER BY u.email, t.transaction_date;