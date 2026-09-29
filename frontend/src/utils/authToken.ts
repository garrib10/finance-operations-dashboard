/**
 * The access token lives only in this module's memory. It is never persisted, so a
 * reload loses it and the app restores the session through the refresh cookie.
 * The refresh token itself is an HttpOnly cookie that JavaScript cannot read.
 */
let accessToken: string | null = null;

/** Key used by releases that stored the access token in localStorage. */
const LEGACY_TOKEN_KEY = "fintrack_access_token";

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string): void {
  accessToken = token;
}

export function clearAccessToken(): void {
  accessToken = null;
}

/** Deletes a token left by an earlier release without reading, decoding, or reusing it. */
export function removeLegacyAccessToken(): void {
  try {
    window.localStorage.removeItem(LEGACY_TOKEN_KEY);
  } catch {
    // Storage can be unavailable (privacy modes); there is nothing to remove then.
  }
}
