# Frontend and staging verification

This guide describes the issue #16 branch, not a claim that it is deployed.
Use dedicated fictional accounts in an isolated test environment. Store credentials
in a password manager or local environment, never in documentation or screenshots.
The private `docs/testing/` directory is ignored and is not a release artifact.

## Local commands

Use Java 21 for the backend and the Node version specified by CI where available
(frontend CI uses Node 24; the separate ESLint workflow uses Node 22). The local
Phase 5 run used Node 25.1.0/npm 11.7.0; CI must still validate its own runtime.

From the repository root:

```bash
./mvnw --batch-mode clean verify
```

From `frontend`, run installation before the other commands:

```bash
npm ci
npm run lint -- --max-warnings=0
npm run test:coverage
npm run build
npm audit
```

Run backend and frontend suites sequentially on resource-constrained machines.
Do not weaken assertions, coverage thresholds, or timeouts to hide failures.
Mockito may require permission to attach its test agent when Maven runs in a sandbox.

Frontend thresholds remain 85% for statements, branches, functions, and lines.
Backend JaCoCo gates remain 90% instructions and 85% branches. Results are generated
at `frontend/coverage/index.html` and `target/site/jacoco/index.html`; do not commit
reports, build output, logs, local environment files, or database backups.

To run the application against your local database, start `./scripts/run-local.sh`
from the root and `npm run dev` from `frontend` in separate terminals. Run only one
backend instance on port 8080. The Vite dev server proxies `/api` to
`http://localhost:8080` (override with `DEV_API_PROXY_TARGET`); `VITE_API_BASE_URL` is
no longer used. Do not point local mutation tests at production.

Vitest uses jsdom and disables Node's built-in web storage for Node 25 or later.
These component tests verify semantics and interactions, not actual browser layout,
zoom, screen-reader announcements, or CSS rendering. No browser accessibility runner
is configured; use the checklist below in a real browser.

## Account behavior to verify

- `/profile` and `/settings` are protected, including direct navigation and refresh.
- The account dropdown uses display name and initials, opens both pages, dismisses
  with Escape/outside click, and restores focus appropriately.
- Profile accepts only display/first/last names, trims them, and keeps email read-only.
  The email explanation appears on hover/focus and remains associated with the input.
- Save updates the header immediately. Reset restores the current canonical values;
  errors preserve edits and existing authenticated identity.
- Preferences allow only MEDIUM/ISO and numeric 10/25/50. Date-only values retain
  their calendar day. Every Transaction list request uses the selected page size.
- Password requirements show empty circles that become checked when satisfied:
  at least 15 Unicode code points and no more than 72 UTF-8 bytes. Confirmation
  matches exactly and never leaves the frontend. Spaces are not trimmed.
- Backend common-password, all-blank, and reuse checks remain authoritative.
  Incorrect current password is a field error (400), not a logout (401).
- Independent forms have separate pending/error/success state. Passwords stay in
  component memory and clear after success; never copy them into diagnostics.
- Genuine authenticated 401 invalidates the current session. Temporary restoration
  failures retain the token and offer retry; stale responses cannot replace a newer session.
- Existing JWTs are not revoked by logout/password changes; issue #18 owns rotation/revocation.

## Staging smoke checklist

Do not execute this against a shared production database merely because its frontend
is called staging. Verify backend/database isolation and obtain the normal deployment
approval separately. No hosted settings or data are changed by Phase 5.

1. Confirm release revision, frontend API URL, allowed origin, health endpoint, and
   the migration checks in [Database Migrations](database-migrations.md).
2. Register a dedicated user with a unique fictional email and a privately generated
   password satisfying the policy; confirm invalid registration gives useful errors.
3. Sign in, open Profile from the dropdown, and verify prefilled names/read-only email.
4. Update display name and both names; confirm immediate header identity changes.
5. Refresh, then sign out/in; confirm saved names persist.
6. Open Account Settings. Save ISO and then MEDIUM; verify Dashboard and Transaction
   dates, including a date near a month/year boundary with no timezone shift.
7. Save page sizes 10, 25, and 50. Verify request sizes on initial load, filter apply,
   filter reset, previous/next, and refresh after create/edit/delete. A size change
   starts at page zero and preserves current filters/sort.
