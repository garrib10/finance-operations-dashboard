import type { SessionEndEvent } from "../services/authSession";
import { SESSION_EXPIRED_MESSAGE } from "../services/sessionRefresh";

export const PASSWORD_CHANGED_NOTICE = "Your password was changed. Please sign in again.";
export const SIGNED_OUT_ELSEWHERE_NOTICE = "You were signed out in another tab.";

/** Message shown on the login page after a session ends, or null for a quiet sign-out. */
export function noticeFor(event: SessionEndEvent): string | null {
  switch (event.reason) {
    case "SESSION_TERMINATED":
      return SESSION_EXPIRED_MESSAGE;
    case "PASSWORD_CHANGED":
      return PASSWORD_CHANGED_NOTICE;
    case "LOGOUT":
      return event.origin === "remote" ? SIGNED_OUT_ELSEWHERE_NOTICE : null;
    default:
      return null;
  }
}
