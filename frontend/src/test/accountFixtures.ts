import { vi } from "vitest";
import type { UserResponse } from "../types/auth";

export const accountUser: UserResponse = {
  id: 1,
  firstName: "Demo",
  lastName: "User",
  displayName: "River Walker",
  email: "demo@fintrack.dev",
  createdAt: "2026-09-25T12:00:00",
  preferences: { dateFormat: "MEDIUM", transactionPageSize: 10 },
};

export function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

export function accountContext(user = accountUser) {
  return {
    user, isAuthenticated: true, isLoading: false, restorationError: null,
    login: vi.fn(), logout: vi.fn(), retrySessionRestore: vi.fn(),
    updateProfile: vi.fn().mockResolvedValue(user), updatePreferences: vi.fn().mockResolvedValue(user),
  };
}
