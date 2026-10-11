import { createElement } from "react";
import { iconComponent } from "./categoryIconRegistry";

interface CategoryIconProps {
  iconKey: string | null | undefined;
  className?: string;
}

/**
 * A decorative category icon. Always placed next to the visible category name, so it is
 * hidden from assistive technology; the name carries the meaning.
 */
export function CategoryIcon({ iconKey, className = "category-icon" }: CategoryIconProps) {
  // The component always comes from the fixed registry, never from the server string.
  return createElement(iconComponent(iconKey), {
    className,
    "aria-hidden": "true",
    focusable: "false",
    size: 18,
  });
}

interface CategoryLabelProps {
  name: string;
  iconKey: string | null | undefined;
}

/** Icon plus the visible category name; long names wrap. */
export function CategoryLabel({ name, iconKey }: CategoryLabelProps) {
  return (
    <span className="category-label">
      <CategoryIcon iconKey={iconKey} />
      <span className="category-label__name">{name}</span>
    </span>
  );
}
