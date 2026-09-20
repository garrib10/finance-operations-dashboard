CREATE INDEX idx_transactions_user_date
    ON transactions (user_id, transaction_date);