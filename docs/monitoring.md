# Monitoring

FinTrack's monitoring is built in three phases under issue #20:

1. **Health foundation:** Spring Boot Actuator, health probes, management security, and
   safe per-environment settings.
2. **Metrics and logs:** request and error metrics, authentication-failure counts,
   structured JSON logs, and correlation IDs (sections below).
3. **External monitoring:** the uptime monitor and alert setup, signals, and the operations
   runbook ([production-troubleshooting.md](production-troubleshooting.md)). The live monitor
   was configured by the owner in Better Stack on 2026-10-11 (see below).

The application has no monitoring dependency, account, credential, or Railway setting: the
external monitor only calls the public health URL. Metrics stay in memory inside the
application and are never sent anywhere.

## Endpoints

| Path | Who can call it | Response | Profiles |
| --- | --- | --- | --- |
| `GET /api/health` | anyone | `{"status":"ok","application":"Finance Operations Dashboard"}`, unchanged from before | all |
| `GET /actuator/health/liveness` | anyone | `{"status":"UP"}`: the process is running | all |
| `GET /actuator/health/readiness` | anyone | `{"status":"UP"}`, or `{"status":"DOWN"}` with `503` when the database is unreachable | all |
| `GET /actuator/health` | valid access token | development: overall status with each component (`db`, `livenessState`, `readinessState`) and safe details; production: `{"groups":["liveness","readiness"],"status":"UP"}` only | all |
| `GET /actuator/health/{component}` | valid access token | development: that component; production: `404` | all |
| `GET /actuator/metrics`, `/actuator/metrics/{name}` | valid access token | metric names and values | development and test only (`404` in production) |
| `GET /actuator/info` | valid access token | `{}` | development and test only (`404` in production) |
| Any other `/actuator/...`, including `/actuator` itself | nobody | `401` without a token, `404` with one | all |

`/api/health` keeps its exact contract because the README, the deployment checklist, and any
Railway health check already use it. It only shows that the application answers; it does not
check the database. For an external uptime monitor (Phase 3), use
`/actuator/health/readiness`, which also fails when the database is down.

Public responses never include components, details, database names, URLs, usernames,
versions, hostnames, paths, error messages, or any user data. When the database is down,
readiness says only `DOWN`; the reason stays private.

## Why detailed health and metrics are not public

Registration is open and every signed-in user is an ordinary customer: there is no
administrator or operator role. "Authenticated" therefore cannot mean "allowed to see
operational data" in production. So:

- **Production** (`prod` profile, used on Railway for staging and production) shows health
  status to everyone and nothing more, even with an access token. Metrics and info are
  disabled. Detailed diagnosis happens locally, or through Phase 2 and 3 tooling.
- **Local development and tests** show component details and metrics to anyone with a valid
  access token. Locally, that is the developer.

A future operator role could unlock detailed health in production by setting
`management.endpoint.health.roles`. This phase does not invent one.

## How access is decided

Management endpoints use the same rules as the rest of the API, with no separate login,
management user, or password:

- Only a valid bearer **access token** counts. An invalid one gets `401
  AUTHENTICATION_REQUIRED`, and an expired one gets `401 ACCESS_TOKEN_EXPIRED`.
- The **refresh cookie** grants nothing here. It is read only by `/api/auth/refresh` and
  `/api/auth/logout`, so a request that carries only the cookie is treated as signed out.
- Only the two probe paths (`GET`, exact paths) are listed as public in `SecurityConfig`.
  Everything else under `/actuator` falls under the same "authenticated" rule as the
  business API, so adding an endpoint later can never make it public by accident.
- Unauthenticated requests get the usual JSON `401` before anything else, so the response
  never reveals which management endpoints exist.

## Health model

| Group | Includes | Why |
| --- | --- | --- |
| liveness | the process's liveness state | restarts are only for a stuck process, never for a database blip |
| readiness | readiness state and the database (`db`) | traffic should only be sent when the database answers |

The database check is Spring Boot's own `DataSourceHealthIndicator` (a JDBC `isValid()`
call). It reads no data and changes nothing. Its details, shown only in development, are the
database product name and validation query: never the URL or credentials. The disk-space
check is turned off, because it means little on a Railway container and its details include
filesystem paths.

## Metrics

