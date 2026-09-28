import { clearAccessToken } from "../utils/authToken";
import { publishSessionEvent, type SessionBroadcastType } from "./sessionBroadcast";

/**
 * Why the current session ended in this tab. INVALID is a rejected access token
 * that is not worth telling other tabs about; TEMPORARY_FAILURE means refresh could
 * not reach a verdict (network, 503) and the session may still be valid.
 */
export type SessionEndReason = SessionBroadcastType | "INVALID" | "TEMPORARY_FAILURE";

export interface SessionEndEvent {
  reason: SessionEndReason;
  /** "remote" when another tab reported the change. */
  origin: "local" | "remote";
}

type SessionListener = (event: SessionEndEvent) => void;

const listeners = new Set<SessionListener>();
const BROADCAST_REASONS: readonly SessionEndReason[] = ["LOGOUT", "PASSWORD_CHANGED", "SESSION_TERMINATED"];

/** Advances whenever the session changes, so late responses from an old one are ignored. */
let generation = 0;

export function getSessionGeneration(): number {
  return generation;
}

/** Called when a new session begins (login), superseding pending refreshes. */
export function beginNewSession(): number {
  generation += 1;
  return generation;
}

export function invalidateAuthSession(
  reason: SessionEndReason = "INVALID",
  origin: SessionEndEvent["origin"] = "local",
): void {
  clearAccessToken();
  generation += 1;

  if (origin === "local" && BROADCAST_REASONS.includes(reason)) {
    publishSessionEvent(reason as SessionBroadcastType);
  }

  listeners.forEach((listener) => {
    listener({ reason, origin });
  });
}

export function subscribeToSessionInvalidation(listener: SessionListener): () => void {
  listeners.add(listener);

  return () => {
    listeners.delete(listener);
  };
}
