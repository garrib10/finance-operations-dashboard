import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  invalidateAuthSession,
  subscribeToSessionInvalidation,
} from "./authSession";
import { removeAuthToken } from "../utils/authToken";

vi.mock("../utils/authToken", () => ({
  removeAuthToken: vi.fn(),
}));

const mockRemoveAuthToken = vi.mocked(removeAuthToken);

describe("authSession", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("removes the token and notifies subscribed listeners", () => {
    const listener = vi.fn();
    const unsubscribe = subscribeToSessionInvalidation(listener);

    invalidateAuthSession();

    expect(mockRemoveAuthToken).toHaveBeenCalledOnce();
    expect(listener).toHaveBeenCalledOnce();

    unsubscribe();
  });

  it("stops notifying a listener after it unsubscribes", () => {
    const listener = vi.fn();
    const unsubscribe = subscribeToSessionInvalidation(listener);

    unsubscribe();
    invalidateAuthSession();

    expect(mockRemoveAuthToken).toHaveBeenCalledOnce();
    expect(listener).not.toHaveBeenCalled();
  });
});
