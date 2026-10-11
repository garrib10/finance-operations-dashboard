import { describe, expect, it } from "vitest";
import {
  APPROVED_ICON_KEYS,
  DEFAULT_ICON_KEY,
  iconComponent,
  iconLabel,
  isApprovedIconKey,
  resolveIconKey,
} from "./categoryIconRegistry";

/** Mirror of CategoryIcon.java; the backend validates the same exact keys. */
const BACKEND_CATALOG = [
  "house", "shopping-cart", "utensils", "car", "lightbulb", "shield", "heart-pulse",
  "clapperboard", "shopping-bag", "plane", "circle-dollar-sign", "piggy-bank", "tag",
  "paw-print", "gift", "dumbbell", "graduation-cap", "baby", "wrench", "smartphone", "tv",
  "music", "coffee", "wine", "fuel", "bus", "shirt", "sparkles", "pill", "briefcase",
  "credit-card", "receipt", "hand-heart", "sofa", "sprout", "gamepad-2", "ticket",
  "package", "wallet",
];

describe("categoryIconRegistry", () => {
  it("covers exactly the backend's approved icon catalog", () => {
    expect([...APPROVED_ICON_KEYS].sort()).toEqual([...BACKEND_CATALOG].sort());
    expect(DEFAULT_ICON_KEY).toBe("tag");
  });

  it("gives every approved key a component and a text name", () => {
    for (const key of APPROVED_ICON_KEYS) {
      expect(iconComponent(key)).toBeTruthy();
      expect(iconLabel(key)).toMatch(/^[A-Z][a-z ]+$/);
    }
    expect(iconLabel("paw-print")).toBe("Paw print");
  });

  it.each([
    undefined, null, "", "Tag", "unknown", "<svg onload=alert(1)>", "https://example.com/i.svg",
    "constructor", "__proto__", "toString", "hasOwnProperty", 42,
  ])("treats %s as the generic tag icon", (value) => {
    expect(isApprovedIconKey(value)).toBe(false);
    expect(resolveIconKey(value)).toBe("tag");
    expect(iconComponent(value)).toBe(iconComponent("tag"));
  });

  it("keeps approved keys unchanged", () => {
    expect(resolveIconKey("gamepad-2")).toBe("gamepad-2");
    expect(isApprovedIconKey("heart-pulse")).toBe(true);
  });
});
