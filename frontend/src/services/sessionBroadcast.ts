/**
 * Cross-tab session notifications. Messages carry only an event type and a random
 * per-tab sender ID: never tokens, cookies, passwords, or user data. Each tab keeps
 * its own in-memory access token and restores through the refresh cookie itself.
 */
export type SessionBroadcastType =
  | "LOGOUT"
  | "PASSWORD_CHANGED"
  | "SESSION_TERMINATED"
  | "ACCOUNT_CHANGED";

export const SESSION_CHANNEL_NAME = "fintrack-auth";

const BROADCAST_TYPES: readonly SessionBroadcastType[] = [
  "LOGOUT",
  "PASSWORD_CHANGED",
  "SESSION_TERMINATED",
  "ACCOUNT_CHANGED",
];

const senderId = Math.random().toString(36).slice(2);

let channel: BroadcastChannel | null = null;

interface SessionBroadcastMessage {
  type: SessionBroadcastType;
  sender: string;
}

function isSessionMessage(data: unknown): data is SessionBroadcastMessage {
  if (typeof data !== "object" || data === null) return false;
  const message = data as Record<string, unknown>;
  return typeof message.sender === "string"
    && BROADCAST_TYPES.includes(message.type as SessionBroadcastType);
}

/** Opens the channel for this tab. Without BroadcastChannel it safely does nothing. */
export function openSessionChannel(onEvent: (type: SessionBroadcastType) => void): () => void {
  if (typeof BroadcastChannel === "undefined") {
    return () => {};
  }

  const current = new BroadcastChannel(SESSION_CHANNEL_NAME);
  channel = current;
  current.onmessage = (event: MessageEvent) => {
    if (isSessionMessage(event.data) && event.data.sender !== senderId) {
      onEvent(event.data.type);
    }
  };

  return () => {
    current.close();
    if (channel === current) channel = null;
  };
}

export function publishSessionEvent(type: SessionBroadcastType): void {
  channel?.postMessage({ type, sender: senderId } satisfies SessionBroadcastMessage);
}
