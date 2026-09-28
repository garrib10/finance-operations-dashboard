import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  beginNewSession,
  getSessionGeneration,
  invalidateAuthSession,
  subscribeToSessionInvalidation,
} from "./authSession";
import { publishSessionEvent } from "./sessionBroadcast";
import { getAccessToken, setAccessToken } from "../utils/authToken";

vi.mock("./sessionBroadcast", () => ({
  publishSessionEvent: vi.fn(),
}));

const publish = vi.mocked(publishSessionEvent);

describe("authSession", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setAccessToken("current-token");
  });

  it("clears the token, advances the generation, and notifies listeners", () => {
    const listener = vi.fn();
    const unsubscribe = subscribeToSessionInvalidation(listener);
    const before = getSessionGeneration();

    invalidateAuthSession();

    expect(getAccessToken()).toBeNull();
    expect(getSessionGeneration()).toBe(before + 1);
    expect(listener).toHaveBeenCalledExactlyOnceWith({ reason: "INVALID", origin: "local" });
    expect(publish).not.toHaveBeenCalled();

    unsubscribe();
  });

  it("stops notifying a listener after it unsubscribes", () => {
    const listener = vi.fn();
    const unsubscribe = subscribeToSessionInvalidation(listener);

    unsubscribe();
    invalidateAuthSession();

    expect(getAccessToken()).toBeNull();
    expect(listener).not.toHaveBeenCalled();
  });

  it.each(["LOGOUT", "PASSWORD_CHANGED", "SESSION_TERMINATED"] as const)(
    "broadcasts a local %s to other tabs",
    (reason) => {
      invalidateAuthSession(reason);

      expect(publish).toHaveBeenCalledExactlyOnceWith(reason);
    },
  );

  it.each(["INVALID", "TEMPORARY_FAILURE", "ACCOUNT_CHANGED"] as const)(
    "keeps %s local to this tab",
    (reason) => {
      invalidateAuthSession(reason);

      expect(publish).not.toHaveBeenCalled();
    },
  );

  it("never rebroadcasts an event received from another tab", () => {
    const listener = vi.fn();
    const unsubscribe = subscribeToSessionInvalidation(listener);

    invalidateAuthSession("LOGOUT", "remote");

    expect(publish).not.toHaveBeenCalled();
    expect(listener).toHaveBeenCalledWith({ reason: "LOGOUT", origin: "remote" });
    unsubscribe();
  });

  it("advances the generation when a new session begins", () => {
    const before = getSessionGeneration();

    expect(beginNewSession()).toBe(before + 1);
    expect(getAccessToken()).toBe("current-token");
  });
});
