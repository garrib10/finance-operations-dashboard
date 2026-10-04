import { CircleAlert, Info, TriangleAlert, X } from "lucide-react";
import type { ReactNode } from "react";

export type InlineNoticeVariant = "info" | "warning" | "error";

const VARIANTS = {
  // Errors need attention now; warnings and notes are polite updates.
  error: { label: "Error", role: "alert", Icon: CircleAlert },
  warning: { label: "Warning", role: "status", Icon: TriangleAlert },
  info: { label: "Note", role: "status", Icon: Info },
} as const;

interface InlineNoticeProps {
  variant: InlineNoticeVariant;
  children: ReactNode;
  /** A recovery action such as "Try again". */
  action?: { label: string; onClick: () => void };
  /** Shows a dismiss button; the notice never closes by itself. */
  onDismiss?: () => void;
  /** Lets the page move focus here (for example after a failure with no field to fix). */
  id?: string;
  /**
   * False renders static content with no live-region role, for advice that is simply part
   * of the page (such as a card's warning) and should not be announced as an update.
   */
  live?: boolean;
}

/**
 * A persistent message in the page flow, for anything that needs the user's attention:
 * errors, warnings, and notes. Severity is shown by a visible label and an icon, never by
 * colour alone; the icon is decorative. Success confirmations use StatusBanner instead,
 * which floats and closes itself.
 */
export function InlineNotice({ variant, children, action, onDismiss, id, live = true }: InlineNoticeProps) {
  const { label, role, Icon } = VARIANTS[variant];

  return (
    <div id={id} tabIndex={id ? -1 : undefined} className={`inline-notice inline-notice--${variant}`} role={live ? role : undefined}>
      <Icon className="inline-notice__icon" aria-hidden="true" focusable="false" size={20} />
      <p className="inline-notice__message">
        <strong className="inline-notice__label">{label}:</strong> {children}
      </p>
      {(action || onDismiss) && (
        <div className="inline-notice__actions">
          {action && (
            <button type="button" className="button button--secondary button--small" onClick={action.onClick}>
              {action.label}
            </button>
          )}
          {onDismiss && (
            <button
              type="button"
              className="inline-notice__dismiss"
              aria-label={`Dismiss ${label.toLowerCase()}`}
              onClick={onDismiss}
            >
              <X aria-hidden="true" size={18} />
            </button>
          )}
        </div>
      )}
    </div>
  );
}
