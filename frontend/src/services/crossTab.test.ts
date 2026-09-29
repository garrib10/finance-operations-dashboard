import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AUTH_LOCK_NAME, withAuthLock } from "./authLock";
import { SESSION_CHANNEL_NAME } from "./sessionBroadcast";
import { FakeBroadcastChannel, installFakeLocks, removeFakeLocks } from "../test/crossTab";

const fetchMock = vi.fn();

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { "Content-Type": "application/json" },
});

/** A separate module graph: its own in-memory token and refresh state, like another tab. */
async function openTab() {
  vi.resetModules();
  const refresh = await import("./sessionRefresh");
  const token = await import("../utils/authToken");
  const broadcast = await import("./sessionBroadcast");
  const session = await import("./authSession");
  const lock = await import("./authLock");
  return { ...refresh, ...token, ...broadcast, ...session, ...lock };
}

function deferredResponse() {
  let resolve!: (response: Response) => void;
  const promise = new Promise<Response>((done) => { resolve = done; });
  return { promise, resolve };
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  FakeBroadcastChannel.reset();
});

afterEach(() => {
  // Restore globals first: a test may have stubbed navigator itself away.
  vi.unstubAllGlobals();
  removeFakeLocks();
});

describe("Web Locks coordination", () => {
  it("holds the shared lock for the operation and releases it on success and failure", async () => {
    const locks = installFakeLocks();

    await expect(withAuthLock(async () => {
      expect(locks.held).toEqual([AUTH_LOCK_NAME]);
      return "done";
    })).resolves.toBe("done");
    await expect(withAuthLock(async () => { throw new Error("refresh failed"); })).rejects.toThrow("refresh failed");

    expect(locks.request).toHaveBeenCalledTimes(2);
    expect(locks.request).toHaveBeenCalledWith(AUTH_LOCK_NAME, { mode: "exclusive" }, expect.any(Function));
    expect(locks.held).toEqual([]);
  });

  it("runs directly when there is no navigator at all", async () => {
    vi.stubGlobal("navigator", undefined);

    await expect(withAuthLock(async () => "ran")).resolves.toBe("ran");
  });

  it("discards a refresh whose session ended while it waited for the lock", async () => {
    installFakeLocks();
    const tab = await openTab();
    let releaseHolder!: () => void;
    const holder = tab.withAuthLock(() => new Promise<void>((resolve) => { releaseHolder = resolve; }));
    await vi.waitFor(() => expect(releaseHolder).toBeTypeOf("function"));

    const refresh = tab.refreshAccessToken();
    tab.invalidateAuthSession("LOGOUT");
    releaseHolder();
    await holder;

    await expect(refresh).rejects.toMatchObject({ status: 401, code: "SESSION_SUPERSEDED" });
    expect(fetchMock).not.toHaveBeenCalled();
    expect(tab.getAccessToken()).toBeNull();
  });

  it("starts a fresh refresh for a new session instead of joining one from the old session", async () => {
    removeFakeLocks();
    const old = deferredResponse();
    fetchMock
      .mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce(json({ accessToken: "new-session-token", tokenType: "Bearer", expiresIn: 300 }));
    const tab = await openTab();

    const stale = tab.refreshAccessToken();
    tab.beginNewSession();
    const fresh = tab.refreshAccessToken();

    await expect(fresh).resolves.toBe("new-session-token");
    old.resolve(json({ accessToken: "old-session-token", tokenType: "Bearer", expiresIn: 300 }));
    await expect(stale).rejects.toMatchObject({ code: "SESSION_SUPERSEDED" });

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(tab.getAccessToken()).toBe("new-session-token");
    // The late old refresh did not clear the newer one: this call reuses nothing stale.
    fetchMock.mockResolvedValueOnce(json({ accessToken: "next-token", tokenType: "Bearer", expiresIn: 300 }));
    await expect(tab.refreshAccessToken()).resolves.toBe("next-token");
  });

  it("falls back to running directly when Web Locks is unavailable", async () => {
    removeFakeLocks();
    expect("locks" in navigator).toBe(false);

    await expect(withAuthLock(async () => "ran")).resolves.toBe("ran");
  });

  it("makes a second tab wait, then refresh for itself with the rotated cookie", async () => {
    installFakeLocks();
    const first = deferredResponse();
    fetchMock
      .mockReturnValueOnce(first.promise)
      .mockResolvedValueOnce(json({ accessToken: "tab-b-token", tokenType: "Bearer", expiresIn: 300 }));

    const tabA = await openTab();
    const tabB = await openTab();
    const refreshA = tabA.refreshAccessToken();
    const refreshB = tabB.refreshAccessToken();

    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    // Tab B is queued behind tab A's cookie rotation; it has not sent a refresh yet.
    await Promise.resolve();
    expect(fetchMock).toHaveBeenCalledOnce();

    first.resolve(json({ accessToken: "tab-a-token", tokenType: "Bearer", expiresIn: 300 }));

    await expect(refreshA).resolves.toBe("tab-a-token");
    await expect(refreshB).resolves.toBe("tab-b-token");
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(tabA.getAccessToken()).toBe("tab-a-token");
    expect(tabB.getAccessToken()).toBe("tab-b-token");
  });

  it("releases the lock after a failed refresh so the other tab can proceed", async () => {
    const locks = installFakeLocks();
    fetchMock
      .mockResolvedValueOnce(json({ message: "Sign-in is temporarily unavailable.", code: "SESSION_UNAVAILABLE" }, 503))
      .mockResolvedValueOnce(json({ accessToken: "tab-b-token", tokenType: "Bearer", expiresIn: 300 }));

    const tabA = await openTab();
    const tabB = await openTab();

    await expect(tabA.refreshAccessToken()).rejects.toMatchObject({ status: 503 });
    await expect(tabB.refreshAccessToken()).resolves.toBe("tab-b-token");
    expect(locks.held).toEqual([]);
  });

  it("single-flights refreshes within one tab without Web Locks", async () => {
    removeFakeLocks();
    const pending = deferredResponse();
    fetchMock.mockReturnValueOnce(pending.promise);
    const tab = await openTab();

    const refreshes = [tab.refreshAccessToken(), tab.refreshAccessToken(), tab.refreshAccessToken()];
    pending.resolve(json({ accessToken: "only-token", tokenType: "Bearer", expiresIn: 300 }));

    await expect(Promise.all(refreshes)).resolves.toEqual(["only-token", "only-token", "only-token"]);
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});

describe("BroadcastChannel notifications", () => {
  it("delivers events to other tabs with only a type and sender id", async () => {
    vi.stubGlobal("BroadcastChannel", FakeBroadcastChannel);
    const tabA = await openTab();
    const tabB = await openTab();
    const receivedByB = vi.fn();
    const receivedByA = vi.fn();
    const closeA = tabA.openSessionChannel(receivedByA);
    const closeB = tabB.openSessionChannel(receivedByB);
    tabA.setAccessToken("secret-access-token");

    tabA.publishSessionEvent("LOGOUT");

    expect(receivedByB).toHaveBeenCalledExactlyOnceWith("LOGOUT");
    expect(receivedByA).not.toHaveBeenCalled();
    expect(FakeBroadcastChannel.channels.map((channel) => channel.name))
      .toEqual([SESSION_CHANNEL_NAME, SESSION_CHANNEL_NAME]);
    const [message] = FakeBroadcastChannel.posted as Array<Record<string, unknown>>;
    expect(Object.keys(message).sort()).toEqual(["sender", "type"]);
    expect(JSON.stringify(FakeBroadcastChannel.posted))
      .not.toMatch(/secret-access-token|token|cookie|password|email|Bearer/i);
    closeA();
    closeB();
  });

  it("ignores its own messages and malformed or unknown messages", async () => {
    vi.stubGlobal("BroadcastChannel", FakeBroadcastChannel);
    const tab = await openTab();
    const received = vi.fn();
    const close = tab.openSessionChannel(received);
    const ownSender = await (async () => {
      tab.publishSessionEvent("ACCOUNT_CHANGED");
      return (FakeBroadcastChannel.posted[0] as { sender: string }).sender;
    })();

    for (const data of [
      { type: "ACCOUNT_CHANGED", sender: ownSender },
      { type: "STEAL_TOKEN", sender: "other" },
      { type: "LOGOUT" },
      "LOGOUT",
      null,
    ]) {
      FakeBroadcastChannel.deliverFromAnotherTab(data);
    }

    expect(received).not.toHaveBeenCalled();
    FakeBroadcastChannel.deliverFromAnotherTab({ type: "SESSION_TERMINATED", sender: "other-tab" });
    expect(received).toHaveBeenCalledExactlyOnceWith("SESSION_TERMINATED");
    close();
  });

  it("closes the channel on cleanup and stops publishing", async () => {
    vi.stubGlobal("BroadcastChannel", FakeBroadcastChannel);
    const tab = await openTab();
    const received = vi.fn();
    const close = tab.openSessionChannel(received);

    close();
    tab.publishSessionEvent("LOGOUT");
    FakeBroadcastChannel.deliverFromAnotherTab({ type: "LOGOUT", sender: "other-tab" });

    expect(FakeBroadcastChannel.channels[0].closed).toBe(true);
    expect(FakeBroadcastChannel.posted).toEqual([]);
    expect(received).not.toHaveBeenCalled();
  });

  it("keeps the newest channel when an older one closes late (Strict Mode remount)", async () => {
    vi.stubGlobal("BroadcastChannel", FakeBroadcastChannel);
    const tab = await openTab();
    const closeFirst = tab.openSessionChannel(vi.fn());
    const closeSecond = tab.openSessionChannel(vi.fn());

    closeFirst();
    tab.publishSessionEvent("LOGOUT");

    expect(FakeBroadcastChannel.posted).toHaveLength(1);
    closeSecond();
  });

  it("continues safely without BroadcastChannel", async () => {
    vi.stubGlobal("BroadcastChannel", undefined);
    const tab = await openTab();

    const close = tab.openSessionChannel(vi.fn());

    expect(() => tab.publishSessionEvent("LOGOUT")).not.toThrow();
    expect(() => close()).not.toThrow();
  });
});
