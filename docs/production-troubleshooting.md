# Production troubleshooting

What to do when the uptime monitor alerts or something looks wrong in production. Signals,
endpoints, and alert settings are described in [monitoring.md](monitoring.md); deployment and
rollback in [deployment.md](deployment.md).

Ground rules for every incident:

- Never paste or share database URLs, passwords, JWT secrets, Cloudinary keys, tokens, or
  cookies, in chat, issues, or notes.
- Never search logs for a user's email, transaction text, or amounts; use correlation IDs,
  routes, times, and exception class names.
- Production shows only health status; detailed health and metrics are local-only.
- Roll back only through the documented process in [deployment.md](deployment.md#rollback).

## Public health failure

The monitor reports `/api/health` failing twice in a row.

1. **Confirm it** from a second place: open the URL in a browser, or
   `curl -sS --max-time 30 https://finance-operations-dashboard-production.up.railway.app/api/health`.
   Check the provider's status page in case the monitor itself is failing.
2. **Railway service:** is it running, restarting, or crashed? Is a deployment in progress or
   failed?
3. **Startup logs** of the latest deployment: look for ERROR lines about Flyway migrations,
   missing variables (`DB_URL`, `JWT_SECRET`, …), the port (`PORT`), or Hibernate validation.
4. **Database:** after v1.3.0, `/actuator/health/readiness` returning `503` points to the
   database; check the Railway MySQL service. Never copy its credentials anywhere.
5. **Recent errors:** search the logs for `http.request.failed` around the alert time.
6. **Did it start with a deploy?** Compare the alert time with Railway's deployment history.
   If yes and it is not recovering, roll back (deployment.md).
7. **Recovered:** confirm the recovery email and the app working, then note the time, cause,
   and fix.

## Elevated server errors (5xx)

1. **Time window:** when did `http.request.failed` lines start, and are they still appearing?
2. **Which routes:** the `route` field (for example `/api/budgets/{id}`) shows whether one
   feature or everything is failing. Many routes at once usually means the database or a
   deploy; one route usually means a bug in that feature.
3. **Which exception:** the `exception` field and the message-free stack trace name the class
   and the code location.
4. **One request:** if a user reports an error, ask for the `X-Correlation-ID` of the failed
   response and follow the correlation-ID steps below.
5. **Database and pool:** readiness `DOWN`, or (locally) `hikaricp.connections.pending` and
   `.timeout` rising, point to the database or connection limits.
6. A single failing request with a clear cause is a bug to fix in the normal way, not an
   outage.

## High latency

1. Remember the first request after a quiet period is slow (about 9 s measured) while the
   service wakes; that is not an incident on its own.
2. If it persists: which routes are slow (locally, `http.server.requests` max and mean per
   `uri`)? One route points to that feature's queries.
3. Connection-pool pressure (`hikaricp.connections.pending` above 0 for long) or slow
   database responses point to the database.
4. Check Railway's CPU and memory graphs for the service.
5. Avoid changing infrastructure or resources until the cause is clear.

## Database health failure

1. Readiness returns `503 {"status":"DOWN"}` (public, no details), and requests fail.
2. Check the Railway MySQL service status and recent changes to its variables.
3. Check whether a recent deploy ran a migration that failed (Flyway errors in startup logs).
4. Locally (never in production), detailed health shows the `db` component and the pool
   metrics show exhaustion.
5. Tests use H2 and Testcontainers MySQL; production is MySQL only, so an H2-only success
   does not prove production health.
6. Never log, paste, or share connection strings or passwords while investigating.

## More authentication failures

1. `fintrack.authentication.failures` counts by `flow` and `reason` only; it can never show
   who. Do not try to identify users from it.
2. Read the pattern:
   - `access_token` / `expired` is normal (tokens last 5 minutes, the app refreshes them);
   - many `login` / `invalid_credentials` may be guessing;
   - many `refresh_token` / `reused` may mean stolen refresh tokens (each reuse already ends
     that session family).
3. A spike right after a deploy may be a configuration change: check that `JWT_SECRET` and
   `JWT_EXPIRATION_MS` did not change unexpectedly (changing the secret signs everyone out,
   which is expected for a rotation).
4. If it looks like abuse, record the time window and counts and escalate (for example to
   Railway support or by adding rate limiting), without sharing credentials or user data.

## Finding one request by correlation ID

1. Get the `X-Correlation-ID` value from the failed response (browser DevTools → Network →
   the request → response headers), or send your own ID when reproducing.
2. Search Railway's service logs for that value: every line of that request has it in the
   `correlationId` field, including `http.request.failed`.
3. Read the route, status, exception class, and stack frames from those lines.
4. A correlation ID is only a label for finding log lines. It proves nothing about who sent
   the request and never grants access.

## Probable false alarms

Common causes of a single failed check:

- a Railway deployment in progress (new version starting);
- a short network, DNS, or TLS problem between the provider and Railway;
- an incident at the monitoring provider (check their status page);
- the slow first request after an idle period;
- staging sleeping or being torn down (staging is not monitored continuously).

The two-failure rule filters most of these. **Repeated failures are always investigated**,
never dismissed as false alarms without checking Railway's status and logs.
