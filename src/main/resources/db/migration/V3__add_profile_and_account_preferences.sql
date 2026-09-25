ALTER TABLE users ADD COLUMN display_name VARCHAR(100);
ALTER TABLE users ADD COLUMN date_format VARCHAR(16) NOT NULL DEFAULT 'MEDIUM';
ALTER TABLE users ADD COLUMN transaction_page_size INT NOT NULL DEFAULT 10;

UPDATE users
SET display_name = LEFT(
    COALESCE(NULLIF(TRIM(CONCAT(TRIM(first_name), ' ', TRIM(last_name))), ''), 'Account'),
    100
);

ALTER TABLE users MODIFY COLUMN display_name VARCHAR(100) NOT NULL;
ALTER TABLE users ADD CONSTRAINT ck_users_date_format
    -- CASE avoids H2 retaining a closed migration session in an IN predicate.
    CHECK (CASE date_format WHEN 'MEDIUM' THEN TRUE WHEN 'ISO' THEN TRUE ELSE FALSE END);
ALTER TABLE users ADD CONSTRAINT ck_users_transaction_page_size
    CHECK (CASE transaction_page_size WHEN 10 THEN TRUE WHEN 25 THEN TRUE WHEN 50 THEN TRUE ELSE FALSE END);
