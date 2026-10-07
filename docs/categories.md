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
  categories over budget, and **No budget** ("categories spending in {Month Year} without
  a budget", issue #102, replacing "With spending"), always from every category in the
  response, never the searched, filtered, or sorted cards.
- **Spending without a budget** (issue #102): a category needs a budget when
  `currentMonthSpent > 0`, `currentMonthBudget` is null, and `budgetEnabled` is true.
  Spending is expenses only, so income alone never qualifies; budgets from other months do
  not count; a category that takes no budgets never qualifies. Its card shows a static
  "Warning: {amount} spent in {Month} with no budget." with the card's only "Set budget for
  {name}" link inside it (the ordinary Set budget link is removed from the link row). It is
  advice, not an announcement (no `role` or live region, so many cards never trigger many
  announcements), it has no dismiss button, and it never blocks adding transactions. It
  disappears once a refreshed summary shows a budget for the month; if a refresh fails, the
  last confirmed data stays, with the usual refresh warning.
- **Spending distribution** (below), then **All categories**: the toolbar, then the cards in
  two sections (see "Active and other categories" below), one card per
  category with its icon, name, a "Built-in" or "Custom" text badge, this month's spending
  and share, budget progress and status (or "No budget for {Month}" when it takes budgets),
  and this month's activity (issue #102): "1 transaction in October" / "2 transactions in
  October" (income and expense, the server's month) followed by "· Last used {date}", or
  "Not used yet" when the category has never had a transaction. The all-time transaction
  and budget counts no longer appear on the card; they still decide whether Delete is
  available, so a category that is quiet this month but was used before stays protected
  and its Delete reason still quotes the all-time counts.

The page offers:

- **Create category:** name and icon (the same rules and approved icons as everywhere
  else). New categories are custom and take budgets. Focus moves to the new card.
- **More actions for {name}** (custom only, issue #102): a "⋯" button in the card header
  that shows "Edit {name}" and "Delete {name}". It is a disclosure (a button revealing
  ordinary buttons), not an ARIA menu: opening leaves focus on the trigger and Tab moves
  through the actions. It closes on the trigger, on Escape (focus returns to the trigger),
  on a click outside, or when focus moves elsewhere on the page. Only one card's actions
  are open at a time, and they close when a workflow starts or the search, filter, or
  sort changes. The trigger is hidden while the card shows a form or confirmation.
- **Edit {name}**: rename, change the icon, or both. Focus returns to the card's "More
  actions" button after a save or Cancel; Cancel discards the changes.
- **Delete {name}**: an inline confirmation ("Delete category" / "Keep category").
  Afterwards focus moves to the next card, else the previous one, else the list heading;
  "Keep category" returns focus to the "More actions" button.
- **Built-in categories** have no "More actions" button at all (not a disabled one), so no
  Edit or Delete; the API refuses those changes anyway.
- **Delete eligibility:** Delete is active only when the summary's `canDelete` is true
  (custom and unused). Otherwise it stays focusable inside the "More actions" panel with
  `aria-disabled="true"` and a visible reason built from the all-time counts, such as
  "Used by 12 transactions and 1 budget." (not a tooltip); click, Enter, and Space do
  nothing.
  That is only a pre-check: if a transaction or budget is added before the delete, the
  server's `409 CATEGORY_IN_USE` keeps the category, the usage is refreshed, and a
  persistent message explains that its transactions and budgets must be changed or
  deleted first. Historical budgets also count, so the budget link below does not always
  show what is blocking a delete.
- **Links:** real links with a decorative icon and visible text; the transaction link is
  the stronger, tinted one. "View transactions" opens `/transactions?category={id}`, filtered and scrolled
  to the history table. A category with no transactions shows "Add transaction" instead,
  which opens `/transactions?addCategory={id}` with the new-transaction form focused and the
  category chosen (an invalid `addCategory` is removed silently). Categories that take
  budgets also get "Set budget" or "Edit budget" (`/budgets?category={id}&month={m}&year={yyyy}`,
  for the month being shown); for a category
  spending without a budget, Set budget is inside the warning instead. Categories that
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
- **Focus:** after create, the new card's heading; after an edit is saved or cancelled,
  that card's "More actions" button (found by category ID, so it works after the card
  moves); after delete, the next card
  in the current order, else the previous one, else the "All categories" heading. A form
  failure focuses the first invalid field, or the form's error message when no field is at
  fault; a page-level error with nothing to fix focuses the notice; an in-use refusal
  focuses the card's "More actions" button; a failed delete keeps the confirmation open
  with focus on "Delete category" for a retry.
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

**Earlier months** (issue #103). The Month and Year selects under the page heading (a
"Reporting month" group, not a form) choose which month every monthly figure describes:
the summary strip, spending table, cards, warnings, and Active/Other membership.

- **The URL is the source of truth:** `/categories?month=8&year=2026` shows August 2026;
  no parameters means the server's current month. The current month is always taken from
  the server (`serverCurrentMonth`/`serverCurrentYear`), never the browser clock, so the
  first request is always the plain one and an earlier month is requested once that is
  known. Nothing invalid is ever sent.
- **Accepted:** exactly one `month` and one `year`, written as plain whole numbers (no
  signs, decimals, spaces, or leading zeros), from January 2000 up to the current month.
  Anything else (only one of the two, empty, text, out of range, a future month, or a
  parameter given twice even with the same value) is removed with a history replacement,
  keeping other parameters, and the current month is shown. An explicit current month is
  also replaced by the plain URL.
- **Choosing:** years 2000 to the current year; months only up to the current month in
  the current year, and choosing the current year moves a later month back to the current
  one. Each choice adds a history entry, so Back and Forward step through the months
  viewed, and a refresh keeps the month. "Back to current month" (shown only for an
  earlier month) removes the parameters and keeps focus on the Month select.
- **Loading:** the heading and selects stay; the figures and cards are replaced by
  "Loading {Month Year}…" until that month arrives, so one month's figures are never
  shown under another's label. Only the newest request counts: a newer choice aborts the
  older request, and late answers, failures, and aborts are ignored. When it arrives, a
  single status says "Showing {Month Year}". A failure shows the usual error with "Try
  again" for the same month; the selects (still bounded by the server month already
  known) and "Back to current month" stay, so another month also recovers. A request
  replaced by a newer choice is cancelled quietly, never shown as an error, and a refresh
  after a change can never overwrite a month chosen since.
- **Changes** (create, edit, delete) refresh the month being viewed, not the current one.
- **Open forms:** while a create or edit form or a delete confirmation is open, the selects
  and Back button are disabled, with the visible reason "Finish or cancel the open form or
  confirmation to change the month." (Browser Back and Forward still work; an edit keeps
  its typed values and the create form stays open across the change.)
