# Profile-photo architecture and security policy

## Phase 3 boundary

Issue #17 now includes authenticated backend upload and removal. V1–V4 and the
Phase 2 image-processing/storage contracts remain unchanged. Frontend controls,
avatar rendering, hosted configuration, and deployment are not part of this phase.

`PUT /api/account/photo` accepts `multipart/form-data` with exactly one file part
named `photo`. Extra files, duplicate parts, text fields, and query parameters are
rejected. Filenames and client MIME claims do not determine content or keys.
`DELETE /api/account/photo` accepts no account identifier or storage reference;
any extra input is ignored. Both operations derive the account exclusively from
the authenticated principal's email, using the existing stateless bearer-JWT
security chain. Neither requires a CSRF token, consistent with the existing API.
Both return 200 with the canonical `UserResponse`; removal returns
`profilePhotoUrl: null`. No `profilePhotoKey` property is serialized. As before,
the public delivery URL necessarily contains its opaque public resource path.

Uploads check feature availability, resolve the account, reject empty/oversized
parts, and read at most the configured 2 MiB limit plus one byte before invoking
the Phase 2 processor. Only its freshly encoded JPEG bytes are uploaded under a
new backend UUID key. No original filename, MIME type, bytes, or metadata is saved
in the database.

Provider upload happens before a short, independent database transaction. That
transaction re-reads and locks the authenticated user's row, replaces the current
key, and constructs the canonical response. The lock serializes concurrent photo
mutations without keeping a database transaction open during provider calls.
After commit, the service best-effort deletes the previous key. Cleanup failure
does not undo or fail a successful replacement.

Validation and provider failures leave the previous database key untouched. If
persistence or commit fails after a successful upload, cleanup of the new object
is attempted and a safe 500 response is returned. No cleanup is attempted after
an unsuccessful provider upload: a collision must not delete an existing object,
and ambiguous network failures can leave orphan objects. There is no durable
cleanup table, retry worker, or background reconciliation in this issue.

Removal clears the key in a short transaction, then best-effort deletes the old
object after commit. Repeated removal succeeds with a null URL. A provider object
that is already absent is treated as removed by the adapter. Cleanup failure does
not restore the key. Both mutations return 503 while the feature is disabled;
existing keys remain untouched in that state.

### HTTP errors and multipart limits

Errors use the existing `ApiErrorResponse` JSON structure:

| Condition | Status |
| --- | --- |
| Missing/empty part, extra/duplicate fields, malformed multipart | 400 |
| Corrupt image, animation, unsafe dimensions | 400 |
| File or multipart request too large | 413 |
| Unsupported image or request content type | 415 |
| Missing/invalid authentication | 401 |
| Disabled feature or provider upload failure | 503 |
| Unexpected database failure | 500 |

Servlet limits remain 2 MiB per file and 3 MiB per request. Tomcat's rejected-body
drain allowance is bounded at 4 MiB so modestly oversized requests can receive
the JSON 413 response. Much larger bodies may be disconnected by the container;
this is not an unlimited draining policy. Real random-port servlet tests exercise
these boundaries in addition to the application-level bounded-read tests.

Application events include operation, internal user ID, and fixed safe categories
for upload/replacement/removal success, provider failure, persistence failure, and
cleanup failure. They omit filenames, storage keys, image bytes, credentials,
provider messages, and exception causes. Framework response-body diagnostics are
kept at INFO, alongside the existing safe exception and provider logging settings,
to avoid exposing account data or photo URLs under broader web DEBUG logging.
No browser-direct uploads, signatures, or remote-URL upload APIs are exposed.

Dependencies added:

- `com.cloudinary:cloudinary-http5:2.4.0`: official Java SDK for upload parameter
  construction, signing, and provider response interpretation. SDK types stay in
  the storage package. Version availability was verified against Maven Central.
- `com.drewnoakes:metadata-extractor:2.21.0`: JPEG EXIF extraction for orientation.
  Only the EXIF reader is selected; no general image/document metadata dispatcher
  runs on user input. Extracted directories are transient and never logged.

No frontend dependency, Apache Tika, image codec plugin, or real-provider test
resource is needed. Java ImageIO supplies JPEG/PNG decoding and JPEG encoding.

## Implemented processing policy

| Policy | Value |
| --- | --- |
| Input formats | Static JPEG and PNG only |
| File size | At most 2,097,152 bytes (2 MiB) |
| Whole multipart request | At most 3,145,728 bytes (3 MiB) |
| Width and height | Each at most 4,096 pixels |
| Decoded pixels | At most 12,000,000 |
| Output | Fresh JPEG, fit within 512 by 512, preserve aspect ratio, no upscaling |
| JPEG quality | 0.85 |
| Metadata | Apply orientation, then discard original metadata, including EXIF/GPS |
| Unsupported | SVG, GIF, WebP, animation, and remote-URL uploads |

