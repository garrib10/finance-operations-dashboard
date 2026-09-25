import { removeAuthToken } from "../utils/authToken";

type SessionInvalidationListener = () => void;

const listeners = new Set<SessionInvalidationListener>();

export function invalidateAuthSession(): void {
  removeAuthToken();

  listeners.forEach((listener) => {
    listener();
  });
}

export function subscribeToSessionInvalidation(
  listener: SessionInvalidationListener,
): () => void {
  listeners.add(listener);

  return () => {
    listeners.delete(listener);
  };
}
