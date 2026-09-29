import { ApiError } from "./api";
import { withAuthLock } from "./authLock";
import { getSessionGeneration, invalidateAuthSession } from "./authSession";
import { refreshSession } from "./authService";
import { getAccessToken, setAccessToken } from "../utils/authToken";

export const SESSION_EXPIRED_MESSAGE = "Your session has expired. Please sign in again.";

/** A newer login or sign-out happened while this refresh waited; its result is discarded. */
export const SESSION_SUPERSEDED = "SESSION_SUPERSEDED";

let inFlight: Promise<string> | null = null;
/** Session generation the in-flight refresh started under; it is never shared across sessions. */
let inFlightGeneration = -1;

/** A refresh 401 is a verdict: the family is expired, revoked, or unknown. */
export function isTerminalRefreshFailure(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401 && error.code !== SESSION_SUPERSEDED;
}

function superseded(): ApiError {
  return new ApiError("Your session changed. Please try again.", 401, undefined, SESSION_SUPERSEDED);
}

/**
 * Rotates the refresh cookie and installs this tab's new access token. Concurrent
 * callers in this tab share one request; tabs take turns through the Web Lock. A tab
 * that waited behind another tab's rotation still refreshes for itself, because
 * access tokens are never shared between tabs.
 */
export function refreshAccessToken(): Promise<string> {
  const generation = getSessionGeneration();
  // A refresh begun under an older session (before a login or sign-out) is not reused:
  // it will be discarded, so callers in the current session start their own.
  if (!inFlight || inFlightGeneration !== generation) {
    const current = withAuthLock(async () => {
      if (generation !== getSessionGeneration()) throw superseded();
      const response = await refreshSession();
      if (generation !== getSessionGeneration()) throw superseded();
      setAccessToken(response.accessToken);
      return response.accessToken;
    }).finally(() => {
      if (inFlight === current) inFlight = null;
    });
    inFlight = current;
    inFlightGeneration = generation;
  }
  return inFlight;
}

/**
 * Supplies a token for one retry of a request rejected with ACCESS_TOKEN_EXPIRED.
 * Reuses a newer token another request already installed; otherwise refreshes. A
 * terminal refresh ends the session everywhere; a temporary one ends it here only.
 */
export async function renewAccessToken(expiredToken: string, requestGeneration: number): Promise<string> {
  const current = getAccessToken();
  if (current !== null && current !== expiredToken) {
    return current;
  }
  try {
    return await refreshAccessToken();
  } catch (error) {
    // A superseded refresh always means the session changed, so it never reaches here
    // with a matching generation; the request's own session is already over.
    if (requestGeneration === getSessionGeneration()) {
      if (isTerminalRefreshFailure(error)) {
        invalidateAuthSession("SESSION_TERMINATED");
        throw new ApiError(SESSION_EXPIRED_MESSAGE, 401, undefined, "SESSION_EXPIRED");
      }
      invalidateAuthSession("TEMPORARY_FAILURE");
    }
    throw error;
  }
}
