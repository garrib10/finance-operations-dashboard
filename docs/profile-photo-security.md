# Profile-photo architecture and security policy

## Phase 2 boundary

Issue #17 extends the existing account feature. V4, entity support, and canonical
mapping remain unchanged. Phase 2 adds an in-memory image processor, a UUID key
generator, and a conditional Cloudinary adapter. There are still no upload/remove
endpoints, authenticated replacement workflows, frontend controls, or hosted
configuration changes. Enabling the foundation does not expose an upload API.

`ProfilePhotoStorage` keeps the Phase 1 byte-array contract. Later orchestration
must call `ProfilePhotoProcessor` and pass only its freshly encoded JPEG bytes to
storage, never the original request bytes. The adapter's JPEG envelope check is a
misuse guard, not a second untrusted-file validator. No caller supplies a filename,
remote URL, user-selected public ID, or transformation instructions.

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
streams. Phase 3 must bound the multipart read before constructing this array;
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
new generated key so Phase 3 can attempt cleanup/reconciliation safely.

When disabled, only `DisabledProfilePhotoStorage` is selected: no Cloudinary client
is initialized, URLs remain null, and mutations explicitly throw a DISABLED storage
exception. Later endpoints can map this to 503. There is no filesystem fallback or
no-op upload that claims success. All current tests use mocks, in-memory fixtures,
and synthetic configuration; none calls Cloudinary.

## Public delivery and future consistency behavior

Delivery is public: anyone who possesses a photo URL can view it. Random keys
reduce enumeration but do not provide access control. There is no public listing
or gallery. Later UI instructions must make this privacy behavior clear. Removal
cannot recall downloaded copies, and provider/CDN deletion may not be immediate.

Later upload and replacement behavior must preserve the saved photo on validation
or upload failure. Use bounded synchronous provider operations, attach the new
reference in a short database transaction, and delete the old object only after
commit. Compensate a failed database update with best-effort deletion of the new
object. Removal clears the reference safely before remote cleanup and remains
idempotent. Database transactions cannot roll back cloud storage.

The current issue scope does not include durable cleanup tables, upload-intent
state machines, background workers, or rate limiting. Best-effort cleanup can
leave orphaned objects after crashes, timeouts, or failed deletion; document and
review them operationally in later phases. Durable cleanup and upload throttling
are possible future hardening, not guarantees of this foundation.

## Verification boundaries

Focused tests cover image processing, orientation, boundary/corruption cases,
provider and transport mocks, configuration, URL safety, serialization, entity
persistence, canonical API mapping, and H2 clean/V3-upgrade migrations. No provider call is
needed. Before release, rehearse V4 on the target MySQL version in an isolated
database and verify the real provider delivery convention. H2 is not proof of
MySQL deployment compatibility. Final deployment and README documentation belong
to Phase 5.
