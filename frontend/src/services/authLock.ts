/** Serializes refresh-cookie mutations (login, refresh, logout) across this browser's tabs. */
export const AUTH_LOCK_NAME = "fintrack-auth-session";

/**
 * Runs the operation while holding the shared Web Lock. Browsers without Web Locks
 * run it directly; each tab still single-flights its own refreshes. There is no
 * storage-based fallback lock.
 */
export function withAuthLock<T>(operation: () => Promise<T>): Promise<T> {
  const locks = typeof navigator === "undefined" ? undefined : navigator.locks;

  if (!locks || typeof locks.request !== "function") {
    return operation();
  }

  return locks.request(AUTH_LOCK_NAME, { mode: "exclusive" }, () => operation()) as Promise<T>;
}
