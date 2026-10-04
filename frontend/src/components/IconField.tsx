import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";

interface IconFieldProps {
  icon: LucideIcon;
  /** Defaults to the primary colour; type icons pass their own green or red class. */
  iconClassName?: string;
  /** False hides the icon (and its space), e.g. until the user starts typing. */
  showIcon?: boolean;
  children: ReactNode;
}

/**
 * A form control with a decorative icon inside it, on the left. The control's visible label carries
 * the meaning, so the icon is hidden from assistive technology.
 */
export function IconField({ icon: Icon, iconClassName = "field-icon", showIcon = true, children }: IconFieldProps) {
  return (
    // A span, so it can also sit inside a <label> that wraps its control.
    <span className={showIcon ? "icon-field" : "icon-field icon-field--empty"}>
      {showIcon && <Icon className={iconClassName} aria-hidden="true" focusable="false" size={18} />}
      {children}
    </span>
  );
}

/** A decorative icon before visible text, for table cells such as a transaction's date. */
export function IconLabel({ icon: Icon, children }: { icon: LucideIcon; children: ReactNode }) {
  return (
    <span className="icon-label">
      <Icon className="field-icon" aria-hidden="true" focusable="false" size={18} />
      <span>{children}</span>
    </span>
  );
}
