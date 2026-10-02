## Summary

<!-- What does this PR change, and why? One or two sentences. -->

Closes #

## Changes

<!-- Group by area if helpful (Backend, Frontend, Database, Docs). Delete empty sections. -->

-

## Database migrations

<!-- Applied migrations (V1 and up) are immutable: add a new version instead of editing one. -->

- [ ] None
- [ ] New migration: `V__` (tested on H2 and on MySQL with Testcontainers)

## Testing

<!--
Status: ✅ Pass · ❌ Fail · ⏭️ N/A · ⏳ Pending
Every N/A or Pending needs a reason. Do not mark manual checks as passed unless they were performed.
-->

| Test type | Status | Reason / notes |
| --- | --- | --- |
| Backend unit + integration (`./mvnw clean verify`) | | |
| MySQL integration tests (Docker running, not skipped) | | |
| Frontend tests + coverage (`npm run test:coverage`) | | |
| Lint, TypeScript, and build | | |
| Dependency audit (`npm audit`) | | |
| Manual testing (local) | | |
| Regression (existing features still work) | | |
| Accessibility (keyboard, screen reader, zoom) | | |
| Responsive (phone widths) | | |
| Security review (auth, ownership, secrets, error bodies) | | |
| Staging smoke test | | |

## Screenshots

<!-- For UI changes: before and after. Delete this section otherwise. -->

## Deployment notes

- [ ] No new environment variables
- [ ] New or changed variables are listed here and in `docs/deployment.md`
- [ ] Deploy order: Railway (backend) before Vercel (frontend)

## Not in this PR

<!-- Follow-up work, known limitations, or anything deliberately left out. -->

-