- **Wording for an earlier month:** nothing says "this month". The subtitle reads "How each
  category was used in {Month Year}" (also while that month loads or fails), sections read
  "Active in {Month Year}", "Nothing had spending or a budget in {Month Year}.", and "Every
  category had spending or a budget in {Month Year}.", and the strip reads "Top in {Month
  Year}", "of {n} budgets in {Month Year}", and "No spending in {Month Year}". The spending
  table says "Categories with no spending in {Month Year} are not listed." The current month
  keeps its usual wording ("is used in", "this month", "No spending yet"). Cards name just
  the month ("Spent in September", "2 transactions in September", "No budget for
  September"), since the page names the year; the filter and sort options name it too ("No
  budget in {Month}", "Spending in {Month}").
- **Every figure comes from the month shown:** transaction counts (income and expenses),
  spending (expenses only), the budget with its remaining amount, percentage, and status,
  the No budget count and warnings, Active/Other membership, the strip, and the spending
  table. The rules are the same for every month; delete eligibility stays all-time.
- **Budget links keep the month:** Set budget and Edit budget open
  `/budgets?category={id}&month={m}&year={yyyy}` for the month the cards describe (the
  response's month, never the browser clock or a month still loading), so Budgets edits that
  month's budget or starts one for that month. The current month is written out too, so
  Budgets never relies on the browser clock. Their accessible names include the month ("Set
  budget for Groceries for September 2025"). Browser Back from Budgets returns to the same
  Categories month.
- Switching months and following links never change the saved Active or Other
  categories preferences.

**Active and other categories** (issue #102). While browsing (no search, filter "All
categories"), the cards are split into two sections, and **each category appears exactly
once**:

- **Active this month:** spending this month (`currentMonthSpent > 0`) or a budget for this
  month. This month's transaction count alone (for example only income), earlier
  transactions, earlier budgets, and `budgetEnabled` never make a category active; built-in
  and custom categories follow the same rule. With none, it says "Nothing has spending or a
  budget in {Month} yet."
  **Open by default**, with its own "Hide active categories" / "Show active categories"
  button (`aria-expanded`, `aria-controls`) like Other categories, saved per device in
  `fintrack:categories-active-expanded` (only a saved `"false"` closes it; anything else,
  or unreadable storage, means open). It stays open, with the button hidden, while one of
  its cards has a form or confirmation open, and opens for a card that focus is moving to;
  neither is saved. With nothing active there is no button, only the message.
- **Other categories · {n}:** everything else, with `n` counting only these. It is **closed
  by default**; "Show other categories" / "Hide other categories" (`aria-expanded`,
  `aria-controls`) opens and closes it, and closed cards are not rendered, so nothing hidden
  can take focus.
- **Saved choice:** only pressing that button saves it, per device, in `localStorage` key
  `fintrack:categories-others-expanded` (`"true"` or `"false"`; anything else, or unreadable
  storage, means closed).
- **Opened automatically, never saved:** while the create form is open; while an Other card
  is being edited or has its delete confirmation open (the button is hidden for these two);
  to show a card that focus is about to land on (a new category, a saved or cancelled one,
  one that moved into Other after a refresh, or the next card after a delete; pressing
  Show/Hide ends this); and when nothing is active in the month shown, where it **starts
  open by default but keeps its Show/Hide button**, as on every other month. Hiding it
  then saves the choice like any Hide and keeps that month closed; another month with
  nothing active starts open again.
- **Sorting** applies inside each section and keeps the sections.
- **Searching or filtering** shows the single flat results list instead (every match visible
  whatever the saved choice); clearing them returns to the sections and the saved choice.
  An edit in progress keeps its typed values when the page switches between the two.
- **Focus after a delete** moves to the next card in rendered order (Active, then Other),
  else the previous one, else the list heading.
- The summary strip, the No budget count, and the spending table always use every
  category, whether Other categories is open or closed.

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
  A category that does not take budgets is ignored. Focus moves to the form heading ("Edit
  Budget" or "Create Budget") once the budgets have loaded; an ignored link moves no focus.
- **Budgets month (issue #103):** `?month={m}&year={yyyy}` (checked by the same parser as
  Categories: one of each, plain whole numbers, month 1–12, year 2000 or later) opens that
  month in the Budget Period selects, and the category link then uses that month's budget
  (never another month's). The link stays in the URL, so a refresh or a copied URL opens the
  same form, and Back/Forward between links follow the month. An invalid month is removed
  with a history replacement and the category applies to today's month; an unknown or
  another user's category is removed and the valid month kept. Once the user shows another
  month (or a save moves there), the month and category are removed together, so the URL
  never describes a different month. A plain `/budgets` visit is unchanged.

**Accessibility.** One `h1` ("Categories"), then `h2` sections (spending, All categories,
Active this month, Other categories) and an `h3` per card. Card actions are a disclosure
("More actions for {name}", `aria-expanded`/`aria-controls`), not an ARIA menu; the
Other categories toggle is a native button with `aria-expanded`/`aria-controls`, and
closed cards are not rendered. Card warnings are static text (no live region); page
messages keep their status and alert roles. Every toolbar and form control has a visible label; icon-only
buttons have names ("Dismiss error", "Dismiss message"); decorative icons and bars are
`aria-hidden` and every value they show is also text. Built-in and custom are told apart
by text, budget status by its label, and notice severity by "Error:", "Warning:", or
"Note:", never by colour alone. Delete stays focusable when inactive so its reason can be
reached. No positive `tabIndex` is used, focus never targets a removed card, and passive
refreshes never move focus. The only animation (the success banner sliding in) is turned
off for `prefers-reduced-motion`; the sections and action panel appear without motion.

**Responsive layout.** The summary strip goes from four columns to two (at 1100 px) and
one (at 700 px); cards fill an auto-fit grid; the toolbar controls and notice buttons
wrap; a card's name and badge wrap together (the badge drops under the name) and its two
links share a row or each take the full width; the action panel stays inside its card;
the spending table uses fixed column widths, and below 480 px sizes Spent and Share to
their content so names wrap between words instead of scrolling. The page has no horizontal scrolling at 375 px. It is reached
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

- Budget shortcuts that open the month being viewed on the Budgets page (issue #103, a
  later phase).