Actuator brings Micrometer with an in-memory registry. No registry for an external system
(such as Prometheus) is added until Phase 3 chooses what will read the metrics. In
production the metrics endpoint is off; locally, read them with an access token at
`/actuator/metrics/{name}`.

### HTTP requests (built in)

`http.server.requests` is a timer (count, total and maximum duration, in seconds) recorded
by Spring for every request. Its tags are all bounded:

| Tag | Values |
| --- | --- |
| `uri` | the route template, such as `/api/transactions/{id}`, never the real path, IDs, or query string; `UNKNOWN`/`NOT_FOUND`-style fallbacks for unmatched routes |
| `method` | the HTTP method |
| `status` | the response status code |
| `outcome` | `SUCCESS`, `CLIENT_ERROR`, `SERVER_ERROR`, … (use `SERVER_ERROR` to spot 5xx) |
| `exception` | the exception class name, or `none` |
| `error` | the error class name, or `none` |

No custom request counter or timer is added; this one already gives volume, latency, and
server errors per route.

### Authentication failures (custom)

`fintrack.authentication.failures` is a counter (unit `failures`) with exactly two tags:

| `flow` | `reason` | When |
| --- | --- | --- |
| `login` | `invalid_credentials` | wrong email or wrong password (never which one) |
| `access_token` | `missing` | a protected request with no bearer token |
| `access_token` | `invalid` | a bearer token that is not valid (bad signature, garbage, or a deleted account) |
| `access_token` | `expired` | a correctly signed token past its 5-minute lifetime (the frontend then refreshes) |
| `refresh_token` | `malformed` | a refresh cookie that is unreadable or sent twice |
| `refresh_token` | `invalid` | a well-formed refresh token the server does not know |
| `refresh_token` | `expired` | a refresh token whose session is past its lifetime |
| `refresh_token` | `revoked` | a refresh token whose session was ended (logout, password change, earlier reuse) |
| `refresh_token` | `reused` | an already-used refresh token, which ends its session family |

Each failure is counted once, where it is decided: login in `AuthService`, access tokens in
the 401 handler (so a bad token on a public endpoint is not counted), and refresh tokens in
`RefreshSessionService`. Not counted: successful sign-in, refresh, and logout; a refresh
with no cookie at all (every signed-out visit makes one); and server errors (those show in
`http.server.requests`). A metrics failure never changes an authentication response. Tag
values come from fixed enums in `AuthenticationMetrics`.

### Database and connection pool (built in)

`hikaricp.connections.active`, `.idle`, `.pending`, `.timeout`, `.max`, `.min`,
`.creation`, `.acquire`, and `.usage` are registered automatically, tagged only by `pool`
(the pool name). Nothing polls or runs extra SQL to produce them. Database connectivity
itself is the `db` health component (above).

### Labels that are never allowed

No metric tag may hold a user ID, email, name, token, cookie, authorization header, IP
address, correlation ID, transaction/budget/category ID or name, description, amount, raw
URL, query string, exception message, or stack trace. `MonitoringMetricsIntegrationTest`
sends such values in requests and checks that none appears in any registered tag.

## Logs

### Format

| Profile | Format |
| --- | --- |
| production (`prod`) | one JSON object per line, Spring Boot's built-in Logstash format (`logging.structured.format.console=logstash`), so Railway can read `level` and `message` and keeps stack traces inside the line (capped at 8 KB) |
| local development and tests | Spring Boot's readable text format, with the correlation ID in brackets after the thread |

Fields in a production line: `@timestamp`, `level`, `logger_name`, `thread_name`,
`message`, `correlationId` (during a request), any structured fields of the event (such as
`method`, `route`, `status`, `durationMs`, `exception` on a failed request), and
`stack_trace` when an exception is logged. No dependency was added for this.

### Correlation IDs

- **Header:** `X-Correlation-ID`, on every request and response (including 401, 403, 404,
  and 5xx responses).
- **Accepted:** 1–64 characters of `A-Z a-z 0-9 . _ -`. Anything else (missing, blank,
  longer, spaces, control characters or line breaks, other punctuation, non-ASCII) is ignored
  and a random UUID is used instead; the request itself is never rejected for it.
- **Use:** it is in the logging context (`correlationId`) for every log line written while
  the request runs, and is removed when the request ends, after success or failure, so a
  reused thread never carries it into another request. It is never a credential and never a
  metric tag. The browser does not read it (it is not a CORS-exposed header).
