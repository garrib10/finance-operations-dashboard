import type { Ref } from "react";
import { CategoryIcon } from "./CategoryIcon";
import { APPROVED_ICON_KEYS, iconLabel, type ApprovedIconKey } from "./categoryIconRegistry";

interface CategoryIconPickerProps {
  /** Radio group name; unique per form. */
  name: string;
  labelId: string;
  label?: string;
  value: ApprovedIconKey;
  onChange: (iconKey: ApprovedIconKey) => void;
  error?: string;
  errorId: string;
  disabled?: boolean;
  groupRef?: Ref<HTMLDivElement>;
}

/**
 * Single choice among approved icons, built from native radios so arrow keys, Tab, and
 * screen readers work as usual. Each choice has a text name; the icon is decorative.
 */
export function CategoryIconPicker({
  name,
  labelId,
  label = "Icon",
  value,
  onChange,
  error,
  errorId,
  disabled = false,
  groupRef,
}: CategoryIconPickerProps) {
  return (
    <div className="icon-picker-field">
      <span id={labelId} className="icon-picker__label">
        {label}
      </span>

      <div
        ref={groupRef}
        role="radiogroup"
        aria-labelledby={labelId}
        aria-describedby={error ? errorId : undefined}
        aria-invalid={error ? true : undefined}
        className="icon-picker"
      >
        {APPROVED_ICON_KEYS.map((key) => (
          <label key={key} className="icon-picker__option" title={iconLabel(key)}>
            <input
              type="radio"
              className="visually-hidden icon-picker__input"
              name={name}
              value={key}
              checked={value === key}
              disabled={disabled}
              onChange={() => onChange(key)}
            />
            <CategoryIcon iconKey={key} />
            <span className="visually-hidden">{iconLabel(key)}</span>
          </label>
        ))}
      </div>

      {error && (
        <p id={errorId} className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}
