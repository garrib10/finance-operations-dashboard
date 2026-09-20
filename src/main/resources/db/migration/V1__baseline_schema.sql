CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    budget_enabled BIT(1) NOT NULL,
    name VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_category_user_name UNIQUE (user_id, name),
    CONSTRAINT fk_categories_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    description VARCHAR(255) NOT NULL,
    transaction_date DATE NOT NULL,
    type ENUM('EXPENSE', 'INCOME') NOT NULL,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_transactions_user_id (user_id),
    INDEX idx_transactions_category_id (category_id),
    CONSTRAINT fk_transactions_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_transactions_category
        FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE budgets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    `month` INT NOT NULL,
    monthly_limit DECIMAL(12, 2) NOT NULL,
    `year` INT NOT NULL,
    category_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_budget_user_category_month_year
        UNIQUE (user_id, category_id, `month`, `year`),
    INDEX idx_budgets_category_id (category_id),
    CONSTRAINT fk_budgets_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_budgets_category
        FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;