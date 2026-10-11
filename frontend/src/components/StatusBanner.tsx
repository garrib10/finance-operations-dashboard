import { useEffect, useEffectEvent, useState } from "react";
import { createPortal } from "react-dom";
import { Check, X } from "lucide-react";

export const STATUS_BANNER_DURATION_MS = 3000;

const STACK_ID = "status-banner-stack";

/** One fixed stack at the top of the window, so banners from different parts of a page never overlap. */
function getBannerStack(): HTMLElement {
  let stack = document.getElementById(STACK_ID);

  if (!stack) {
    stack = document.createElement("div");
    stack.id = STACK_ID;
    stack.className = "status-banner-stack";
    document.body.append(stack);
  }

  return stack;
}

interface StatusBannerProps {
  /** The confirmation to show; an empty string shows nothing. */
  message: string;
  onDismiss: () => void;
  durationMs?: number;
}

/**
 * A success confirmation pinned to the top of the window that dismisses itself.
 * The live region is always rendered so screen readers announce each new message.
 * The timer pauses while the banner is hovered or focused, and it can be closed early.
 * Use it only for confirmations: errors and warnings must stay until the user acts.
 */
export function StatusBanner({
  message,
  onDismiss,
  durationMs = STATUS_BANNER_DURATION_MS,
}: StatusBannerProps) {
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const [stack] = useState(getBannerStack);
  const paused = hovered || focused;
  const dismiss = useEffectEvent(onDismiss);

  useEffect(() => {
    if (!message || paused) return;

    const timer = window.setTimeout(() => dismiss(), durationMs);
    return () => window.clearTimeout(timer);
  }, [message, paused, durationMs]);

  return createPortal(
    <div
      className={message ? "status-banner status-banner--visible" : "status-banner"}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onFocus={() => setFocused(true)}
      onBlur={() => setFocused(false)}
    >
      {message && (
        <span className="status-banner__icon" aria-hidden="true">
          <Check size={14} strokeWidth={3} />
        </span>
      )}

      <p role="status" className="status-banner__message">
        {message}
      </p>

      {message && (
        <button
          type="button"
          className="status-banner__dismiss"
          aria-label="Dismiss message"
          onClick={() => {
            setFocused(false);
            setHovered(false);
            onDismiss();
          }}
        >
          <X aria-hidden="true" size={18} />
        </button>
      )}
    </div>,
    stack,
  );
}
