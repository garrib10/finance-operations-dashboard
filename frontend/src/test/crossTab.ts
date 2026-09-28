import { vi } from "vitest";

/**
 * In-memory Web Locks: one exclusive queue per lock name, shared by every "tab" in the
 * test, like the browser's per-origin lock manager.
 */
export function installFakeLocks() {
  const queues = new Map<string, Promise<unknown>>();
  const held: string[] = [];
  const request = vi.fn((name: string, _options: LockOptions, callback: () => Promise<unknown>) => {
    const previous = queues.get(name) ?? Promise.resolve();
    const run = previous.then(async () => {
      held.push(name);
      try {
        return await callback();
      } finally {
        held.splice(held.indexOf(name), 1);
      }
    });
    queues.set(name, run.catch(() => undefined));
    return run;
  });
  Object.defineProperty(navigator, "locks", { value: { request, query: vi.fn() }, configurable: true });
  return { request, held };
}

export function removeFakeLocks(): void {
  Reflect.deleteProperty(navigator, "locks");
}

/** In-memory BroadcastChannel: delivers to every other open channel with the same name. */
export class FakeBroadcastChannel {
  static channels: FakeBroadcastChannel[] = [];
  static posted: unknown[] = [];

  onmessage: ((event: MessageEvent) => void) | null = null;
  closed = false;
  readonly name: string;

  constructor(name: string) {
    this.name = name;
    FakeBroadcastChannel.channels.push(this);
  }

  postMessage(data: unknown): void {
    FakeBroadcastChannel.posted.push(data);
    for (const channel of FakeBroadcastChannel.channels) {
      if (channel !== this && !channel.closed && channel.name === this.name) {
        channel.onmessage?.({ data } as MessageEvent);
      }
    }
  }

  close(): void {
    this.closed = true;
  }

  static reset(): void {
    FakeBroadcastChannel.channels = [];
    FakeBroadcastChannel.posted = [];
  }

  /** A message as it would arrive from a different tab. */
  static deliverFromAnotherTab(data: unknown): void {
    for (const channel of FakeBroadcastChannel.channels) {
      if (!channel.closed) channel.onmessage?.({ data } as MessageEvent);
    }
  }
}