8. Reload and sign in again to confirm preferences persist.
9. Submit an incorrect current password; verify the current-password field error
   and that authenticated navigation still works.
10. Check short, over-72-byte Unicode, reused, common, and mismatched new passwords.
    Confirm no unintended request for client-invalid values and no credentials in errors.
11. Change the password successfully; verify all password fields clear and the current
    session stays signed in. Record the new credential privately.
12. Log out. Verify old-password login fails and new-password login succeeds.
13. Sign in as a second dedicated user; confirm their names, preferences, password,
    and financial records are unaffected. Cross-account ID/email injection must not
    select that user. Avoid capturing authorization headers in test artifacts.
14. Simulate a failed save and a slow request: edits remain, errors are useful, and
    repeated clicks do not duplicate saves. Retry after connectivity returns.
15. Create/edit/delete dedicated transaction and budget records, check dashboard totals,
    then remove only the records created by this smoke test. Confirm `/api/health`.
16. Review application/proxy/APM logs privately: no credentials, tokens, authorization
    headers, rejected secret values, SQL bind values, or stack traces in API errors.

### Manual accessibility and layout

- Use Tab/Shift+Tab, Enter, Space, and Escape throughout the dropdown and forms.
- Confirm visible focus, logical headings and labels, no nested interactive elements,
  and that email remains selectable/read-only with its explanation available on focus.
- Trigger multiple errors: focus goes to the first invalid field or error summary;
  instructions and error text are announced with the associated field.
- Confirm successful save focuses and announces status without competing announcements.
- Toggle password visibility using the keyboard; focus and value remain unchanged,
  accessible name changes between Show/Hide, and the form does not submit.
- Check requirement states with a screen reader; do not rely only on green color.
- Test narrow widths (for example 320 CSS pixels), 200% zoom, and enlarged text:
  controls/text wrap, show/hide buttons remain reachable, and no form overflows.
- Confirm pending text and disabled controls are understandable on each independent form.

Record browser/version, viewport/zoom, assistive technology, release revision, and
pass/fail evidence without credentials. A checklist is not evidence that these
browser/staging checks have already passed.

## Issue #18 (v1.2.0) verification snapshot

Measured on September 27, 2026 on the issue #18 branch, locally, with no hosted
service contacted:

| Check | Result |
| --- | --- |
| `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw --batch-mode clean verify` | BUILD SUCCESS |
| Surefire (unit and H2 integration) | 709 tests in 62 classes; 0 failures, errors, or skips |
| Failsafe (`*IT`, real MySQL 8.4.6 via Testcontainers) | 23 tests in 4 classes; 0 failures, errors, or skips |
| Total backend tests | 732 |
| JaCoCo instructions / branches | 98.78% (8440/8544) / 96.49% (742/769) |
| JaCoCo lines / methods | 98.59% (1886/1913) / 98.30% (463/471) |
| `npm ci` | Passed; 0 vulnerabilities |
| `npm run lint -- --max-warnings=0` | Passed |
| `npm run test:coverage` | 540 tests in 33 files passed |
| Frontend statements / branches | 100% (1137/1137) / 100% (721/721) |
| Frontend functions / lines | 100% (284/284) / 100% (1070/1070) |
| Proxy, routing, and dev-proxy tests (`frontend/tests/`) | 76 tests in 3 files, included above |
| `npm run build` | TypeScript and Vite passed; existing >500 kB chunk warning |
| Bundle scan | No server-only names or values in `dist/` (no source maps emitted) |
| `npm audit` | 0 vulnerabilities |

The MySQL tests need a running Docker-compatible runtime. With Colima, export
`DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` and
`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`. GitHub-hosted runners
provide Docker already, and CI needs no hosted credentials. Tests never contact
Railway, Vercel, Cloudinary, or a real database.

