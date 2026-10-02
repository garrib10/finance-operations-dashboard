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
"Manage categories", try deleting it while in use (refused), then delete an unused one.
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
- [ ] Rename and re-icon from "Manage categories"; focus returns to the edit button.
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
