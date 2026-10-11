# Monitoring

FinTrack's monitoring is built in three phases under issue #20:

1. **Health foundation (this document):** Spring Boot Actuator, health probes, management
   security, and safe per-environment settings.
2. **Metrics and logs (planned):** request and error metrics, authentication-failure counts,
   structured JSON logs, and correlation IDs.
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

Actuator brings Micrometer with an in-memory registry. JVM, HTTP-server
(`http.server.requests`), and HikariCP connection-pool metrics (`hikaricp.connections.*`)
are registered automatically, without custom code. No registry for an external system (such
as Prometheus) is added until Phase 3 chooses what will read the metrics.

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
