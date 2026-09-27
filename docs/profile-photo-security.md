# Profile-photo architecture and security policy

## Phase 1 boundary

Issue #17 extends the existing account feature. This phase adds the V4 nullable
storage reference, configuration, provider-neutral contracts, and canonical user
response mapping only. There are no upload/remove endpoints, provider SDK/network
operations, image processing, frontend controls, or deployment changes yet.

Cloudinary is the approved future provider. `ProfilePhotoStorage` defines storage
of processed JPEG bytes under a fresh backend-generated key, idempotent deletion
by persisted key, and delivery URL resolution through `ProfilePhotoUrlResolver`.
Only a pure URL resolver is registered now; there is no writable storage bean or
filesystem fallback. The adapter and image-processing implementation belong to
later phases. Do not treat enabling the configuration as enabling uploads.

## Approved policy for later upload implementation

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

Future upload code must inspect actual bytes, determine the allowed decoder,
check dimensions before full decoding, bound reads and decoded work, and
re-encode a fresh image. Filename extensions and browser Content-Type are not
proof of content. Only processed output may leave the backend for storage.
Servlet multipart limits are configured now but do not replace those checks.
There is no browser-direct Cloudinary upload or client-submitted storage key/URL.

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
Each later replacement must use a fresh UUID and never overwrite an existing key.

The resolver permits only the fixed HTTPS delivery origin `res.cloudinary.com`,
the validated cloud name, and a matching persisted key. It uses an explicit `v1`
delivery path component and the fixed `.jpg` format; future SDK integration must
verify this convention against the adapter's returned public ID and delivery URL.
No API endpoint, upload signature, arbitrary host, or remote request is involved.
Invalid or foreign-namespace keys resolve to null without echoing their value.

When disabled, all photo URLs resolve to null, even if a row already contains a
key. The stored key is retained. Initials remain the frontend fallback; the current
frontend is unchanged in Phase 1. Switching namespaces hides old-namespace photos
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

Focused tests cover configuration, URL safety, serialization, entity persistence,
canonical API mapping, and H2 clean/V3-upgrade migrations. No provider call is
needed. Before release, rehearse V4 on the target MySQL version in an isolated
database and verify the real provider delivery convention. H2 is not proof of
MySQL deployment compatibility. Final deployment and README documentation belong
to Phase 5.