Automation does not prove hosted cookie handling, the Vercel proxy on real
infrastructure, browser restart behavior, or cross-browser Web Locks and
BroadcastChannel behavior. Those belong to the
[staging smoke checklist](deployment.md#staging-smoke-checklist).

## Final issue #16 verification snapshot

Measured in Phase 5 on September 26, 2026:

| Check                                      | Result                                                      |
| ------------------------------------------ | ----------------------------------------------------------- |
| Java 21 `./mvnw --batch-mode clean verify` | 235 tests across 32 suites; zero failures, errors, or skips |
| Migration tests (included above)           | 4 passed, H2 MySQL mode                                     |
| JaCoCo instructions                        | 98.55% (3750/3805)                                          |
| JaCoCo branches                            | 94.44% (153/162)                                            |
| JaCoCo lines                               | 98.30% (1039/1057)                                          |
| JaCoCo methods / classes                   | 98.77% (241/244) / 100% (73/73)                             |
| `npm ci`                                   | Passed; 299 packages installed                              |
| `npm run lint -- --max-warnings=0`         | Passed                                                      |
| `npm run test:coverage`                    | 291 tests in 25 files passed                                |
| Frontend statements / branches             | 100% (766/766) / 100% (442/442)                             |
| Frontend functions / lines                 | 100% (200/200) / 100% (725/725)                             |
| `npm run build`                            | TypeScript and Vite passed; existing >500 kB chunk warning  |
| `npm audit`                                | 0 vulnerabilities                                           |

The first sandboxed Java run failed because Mockito could not attach its agent;
the final run passed with attachment permitted. A concurrent frontend run timed
out under load; the isolated final coverage run passed with unchanged timeouts,
assertions, and thresholds. The audit initially failed on registry DNS access;
the network-enabled retry returned the result above.

The Phase 5 logging regression first reproduced malformed credential text in
Spring HandlerMethod DEBUG output. Pinning that logger at INFO fixed the leak;
the new real-filter-chain integration test passes with broad web DEBUG enabled.
No API contract, migration SQL, dependency version, or authentication policy changed
in Phase 5. Deployment, target MySQL rehearsal, hosted logging review, and real
browser accessibility remain separate checks, not implied by these measurements.

## Issue #19 (v1.3.0) custom categories

Automated coverage (`npm run test:coverage`):

| Area | Tests |
| --- | --- |
| Icon registry matches the backend catalog; unknown, prototype, and markup keys fall back to `tag` | `categoryIconRegistry.test.ts` |
| Shared state: lazy load, retry, refresh after changes, refresh-failure warning, stale responses after an account change, previous user never rendered, sign-out clearing | `CategoryProvider.test.tsx` |
| Selector: built-in, custom, and saved "Other" options; separate create option; name and icon fields; keyboard icon choice; loading, failure, retry; error associations | `CategorySelect.test.tsx` |
| Management: no controls on built-ins; rename and icon change; duplicate, missing, built-in, and in-use responses; delete confirmation by keyboard; focus restoration; announcements | `CategoryManager.test.tsx` |
| Transactions and budgets: exact-one payloads, new category on create and edit, immediate reuse, server `amount` / `monthlyLimit` / `newCategory.name` / `newCategory.iconKey` messages beside their controls with focus and clearing, duplicate handling, refresh-failure warning, double-submit guard, filters, icons and fallbacks, long names | `TransactionPage.categories.test.tsx`, `BudgetPage.categories.test.tsx` |
| Dashboard icons beside names without changing amounts or statuses | `DashboardPage.test.tsx` |

Manual check (desktop, 375 px, and 200% zoom): create a transaction with a new category and
icon, confirm it appears in the budget form and both filters, rename and re-icon it from
the Categories page (management moved there in issue #100), try deleting it while in use
(refused), then delete an unused one.
Icon grids should wrap, long names should wrap, and nothing should scroll horizontally.

### Issue #19 local manual verification (October 2, 2026)

Performed by the maintainer in a desktop browser against the local backend and local
MySQL (V6 applied), not staging:

- Created a custom category from the transaction and budget forms, renamed it and changed
  its icon in "Manage categories", and deleted an unused custom category.
- Deleting a category used by a transaction was refused with the in-use message, which
  closed after about 3 seconds while the category stayed listed.
- Duplicate names were refused, including a different-case match, with the "Use existing
  category" option shown. Two categories may share an icon.
- Created, edited, and deleted transactions and monthly budgets; each showed its
  confirmation banner.
- Confirmation banners closed after about 3 seconds, stayed while hovered, and the
  dismiss button was reachable with Tab.
- The dashboard showed current-month expense activity by category, and the monthly budget
  cards updated their amounts and progress bars.
- Built-in categories showed no edit or delete controls, and category icons line up with
  the text in tables, cards, and the management list.
- Regression: changing the date format and transactions-per-page preferences, the display
  name, and the first and last name still worked.

This is not a staging smoke test; the staging checklist in
[deployment.md](deployment.md#v130-staging-smoke-checklist) is still pending.

### Issue #19 manual accessibility and responsive checklist

Items not ticked have not been performed yet. Record evidence as described above.

- [ ] Keyboard only: create a transaction and a budget with a new category, including
      choosing an icon with arrow keys, then save.
- [ ] Icon picker: the group is announced as "Icon", each radio by its name, the checked
      state is clear without color, and focus is visible.
- [ ] Duplicate recovery: the name error is announced with the field, and "Use existing
      category" selects it and keeps the other values.
- [ ] Rename and re-icon on the Categories page; focus returns to the Edit button.
- [ ] In-use delete refusal is announced; focus returns to the delete button.
- [ ] Cancel edit and keep-category return focus to the button that opened them; a
      completed delete moves focus to the "Hide categories" toggle.
- [ ] Both "Filter by category" selects work with the keyboard; empty states are clear.
- [x] Confirmation banners stay while hovered or focused, close after about 3 seconds
      otherwise, and the dismiss button is reachable with the keyboard. The in-use delete
      refusal closes after 3 seconds; other errors and warnings stay. (October 2, 2026, local)
- [ ] Screen reader pass (VoiceOver/Safari and NVDA or JAWS/Chrome if available):
      icons are not announced, names are, and each confirmation banner is read once.
- [ ] 200% zoom and 320/375 px widths: icon grid and long names wrap, no horizontal
      scroll, management controls remain reachable.
- [ ] Reduced motion setting: no unexpected animation.

## Issue #95 (v1.3.0) responsive sidebar navigation

Signed-in pages use an application shell (`AppLayout`); Login, Register, and the session
restoration screens use `PublicLayout` and never show the sidebar or drawer.

| Width | Navigation |
| --- | --- |
| Above 1100px | Sticky sidebar, 240px expanded or 72px collapsed (icons only, tooltips on hover and keyboard focus). The collapse toggle sits beside the brand; when collapsed, hovering the "F" or focusing the toggle reveals the expand button. The width changes instantly. |
| 1100px and below | Compact top bar (menu button, brand, account menu) and a native modal `<dialog>` drawer that slides in (no motion with reduced motion). Below 640px the account button shows only the avatar. |

- **Destinations:** Dashboard (`/`, exact match), Transactions, Budgets, and Categories
  (added by issue #100), defined once in
  `navigation.ts`. Profile and Account Settings stay in the account menu, which is in the
  top bar at every size. The current page has `aria-current="page"` and a visible bar.
- **Collapse preference:** `localStorage` key `fintrack:sidebar-collapsed` (`"true"` or
  `"false"`), per device, kept after logout, written only when toggled. Anything else, or
  unavailable storage, means expanded. The drawer ignores it.
- **Drawer:** opens with focus on "Close navigation menu"; closes on that button, Escape,
  a backdrop click, following a link, any route change (including Back and Forward),
  widening past 1100px, and logout or session expiry. Focus returns to the menu button
  when it still exists, and page scrolling is locked only while the drawer is open.

### Automated coverage

| Area | Tests |
| --- | --- |
| Public and signed-in layouts, one `main` and one `header`, skip link, restoration without sign-in links, logout removing the shell, Profile and Settings through the account menu | `App.test.tsx`, `PublicLayout.test.tsx`, `ProtectedRoute.test.tsx` |
| Only the four destinations (Categories since issue #100), current page for each route, query strings and trailing slashes, Dashboard exact match, decorative icons and tooltips | `PrimaryNav.test.tsx` |
| Collapse toggle name, state, focus, saving, storage failures, collapsed names and tooltips, page and account menu not remounted, Back and Forward moving the current-page marker | `AppLayout.test.tsx`, `sidebarPreference.test.ts` |
| Drawer: menu button attributes, `showModal`, initial focus, every close path, focus return, scroll-lock cleanup, desktop resize, browser-initiated close, unmount, independence from the collapse preference | `AppLayout.test.tsx` |
| Account menu behaviour, photo and initials fallback, logout | `AccountMenu.test.tsx`, `AuthProvider.account.test.tsx`, `AccountPhoto.test.tsx` |
| Breakpoint shared by CSS and code, no width animation, reduced-motion drawer rule, collapsed styles scoped to the sidebar | `tests/navigationStyles.test.ts` |

**Limits of jsdom.** jsdom has no `showModal()`, so `src/test/setup.ts` provides a
stand-in that only tracks the open state and the `close` event. Unit tests therefore
cannot prove native focus containment, the inert background, backdrop rendering, CSS
geometry, zoom behaviour, or screen-reader output; those are manual checks below.

### Issue #95 manual verification (October 2, 2026)

Performed by the maintainer in Chrome with DevTools device emulation against the local
backend (not staging):

- Desktop: sidebar layout, current-page marker, sidebar staying in place while the page
  scrolls, collapse and expand, the "F" hover swap, tooltips on hover and keyboard focus
  (to the right), compact brand, preference kept after refresh and logout, no toggle and
  full labels below 1100px, and the collapsed layout at 1102px.
- Narrow layout: top bar at 1100px and 375px (iPhone SE); account menu opening and fitting
  at both widths; Dashboard, Transactions, and Budgets without sideways page scrolling at
  375px (`scrollWidth` 375); tables scrolling inside their cards.
- Drawer: slide-in and dimmed page, focus starting on the close button, Tab contained in
  the drawer, page behind not scrollable or clickable, and closing with Escape, the close
  button, the backdrop, a link, browser Back, and widening past 1100px; scrolling restored
  and focus back on the menu button afterwards.
- Account and session: account menu, Profile and Account Settings links, display-name
  update in the menu, logout to Login from both layouts, no Login/Register flash on
  refresh, skip link first with visible focus outlines, and 200% zoom switching to the
  narrow layout.

A Dashboard overflow found at 375px (a grid column stretched by the Recent Transactions
table) was fixed during Phase 3 by using `minmax(0, …)` columns.

### Issue #95 checks still outstanding

- [x] 320px, 768px, 1024px, 1280px, and 1440px widths, and the drawer at 200% zoom
      (scripted Chromium pass, October 9, 2026; see Issue #103 manual checks)
- [ ] Screen reader pass (VoiceOver): link names, toggle and menu-button state, dialog
      name, current page, no duplicate tooltip announcements (the accessibility tree was
      checked on October 9, 2026: single link names, `aria-current="page"`, expanded
      states, modal `dialog` "Navigation menu"; spoken output still unchecked)
- [x] Reduced motion: drawer appears without sliding (October 9, 2026)
- [ ] A long display name: truncated with an ellipsis in the top bar, shown in full in the
      open account menu (CSS added in Phase 4)
- [ ] Budgets charts redraw once after collapsing or expanding the sidebar
- [ ] Windows High Contrast or forced colours: current-page bar still visible

## Issue #100 (v1.3.0) Categories page

Behaviour is documented in [categories.md](categories.md#categories-page-issue-100) and
the endpoint in [categories-api.md](categories-api.md#get-apicategoriessummary).

### Automated coverage

| Area | Tests |
| --- | --- |
| Summary endpoint: authentication, ownership, ordering, month boundaries, income versus spending, budget counts and metrics, dashboard parity, decimals, fixed query count, MySQL aggregation | `CategorySummaryServiceTest`, `CategoryControllerTest`, `CategorySummaryIntegrationTest`, `CategorySummaryQueryTest`, `CategorySummaryQueryCountTest`, `CategorySummaryMySqlIT` |
| Route and navigation (sidebar, collapsed sidebar, drawer) | `App.test.tsx`, `PrimaryNav.test.tsx`, `AppLayout.test.tsx` |
| Loading, load error and retry, empty, populated, summary strip, cards, budget states, links, server month | `CategoriesPage.test.tsx`, `useCategorySummary.test.ts` |
| Create, edit, delete, delete pre-check, in-use and changed-elsewhere errors, refresh warnings, focus after every outcome, active-form protection | `CategoriesPage.test.tsx`, `CategoryForm.test.tsx`, `CategoryDeleteConfirm.test.tsx`, `CategoryProvider.test.tsx` |
| Spending table, shares, rounding note, no-spending state | `CategorySpendingTable.test.tsx`, `categorySummary.test.ts` |
| Search normalization, every filter and sort, ties, no mutation, result count, clear, filtered-empty state | `categoryDiscovery.test.ts`, `CategoriesPage.test.tsx` |
| Notices (roles, labels, dismiss, actions) and page-level errors on Transactions, Budgets, and the Dashboard | `InlineNotice.test.tsx`, `TransactionPage.test.tsx`, `BudgetPage.test.tsx`, `DashboardPage.test.tsx` |
| Deep links (`?category=`, `?addCategory=`), manage panels removed, inline creation kept | `TransactionPage.categories.test.tsx`, `BudgetPage.categories.test.tsx`, `categoryDeepLink.test.ts` |

### Issue #100 manual verification (October 3, 2026)

Performed by the maintainer in Chrome against the local backend (not staging):

- Creating, renaming, re-iconing, and deleting custom categories, with the success banner
  and updated summary figures; in-use Delete shown inactive with its reason.
- Card links: "Set budget" and "Edit budget" opening the Budgets form preselected for the
  month, "Add transaction" opening the form with the category and today's date, and
  "View transactions" opening the filtered history.
- Search, every filter, every sort, and Clear.
- A duplicate name on edit shown once, beside the field, with the typed value kept.
- With DevTools: a delete while offline (error, card and confirmation kept, retry
  succeeding), a blocked summary refresh after an edit (warning, then "Try again"
  clearing it), and a blocked initial load (error with "Try again", no stale data), the
  last at 376px.

### Issue #100 checks still outstanding

- [x] 768px, 1024px, and 1440px widths, and 200% zoom (October 9, 2026)
- [ ] Keyboard-only pass of the toolbar, cards, forms, confirmation, and notices (toolbar,
      card links, section toggles, and "More actions" with Esc checked on October 9, 2026;
      the edit form, delete confirmation, and notices still need a pass)
- [ ] Screen reader pass (VoiceOver): result-count updates, error versus warning
      announcements, spending-table headers
- [x] Reduced motion: the success banner appears without sliding (October 9, 2026)
- [x] The in-use race: a transaction added in another tab before confirming a delete
      (October 9, 2026: refused with "… is still used by transactions or budgets …", card
      kept, focus on "More actions"; the test category and transaction were removed)
- [ ] The restyled page-level errors and refresh warnings on Transactions, Budgets, and
      the Dashboard (changed in Phase 6, covered by tests only)

## Issue #102 (v1.3.0) Categories page refinements

Behaviour is documented in [categories.md](categories.md#categories-page-issue-100) and the
`currentMonthTransactionCount` field in [categories-api.md](categories-api.md#get-apicategoriessummary).

### Automated coverage

| Area | Tests |
| --- | --- |
| Current-month count: income and expense, month boundaries (1st, last day, future-dated, previous and next month excluded), user isolation, zero for unused, all-time delete eligibility unchanged, fixed query count, MySQL `COUNT(CASE …)` | `CategorySummaryQueryTest`, `CategorySummaryServiceTest`, `CategoryControllerTest`, `CategorySummaryIntegrationTest`, `CategorySummaryQueryCountTest`, `CategorySummaryMySqlIT` |
| Card actions disclosure: names and attributes, open and close (trigger, Escape with focus return, outside click, focus leaving), one open at a time, unavailable Delete inert and explained, focus back on the trigger after edit and delete outcomes | `CategoryActionsMenu.test.tsx`, `CategoriesPage.test.tsx` |
| Activity wording (singular, plural, zero, never used, server month), card links as real links with decorative icons | `categoryUsage.test.ts`, `CategoriesPage.test.tsx` |
| No-budget rule, card warning (static, single Set budget link), "No budget" count over every category, refresh behaviour | `categorySummary.test.ts`, `InlineNotice.test.tsx`, `CategoriesPage.test.tsx` |
| Active and Other: membership and partition, each category once, closed by default, toggle and saved preference (invalid and unavailable storage), automatic opening never saved, flat list for search and filter, sorting inside sections, empty Active state, edit draft kept across layout changes, focus after create, edit, and delete | `categorySummary.test.ts`, `categoriesSectionPreference.test.ts`, `CategoryForm.test.tsx`, `CategoriesPage.test.tsx` |
| Page-wide: summary strip, table, and cards agree; heading order, named controls, no menu roles, no positive tab order, static card warnings | `CategoriesPage.test.tsx` |

### Issue #102 manual verification (October 4, 2026)

Performed by the maintainer in Chrome against the local backend (not staging):

- The "⋯" disclosure: panel position, Escape, an unavailable Delete with its reason, focus
  back on "⋯" after Cancel (keyboard), one open at a time, and the panel inside the card at
  375 px.
- Current-month activity on cards, including an income transaction raising the count but
  not "Spent", and Delete's reason still quoting all-time counts.
- The no-budget warning and its Set budget link, without the link icon or accent bar.
- Active this month and Other categories: the sections, the Show/Hide toggle, and the
  layout at 375 px and full desktop width, including the card header, card links, and the
  spending table at 375 px.
- Accessibility and responsive pass: 768 px, 1024 px, 200% zoom, and a screen reader pass
  (VoiceOver) of the "More actions" disclosure, the Other categories toggle, and the card
  warnings.

### Issue #102 checks still outstanding

- [ ] The saved Show/Hide choice surviving a page refresh, and creating a category while
      Other categories is closed (opens, focuses the new card, choice not saved)
- [ ] The README screenshots (`categories.png`, `categories-manage.png`,
      `categories-mobile.png`), deferred to the final v1.3.0 release updates

## Issue #103 (v1.3.0) Categories page for earlier months

Behaviour is documented in [categories.md](categories.md#categories-page-issue-100) and the
API parameters in [categories-api.md](categories-api.md#get-apicategoriessummary).

### Automated coverage (Phase 2, frontend)

| Area | Tests |
| --- | --- |
| URL parsing: plain whole numbers only, partial, empty, text, decimals, signs, leading zeros, ranges, year 2000, repeated and conflicting parameters; available periods, years, months, clamping; other parameters kept | `reportingPeriod.test.ts` |
| Requests: no parameters by default, both together for an earlier month, abort signal | `categoryService.test.ts` |
| Start-up via the default request, current and future months never requested, newer months win over late answers, failures, and aborts, loading not ended by an older request, reload of the chosen month, abort on unmount | `useCategorySummary.test.ts` |
| Selects: labels, options, clamping a future month, Back only for an earlier month, plain buttons, disabled with a reason | `CategoryPeriodControls.test.tsx` |
| Page: current and earlier months from the URL, URL cleanup by replacement, history entries with Back and Forward, Back to current month with focus, loading without stale figures, failure and retry, refresh after a change uses the chosen month, locked controls during a workflow, preference untouched, past-tense wording | `CategoriesPage.test.tsx` |

### Automated coverage (Phase 3, wording and budget links)

| Area | Tests |
| --- | --- |
| Historical wording: subtitle (shown, loading), strip, spending-table intro, cards (singular and plural), warning, filter and sort options, no "this month" anywhere; present tense kept for the current month | `CategoriesPage.test.tsx`, `CategorySpendingTable.test.tsx` |
| Budget links: month, year, and category ID in every Set/Edit link (current month included), one of each parameter with no empty values, names with "for {Month Year}", the warning's link not duplicated, none for categories without budgets, search results and a month switch, no links while a month loads, Back from Budgets returns to the same month without touching preferences | `CategoriesPage.test.tsx`, `categoryDeepLink.test.ts` |
| Budgets month link: linked month selected, that month's budget edited (not this month's), Create with the month preselected, focus on the form heading, out-of-range year offered, refresh, Back/Forward between months, invalid/partial/repeated months dropped with the category applied to today, unknown category dropped with the month kept and no focus moved, month-only link, using the link up after choosing another month, plain `/budgets` unchanged | `BudgetPage.categories.test.tsx` |

### Automated coverage (Phase 4, hardening)

| Area | Tests |
| --- | --- |
| More URL rejections: trailing space, tab, plus sign, repeated identical year; page clean-up of a later year, year only, empty month, padded and signed values, scientific notation, identical repeats (all replaced, nothing invalid sent, Back skips them); parameters in any order with others kept | `reportingPeriod.test.ts`, `CategoriesPage.test.tsx` |
| Races: two quick month choices answering out of order (only the last shown, no error for the cancelled one); a refresh after a change superseded by a newer month | `CategoriesPage.test.tsx`, `useCategorySummary.test.ts` |
| Recovery: a failed month keeps the server-bounded selects and Back to current month, which recovers | `CategoriesPage.test.tsx` |
| Workflows in an earlier month: create and delete refresh that month, the new card appears once and takes focus, focus after a delete lands on a remaining card | `CategoriesPage.test.tsx` |
| Semantics in an earlier month: one `h1`, `h2` order, no `menu` role, one "Showing" status, static warnings; Tab order of the month controls | `CategoriesPage.test.tsx`, `CategoryPeriodControls.test.tsx` |
| Budgets month link: one `h1`, focus moved once and not taken back while typing, nothing saved without a submit | `BudgetPage.categories.test.tsx` |

Backend (Phase 4): first and last days of January, December (into January), and February
in common and leap years, with income counted but never spent and all-time values
unchanged (`CategorySummaryQueryTest`); month 0, a later year, the current month written
out (identical to the default), and January 2000 over HTTP
(`CategorySummaryIntegrationTest`); an earlier month uses exactly the default request's
statement count (`CategorySummaryQueryCountTest`).

Backend (Phase 5): a default request whose clock passes midnight at a month end between
readings still reports one month for `month` and `serverCurrentMonth` (fails on the old
two-reading code with "expected: 10 but was: 11"; `CategorySummaryServiceTest`).

### Issue #103 manual checks

These need a real browser (jsdom has no layout, native select keyboard handling, or
screen reader). On October 9, 2026 a scripted Chromium pass (Playwright, run outside the
repository against the local app and the local demo account) checked layout, keyboard
order, the accessibility tree, reduced motion, and the delete race; VoiceOver itself
still needs a person.

- [x] 320, 375, 768, 1024, 1280, and 1440 px and 200% zoom (720 CSS px at 2×) on
  Categories in September 2026: no page-level horizontal scroll and no element outside
  the viewport; sidebar expanded and collapsed at 1280 and 1440; "More actions" panels
  inside the viewport at 320–1440 px with Esc returning focus. Found and fixed: at 320 px a
  long category name was squeezed beside the icon and "More actions" and broke mid-word;
  a card under 12rem wide now puts the name on its own row (container query; 375 px and
  wider unchanged)
- [x] The same widths on a Budgets month link (`?category=25&month=9&year=2026`): no
  horizontal scroll, focus on "Edit Budget"
- [x] Tab order: Month → Year → Back to current month → Create category → search, filter,
  sort → section toggle → card links → Other toggle; Enter on Back to current month leaves
  focus on Month; Enter on a section toggle flips `aria-expanded` and keeps focus
- [x] Month and Year selects by keyboard (October 9, 2026, by hand): Space or ↓ opens the
  native menu, arrows and Return choose, the month loads, and focus stays on the select;
  Return on Back to current month returns to the current month with focus on Month
- [x] Accessibility tree: one `h1`, `h2` order as documented, budget link names "Edit
  budget for {name} for September 2026", status texts "Showing September 2026" and
  "Showing n of 13 categories", toggles' `aria-controls` targets exist, no `menu` roles,
  no live regions in cards, no exposed icons, spending-table column and row headers,
  sidebar names without duplicated tooltip text, drawer a modal `dialog` "Navigation menu"
- [x] VoiceOver in Safari (October 9, 2026, by hand): "Showing August 2026" and "Showing
  September 2026" each spoken once after switching months; "Back to current month,
  button", "Create category, button", and the create form's "Category name, edit text"
  read as expected. Budget link names, the "Edit Budget" focus target, and toggle states
  were confirmed in the accessibility tree by the scripted pass, not by ear. (Return in the
  create form's name field submits it, as it should; a category created by accident
  during the check was deleted.)
- [x] `prefers-reduced-motion`: the success banner and the mobile drawer have no animation

### Issue #103 checks still outstanding

- [x] Browser check of the selects, Back and Forward, and Back to current month
- [x] Browser check of Set/Edit budget from an earlier month, Back to Categories, and the Budgets form at 375 px
- [x] 375 px and 200% zoom with the selects (scripted pass above)
- [x] VoiceOver pass of the month status (October 9, 2026)