- **Scope:** one request on its servlet thread. Work on other threads would not inherit it;
  the application has none today.
- **Finding a request:** ask the user (or a support tool) for the `X-Correlation-ID` of the
  failed response, or send your own, then search Railway's logs for that value: every line
  of that request, including `http.request.failed`, carries it.

### What is logged

- **Failed requests:** a request that ends in a 5xx or an unhandled exception gets one ERROR
  line, `http.request.failed`, with the method, route template, status, duration, and the
  innermost exception's class name. Never the URL, query string, headers, cookies, bodies,
  or exception message.
- **Routine requests:** logged only at DEBUG (`http.request.completed`), which production
  does not show. Request volume and latency come from the `http.server.requests` metric, and
  Railway records its own HTTP request logs, so there is no second access log.
- **Authentication:** existing safe events (`auth.login.failed category=invalid_credentials`,
  `auth.refresh.rejected category=…`) with no email, password, or token. Counts come from the
  metric above.

Never logged: passwords or hashes, access or refresh tokens, authorization headers, cookies,
emails, names, photo storage keys, transaction descriptions or amounts, budget details,
category names, request or response bodies, and database credentials or SQL.
`CorrelationAndLogSafetyIntegrationTest` runs sign-in, refresh, financial, profile, and
failing requests with distinctive stand-in values and checks that none reaches the logs.

### Production log levels

`root` and the application at INFO; no DEBUG or TRACE. Hibernate SQL at WARN and bound
parameter values off (they hold user data); Spring Security at WARN. Startup, database,
migration, and security failures still appear at WARN or ERROR. The existing targeted
silences (Tomcat cookie parsing, raw Hibernate error text, validation and response-body
diagnostics) remain in every profile.

### Unhandled exceptions

Tomcat would normally log an unhandled exception a second time, with its full message (which
can contain request data) and no correlation ID. That one logger
(`org.apache.catalina.core.ContainerBase.[Tomcat].[localhost].[/].[dispatcherServlet]`) is
switched off by `MonitoringConfig` once the server starts, using the running server's engine
and host names (a properties file cannot express the brackets, and the engine name can vary). Instead, `http.request.failed` carries the stack trace with every message replaced
by `<class> (message removed)`: class names and code locations to find the bug, never the data.
Tomcat's startup and other container logs are unaffected (`ServerErrorLoggingIntegrationTest`
checks this on a real server).