`ProfilePhotoProcessor.process(byte[])` rejects null/empty input and lengths above
the configured 2 MiB ceiling. It does not accept filenames, MIME claims, URLs, or
streams. The orchestration service bounds the multipart read before constructing this array;
servlet limits alone do not replace that check.

The inspection sequence is:

1. Verify a JPEG or PNG signature and bounded container structure. PNG chunk
   lengths, CRCs, types, header/end structure, and data ordering are checked.
   APNG `acTL`, `fcTL`, and `fdAT` chunks are rejected explicitly. JPEGs require a
   complete scan/end marker; MPF and concatenated image containers are rejected.
2. Strip PNG ancillary metadata before decoding, preventing compressed text or ICC
   metadata from being inflated merely to obtain raster pixels. Raster palette and
   transparency chunks are retained. Unrelated trailing payload is not passed to
   the decoder.
3. Select an ImageIO reader whose reported format matches JPEG/PNG, inspect width
   and height, and check the pixel product using long arithmetic before allocating
   the full decoded image. Reject multiple-image counts and decoder warnings or
   failures. Readers and memory-only streams are disposed/closed on failure too.
4. For JPEG, extract EXIF orientation. All eight standard rotations/reflections
   are applied to pixels. Absent orientation means normal presentation; malformed
   EXIF or values outside 1–8 are rejected with a sanitized validation error.
5. Resize with bicubic interpolation, preserving aspect ratio within the output
   box and never upscaling. Flatten alpha onto **white (`#FFFFFF`)**. The stored
   image remains rectangular; circular cropping belongs to frontend CSS.
6. Write a fresh RGB JPEG at configured quality 0.85, without copying EXIF, GPS,
   camera details, comments, XMP, or other input metadata. Return defensive copies
   of JPEG bytes with `image/jpeg` and output dimensions. Its string representation
   redacts image content.

The processor has no user, database, provider, or filesystem dependency. Tests
create synthetic images in memory; no photographs or generated output are tracked.
Signature checks alone are not proof of safety. Re-encoding does not detect every
polyglot: protection comes from decoding allowlisted raster pixels and storing
only a newly written JPEG. Keep the JDK and parser dependencies patched.

## Persistence and canonical responses

MySQL stores only `users.profile_photo_key VARCHAR(255) NULL`. No image bytes,
Base64, filenames, MIME types, EXIF, credentials, or signed URLs are stored.
Registration leaves the key null. Backend account behavior will set or clear it;
it is excluded from default entity JSON serialization.

`UserResponseMapper` is the sole canonical mapper for registration, `/api/auth/me`,
profile updates, and preference updates. It returns `profilePhotoUrl`, including an
explicit JSON null. No separate `profilePhotoKey` or provider credential field is
returned. A public Cloudinary URL necessarily includes the public ID in its path;
the ID is not a secret or an authorization credential.

Keys must match the configured namespace followed by a lowercase UUID v4, for
example `fintrack/development/profile-photos/<uuid>`. Namespace segments use
lowercase ASCII letters, digits, and hyphens, separated by single slashes, with a
maximum namespace length of 180. The resulting key fits the 255-character column.
The key generator uses `UUID.randomUUID()` (version 4). Each replacement receives
a fresh UUID and never overwrites an existing key. The existing prefix already
contains `profile-photos`; it is not appended a second time.

The resolver permits only the fixed HTTPS delivery origin `res.cloudinary.com`,
the validated cloud name, and a matching persisted key. It uses an explicit `v1`
delivery path component and the fixed `.jpg` format; the adapter checks that the returned public ID, resource type, and format match
the request. Canonical responses use the same pure resolver, ignoring provider
URLs. A real development-provider smoke test remains necessary before release.
No API endpoint, upload signature, arbitrary host, or remote request is involved.
Invalid or foreign-namespace keys resolve to null without echoing their value.

When disabled, all photo URLs resolve to null, even if a row already contains a
key. The stored key is retained. Initials remain the frontend fallback; the current
frontend is unchanged. Switching namespaces hides old-namespace photos
until an intentional migration is performed; do not casually change a namespace.

## Backend configuration and environment separation

| Environment variable | Purpose |
| --- | --- |
| `PROFILE_PHOTOS_ENABLED` | Defaults to false |
| `CLOUDINARY_CLOUD_NAME` | Required valid cloud identifier when enabled |
| `CLOUDINARY_API_KEY` | Required backend credential when enabled |
| `CLOUDINARY_API_SECRET` | Required backend secret when enabled |
| `PROFILE_PHOTO_KEY_PREFIX` | Required environment namespace when enabled |

