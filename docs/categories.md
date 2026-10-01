# Categories

Every category belongs to exactly one user. Transactions and budgets reference a
category by ID, never by name. This page records the category policy from issue #19:
phase 1 (migration V6) and phase 2 (the hardened [category API](categories-api.md)).
Details of the migration itself are in
[database migrations](database-migrations.md#v6-category-normalization-built-in-metadata-icons-and-ownership).

## Built-in and custom categories

Each new user receives their own 13 **built-in** rows (`built_in = true`). They are
defined in `BuiltInCategory`:

| Category | Icon key | Budget enabled |
| --- | --- | --- |
| Housing | `house` | yes |
| Groceries | `shopping-cart` | yes |
| Dining | `utensils` | yes |
| Transportation | `car` | yes |
| Utilities | `lightbulb` | yes |
| Insurance | `shield` | yes |
| Healthcare | `heart-pulse` | yes |
| Entertainment | `clapperboard` | yes |
| Shopping | `shopping-bag` | yes |
| Travel | `plane` | yes |
| Income | `circle-dollar-sign` | no |
| Savings | `piggy-bank` | no |
| Other | `tag` | yes |

A category created through `POST /api/categories` is **custom** (`built_in = false`)
with the approved icon the client chose, or `tag` by default. Clients cannot set
ownership or built-in status: the owner comes from the authenticated user, and
`built_in` is not updatable after insert.

Built-in categories cannot be renamed, re-iconed, have their budget flag changed, or be
deleted (`403 CATEGORY_BUILT_IN`); the entity itself also refuses those changes. Custom
categories can be renamed (even while in use) and re-iconed, and can be deleted only
when no transaction or budget references them (`409 CATEGORY_IN_USE` otherwise).

There are no shared or ownerless categories, no hierarchy, no archiving, and no
name snapshots on financial records.

## Name normalization

`CategoryNameNormalizer` is the only application implementation. It produces two
values from the submitted name:

- **Display name** (`name`, shown to users): Unicode NFC; every run of whitespace
  (Java `Character.isWhitespace` or `isSpaceChar`, including non-breaking and
  ideographic spaces) collapsed to one ASCII space; leading and trailing whitespace
  removed. It must not be empty, must not contain other control characters or
  malformed surrogate pairs, and must be at most 100 UTF-16 code units after this
  cleanup. Invalid names are rejected, never truncated.
- **Comparison name** (`normalized_name`, internal only): the display name lowercased
  with `Locale.ROOT`, then NFC again.

Consequences of this policy:

- `Groceries`, `GROCERIES`, and `  groceries ` are the same category name.
- Composed `Café` (`é`) and decomposed `Cafe` + U+0301 are the same.
- `Café` and `Cafe` are **different** (accents are significant).
- It is lowercase equality, not full case folding: `Straße` and `STRASSE` are
  **different**.
- The result never depends on the server's default locale.

Invalid names return the standard `400` validation response with a fixed `name`
message that never echoes the input: `Category name is required`,
`Category name contains unsupported characters`, or
`Category name must be 100 characters or fewer`.

V6 has a frozen copy of this algorithm for the legacy backfill. Changing
`CategoryNameNormalizer` later does not change V6; a policy change needs a new
migration that re-normalizes stored rows.

## Per-user uniqueness

`uk_categories_user_normalized_name (user_id, normalized_name)` is authoritative, with
an exact binary collation on MySQL (`utf8mb4_0900_bin`). Different users may use the
same name, but one user cannot have two categories with the same comparison name.

The service checks first for a friendly `409 CATEGORY_DUPLICATE` ("Category already
exists"). Two concurrent requests can both pass that check, so a violation of the unique
constraint on save is also mapped to the same `409`. Renaming only the case or spacing
of a category's own name is allowed.

## Icons

`CategoryIcon` is the backend-owned catalog of approved semantic keys: lowercase words
joined by hyphens, at most 64 characters. The database stores only the key, never SVG,
HTML, CSS classes, component names, URLs, file paths, or uploaded images. The frontend
decides how each key is drawn. `ck_categories_icon_key_format` rejects anything that
isn't a slug, and requests are validated against the exact catalog. The entity stores
the raw key; a stored key outside the current catalog (for example one retired later)
is returned as `tag` without rewriting the row. Adding an icon means adding a
`CategoryIcon` constant (no migration needed for a new slug). Keys follow
[Lucide](https://lucide.dev/icons) icon names so the frontend can map them directly,
but no icon library is a backend dependency.

The catalog (39 keys) is the 13 built-in icons plus 26 extra choices for custom
categories:

| Area | Key | Area | Key |
| --- | --- | --- | --- |
| Pets | `paw-print` | Clothing | `shirt` |
| Gifts | `gift` | Personal care | `sparkles` |
| Fitness | `dumbbell` | Medicine | `pill` |
| Education | `graduation-cap` | Work | `briefcase` |
| Kids | `baby` | Debt | `credit-card` |
| Home repairs | `wrench` | Taxes and fees | `receipt` |
| Phone and internet | `smartphone` | Charity | `hand-heart` |
| Subscriptions | `tv` | Furniture | `sofa` |
| Music and hobbies | `music` | Garden and outdoors | `sprout` |
| Coffee | `coffee` | Games | `gamepad-2` |
| Drinks | `wine` | Events | `ticket` |
| Gas | `fuel` | Deliveries | `package` |
| Transit | `bus` | Side income | `wallet` |

## Ownership safeguards

Services load categories only by `(id, user_id)`. The database also enforces this:
`fk_transactions_category_owner` and `fk_budgets_category_owner` reference
`categories (id, user_id)`, so a transaction or budget can never point at another
user's category, even through direct SQL. Deleting a category that transactions or
budgets still reference is rejected by the foreign keys.

## Legacy classification limitation

Existing rows were classified by V6. Their origin cannot be reconstructed, so a row
whose name normalizes to a canonical default name is treated as built-in (with that
default's icon), and every other row is custom with `tag`. A renamed default is
therefore custom. V6 does not add missing defaults or rename anything.

## Categories in financial writes (phase 3)

Transaction and budget writes take exactly one of `categoryId` or `newCategory`; a new
category is created through the same `CategoryService.createCustomCategory` path as the
category API (one normalization, one duplicate rule), by the shared
`CategorySelectionService`, inside the financial write's transaction (never
`REQUIRES_NEW`). Categories created this way are custom, budget-enabled, and reusable by
both workflows. Financial and dashboard responses carry `categoryIconKey`. See the
[category API](categories-api.md#categories-in-transactions-and-budgets).

## Frontend (phase 4)

- **Icons:** `lucide-react` draws the approved keys through one explicit registry
  (`components/categoryIconRegistry.ts`). Server strings are only used as keys into it;
  anything unknown renders `tag`. Icons are decorative (`aria-hidden`) and always sit next
  to the visible category name.
- **Shared state:** `CategoryProvider` (around the routes in `App.tsx`) loads the signed-in
  user's categories on first use, keeps them in memory only, ignores responses for an
  earlier user or an older request, and renders nothing from a previous user while the
  next one loads. A failed refresh after a successful change keeps the last list and
  shows a warning with a retry; the change itself is never resubmitted.
- **Choosing a category:** transaction and budget forms use `CategorySelect`, a native
  select of the user's categories (a saved "Other" is an ordinary option) plus a separate
  **Create a custom category…** option with a non-numeric internal value. That option
  reveals a name field and an icon picker (native radios, default `tag`), and the form
  sends exactly one of `categoryId` or `newCategory`. The new category is saved together
  with the record and appears in every selector and filter right after.
- **Errors:** server field errors are shown beside their controls from an explicit
  allowlist per form (`amount`, `monthlyLimit`, `month`, `year`, `type`, `description`,
  `transactionDate`, `categoryId`, `newCategory`, `newCategory.name`,
  `newCategory.iconKey`), with `aria-invalid` and `aria-describedby`; the first invalid
  control receives focus, and anything unrecognized is shown as text in the form summary.
  A duplicate new-category name keeps the whole form and offers the existing category.
- **Management:** "Manage categories" on the transaction and budget pages renames,
  re-icons, and deletes (with confirmation) custom categories; built-in ones show no
  controls. In-use, duplicate, missing, and built-in responses are shown, never hidden.
- **Filters:** transactions filter by `categoryId` on the server (first page on apply,
  cleared by Reset). Budgets filter the loaded month by category in the browser, with a
  separate message when the month has budgets but none in that category.

## Not implemented yet

- Responsive collapsible sidebar (tracked by a separate v1.3.0 issue).