The client receives `500` with the same JSON shape as other API errors and a fixed message,
plus its `X-Correlation-ID` (issue #123):

```json
{"timestamp":"…","status":500,"error":"Internal Server Error","message":"An unexpected error occurred. Please try again."}
```

The body never contains the exception message, its class, a stack trace, or the request path
(`ErrorResponseConfig`). Only the server's internal error dispatch is allowed through
security; a request sent to `/error` directly still needs an access token (`401`). Before
v1.4.0 the secured error page turned these failures into `401` for the client, while logs and
metrics already recorded `500`.

## Signals at a glance

| Signal | Where | Access | Normal | Worth investigating |
| --- | --- | --- | --- | --- |
| Availability | external monitor on `/api/health`; `/actuator/health/liveness` | public | `200`, under a second once warm | 2+ failed checks in a row |
| Readiness (database included) | `/actuator/health/readiness` | public | `200 {"status":"UP"}` | `503 {"status":"DOWN"}` |
| Request volume and latency | `http.server.requests` (count, total, max) per route | access token, local only | steady per route; occasional slow first request after idle | sustained rise in one route's max or mean |
| Server errors | `http.server.requests` with `outcome=SERVER_ERROR`; `http.request.failed` log lines | metric local only; logs in Railway | none or rare | repeated failures on one route, or many routes at once |
| Authentication failures | `fintrack.authentication.failures` by `flow`/`reason` | access token, local only | some `access_token`/`expired` (tokens last 5 minutes) and occasional `login`/`invalid_credentials` | a sudden jump, many `refresh_token`/`reused`, or many logins failing |
| Database | `db` health component | access token, local only | `UP` | `DOWN`, or readiness failing |
| Connection pool | `hikaricp.connections.active`, `.pending`, `.timeout` | access token, local only | `pending` near 0, `timeout` not growing | `pending` stays above 0, `timeout` grows |
| Structured logs | Railway service logs (JSON) | Railway project members | INFO lines; no `http.request.failed` | ERROR lines, especially repeated ones |
| Correlation ID | `X-Correlation-ID` response header; `correlationId` log field | the caller; Railway logs | one per request | the key to find one failed request |
| Deployments | Railway deployment history | Railway project members | success, short build/start | failed or crash-looping deploys |
| Monitor and alert history | the uptime provider's dashboard | the monitor owner | green | any incident |

In production the metrics endpoint is off, so request, error, authentication, and pool numbers
are read locally (or in a future monitoring backend). Production investigation uses the
external monitor, the public probes, Railway logs (searchable by correlation ID), and Railway
deployment history. None of these signals may ever contain passwords, tokens, cookies,
emails, names, amounts, descriptions, category names, or request bodies.

## External uptime monitoring and alerts

**Status:** live since 2026-10-11. The owner created the monitor in their own Better Stack
account (nothing in the repository or on Railway changed). The same day, a test alert from the
monitor was received by email, and the monitor reported the API **Up** on its regular
3-minute checks with no incidents. The provider holds the account and the alert address;
neither is recorded here.

**Provider:** [Better Stack Uptime](https://betterstack.com/uptime), free plan: HTTPS checks
as often as every 3 minutes, email alerts, configurable confirmation before alerting,
recovery alerts, and pause controls, with no code or credentials added to FinTrack.
Alternative: UptimeRobot (free for personal, non-commercial use, 5-minute checks). Use one
provider only. Confirm the current plan limits when signing up; they change over time.

### Production monitor

| Setting | Value | Why |
| --- | --- | --- |
| Name | `FinTrack Production API Health` | names the app and environment; no personal data |
| Type / method | HTTPS, `GET` | |
| URL | `https://finance-operations-dashboard-production.up.railway.app/api/health` | public, minimal, stable since v1.0; no authentication |
| Expected | status `200` (optionally the body keyword `"status":"ok"`) | |
| Interval | 3 minutes (free minimum; 5 is also fine) | quick detection, light load |
| Timeout | 30 seconds | the first request after an idle period took about 9 s (measured 2026-10-11); later ones about 0.2 s |
| Alert after | 2 consecutive failures (Better Stack: confirmation period 3 minutes) | a single failed poll during a deploy or network blip does not alert |
| Recovery | on (recovery period 3 minutes) | the owner knows it is over |
| Notification | email only, to the primary responder (the owner); no call, SMS, or push | |
| Regions | Europe, North America, Asia, Australia (provider default) | one region's network problem does not look like an outage |
| Headers / authentication | none | the check needs no credentials, and none are ever stored in the monitor |
| TLS verification | on | |
| Redirects | follow HTTPS only (none are expected) | |
| Alert destination | `<MONITORING_ALERT_EMAIL>`, the owner's email, set in the provider only | never in the repository |

After v1.3.0 is deployed, `https://finance-operations-dashboard-production.up.railway.app/actuator/health/readiness`
also catches a database outage (`503`) and may replace or join the `/api/health` check. It is
just as public and minimal.

### Staging

Staging exists on Railway (`<staging Railway origin>`; the URL is not recorded in the
repository). It is not monitored continuously. When testing the alert path, a temporary
monitor named `FinTrack Staging API Health` on `https://<staging Railway origin>/api/health` is
the safe target; pause or delete it afterwards. Temporary pull-request deployments are never
monitored.

### Testing the alert path safely

1. Use the monitor's **Send test alert**: proves email delivery without any outage. Done
   2026-10-11: the email arrived and no incident was left open.
2. Optionally, for the full path, point a temporary staging monitor at a path that returns
   `404` (for example `/api/health-check-test`) for two intervals, confirm the alert and
   recovery emails, then delete it.

Never stop production, deploy broken code, or change production settings to test alerts.
Record whether the email actually arrived (screenshots of the monitor settings and the test
email are good evidence for the pull request).

### Ownership and response

This is a single-owner portfolio project, so there is no on-call rotation.

- **Owner:** the repository and application owner, who receives the alerts.
- **Goal (not an SLA):** look within a few hours when an alert arrives during the day.
- **Acknowledge:** in the provider (acknowledging stops repeat notifications), then follow
  [production-troubleshooting.md](production-troubleshooting.md).
- **Planned maintenance:** pause the monitor first, resume it afterwards. Never pause to hide
  an unexplained alert.
- **Escalate** (to Railway support or a rollback) when the failure persists after a restart,
  a deploy is crash-looping, or the database is unreachable.
- **Recovered:** confirm the recovery email, open the app once, and write a short note (time,
  cause, fix) in the pull request or issue that fixed it.

### Railway deployments and false alarms

- A deploy starts the new version (Spring Boot, then Flyway migrations, then traffic), which
  takes tens of seconds. Zero-downtime switching has not been verified, so a check during a
  deploy may fail once. The two-failure rule exists for that.
- If checks keep failing after a deploy, treat it as a failed deploy, not a false alarm:
  check Railway's deployment status and logs (see the runbook).
- A provider incident (their status page), a DNS or TLS blip, or the first slow request after
  an idle period can cause a single failure. Repeated failures are always investigated.

### Retention and privacy

- Railway keeps service logs for a period set by the plan; check the project's plan rather
  than assuming. Do not export or copy logs unless needed for an incident.
- The uptime provider keeps check and alert history for a plan-dependent period.
- Metrics are in memory and reset when the application restarts.
- Logs and metrics are operational aids, not a financial or audit record. Correlation IDs
  are operational metadata, not user identity. Monitor names, tags, and alert texts never
  contain secrets or personal data.

## Configuration

Everything lives in `application.properties` (base and development) and
`application-prod.properties` (production overrides). No environment variable is required.

- `management.endpoints.access.default=none`: every endpoint is off unless switched on
  (`health`, `info`, `metrics`; production keeps only `health`).
- `management.endpoints.web.exposure.include`: an explicit list, never `*`.
- `management.endpoints.web.discovery.enabled=false`: no `/actuator` index.
- `show-components` and `show-details`: `when-authorized` in development, `never` in
  production.
- An invalid value, such as a mistyped `show-details`, stops the application from starting
  rather than falling back silently.

These must never be exposed in any environment: `env`, `configprops`, `beans`, `mappings`,
`heapdump`, `threaddump`, `loggers`, `shutdown`, `scheduledtasks`, `caches`, `conditions`,
`startup`, `sessions`. None of them is switched on.

## Tests

| Area | Tests |
| --- | --- |
| Public health contracts (status only, no leaks), refresh cookie grants nothing, invalid and expired tokens refused, component and metric access with a token, the HikariCP pool registered, unexposed endpoints, the business API unchanged, no external registry | `ManagementEndpointSecurityTest` |
| Database down: readiness `503 {"status":"DOWN"}` with no detail, liveness and `/api/health` still up | `ManagementDatabaseDownTest` |
| Production profile: health only, status alone even with a token, metrics and info absent | `ProductionManagementConfigTest` |
| An invalid setting stops startup | `ManagementConfigValidationTest` |
| Unhandled exception on a real server: logged once with the ID and a message-free stack trace, Tomcat's copy off | `ServerErrorLoggingIntegrationTest` |
| Unhandled exception answered with `500`, a fixed safe body, and the correlation header; `/error` itself still needs a token; signed-in unknown routes get `404` | `ServerErrorLoggingIntegrationTest`, `ErrorResponseConfigTest` |
| Production logging policy: JSON, no SQL values, Security at WARN, INFO levels | `ProductionManagementConfigTest` |
| HTTP timer with route templates (no IDs or query strings), 4xx and 5xx outcomes, the authentication-failure counter per flow and reason (counted once, never for successes, logout, or a missing cookie), connection-pool meters tagged by pool only, no request value in any tag | `MonitoringMetricsIntegrationTest`, `AuthenticationMetricsTest` |
| Correlation IDs: accepted and replaced values, response header on success and errors, logging context set during and cleared after success, handled and unhandled failures, distinct per request, never a credential | `CorrelationIdFilterTest`, `CorrelationAndLogSafetyIntegrationTest` |
| No private value in the logs; failed requests logged once with the ID and class but not the message | `CorrelationAndLogSafetyIntegrationTest` |
| Production JSON line: level, logger, message, correlation ID, request fields, stack trace in one line | `StructuredLoggingFormatTest` |
