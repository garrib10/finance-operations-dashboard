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

## V3: profile and account preferences

`V3__add_profile_and_account_preferences.sql` adds these `users` columns:

| Column                  | Definition              | Default/backfill                                                                  |
| ----------------------- | ----------------------- | --------------------------------------------------------------------------------- |
| `display_name`          | `VARCHAR(100) NOT NULL` | Trimmed first and last name, limited to 100 characters; `Account` for empty names |
| `date_format`           | `VARCHAR(16) NOT NULL`  | `MEDIUM`; allowed values `MEDIUM`, `ISO`                                          |
| `transaction_page_size` | `INT NOT NULL`          | `10`; allowed values 10, 25, 50                                                   |

The migration first adds a nullable display name, backfills legacy rows, and only
then applies NOT NULL. IDs, email, password hashes, timestamps, and relationships
are not changed. Both preference columns have named CHECK constraints. Their CASE
expressions are explicitly compared with 1, which is required for MySQL Boolean
constraint expressions and avoids the H2 IN-predicate migration-session issue.

An empty database runs V1, V2, and V3. A database already at V2 runs only V3.
A legacy schema with no Flyway history must follow the controlled V1 adoption
procedure above; do not enable baseline-on-migrate for routine upgrades. Normal
startup keeps it false and validates migration history. Once successfully applied,
V3 is recorded in history; later starts validate without rerunning it and apply
any newer migrations, including V4.

Do not edit a successfully applied migration, including V3. Subsequent changes
require V4 or later. MySQL DDL can leave partial changes after failure: stop,
back up, inspect schema and history, and reconcile the partial schema before a
controlled repair/retry. Repairing history alone does not undo existing columns.
Never mark a failed migration successful without verifying its complete schema.

### Staging migration checks

The automated migration suite uses H2 in MySQL mode, not a MySQL server. There is
no configured disposable MySQL/Testcontainers workflow in this repository.
A previous user-run local MySQL 9.7 recovery validated V3, but does not replace
clean-install and populated-V2 rehearsal on the staging MySQL version.

Before deployment, use an isolated disposable database or restored copy, never a
shared production schema, to:

1. Back up and verify restoration; record server version, collation, and baseline.
2. Run a clean install and a populated V2 upgrade with the exact release artifact.
3. Compare IDs, credential hashes, timestamps, and financial records privately,
   without including credential values in reports.
4. Check backfills, defaults, NOT NULL, and both allowed-value constraints, including
   rejected unsupported values. Verify the uppercase enum contract under the target collation.
5. Confirm V3 success in `flyway_schema_history`, then restart to confirm no repeat migration.
6. Check Hibernate validation, `/api/health`, and account/financial workflows.

Flyway previously warned that local MySQL 9.7 was newer than its verified 9.4
version. Confirm the target server/Flyway compatibility during staging rehearsal.


## V4: optional profile-photo storage reference

`V4__add_profile_photo_storage.sql` adds only
`users.profile_photo_key VARCHAR(255) NULL`. This is a backend-generated storage
key/Cloudinary public ID, not image bytes or a client-provided URL. No cleanup
or work table is introduced. See [profile-photo policy](profile-photo-security.md).

Existing users receive NULL and retain initials. Registration also leaves the
column null. IDs, names, email, password hashes, preferences, timestamps,
categories, budgets, and transactions remain unchanged. Clearing a future photo
sets the reference to null. Provider secrets, binary data, Base64, original
filenames, image metadata, and signed URLs must never be stored in this column.

A clean installation now applies V1 through V4. A populated V3 database applies
only V4. Current version becomes 4, and subsequent startups validate without
reapplying it. Existing baseline-on-migrate behavior is unchanged and remains
disabled for routine upgrades. V1–V4 are immutable after successful application;
future schema corrections require a new versioned migration.

Automated tests use H2 in MySQL mode and compare all preexisting user and financial
columns before/after a populated V3 upgrade, including microsecond timestamps.
They also check the nullable 255-character column and repeat migration behavior.
The SQL uses a simple nullable VARCHAR addition shared by MySQL and H2. Rehearse
clean and populated V3 upgrades against the target MySQL version before deployment;
this phase does not run migrations on local/shared or hosted MySQL databases.
