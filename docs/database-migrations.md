# Database Migrations

FinTrack uses Flyway to manage MySQL schema changes consistently across
local, staging, and production environments.

## Current Schema Baseline

The initial Flyway baseline represents the existing production schema as
reviewed on September 20, 2026.

### users

- `id` — `BIGINT`, primary key, auto-increment
- `created_at` — `DATETIME(6)`, required
- `updated_at` — `DATETIME(6)`, required
- `email` — `VARCHAR(255)`, required and unique
- `first_name` — `VARCHAR(100)`, required
- `last_name` — `VARCHAR(100)`, required
- `password_hash` — `VARCHAR(255)`, required

### categories

- `id` — `BIGINT`, primary key, auto-increment
- `created_at` — `DATETIME(6)`, required
- `updated_at` — `DATETIME(6)`, required
- `budget_enabled` — `BIT(1)`, required
- `name` — `VARCHAR(100)`, required
- `user_id` — `BIGINT`, required
- Unique constraint on `user_id` and `name`
- Foreign key from `user_id` to `users.id`

### transactions

- `id` — `BIGINT`, primary key, auto-increment
- `created_at` — `DATETIME(6)`, required
- `updated_at` — `DATETIME(6)`, required
- `amount` — `DECIMAL(12,2)`, required
- `description` — `VARCHAR(255)`, required
- `transaction_date` — `DATE`, required
- `type` — `ENUM('EXPENSE','INCOME')`, required
- `user_id` — `BIGINT`, required
- `category_id` — `BIGINT`, required
- Foreign keys to `users.id` and `categories.id`

### budgets

- `id` — `BIGINT`, primary key, auto-increment
- `created_at` — `DATETIME(6)`, required
- `updated_at` — `DATETIME(6)`, required
- `month` — `INT`, required
- `monthly_limit` — `DECIMAL(12,2)`, required
- `year` — `INT`, required
- `category_id` — `BIGINT`, required
- `user_id` — `BIGINT`, required
- Unique constraint on `user_id`, `category_id`, `month`, and `year`
- Foreign keys to `users.id` and `categories.id`

All application tables use the InnoDB engine, the `utf8mb4` character set,
and the `utf8mb4_unicode_ci` collation.

## Migration Naming

Versioned migrations are stored in:

`src/main/resources/db/migration`

Use this format:

`V<number>__short_description.sql`

Examples:

- `V1__baseline_schema.sql`
- `V2__add_transaction_lookup_index.sql`

Migration versions must be unique and applied in ascending order.

## Migration Rules

- Never edit or rename a migration after it has been applied.
- Create a new migration for every subsequent schema change.
- Test migrations against both a clean database and an existing schema.
- Review destructive operations carefully before deployment.
- Verify migrations in staging before production.
- Hibernate validates the schema but does not create or update it.

## Clean Database Setup

For an empty database, Flyway executes the baseline migration followed by
all later migrations. Flyway records each successful migration in its
`flyway_schema_history` table.

## Existing Database Adoption

The existing staging and production databases already contain the FinTrack
tables. They must be baselined at version 1 so Flyway recognizes the
existing schema without executing the V1 table-creation statements.

`baseline-on-migrate` is disabled by default. It should be enabled only for
the controlled first adoption of an existing database and disabled again
after the Flyway schema-history table has been created.

## Backup and Rollback

Create a database backup before applying migrations in staging or
production.

FinTrack does not automatically perform destructive rollbacks. Recovery
uses one of the following approaches:

1. Restore the verified pre-migration backup.
2. Create a new forward migration that corrects the previous change.

A failed migration must prevent application startup and be investigated
before another deployment is attempted.
