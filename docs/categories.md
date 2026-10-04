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
- **Management:** moved to the Categories page in issue #100 (see below), reached from the
  sidebar. Inline creation in the transaction and budget forms is unchanged.
- **Filters:** transactions filter by `categoryId` on the server (first page on apply,
  cleared by Reset). Budgets filter the loaded month by category in the browser, with a
  separate message when the month has budgets but none in that category.
- **After saving:** the form resets, so a newly created category is not left selected;
  it is immediately available in every selector and filter.
- **Confirmations:** successful saves and deletes ("Transaction added.", "Transaction
  updated.", "Transaction deleted.", "Budget created.", "Budget updated.", "Budget
  deleted.", and "“name” was created/updated/deleted successfully." on the Categories page) appear in a banner at the
  top of the window (`StatusBanner`). It is announced through an always-present
  `role="status"` region, closes itself after 3 seconds, pauses while hovered or focused,
  and has a "Dismiss message" button. Starting another edit or delete clears it. Errors
  and warnings never use the banner: they are an `InlineNotice` in the page (see
  "Messages and focus" below) and stay until the user acts or dismisses them; if the list
  cannot be refreshed after a save, a persistent warning replaces the banner.

## Categories page (issue #100)

`/categories` (the "Categories" link in the sidebar and drawer) is where categories are
managed. It shows each category's usage for the server's reporting month (see the
[summary endpoint](categories-api.md#get-apicategoriessummary)). Top to bottom:

- **Summary strip:** total categories (custom and built-in), this month's top category,
  categories over budget, and categories with spending, always from every category.
- **Spending distribution** (below), then **All categories**: the toolbar and one card per
  category with its icon, name, a "Built-in" or "Custom" text badge, this month's spending
  and share, budget progress and status (or "No budget for {Month}" when it takes budgets),
  usage counts, and the last-used date.

The page offers:

- **Create category:** name and icon (the same rules and approved icons as everywhere
  else). New categories are custom and take budgets. Focus moves to the new card.
- **Edit {name}** (custom only): rename, change the icon, or both. Focus returns to the
  card's Edit button; Cancel discards the changes.
- **Delete {name}** (custom only): an inline confirmation ("Delete category" / "Keep
  category"). Afterwards focus moves to the next card, else the previous one, else the
  list heading.
- **Built-in categories** show no Edit or Delete; the API refuses those changes anyway.
- **Delete eligibility:** Delete is active only when the summary's `canDelete` is true
  (custom and unused). Otherwise it stays focusable with `aria-disabled="true"` and a
  visible reason built from the counts, such as "Used by 12 transactions and 1 budget."
  That is only a pre-check: if a transaction or budget is added before the delete, the
  server's `409 CATEGORY_IN_USE` keeps the category, the usage is refreshed, and a
  persistent message explains that its transactions and budgets must be changed or
  deleted first. Historical budgets also count, so the budget link below does not always
  show what is blocking a delete.
- **Links:** "View transactions" opens `/transactions?category={id}`, filtered and scrolled
  to the history table. A category with no transactions shows "Add transaction" instead,
  which opens `/transactions?addCategory={id}` with the new-transaction form focused and the
  category chosen (an invalid `addCategory` is removed silently). Categories that take
  budgets also get "Set budget" or "Edit budget" (`/budgets?category={id}`). Categories that
  do not take budgets get no budget action, even if a budget exists.
- **After a change:** the shared category list (used by every dropdown) updates first,
  then the usage summary reloads. A failed refresh never undoes or misreports a change
  that the server accepted: the last data stays (a deleted category is still hidden),
  with a warning and a retry.

**Messages and focus.** The page has one status area holding at most one message:

- **Success** ("“{name}” was created successfully." / "updated" / "deleted") appears only
  after the server accepted the change **and** the usage summary has reloaded. It uses the
  floating `StatusBanner` (`role="status"`, closes after 3 seconds, pauses on hover or
  focus, "Dismiss message"). The name is captured before a delete, and an edit reports the
  new name.
- **Refresh warning:** if the change succeeded but the summary could not be reloaded, a
  warning (never a failure or a plain success) says so: "“{name}” was updated, but the
  latest category summary could not be loaded. Try again, or refresh the page to see the
  current data." Its "Try again" reloads the summary and clears the warning on success.
- **Errors and warnings** use `InlineNotice` in the page flow: a visible label ("Error:",
  "Warning:", "Note:") and an icon carry the severity, never colour alone. Errors are
  `role="alert"`; warnings and notes are `role="status"`. They never close by
  themselves; "Dismiss error" / "Dismiss warning" closes one, and it stays closed until a
  new message appears.
- **Initial load failure:** "Error: Unable to load categories. Please try again." with
  "Try again"; no summary, table, or cards are shown, so nothing stale looks current.
  Server and network details are never displayed.
- **Starting an operation** (Create, Edit, or Delete) replaces any earlier message.
  Searching, filtering, sorting, and summary refreshes never bring one back.
- **Field errors stay on the form:** client validation and server field messages (`name`,
  `iconKey`) appear beside their inputs with `aria-invalid` and `aria-describedby`; the
  form's own summary (`role="alert"`) holds a general submission error once, and the page
  does not repeat it. The form stays open with the user's values.
- **Delete failures:** nothing is removed until the server confirms. An unexpected failure
  shows "Error: “{name}” was not deleted. Please try again." and keeps the confirmation
  open for a retry; `409 CATEGORY_IN_USE` closes it with an explanation (a retry cannot
  succeed). A category changed elsewhere (`404`, or built-in `403`) closes the form or
  confirmation and refreshes both lists.
- **Focus:** after create, the new card's heading; after edit, that card's Edit button
  (found by category ID, so it works after the card moves); after delete, the next card
  in the current order, else the previous one, else the "All categories" heading. A form
  failure focuses the first invalid field, or the form's error message when no field is at
  fault; a page-level error with nothing to fix focuses the notice; an in-use refusal
  focuses the card's Delete button; a failed delete keeps focus on "Delete category".
  Passive refreshes never move focus.
- **Pending:** "Creating…", "Saving…", and "Deleting…" replace the button text and the
  buttons are disabled until the request finishes, so nothing is submitted twice.
  Banners report outcomes only.

**Spending distribution.** "Spending in {Month Year}" (the server's reporting month, never
the browser clock) is a table with the columns Category, Spent, and Share:

- Only categories with expense spending this month (`currentMonthSpent > 0`) are listed;
  the section says so. With no spending at all it shows "No spending recorded for
  {Month Year}."
- Rows are ordered by spending (highest first), then name, then ID.
- Share = category spending ÷ the month's total spending × 100, shown to one decimal
  place ("<0.1%" for a tiny non-zero share). A zero, missing, or invalid amount counts as
  0, so the page never divides by zero.
- Each row has a bar whose width is the unrounded share, clamped to 0–100%. The bar and
  icon are decorative (`aria-hidden`); the amount and share are always visible as text.
- If the shown (rounded) shares do not add up to exactly 100%, a note says the
  percentages are rounded.

**Finding categories.** A toolbar above the cards searches, filters, and sorts **the cards
only**. The summary strip and the spending table always cover every category. The state
lives on the page only (no URL parameters) and is applied in this order:

1. **Search** ("Search categories"): the name contains the query, after trimming, Unicode
   NFC normalization, and ignoring case (accents still count, so "cafe" does not find
   "Café"). An empty or whitespace-only query matches everything.
2. **Filter** ("Filter categories"):
   - All categories.
   - Custom: `builtIn` is false.
   - Built-in: `builtIn` is true.
   - Unused: no transactions and no budgets ever (`transactionCount` and `budgetCount`
     both 0). Built-ins can be unused, though they still cannot be deleted.
   - No budget this month: `currentMonthBudget` is null, whether or not the category takes
     budgets.
3. **Sort** ("Sort categories"), always ending on name (locale-aware, ignoring case) and
   then ID, so the order never shuffles:
   - Name: name, then ID (the default).
   - This month's spending: highest first, then name, then ID.
   - Most used: most transactions (all time), then this month's spending, then name, then ID.

"Showing {n} of {total} categories" is a polite status region. "Clear category filters"
(shown when anything differs from the defaults) resets all three and returns focus to the
search box. When nothing matches, the page says so (instead of the "no categories" empty
state) and offers "Show all categories".

**Active forms while filtering.** A category being edited, or with its delete
confirmation open, always stays visible, even if the search or filter no longer matches
it, so unsaved changes are never discarded; a note explains why it is shown. After a
save, cancel, or refused delete it stays until the toolbar next changes, so focus can
return to it. A newly created category is likewise shown (and focused) until the next
toolbar change. After a delete, focus moves to the next card in the current filtered and
sorted order. After any change the refreshed data is searched, filtered, and sorted again.

**Category deep links.** `?category={id}` is read once the user's category list has
loaded, and accepted only as a positive whole number that is one of the user's own
categories (built-in or custom). Anything else (text, decimals, zero, negatives, unknown,
deleted, or another user's ID) is never sent to the API: the parameter is removed with a
history replacement, other parameters are kept, and the page loads normally, so another
user's ID behaves exactly like a missing one.

- **Transactions:** a valid link sets the category filter and loads page 1 once (no
  unfiltered request first). Browser Back and Forward re-apply or clear it.
- **Budgets:** if the displayed month has a budget for the category, it opens in the
  existing edit form; otherwise, for a category that takes budgets, the create form opens
  with the category preselected for the displayed month (nothing is saved automatically).
  A category that does not take budgets is ignored.

**Accessibility.** One `h1` ("Categories"), then `h2` sections (spending, All categories)
and an `h3` per card. Every toolbar and form control has a visible label; icon-only
buttons have names ("Dismiss error", "Dismiss message"); decorative icons and bars are
`aria-hidden` and every value they show is also text. Built-in and custom are told apart
by text, budget status by its label, and notice severity by "Error:", "Warning:", or
"Note:", never by colour alone. Delete stays focusable when inactive so its reason can be
reached. No positive `tabIndex` is used, focus never targets a removed card, and passive
refreshes never move focus. The only animation (the success banner sliding in) is turned
off for `prefers-reduced-motion`.

**Responsive layout.** The summary strip goes from four columns to two (at 1100 px) and
one (at 700 px); cards fill an auto-fit grid; the toolbar controls and notice buttons
wrap; the spending table uses fixed column widths (narrower below 480 px) so long names
wrap instead of scrolling. The page has no horizontal scrolling at 375 px. It is reached
from the sidebar, the collapsed sidebar, and the mobile drawer.

**Protections.** The summary only ever reads the signed-in user's data, takes no IDs,
and runs a fixed number of queries however many categories exist (see the
[summary endpoint](categories-api.md#get-apicategoriessummary)). Deep-link IDs are checked
against the user's own categories before any request.

**Decisions made during implementation.**

- The "Manage categories" links originally planned for Transactions and Budgets were
  removed at review; the Categories page is reached from the navigation.
- Warnings and errors use the in-page `InlineNotice` instead of new `StatusBanner`
  variants, so persistent messages sit next to the related content and do not float
  over the page; `StatusBanner` remains the 3-second success confirmation. Page-level
  errors and refresh warnings on Transactions, Budgets, and the Dashboard use the same
  notice.

**Test coverage.** Backend: service, controller, H2 integration (authentication,
ownership, ordering, dashboard parity), repository (month boundaries, income versus
spending, budget counts), query-count, and MySQL tests. Frontend: utility tests for
search, filters, sorting, shares, and deep links; component tests for the spending table,
notices, forms, and delete confirmation; page tests for every state, workflow, message,
and focus rule above; navigation tests for the sidebar, collapsed sidebar, and drawer.
Layout, zoom, and screen-reader announcements are verified manually (see
[frontend testing](frontend-testing.md)).

## Not implemented yet

- Viewing the Categories page for an earlier month (the page always shows the server's
  current reporting month).