Disabled support starts without credentials. Enabled support requires all provider
settings and rejects incomplete configuration at startup with a sanitized
validation message. Credential validation uses boolean aggregate constraints so
rejected secret values are not included in binding errors. The configuration's
string representation is fully redacted. Do not log configuration values or
provider request/response dumps.

Numeric settings under `app.profile-photo` have the approved defaults and reject
nonpositive or above-policy values, invalid quality, request limits not exceeding
file limits, and output dimensions exceeding input dimensions. They may be
restricted further. If changing byte limits, keep the servlet multipart limits
aligned; application-level checks remain required. JPEG and PNG are a fixed
allowlist, not an environment-expandable list.

Later hosted setup supplies credentials only to Railway, never to Vercel or a
`VITE_` variable. No new frontend secret is needed. Local development must use
ignored local configuration and a dedicated development namespace/environment.
Production and nonproduction should use separate provider environments and
credentials; prefixes alone are not an authorization boundary. Tests use synthetic
configuration and fake/mocked storage, never actual provider accounts.

Do not commit real credentials, uploaded photographs, or provider debug logs.
Rotate credentials by deploying replacement credentials, verifying later storage
operations, and then revoking the old credentials. No cloud resources or hosted
environment variables have been created for this phase.

## Cloudinary operations and safe failures

When enabled, `CloudinaryProfilePhotoStorage` uses backend credentials and fresh,
namespace-validated keys. Uploads fix `resource_type=image`, `type=upload`,
`format=jpg`, `overwrite=false`, `use_filename=false`, `unique_filename=false`,
and `backup=false`. Multipart filenames are the fixed `photo.jpg`. Existing or
unexpectedly overwritten results are safe collision failures, not successes.
Missing/mismatched responses also fail safely. No provider result map escapes.

Deletion uses the persisted key and requests CDN invalidation; both `ok` and
`not found` are success. Other results and exceptions become application-owned
exceptions without SDK messages or nested causes. No adapter/processor logs image
bytes, configuration, metadata, or provider responses. Cloudinary and Apache HTTP5
client/core loggers are disabled to prevent wire/header diagnostics from exposing
credentials or multipart image data when broader DEBUG logging is enabled.

The SDK's default transport does not expose automatic-retry controls. A small
`AbstractUploaderStrategy` implementation uses the SDK signing/response helpers
with Apache HTTP5 retries and redirects disabled. Connect, socket, pool-acquisition,
and response timeouts are 10 seconds, and provider response reads are capped at
64 KiB. These are per-stage/inactivity bounds, not a guaranteed total wall-clock
deadline. Client resources close with the Spring bean. No automatic application
retry is performed, including after ambiguous timeouts. The caller retains the
new generated key for cleanup after a confirmed upload followed by persistence failure.

When disabled, only `DisabledProfilePhotoStorage` is selected: no Cloudinary client
is initialized, URLs remain null, and mutations explicitly throw a DISABLED storage
exception. Account photo endpoints map this to 503. There is no filesystem fallback or
no-op upload that claims success. All current tests use mocks, in-memory fixtures,
and synthetic configuration; none calls Cloudinary.

## Public delivery and consistency limits

Delivery is public: anyone who possesses a photo URL can view it. Random keys
reduce enumeration but do not provide access control. There is no public listing
or gallery. Later UI instructions must make this privacy behavior clear. Removal
cannot recall downloaded copies, and provider/CDN deletion may not be immediate.

The workflow above cannot make database and cloud storage changes atomic.

The current issue scope does not include durable cleanup tables, upload-intent
state machines, background workers, or rate limiting. Best-effort cleanup can
leave orphaned objects after crashes, timeouts, or failed deletion; document and
review them operationally in later phases. Durable cleanup and upload throttling
are possible future hardening, not guarantees of this foundation.

## Verification boundaries

Focused tests cover image processing, orientation, boundary/corruption cases,
provider and transport mocks, configuration, URL safety, serialization, entity
persistence, canonical API mapping, and H2 clean/V3-upgrade migrations. Phase 3
adds service/controller tests and random-port HTTP tests for multipart limits,
JWT ownership, rollback, cleanup failures, and concurrent replacement. Storage is
mocked; no provider call is needed. Before release, rehearse V4 on the target MySQL version in an isolated
database and verify the real provider delivery convention. H2 is not proof of
MySQL deployment compatibility. Final deployment and README documentation belong
to Phase 5.
