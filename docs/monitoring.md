# Monitoring

FinTrack's monitoring is built in three phases under issue #20:

1. **Health foundation:** Spring Boot Actuator, health probes, management security, and
   safe per-environment settings.
2. **Metrics and logs:** request and error metrics, authentication-failure counts,
   structured JSON logs, and correlation IDs (sections below).
3. **External monitoring (planned):** an uptime monitor, alerts, and the operations runbook.

No external monitoring service, account, credential, or Railway setting is involved yet.
Metrics stay in memory inside the application and are never sent anywhere.

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

### Known limit

When a request fails with an exception nothing handles, Tomcat also logs it itself, with the
exception message and stack trace and without the correlation ID (it runs after the request
context ends). The application's own `http.request.failed` line carries the ID. Catching every
exception in one handler would remove that second line, but would change the 500 response
format, so it is left for a follow-up.

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
| HTTP timer with route templates (no IDs or query strings), 4xx and 5xx outcomes, the authentication-failure counter per flow and reason (counted once, never for successes, logout, or a missing cookie), connection-pool meters tagged by pool only, no request value in any tag | `MonitoringMetricsIntegrationTest`, `AuthenticationMetricsTest` |
| Correlation IDs: accepted and replaced values, response header on success and errors, logging context set during and cleared after success, handled and unhandled failures, distinct per request, never a credential | `CorrelationIdFilterTest`, `CorrelationAndLogSafetyIntegrationTest` |
| No private value in the logs; failed requests logged once with the ID and class but not the message | `CorrelationAndLogSafetyIntegrationTest` |
| Production JSON line: level, logger, message, correlation ID, request fields, stack trace in one line | `StructuredLoggingFormatTest` |
